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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads natively stored OTLP spans the way the TraceQL API does on the JDBC storages, through
 * {@link JDBCOTLPTraceQueryDAO}: the trace search, the read of one trace by id, and the catalogs of services, span
 * names and peer services. A subclass names the server.
 *
 * <p>The search range is a minute either side of midnight, so it reads two day tables, and an attribute condition
 * joins the spans' tag table of each. Each search condition is tried alone, in both orders, and with all the others.
 * For every condition a near-miss trace matches all the others but that one, the two matching traces lie on the bounds
 * of the range (a span is kept by its start millisecond, the range's end counted) and of the span duration, and a
 * search asks for exactly as many traces as should match, so a near miss that leaks in pushes a match out. A search
 * ranks a trace by its earliest matching span, or by its longest, and returns it whole: the first matching trace has a
 * span of another service after midnight, the last one a root span of another service before the range.
 *
 * <p>The DAO serves {@code data_binary} untouched, so each span stores a label there instead of a
 * {@code ResourceSpans}. The catalogs keep each name once, in the minute it was first seen, on either side of
 * midnight, so in two day tables; a service name that arrived with two layers is two rows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCOTLPTraceQueryITBase {
    /**
     * A minute either side of midnight, read by the second: its end timestamp, which the search counts, is the first
     * millisecond after its last second.
     */
    private static final Duration AROUND_MIDNIGHT = duration(MIDNIGHT - 60_000L, MIDNIGHT + 59_000L);
    private static final long START = AROUND_MIDNIGHT.getStartTimestamp();
    private static final long END = AROUND_MIDNIGHT.getEndTimestamp();

    private static final String CHECKOUT = "checkout";
    private static final String CANARY = "checkout-canary";
    private static final String INVENTORY = "inventory";
    private static final String FRONTEND = "frontend";
    private static final String CHECKOUT_1 = "checkout-1";
    private static final String OKHTTP = "io.opentelemetry.okhttp-3.0";
    private static final String POST_STOCK = "POST /stock";
    private static final String PEER = INVENTORY;
    private static final int INTERNAL = 1;
    private static final int SERVER = 2;
    private static final int CLIENT = 3;
    private static final int STATUS_UNSET = 0;
    private static final int STATUS_OK = 1;
    private static final int STATUS_ERROR = 2;
    private static final long MIN_DURATION_NS = 1_000_000L;
    private static final long MAX_DURATION_NS = 9_000_000L;
    private static final String ENVIRONMENT = "deployment.environment";
    private static final String TENANT = "tenant";

    /** Found by its root span on the range's start; its child, after midnight, is the inventory's. */
    private static final String FIRST_MATCH = "4bf92f3577b34da6a3ce929d0e0e0a01";
    /** Found by a span on the range's end, whose root, before the range, is the frontend's; one digit off the first. */
    private static final String LAST_MATCH = "4bf92f3577b34da6a3ce929d0e0e0a02";
    private static final String OTHER_SERVICE = "4bf92f3577b34da6a3ce929d0e0e0b01";
    private static final String OTHER_INSTANCE = "4bf92f3577b34da6a3ce929d0e0e0b02";
    private static final String OTHER_SCOPE = "4bf92f3577b34da6a3ce929d0e0e0b03";
    private static final String OTHER_SPAN_NAME = "4bf92f3577b34da6a3ce929d0e0e0b04";
    private static final String OTHER_PEER = "4bf92f3577b34da6a3ce929d0e0e0b05";
    private static final String OTHER_KIND = "4bf92f3577b34da6a3ce929d0e0e0b06";
    private static final String OTHER_STATUS = "4bf92f3577b34da6a3ce929d0e0e0b07";
    private static final String BELOW_MIN_DURATION = "4bf92f3577b34da6a3ce929d0e0e0b08";
    private static final String ABOVE_MAX_DURATION = "4bf92f3577b34da6a3ce929d0e0e0b09";
    private static final String OTHER_RESOURCE_ATTRIBUTE = "4bf92f3577b34da6a3ce929d0e0e0b0a";
    private static final String OTHER_SPAN_ATTRIBUTE = "4bf92f3577b34da6a3ce929d0e0e0b0b";
    /** Has the tenant as a resource attribute, not as a span attribute. */
    private static final String TENANT_OF_RESOURCE = "4bf92f3577b34da6a3ce929d0e0e0b0c";
    private static final String BEFORE_START = "4bf92f3577b34da6a3ce929d0e0e0b0d";
    private static final String AFTER_END = "4bf92f3577b34da6a3ce929d0e0e0b0e";
    private static final String UNKNOWN = "4bf92f3577b34da6a3ce929d0e0e0fff";

    /** The span each trace is found by. */
    private static final Map<String, StoredSpan> MATCHED_BY = new LinkedHashMap<>();
    /** Every trace's spans. */
    private static final Map<String, List<StoredSpan>> TRACES = fixture();
    /** The traces in the range, latest first. */
    private static final List<String> IN_RANGE = Arrays.asList(
        LAST_MATCH, TENANT_OF_RESOURCE, OTHER_SPAN_ATTRIBUTE, OTHER_RESOURCE_ATTRIBUTE, ABOVE_MAX_DURATION,
        BELOW_MIN_DURATION, OTHER_STATUS, OTHER_KIND, OTHER_PEER, OTHER_SPAN_NAME, OTHER_SCOPE, OTHER_INSTANCE,
        OTHER_SERVICE, FIRST_MATCH);

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCOTLPTraceQueryDAO dao;
    private long yesterday;
    private long today;

    JDBCOTLPTraceQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeSpans() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        final Model spans = support.install(
            OTLPSpanRecord.class, DefaultScopeDefine.OTLP_SPAN, OTLPSpanRecord.INDEX_NAME, DownSampling.Second);
        final Model services = support.install(
            OTLPServiceTraffic.class, DefaultScopeDefine.OTLP_SERVICE, OTLPServiceTraffic.INDEX_NAME,
            DownSampling.Minute);
        final Model spanNames = support.install(
            OTLPServiceSpanTraffic.class, DefaultScopeDefine.OTLP_SERVICE_SPAN, OTLPServiceSpanTraffic.INDEX_NAME,
            DownSampling.Minute);
        final Model peers = support.install(
            OTLPServiceRelationTraffic.class, DefaultScopeDefine.OTLP_SERVICE_RELATION,
            OTLPServiceRelationTraffic.INDEX_NAME, DownSampling.Minute);

        // Neither in time order nor against it, and each trace's spans child first.
        final List<OTLPSpanRecord> records = new ArrayList<>();
        for (final String traceId : Arrays.asList(
            OTHER_STATUS, LAST_MATCH, BELOW_MIN_DURATION, OTHER_SPAN_ATTRIBUTE, AFTER_END, OTHER_SERVICE, FIRST_MATCH,
            OTHER_PEER, TENANT_OF_RESOURCE, OTHER_SCOPE, BEFORE_START, ABOVE_MAX_DURATION, OTHER_INSTANCE,
            OTHER_RESOURCE_ATTRIBUTE, OTHER_KIND, OTHER_SPAN_NAME)) {
            final List<StoredSpan> trace = new ArrayList<>(TRACES.get(traceId));
            Collections.reverse(trace);
            for (final StoredSpan span : trace) {
                records.add(span.record());
            }
        }
        support.writeRecords(spans, new OTLPSpanRecord.Builder(), records);

        yesterday = TimeBucket.getMinuteTimeBucket(MIDNIGHT - 120_000L);
        today = TimeBucket.getMinuteTimeBucket(MIDNIGHT + 60_000L);
        support.writeMetrics(services, new OTLPServiceTraffic.Builder(), Arrays.asList(
            service(CHECKOUT, Layer.GENERAL.name(), yesterday),
            service(CANARY, Layer.GENERAL.name(), yesterday),
            service(FRONTEND, Layer.GENERAL.name(), yesterday),
            service(CHECKOUT, Layer.MESH.name(), today),
            service(INVENTORY, Layer.UNDEFINED.name(), today)));
        support.writeMetrics(spanNames, new OTLPServiceSpanTraffic.Builder(), Arrays.asList(
            spanName(CHECKOUT, POST_STOCK, yesterday),
            spanName(CHECKOUT, "GET /stock", yesterday),
            spanName(CANARY, POST_STOCK, yesterday),
            spanName(CHECKOUT, "POST /orders", today),
            spanName(INVENTORY, "POST /stock/reserve", today)));
        support.writeMetrics(peers, new OTLPServiceRelationTraffic.Builder(), Arrays.asList(
            peer(CHECKOUT, PEER, yesterday),
            peer(CANARY, PEER, yesterday),
            peer(CHECKOUT, "payments", today)));

        dao = new JDBCOTLPTraceQueryDAO(support.getClient(), support.getTableHelper());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void listsEveryServiceRowOfEveryLayer() {
        assertEquals(sorted(Arrays.asList(
            CANARY + " in GENERAL since " + yesterday,
            CHECKOUT + " in GENERAL since " + yesterday,
            CHECKOUT + " in MESH since " + today,
            FRONTEND + " in GENERAL since " + yesterday,
            INVENTORY + " in UNDEFINED since " + today)), services(null));
    }

    @Test
    public void listsTheServicesOfOneLayer() {
        assertEquals(sorted(Arrays.asList(
            CANARY + " in GENERAL since " + yesterday,
            CHECKOUT + " in GENERAL since " + yesterday,
            FRONTEND + " in GENERAL since " + yesterday)), services(Layer.GENERAL.name()));
        assertEquals(Collections.singletonList(CHECKOUT + " in MESH since " + today), services(Layer.MESH.name()));
        assertEquals(Collections.emptyList(), services(Layer.K8S.name()));
    }

    @Test
    public void listsTheSpanNamesOfEachService() {
        assertEquals(Arrays.asList("GET /stock", "POST /orders", POST_STOCK), sorted(dao.getSpanNames(CHECKOUT)));
        assertEquals(Collections.singletonList(POST_STOCK), sorted(dao.getSpanNames(CANARY)));
        assertEquals(Collections.emptyList(), dao.getSpanNames("unknown"));
    }

    @Test
    public void listsThePeerServicesEachServiceCalled() {
        assertEquals(Arrays.asList(PEER, "payments"), sorted(dao.getPeerServiceNames(CHECKOUT)));
        assertEquals(Collections.singletonList(PEER), sorted(dao.getPeerServiceNames(CANARY)));
        assertEquals(Collections.emptyList(), dao.getPeerServiceNames(INVENTORY));
    }

    /**
     * A range bounds the read to the spans that started in it, the range's end counted; no range reads them all.
     */
    @Test
    public void readsATraceById() {
        assertEquals(trace(FIRST_MATCH), render(dao.queryTraceById(FIRST_MATCH, AROUND_MIDNIGHT)));
        assertEquals(trace(FIRST_MATCH), render(dao.queryTraceById(FIRST_MATCH, null)));
        assertEquals(trace(LAST_MATCH), render(dao.queryTraceById(LAST_MATCH, null)));
        assertEquals(Collections.singletonList(MATCHED_BY.get(LAST_MATCH).render()),
                     render(dao.queryTraceById(LAST_MATCH, AROUND_MIDNIGHT)));
        assertEquals(Collections.emptyList(), dao.queryTraceById(BEFORE_START, AROUND_MIDNIGHT));
        assertEquals(Collections.emptyList(), dao.queryTraceById(AFTER_END, AROUND_MIDNIGHT));
        assertEquals(Collections.emptyList(), dao.queryTraceById(UNKNOWN, null));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("conditionsInBothOrders")
    public void findsTheTracesMatchingOneCondition(final Condition condition, final QueryOrder order) {
        final List<List<String>> expected = expected(condition.matching(), order);
        assertEquals(expected, search(condition.criterion, order, expected.size()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(QueryOrder.class)
    public void findsOnlyTheTracesMatchingEveryCondition(final QueryOrder order) {
        final Consumer<OTLPTraceQueryCondition> everyCondition = condition -> {
            for (final Condition each : Condition.values()) {
                if (each != Condition.TRACE_ID) {
                    each.criterion.accept(condition);
                }
            }
        };
        assertEquals(expected(Arrays.asList(FIRST_MATCH, LAST_MATCH), order), search(everyCondition, order, 2));
        assertEquals(expected(Collections.singletonList(FIRST_MATCH), order),
                     search(everyCondition.andThen(Condition.TRACE_ID.criterion), order, 2));
    }

    @Test
    public void findsNoTraceWhenNoSpanMatches() {
        assertEquals(Collections.emptyList(),
                     search(condition -> condition.setServiceName("unknown"), QueryOrder.BY_START_TIME, 10));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(QueryOrder.class)
    public void keepsAsManyTracesAsTheLimit(final QueryOrder order) {
        final List<List<String>> all = expected(Condition.SERVICE.matching(), order);
        for (final int limit : new int[] {1, 3, 5}) {
            assertEquals(all.subList(0, limit), search(Condition.SERVICE.criterion, order, limit), "limit " + limit);
        }
    }

    /** What the TraceQL search filters by, and the traces in the range each keeps. */
    enum Condition {
        TIME_RANGE_ONLY(condition -> { }),
        SERVICE(condition -> condition.setServiceName(CHECKOUT), OTHER_SERVICE),
        INSTANCE(condition -> condition.setServiceInstance(CHECKOUT_1), OTHER_INSTANCE),
        SCOPE(condition -> condition.setScopeName(OKHTTP), OTHER_SCOPE),
        SPAN_NAME(condition -> condition.setSpanName(POST_STOCK), OTHER_SPAN_NAME),
        PEER_SERVICE(condition -> condition.setPeerService(PEER), OTHER_PEER),
        KIND(condition -> condition.setKind(CLIENT), OTHER_KIND),
        STATUS(condition -> condition.setStatusCode(STATUS_ERROR), OTHER_STATUS),
        MIN_DURATION(condition -> condition.setMinDurationNanos(MIN_DURATION_NS), BELOW_MIN_DURATION),
        MAX_DURATION(condition -> condition.setMaxDurationNanos(MAX_DURATION_NS), ABOVE_MAX_DURATION),
        RESOURCE_ATTRIBUTE(condition -> condition.getTags().add(
            new Tag(OTLPSpanRecord.RESOURCE_TAG_PREFIX + ENVIRONMENT, "production")), OTHER_RESOURCE_ATTRIBUTE),
        SPAN_ATTRIBUTE(condition -> condition.getTags().add(new Tag(OTLPSpanRecord.SPAN_TAG_PREFIX + TENANT, "acme")),
                       OTHER_SPAN_ATTRIBUTE, TENANT_OF_RESOURCE),
        ATTRIBUTE_OF_EITHER_SCOPE(condition -> condition.getTags().add(new Tag(TENANT, "acme")), OTHER_SPAN_ATTRIBUTE),
        // The last matching trace's id is one digit off the first's.
        TRACE_ID(condition -> condition.setTraceId(FIRST_MATCH), only(FIRST_MATCH));

        private final Consumer<OTLPTraceQueryCondition> criterion;
        private final List<String> excluded;
        private final List<String> matching;

        Condition(final Consumer<OTLPTraceQueryCondition> criterion, final String... excluded) {
            this.criterion = criterion;
            this.excluded = Arrays.asList(excluded);
            this.matching = null;
        }

        Condition(final Consumer<OTLPTraceQueryCondition> criterion, final List<String> matching) {
            this.criterion = criterion;
            this.excluded = Collections.emptyList();
            this.matching = matching;
        }

        private List<String> matching() {
            if (matching != null) {
                return matching;
            }
            final List<String> kept = new ArrayList<>(IN_RANGE);
            kept.removeAll(excluded);
            return kept;
        }

        private static List<String> only(final String... traceIds) {
            return Arrays.asList(traceIds);
        }
    }

    static Stream<Arguments> conditionsInBothOrders() {
        return Arrays.stream(Condition.values()).flatMap(condition -> Stream.of(
            Arguments.of(condition, QueryOrder.BY_START_TIME),
            Arguments.of(condition, QueryOrder.BY_DURATION)));
    }

    private List<List<String>> search(final Consumer<OTLPTraceQueryCondition> criteria, final QueryOrder order,
                                      final int limit) {
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        criteria.accept(condition);
        condition.setQueryDuration(AROUND_MIDNIGHT);
        condition.setQueryOrder(order);
        condition.setLimit(limit);
        final List<List<String>> traces = new ArrayList<>();
        for (final List<SpanWrapper> trace : dao.queryTraces(condition)) {
            traces.add(render(trace));
        }
        return traces;
    }

    /** The traces whole, the latest or the longest first by the span each is found by. */
    private static List<List<String>> expected(final List<String> traceIds, final QueryOrder order) {
        final Comparator<String> latestFirst = Comparator.comparingLong(traceId -> -MATCHED_BY.get(traceId).start);
        final Comparator<String> longestFirst =
            Comparator.comparingLong(traceId -> -MATCHED_BY.get(traceId).duration);
        return traceIds.stream()
                       .sorted(order == QueryOrder.BY_DURATION ? longestFirst : latestFirst)
                       .map(JDBCOTLPTraceQueryITBase::trace)
                       .collect(Collectors.toList());
    }

    private static List<String> trace(final String traceId) {
        return TRACES.get(traceId).stream().map(StoredSpan::render).sorted().collect(Collectors.toList());
    }

    /** The trace's spans in label order: a trace's spans come in no promised order. */
    private static List<String> render(final List<SpanWrapper> trace) {
        return trace.stream()
                    .map(span -> span.getSpan().toStringUtf8() + " of " + span.getService() + ", " + span.getSource())
                    .sorted()
                    .collect(Collectors.toList());
    }

    private List<String> services(final String layer) {
        return dao.getServices(layer).stream()
                  .map(service -> service.getServiceName() + " in " + service.getLayer() + " since "
                      + service.getTimeBucket())
                  .sorted()
                  .collect(Collectors.toList());
    }

    private static List<String> sorted(final List<String> names) {
        final List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        return sorted;
    }

    private static Map<String, List<StoredSpan>> fixture() {
        final Map<String, List<StoredSpan>> traces = new LinkedHashMap<>();
        found(traces, FIRST_MATCH, new StoredSpan(FIRST_MATCH, "first match", START, MIN_DURATION_NS),
              new StoredSpan(FIRST_MATCH, "first match, the inventory's", MIDNIGHT + 40_000L, 10_000L)
                  .childOf("first match").at(INVENTORY, "inventory-1", "io.opentelemetry.servlet-5.0")
                  .call("POST /stock/reserve", SERVER, STATUS_UNSET, "").attributes("production", "acme"));
        found(traces, OTHER_SERVICE, new StoredSpan(OTHER_SERVICE, "other service", MIDNIGHT - 55_000L, 1_500_000L)
            .at(CANARY, CHECKOUT_1, OKHTTP));
        found(traces, OTHER_INSTANCE, new StoredSpan(OTHER_INSTANCE, "other instance", MIDNIGHT - 50_000L, 2_000_000L)
            .at(CHECKOUT, "checkout-2", OKHTTP));
        found(traces, OTHER_SCOPE, new StoredSpan(OTHER_SCOPE, "other scope", MIDNIGHT - 45_000L, 2_500_000L)
            .at(CHECKOUT, CHECKOUT_1, "io.opentelemetry.java-http-client"));
        found(traces, OTHER_SPAN_NAME, new StoredSpan(OTHER_SPAN_NAME, "other span name", MIDNIGHT - 40_000L,
                                                      3_000_000L).call("GET /stock", CLIENT, STATUS_ERROR, PEER));
        found(traces, OTHER_PEER, new StoredSpan(OTHER_PEER, "other peer", MIDNIGHT - 35_000L, 3_500_000L)
            .call(POST_STOCK, CLIENT, STATUS_ERROR, "payments"));
        found(traces, OTHER_KIND, new StoredSpan(OTHER_KIND, "other kind", MIDNIGHT - 30_000L, 4_000_000L)
            .call(POST_STOCK, INTERNAL, STATUS_ERROR, PEER));
        found(traces, OTHER_STATUS, new StoredSpan(OTHER_STATUS, "other status", MIDNIGHT - 25_000L, 4_500_000L)
            .call(POST_STOCK, CLIENT, STATUS_OK, PEER));
        found(traces, BELOW_MIN_DURATION, new StoredSpan(BELOW_MIN_DURATION, "below the minimum duration",
                                                         MIDNIGHT - 20_000L, MIN_DURATION_NS - 1));
        found(traces, ABOVE_MAX_DURATION, new StoredSpan(ABOVE_MAX_DURATION, "above the maximum duration",
                                                         MIDNIGHT - 15_000L, MAX_DURATION_NS + 1));
        found(traces, OTHER_RESOURCE_ATTRIBUTE, new StoredSpan(OTHER_RESOURCE_ATTRIBUTE, "other environment",
                                                               MIDNIGHT + 10_000L, 5_000_000L)
            .attributes("staging", "acme"));
        found(traces, OTHER_SPAN_ATTRIBUTE, new StoredSpan(OTHER_SPAN_ATTRIBUTE, "other tenant", MIDNIGHT + 20_000L,
                                                           5_500_000L).attributes("production", "globex"));
        found(traces, TENANT_OF_RESOURCE, new StoredSpan(TENANT_OF_RESOURCE, "tenant of the resource",
                                                         MIDNIGHT + 30_000L, 6_000_000L).tenantOfResource());
        found(traces, LAST_MATCH, new StoredSpan(LAST_MATCH, "last match", END, MAX_DURATION_NS).childOf("last root"),
              new StoredSpan(LAST_MATCH, "last root", START - 30_000L, 125_000_000_000L)
                  .at(FRONTEND, "frontend-1", "io.opentelemetry.servlet-5.0")
                  .call("GET /checkout", SERVER, STATUS_ERROR, ""));
        found(traces, BEFORE_START, new StoredSpan(BEFORE_START, "before the start", START - 1, 7_000_000L));
        found(traces, AFTER_END, new StoredSpan(AFTER_END, "after the end", END + 1, 7_500_000L));
        return traces;
    }

    /**
     * @param spans the span the trace is found by first
     */
    private static void found(final Map<String, List<StoredSpan>> traces, final String traceId,
                              final StoredSpan... spans) {
        MATCHED_BY.put(traceId, spans[0]);
        traces.put(traceId, Arrays.asList(spans));
    }

    /** A span matching every condition unless told otherwise. */
    private static final class StoredSpan {
        private final String traceId;
        private final String label;
        private final long start;
        private final long duration;
        private String parentLabel;
        private String service = CHECKOUT;
        private String instance = CHECKOUT_1;
        private String scope = OKHTTP;
        private String name = POST_STOCK;
        private int kind = CLIENT;
        private int status = STATUS_ERROR;
        private String peer = PEER;
        private String environment = "production";
        private String tenant = "acme";
        private String tenantScope = OTLPSpanRecord.SPAN_TAG_PREFIX;

        private StoredSpan(final String traceId, final String label, final long start, final long duration) {
            this.traceId = traceId;
            this.label = label;
            this.start = start;
            this.duration = duration;
        }

        private StoredSpan childOf(final String parentLabel) {
            this.parentLabel = parentLabel;
            return this;
        }

        private StoredSpan at(final String service, final String instance, final String scope) {
            this.service = service;
            this.instance = instance;
            this.scope = scope;
            return this;
        }

        private StoredSpan call(final String name, final int kind, final int status, final String peer) {
            this.name = name;
            this.kind = kind;
            this.status = status;
            this.peer = peer;
            return this;
        }

        private StoredSpan attributes(final String environment, final String tenant) {
            this.environment = environment;
            this.tenant = tenant;
            return this;
        }

        private StoredSpan tenantOfResource() {
            this.tenantScope = OTLPSpanRecord.RESOURCE_TAG_PREFIX;
            return this;
        }

        /** The record the OTLP receiver stores for the span. */
        private OTLPSpanRecord record() {
            final OTLPSpanRecord record = new OTLPSpanRecord();
            record.setTraceId(traceId);
            record.setSpanId(spanId(label));
            record.setParentSpanId(parentLabel == null ? "" : spanId(parentLabel));
            record.setServiceName(service);
            record.setServiceInstance(instance);
            record.setScopeName(scope);
            record.setName(name);
            record.setKind(kind);
            record.setStatusCode(status);
            record.setPeerService(peer);
            record.setStartTime(start);
            record.setDuration(duration);
            record.setTags(Arrays.asList(
                OTLPSpanRecord.RESOURCE_TAG_PREFIX + "service.name=" + service,
                OTLPSpanRecord.RESOURCE_TAG_PREFIX + ENVIRONMENT + "=" + environment,
                tenantScope + TENANT + "=" + tenant));
            record.setDataBinary(label.getBytes(StandardCharsets.UTF_8));
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(start));
            return record;
        }

        /** The span as the DAO serves it. */
        private String render() {
            return label + " of " + service + ", " + Source.OTLP;
        }
    }

    /** A span id derived from the span's label, unique within the fixture. */
    private static String spanId(final String label) {
        return String.format("%016x", label.hashCode() & 0xffffffffL);
    }

    private static OTLPServiceTraffic service(final String name, final String layer, final long minute) {
        final OTLPServiceTraffic traffic = new OTLPServiceTraffic();
        traffic.setServiceName(name);
        traffic.setLayer(layer);
        traffic.setTimeBucket(minute);
        return traffic;
    }

    private static OTLPServiceSpanTraffic spanName(final String service, final String name, final long minute) {
        final OTLPServiceSpanTraffic traffic = new OTLPServiceSpanTraffic();
        traffic.setServiceName(service);
        traffic.setSpanName(name);
        traffic.setTimeBucket(minute);
        return traffic;
    }

    private static OTLPServiceRelationTraffic peer(final String service, final String peer, final long minute) {
        final OTLPServiceRelationTraffic traffic = new OTLPServiceRelationTraffic();
        traffic.setServiceName(service);
        traffic.setPeerService(peer);
        traffic.setTimeBucket(minute);
        return traffic;
    }

    private static Duration duration(final long start, final long end) {
        return JDBCQueryITSupport.duration(start, end, Step.SECOND);
    }
}
