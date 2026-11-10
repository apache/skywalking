/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.trace.analysis.checker;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.library.util.genai.GenAIModelMatcher;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.AliasRegistrar;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The Java native analysis and the TAL native rules run the same segments, each with its own collaborators, and must
 * do the same with every one: the same sources with the same fields in the same order, the same naming calls,
 * sampler, searchable-tag and alias-cache reads, the same log lines and the same failure, and, for a segment that
 * completes, the same evaluation samples; after each segment the endpoint grouping holds the same state.
 */
class NativeTraceComparisonTest {
    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final List<String> SOURCES = List.of("Service", "ServiceInstance", "Endpoint", "ServiceRelation",
        "ServiceInstanceRelation", "EndpointRelation", "ServiceMeta", "EndpointMeta", "DatabaseAccess",
        "DatabaseSlowStatement", "ServiceDatabaseSlowStatement", "CacheAccess", "CacheSlowAccess", "MQAccess",
        "MQEndpointAccess", "Segment", "TagAutocomplete", "GenAIProviderAccess", "GenAIModelAccess",
        "GenAIEvaluationSample");

    /**
     * The debug line {@code UninstrumentedGatewaysConfig} writes for each address it is asked about, up to its
     * arguments.
     */
    private static final String GATEWAY_LINE = "log org.apache.skywalking.oap.server.analyzer.provider.trace."
        + "UninstrumentedGatewaysConfig DEBUG Address [{}] is configured as gateway: {} [";
    /**
     * A gateway line's arguments, the quoted address and the quoted answer.
     */
    private static final Pattern GATEWAY_ARGUMENTS = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\", \"(true|false)\"]");
    private static final Pattern ESCAPE = Pattern.compile("\\\\(.)");

    private static CheckerLogCapture CAPTURE;
    private static final AtomicInteger PROFILES_RUN = new AtomicInteger();

    @BeforeAll
    static void setUp() throws Exception {
        CAPTURE = CheckerLogCapture.install();
        registerScopes();
    }

    /**
     * Register the sources the rules send by their scope names, and load the GenAI model matcher before either side
     * runs, so its one-time load lines belong to neither.
     */
    static void registerScopes() throws Exception {
        final DefaultScopeDefine.Listener scopes = new DefaultScopeDefine.Listener();
        for (final String source : SOURCES) {
            final Class<?> type = Class.forName(SOURCE_PACKAGE + source);
            if (DefaultScopeDefine.classOf(type.getAnnotation(ScopeDeclaration.class).name()) == null) {
                scopes.notify(type);
            }
        }
        GenAIModelMatcher.getInstance();
    }

    @AfterAll
    static void tearDown() throws Exception {
        CAPTURE.uninstall();
        // Only a full run reaches the branches; a run of some profiles would report the rest as missed.
        if (PROFILES_RUN.get() == profiles().count()) {
            final List<String> problems = CheckerListenerCoverage.problems();
            if (!problems.isEmpty()) {
                fail(problems.size() + " branch problem(s) in the moved Java analysis, see "
                    + "target/checker-missed-branches.txt:\n" + String.join("\n", problems));
            }
        }
    }

    static Stream<CheckerProfile> profiles() {
        final CheckerProfile defaults = CheckerProfile.builder().name("defaults").positiveGuard(true).build();
        final String lowRatePolicy = "default:\n  rate: 1\n  duration: -1\nservices:\n  - name: orders\n"
            + "    rate: 5000\n    duration: 100\n";
        return Stream.of(
            defaults,
            defaults.toBuilder().name("trace-analysis-off").traceAnalysis(false).positiveGuard(false).build(),
            defaults.toBuilder().name("low-thresholds").slowDBAccessThreshold("default:0")
                    .slowCacheReadThreshold("default:0").slowCacheWriteThreshold("default:0").maxSlowSQLLength(10)
                    .positiveGuard(false).build(),
            defaults.toBuilder().name("evaluation-active").evaluationLoaded(true).evaluationActive(true).build(),
            defaults.toBuilder().name("evaluation-inactive").evaluationLoaded(true).evaluationActive(false)
                    .positiveGuard(false).build(),
            defaults.toBuilder().name("evaluation-without-gen-ai").genAiLoaded(false).evaluationLoaded(true)
                    .evaluationActive(true).positiveGuard(false).build(),
            defaults.toBuilder().name("no-optional-modules").genAiLoaded(false).evaluationLoaded(false)
                    .positiveGuard(false).build(),
            defaults.toBuilder().name("gateways-and-clusters").positiveGuard(false)
                    .gateways("gateways:\n  - name: proxy0\n    instances:\n      - host: gateway.local\n"
                        + "        port: 8080\n      - host: " + CheckerFixtures.BARE_GATEWAY + "\n"
                        + "      - host: " + CheckerFixtures.LONG_GATEWAY.substring(0,
                            CheckerFixtures.LONG_GATEWAY.lastIndexOf(':')) + "\n        port: 8080\n")
                    .serverClusters("clusters:\n  - name: kafka-prod\n    members:\n      - kafka:9092\n"
                        + "  - name: orders-db\n    members:\n      - mysql:3306\n"
                        + "  - name: cart-cache\n    members:\n      - redis:6379\n")
                    .build(),
            defaults.toBuilder().name("no-upstream-agents-empty").noUpstreamRealAddressAgents("")
                    .positiveGuard(false).build(),
            defaults.toBuilder().name("no-upstream-agents-illegal").noUpstreamRealAddressAgents("6000,abc")
                    .positiveGuard(false).build(),
            defaults.toBuilder().name("short-names").serviceNameMaxLength(12).instanceNameMaxLength(12)
                    .endpointNameMaxLength(14).positiveGuard(false).build(),
            defaults.toBuilder().name("grouping-rules").maxHttpUrisNumberPerService(3).positiveGuard(false)
                    .endpointGroupingRules("grouping:\n  - service-name: catalog\n    rules:\n      - /items/{var}\n"
                        + "  - service-name: orders\n    rules:\n      - /orders/{var}\n")
                    .build(),
            defaults.toBuilder().name("strategy-from-entry-span").segmentStatusAnalysisStrategy("FROM_ENTRY_SPAN")
                    .positiveGuard(false).build(),
            defaults.toBuilder().name("strategy-from-first-span").segmentStatusAnalysisStrategy("FROM_FIRST_SPAN")
                    .positiveGuard(false).build(),
            defaults.toBuilder().name("sampling-low-rate").samplingPolicy(lowRatePolicy).positiveGuard(false).build(),
            defaults.toBuilder().name("sampling-low-rate-no-forced-errors").samplingPolicy(lowRatePolicy)
                    .forceSampleErrorSegment(false).positiveGuard(false).build(),
            defaults.toBuilder().name("searchable-without-recorded-keys").searchableTracesTags("nothing.here")
                    .positiveGuard(false).build(),
            defaults.toBuilder().name("turkish-locale").locale(new Locale("tr", "TR")).positiveGuard(false).build(),
            defaults.toBuilder().name("mid-run-updates").midRunSamplingPolicy("default:\n  rate: 0\n")
                    .midRunSearchableTags("db.type,http.method").positiveGuard(false).build(),
            defaults.toBuilder().name("small-alias-cache").aliasCacheSize(2).positiveGuard(false).build(),
            defaults.toBuilder().name("quiet-logs").logLevel(Level.WARN).samplingPolicy(lowRatePolicy)
                    .positiveGuard(false).build());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void bothSidesDoTheSameWithEverySegment(final CheckerProfile profile) throws Exception {
        final Locale jvmLocale = Locale.getDefault();
        if (profile.getLocale() != null) {
            Locale.setDefault(profile.getLocale());
        }
        if (profile.getLogLevel() != null) {
            CAPTURE.setLevel(profile.getLogLevel());
        }
        try {
            run(profile);
        } finally {
            Locale.setDefault(jvmLocale);
            CAPTURE.setLevel(Level.TRACE);
            PROFILES_RUN.incrementAndGet();
        }
    }

    /**
     * One multi-segment run of every identical-behaviour fixture, a cold pass with empty alias caches and a warm pass
     * whose caches hold the rows the cold pass's alias setups make plus a few odd rows; then each difference fixture
     * alone, on sides of its own, so the change it shows cannot leave the stateful collaborators of the main run apart.
     */
    private static void run(final CheckerProfile profile) throws Exception {
        final CheckerV1ListenerSide v1 = new CheckerV1ListenerSide(profile, CAPTURE);
        final CheckerV2RuleSide v2 = new CheckerV2RuleSide(profile, CAPTURE);
        final List<String> mismatches = new ArrayList<>();
        final StringBuilder dump = new StringBuilder();
        final List<CheckerFixtures.CheckerFixture> fixtures = new ArrayList<>();
        final List<CheckerFixtures.CheckerFixture> differences = new ArrayList<>();
        CheckerFixtures.all().forEach(f -> (f.getDifference() == null ? fixtures : differences).add(f));
        if (profile.isPositiveGuard()) {
            for (final String rule : v2.ruleNames()) {
                if (fixtures.stream().noneMatch(f -> f.getPositiveFor().contains(rule))) {
                    mismatches.add("the compiled rule " + rule + " has no positive fixture");
                }
            }
        }
        int events = 0;
        for (final String pass : List.of("cold", "warm")) {
            if ("warm".equals(pass)) {
                final List<NetworkAddressAlias> rows = aliasRows(v1.aliasSetups());
                v1.loadAliasRows(rows);
                v2.loadAliasRows(rows);
            }
            for (int i = 0; i < fixtures.size(); i++) {
                if (i == fixtures.size() / 2) {
                    v1.applyMidRunUpdates();
                    v2.applyMidRunUpdates();
                    if ("warm".equals(pass)) {
                        // A load after the first half's reads, so a small cache evicts by what those reads touched.
                        final List<NetworkAddressAlias> late = List.of(lateAliasRow());
                        v1.loadAliasRows(late);
                        v2.loadAliasRows(late);
                    }
                }
                final CheckerFixtures.CheckerFixture fixture = fixtures.get(i);
                final String label = fixture + " (" + pass + ")";
                events += compareFixture(label, fixture, null, profile, v1, v2, null, mismatches, dump);
                if (profile.isPositiveGuard()) {
                    for (final String rule : fixture.getPositiveFor()) {
                        if (v2.ruleNames().contains(rule) && !v2.log().owners().contains(rule)
                            && !v2.log().sampleOwners().contains(rule)) {
                            mismatches.add(label + ": its positive rule " + rule + " produced no event");
                        }
                    }
                }
            }
        }
        for (final CheckerFixtures.CheckerFixture fixture : differences) {
            final CheckerV1ListenerSide alone1 = new CheckerV1ListenerSide(profile, CAPTURE);
            final CheckerV2RuleSide alone2 = new CheckerV2RuleSide(profile, CAPTURE);
            final CheckerV1ListenerSide twin = fixture.getTwin() != null && differenceShows(fixture.getDifference(), profile)
                ? new CheckerV1ListenerSide(profile, CAPTURE) : null;
            events += compareFixture(fixture + " (alone)", fixture, fixture.getDifference(), profile, alone1, alone2,
                twin, mismatches, dump);
        }
        // What v1 did, for review.
        Files.createDirectories(Paths.get("target"));
        Files.write(Paths.get("target", "checker-events-" + profile + ".txt"),
            dump.toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(events > 0, "the fixtures produced no event under " + profile);
        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " difference(s) under profile " + profile + ":\n" + String.join("\n", mismatches));
        }
    }

    /**
     * Run one fixture on both sides, and on the twin when the difference it shows has one, and record where they part:
     * the events, the evaluation samples and the endpoint grouping state.
     *
     * @return the number of v1 events
     */
    private static int compareFixture(final String label, final CheckerFixtures.CheckerFixture fixture,
                                      final CheckerDifference difference, final CheckerProfile profile,
                                      final CheckerSide v1, final CheckerSide v2, final CheckerSide twin,
                                      final List<String> mismatches, final StringBuilder dump) throws Exception {
        v1.analyze(fixture.getSegment());
        v2.analyze(fixture.getSegment());
        if (twin != null) {
            twin.analyze(fixture.getTwin());
        }
        final List<String> v2Raw = v2.log().events();
        final List<String> v1Events = withoutEmptyAddressGatewayLine(v1.log().events());
        final List<String> v2Events = withoutBareHostSecondGatewayLine(v2Raw);
        final List<String> twinEvents = twin == null ? null : withoutEmptyAddressGatewayLine(twin.log().events());
        dump.append("== ").append(label).append('\n');
        v1Events.forEach(e -> dump.append("  ").append(e).append('\n'));
        v1.log().samples().forEach(e -> dump.append("  sample ").append(e).append('\n'));
        final boolean shows = difference != null && differenceShows(difference, profile);
        if (shows) {
            checkDifference(label, fixture, v1Events, v2Events, v2Raw, twinEvents, mismatches);
        } else {
            compare(mismatches, label + " events", v1Events, v2Events);
        }

        // The reference for the rest: v1 itself, or v1 on the twin, mapped, or v1 with the repeated keys' first values.
        final Function<List<String>, List<String>> reference;
        final CheckerSide referenceSide;
        if (twin != null) {
            reference = fixture::mapTwin;
            referenceSide = twin;
        } else if (shows && difference == CheckerDifference.REPEATED_TAG_FIRST_VALUE) {
            reference = list -> applyMapping(firstValueMapping(fixture), list);
            referenceSide = v1;
        } else {
            reference = Function.identity();
            referenceSide = v1;
        }
        if (failed(v2Events)) {
            if (!v2.log().samples().isEmpty()) {
                mismatches.add(label + ": a failed segment took evaluation samples " + v2.log().samples());
            }
        } else if (twin != null || !failed(v1Events)) {
            compare(mismatches, label + " samples", reference.apply(referenceSide.log().samples()), v2.log().samples());
        }
        final String referenceGrouping = reference.apply(List.of(referenceSide.groupingState())).get(0);
        final String v2Grouping = v2.groupingState();
        if (!referenceGrouping.equals(v2Grouping)) {
            mismatches.add(label + ": endpoint grouping differs\n  v1 " + referenceGrouping + "\n  v2 " + v2Grouping);
        }
        return v1Events.size();
    }

    /**
     * @return the replacements that turn v1's tag maps, which kept each repeated key's last value, into v2's, which keep
     * its first
     */
    static List<String[]> firstValueMapping(final CheckerFixtures.CheckerFixture fixture) {
        final List<String[]> mapping = new ArrayList<>();
        for (final SpanObject span : fixture.getSegment().getSpansList()) {
            final Map<String, String> first = new LinkedHashMap<>();
            final Map<String, String> last = new LinkedHashMap<>();
            span.getTagsList().forEach(tag -> {
                first.putIfAbsent(tag.getKey(), tag.getValue());
                last.put(tag.getKey(), tag.getValue());
            });
            for (final Map.Entry<String, String> key : last.entrySet()) {
                if (key.getValue().equals(first.get(key.getKey()))) {
                    continue;
                }
                final String kept = CheckerObjectRenderer.render(Map.of(key.getKey(), key.getValue()));
                final String firstValue = CheckerObjectRenderer.render(Map.of(key.getKey(), first.get(key.getKey())));
                mapping.add(new String[] {kept.substring(1, kept.length() - 1),
                    firstValue.substring(1, firstValue.length() - 1)});
            }
        }
        return mapping;
    }

    static List<String> applyMapping(final List<String[]> mapping, final List<String> list) {
        final List<String> mapped = new ArrayList<>(list.size());
        for (final String item : list) {
            String e = item;
            for (final String[] replacement : mapping) {
                e = e.replace(replacement[0], replacement[1]);
            }
            mapped.add(e);
        }
        return mapped;
    }

    private static boolean failed(final List<String> events) {
        return !events.isEmpty() && withoutAnnotation(events.get(events.size() - 1)).startsWith("exception ");
    }

    /**
     * @return whether the profile lets the documented difference show: the repeated tag key needs the RPC rules, the
     * bare host a gateway listed for it
     */
    static boolean differenceShows(final CheckerDifference difference, final CheckerProfile profile) {
        switch (difference) {
            case REPEATED_TAG_FIRST_VALUE:
                return profile.isTraceAnalysis();
            case BARE_HOST_GATEWAY:
                return profile.getGateways() != null
                    && profile.getGateways().contains("host: " + CheckerFixtures.BARE_GATEWAY + "\n");
            default:
                return true;
        }
    }

    /**
     * Check that the two sides differ exactly as the documented difference says, and in nothing else that can be told
     * apart.
     */
    static void checkDifference(final String label, final CheckerFixtures.CheckerFixture fixture,
                                final List<String> v1, final List<String> v2, final List<String> v2Raw,
                                final List<String> twinEvents, final List<String> mismatches) {
        if (fixture.getTwin() != null) {
            if (twinEvents == null) {
                mismatches.add(label + ": the twin was not run");
            } else {
                compare(mismatches, label + " events against v1 on the twin", fixture.mapTwin(twinEvents), v2);
            }
        }
        switch (fixture.getDifference()) {
            case REPEATED_TAG_FIRST_VALUE: {
                final List<String[]> mapping = firstValueMapping(fixture);
                if (mapping.isEmpty() || v1.stream().noneMatch(e -> mapping.stream().anyMatch(m -> e.contains(m[0])))) {
                    mismatches.add(label + ": v1 kept no last value of a repeated key, so the difference did not show");
                }
                compare(mismatches, label + " events, the repeated keys' first values on v2", applyMapping(mapping, v1),
                    v2);
                break;
            }
            case REPEATED_CACHE_TAG_ANALYZED: {
                if (!failed(v1) || !withoutAnnotation(v1.get(v1.size() - 1))
                    .startsWith("exception java.lang.IllegalStateException: \"Duplicate key cache.key")) {
                    mismatches.add(label + ": v1 did not fail on the repeated cache key: "
                        + (v1.isEmpty() ? "no event" : v1.get(v1.size() - 1)));
                    break;
                }
                if (failed(v2)) {
                    mismatches.add(label + ": v2 failed too: " + v2.get(v2.size() - 1));
                }
                final List<String> beforeFailure = v1.subList(0, v1.size() - 1);
                compare(mismatches, label + " events before v1's failure",
                    beforeFailure, v2.subList(0, Math.min(beforeFailure.size(), v2.size())));
                break;
            }
            case BARE_HOST_GATEWAY: {
                final String address = "address=\"" + CheckerFixtures.BARE_GATEWAY + "\",";
                final Predicate<String> alias = e -> withoutAnnotation(e).startsWith("source NetworkAddressAliasSetup{")
                    && e.contains(address);
                if (v1.stream().noneMatch(alias)) {
                    mismatches.add(label + ": v1 wrote no alias row for the bare host, so the difference did not show");
                }
                if (v2.stream().anyMatch(alias)) {
                    mismatches.add(label + ": v2 wrote an alias row for a bare host its gateway list matches at port 80");
                }
                final String fallback = GATEWAY_LINE + CheckerLogCapture.arguments(
                    new Object[] {CheckerFixtures.BARE_GATEWAY + ":80", true}) + "]";
                if (v2Raw.stream().noneMatch(e -> withoutAnnotation(e).equals(fallback))) {
                    mismatches.add(label + ": v2 did not find the bare host's gateway at port 80");
                }
                break;
            }
            default:
                throw new IllegalArgumentException("no check for " + fixture.getDifference());
        }
    }

    /**
     * The alias rows a cache reload would load after the cold pass: one per alias setup, as the alias dispatcher makes
     * it, and the odd rows a real cache can hold, a row without an instance, one with a malformed service id, and one
     * naming a virtual service.
     */
    static List<NetworkAddressAlias> aliasRows(final List<NetworkAddressAliasSetup> setups) {
        final List<NetworkAddressAlias> rows = new ArrayList<>();
        for (final NetworkAddressAliasSetup setup : setups) {
            // Captured at receive, before the dispatcher's prepare(), which builds the ids; the events were rendered then.
            setup.prepare();
            rows.add(aliasRow(setup.getAddress(), setup.getRepresentServiceId(), setup.getRepresentServiceInstanceId(),
                setup.getTimeBucket()));
        }
        final String noInstanceService = IDManager.ServiceID.buildId("noinst-svc", true);
        rows.add(aliasRow(CheckerFixtures.NO_INSTANCE_ALIAS, noInstanceService,
            IDManager.ServiceInstanceID.buildId(noInstanceService, ""), 202311150613L));
        rows.add(aliasRow(CheckerFixtures.MALFORMED_ALIAS, "malformed-service-id", "malformed-instance-id",
            202311150613L));
        final String virtualService = IDManager.ServiceID.buildId("virtual-svc", false);
        rows.add(aliasRow(CheckerFixtures.VIRTUAL_ALIAS, virtualService,
            IDManager.ServiceInstanceID.buildId(virtualService, "virtual-inst"), 202311150613L));
        return rows;
    }

    static NetworkAddressAlias lateAliasRow() {
        final String service = IDManager.ServiceID.buildId("late-svc", true);
        return aliasRow(CheckerFixtures.LATE_ALIAS, service, IDManager.ServiceInstanceID.buildId(service, "late-1"),
            202311150613L);
    }

    /**
     * @return the row the alias dispatcher makes: the ids as the setup's {@code prepare()} builds them, the setup's time
     * bucket, and the refresh stamp from the OAP clock
     */
    private static NetworkAddressAlias aliasRow(final String address, final String serviceId, final String instanceId,
                                                final long timeBucket) {
        final NetworkAddressAlias row = new NetworkAddressAlias();
        row.setAddress(address);
        row.setRepresentServiceId(serviceId);
        row.setRepresentServiceInstanceId(instanceId);
        row.setTimeBucket(timeBucket);
        row.setLastUpdateTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        return row;
    }

    @Test
    void theComparisonSeesEveryKindOfDefect() {
        final List<String> log = List.of(
            "source Service{name=\"a\", layer=Layer.GENERAL}",
            "naming formatServiceName(\"a\") -> \"a\"",
            "log rpc DEBUG parse [x]",
            "source Endpoint{name=null}");
        final Map<String, List<String>> defects = new LinkedHashMap<>();
        defects.put("a changed field", replace(log, 0, "source Service{name=\"b\", layer=Layer.GENERAL}"));
        defects.put("a dropped event", remove(log, 1));
        final List<String> duplicated = new ArrayList<>(log);
        duplicated.add(1, log.get(1));
        defects.put("a duplicated event", duplicated);
        final List<String> swapped = new ArrayList<>(log);
        Collections.swap(swapped, 0, 1);
        defects.put("two events swapped", swapped);
        defects.put("null turned into empty", replace(log, 3, "source Endpoint{name=\"\"}"));
        defects.put("a missing log line", remove(log, 2));
        for (final Map.Entry<String, List<String>> defect : defects.entrySet()) {
            final List<String> mismatches = new ArrayList<>();
            compare(mismatches, defect.getKey(), log, defect.getValue());
            assertEquals(1, mismatches.size(), defect.getKey() + " went unnoticed");
        }
        final List<String> none = new ArrayList<>();
        compare(none, "the same log", log, new ArrayList<>(log));
        assertTrue(none.isEmpty());
    }

    @Test
    void theDifferenceChecksRejectAnUnchangedSide() {
        final Map<CheckerDifference, CheckerFixtures.CheckerFixture> byDifference = new LinkedHashMap<>();
        CheckerFixtures.all().stream().filter(f -> f.getDifference() != null)
                       .forEach(f -> byDifference.putIfAbsent(f.getDifference(), f));
        final Map<CheckerDifference, List<String>> v1 = new LinkedHashMap<>();
        v1.put(CheckerDifference.REPEATED_TAG_FIRST_VALUE,
            List.of("[build rpc] source Endpoint{originalTags={\"region\"=\"second\"}}"));
        v1.put(CheckerDifference.REPEATED_CACHE_TAG_ANALYZED, List.of("[span virtual-service] exception "
            + "java.lang.IllegalStateException: \"Duplicate key cache.key (attempted merging values k1 and k2)\""));
        v1.put(CheckerDifference.BARE_HOST_GATEWAY, List.of("[span alias] source NetworkAddressAliasSetup{timeBucket=1, "
            + "address=\"" + CheckerFixtures.BARE_GATEWAY + "\", isRepresentServiceNormal=true}"));
        assertEquals(EnumSet.allOf(CheckerDifference.class), v1.keySet());
        for (final Map.Entry<CheckerDifference, List<String>> unchanged : v1.entrySet()) {
            final List<String> mismatches = new ArrayList<>();
            checkDifference("self-test", byDifference.get(unchanged.getKey()), unchanged.getValue(),
                unchanged.getValue(), unchanged.getValue(), unchanged.getValue(), mismatches);
            assertTrue(!mismatches.isEmpty(), unchanged.getKey() + " accepted a v2 that behaves as v1");
        }
    }

    @Test
    void theRecordersKeepDifferentValuesApart() {
        final Map<String, String> oneTag = new LinkedHashMap<>();
        oneTag.put("a", "x\", b=\"y");
        final Map<String, String> twoTags = new LinkedHashMap<>();
        twoTags.put("a", "x");
        twoTags.put("b", "y");
        assertNotEquals(CheckerObjectRenderer.render(oneTag), CheckerObjectRenderer.render(twoTags));
        assertNotEquals(CheckerObjectRenderer.render(List.of("a\nb")), CheckerObjectRenderer.render(List.of("a\\nb")));
        assertNotEquals(CheckerObjectRenderer.render(Collections.singletonList(null)),
            CheckerObjectRenderer.render(List.of("null")));
        assertNotEquals(CheckerObjectRenderer.render(List.of("")), CheckerObjectRenderer.render(List.of()));
        assertNotEquals(CheckerLogCapture.arguments(new Object[] {null}), CheckerLogCapture.arguments(new Object[] {"null"}));
        assertNotEquals(CheckerLogCapture.arguments(new Object[] {"a, b"}), CheckerLogCapture.arguments(new Object[] {"a", "b"}));
        assertNotEquals(CheckerObjectRenderer.render(new TreeSet<>(Set.of(""))), CheckerObjectRenderer.render(new TreeSet<>()));
    }

    @Test
    void theCaptureSeesTraceLinesUnderAConfiguredDescendant() {
        final String descendant = "org.apache.skywalking.oap.server.core";
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration configuration = context.getConfiguration();
        CAPTURE.uninstall();
        final LoggerConfig original = configuration.getLoggers().get(descendant);
        if (original != null) {
            configuration.removeLogger(descendant);
        }
        configuration.addLogger(descendant, new LoggerConfig(descendant, Level.DEBUG, false));
        context.updateLoggers();
        try {
            CAPTURE = CheckerLogCapture.install();
            final CheckerEventLog log = new CheckerEventLog();
            CAPTURE.recordInto(log);
            log.start();
            LogManager.getLogger(descendant + ".CheckerProbe").trace("probe {}", 1);
            log.stop();
            CAPTURE.recordInto(null);
            assertEquals(List.of("[- -] log " + descendant + ".CheckerProbe TRACE probe {} [\"1\"]"), log.events());
        } finally {
            CAPTURE.uninstall();
            configuration.removeLogger(descendant);
            if (original != null) {
                configuration.addLogger(descendant, original);
            }
            context.updateLoggers();
            CAPTURE = CheckerLogCapture.install();
        }
    }

    @Test
    void theGatewayFiltersDropOnlyTheDocumentedLines() throws Exception {
        final String empty = gatewayLine("", false);
        final List<String> v1 = List.of(empty, gatewayLine("", true), gatewayLine("proxy [", false), gatewayLine(" ", false));
        assertEquals(v1.subList(1, 4), withoutEmptyAddressGatewayLine(v1));

        final List<String> repeated = List.of(gatewayLine("host", false), gatewayLine("host:80", false),
            gatewayLine("host:80", false));
        assertEquals(List.of(repeated.get(0), repeated.get(2)), withoutBareHostSecondGatewayLine(repeated));

        final List<String> answeredTrue = List.of(gatewayLine("host", true), gatewayLine("host:80", false));
        assertEquals(answeredTrue, withoutBareHostSecondGatewayLine(answeredTrue));

        final List<String> withPort = List.of(gatewayLine("host:8080", false), gatewayLine("host:8080:80", false));
        assertEquals(withPort, withoutBareHostSecondGatewayLine(withPort));

        final List<String> apart = List.of(gatewayLine("host", false), "log rpc DEBUG x []", gatewayLine("host:80", false));
        assertEquals(apart, withoutBareHostSecondGatewayLine(apart));

        final List<String> otherHost = List.of(gatewayLine("host", false), gatewayLine("other:80", false));
        assertEquals(otherHost, withoutBareHostSecondGatewayLine(otherHost));

        final List<String> otherPlace = List.of(gatewayLine("host", false),
            gatewayLine("host:80", false).replace("[span rpc] ", "[span alias] "));
        assertEquals(otherPlace, withoutBareHostSecondGatewayLine(otherPlace));

        final List<String> padded = List.of(gatewayLine(" host ", false), gatewayLine("host:80", true));
        assertEquals(padded.subList(0, 1), withoutBareHostSecondGatewayLine(padded));

        final List<String> quoted = List.of(gatewayLine("h\"o", false), gatewayLine("h\"o:80", false));
        assertEquals(quoted.subList(0, 1), withoutBareHostSecondGatewayLine(quoted));

        final List<String> chained = List.of(gatewayLine("::1", false), gatewayLine("::1:80", false),
            gatewayLine("::1:80:80", false));
        assertEquals(List.of(chained.get(0), chained.get(2)), withoutBareHostSecondGatewayLine(chained));

        final List<String> twoChecks = List.of(gatewayLine("::1", false), gatewayLine("::1:80", false),
            gatewayLine("::1:80", false), gatewayLine("::1:80:80", false));
        assertEquals(List.of(twoChecks.get(0), twoChecks.get(2)), withoutBareHostSecondGatewayLine(twoChecks));
    }

    private static String gatewayLine(final String address, final boolean gateway) {
        return "[span rpc] " + GATEWAY_LINE + CheckerLogCapture.arguments(new Object[] {address, gateway}) + "]";
    }

    private static List<String> replace(final List<String> log, final int index, final String event) {
        final List<String> changed = new ArrayList<>(log);
        changed.set(index, event);
        return changed;
    }

    private static List<String> remove(final List<String> log, final int index) {
        final List<String> changed = new ArrayList<>(log);
        changed.remove(index);
        return changed;
    }

    /**
     * The gateway check's documented log difference: the listeners ask the gateway configuration about an empty
     * address, and log the answer, false, where the shared {@code isGateway} answers false without asking.
     */
    static List<String> withoutEmptyAddressGatewayLine(final List<String> v1Events) {
        final String emptyAddressLine = GATEWAY_LINE + "\"\", \"false\"]";
        final List<String> kept = new ArrayList<>(v1Events.size());
        for (final String event : v1Events) {
            if (!withoutAnnotation(event).equals(emptyAddressLine)) {
                kept.add(event);
            }
        }
        return kept;
    }

    /**
     * The gateway check's other documented log difference: when the shared {@code isGateway} is told a bare host is
     * not a gateway, it asks once more about the host at port 80, and the second answer logs a second line, right
     * after the first. Only that line goes, once per such answer: a line taken as a fallback is never the first line
     * of another pair.
     */
    static List<String> withoutBareHostSecondGatewayLine(final List<String> v2Events) throws Exception {
        final List<String> kept = new ArrayList<>(v2Events.size());
        boolean previousWasFallback = false;
        for (int i = 0; i < v2Events.size(); i++) {
            if (i > 0 && !previousWasFallback && isBareHostFallback(v2Events.get(i - 1), v2Events.get(i))) {
                previousWasFallback = true;
                continue;
            }
            previousWasFallback = false;
            kept.add(v2Events.get(i));
        }
        return kept;
    }

    private static boolean isBareHostFallback(final String previous, final String event) throws Exception {
        final String[] asked = gatewayArguments(previous);
        final String[] fallback = gatewayArguments(event);
        if (asked == null || fallback == null || !"false".equals(asked[1])) {
            return false;
        }
        final String address = asked[0];
        return annotationOf(previous).equals(annotationOf(event))
            && !address.isEmpty() && hostOf(address).equals(address.trim())
            && fallback[0].equals(address.trim() + ":80");
    }

    /**
     * @return the event without its {@code [phase group] } annotation
     */
    static String withoutAnnotation(final String event) {
        return event.substring(annotationOf(event).length());
    }

    private static String annotationOf(final String event) {
        if (!event.startsWith("[")) {
            return "";
        }
        final int end = event.indexOf("] ");
        return end < 0 ? "" : event.substring(0, end + 2);
    }

    /**
     * @return the address and the answer of a gateway line, or null for any other event
     */
    private static String[] gatewayArguments(final String annotatedEvent) {
        final String event = withoutAnnotation(annotatedEvent);
        if (!event.startsWith(GATEWAY_LINE)) {
            return null;
        }
        final Matcher matcher = GATEWAY_ARGUMENTS.matcher(event.substring(GATEWAY_LINE.length()));
        if (!matcher.matches()) {
            return null;
        }
        return new String[] {ESCAPE.matcher(matcher.group(1)).replaceAll(m -> {
            final String escaped = m.group(1);
            return Matcher.quoteReplacement("n".equals(escaped) ? "\n" : "r".equals(escaped) ? "\r" : escaped);
        }), matcher.group(2)};
    }

    /**
     * The host part as {@code isGateway} takes it, read from the real method.
     */
    private static String hostOf(final String address) throws Exception {
        final Method hostOf = AliasRegistrar.class.getDeclaredMethod("hostOf", String.class);
        hostOf.setAccessible(true);
        return (String) hostOf.invoke(null, address);
    }

    /**
     * Records the first event where the two logs part, with the events around it.
     */
    static void compare(final List<String> mismatches, final String what,
                        final List<String> v1, final List<String> v2) {
        if (v1.equals(v2)) {
            return;
        }
        int first = 0;
        while (first < v1.size() && first < v2.size() && v1.get(first).equals(v2.get(first))) {
            first++;
        }
        final StringBuilder sb = new StringBuilder(what).append(": the logs part at event ").append(first)
            .append(" (v1 has ").append(v1.size()).append(", v2 has ").append(v2.size()).append(")");
        for (int i = Math.max(0, first - 2); i < Math.min(Math.max(v1.size(), v2.size()), first + 3); i++) {
            sb.append("\n  ").append(i).append(" v1 ").append(i < v1.size() ? v1.get(i) : "<none>");
            sb.append("\n  ").append(i).append(" v2 ").append(i < v2.size() ? v2.get(i) : "<none>");
        }
        mismatches.add(sb.toString());
    }
}
