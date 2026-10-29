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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.apdex.ApdexFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.ShippedRules;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALClassGenerator;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * The worked example of the design, run for real: the OpenTelemetry Demo's three spans of one
 * {@code GET /api/products} through the shipped {@code otlp-general} rule.
 *
 * <p>The assertions are the numbers the design commits to. The rendered table is logged so the data a rule
 * actually produces can be read rather than inferred.
 */
class TALWorkedExampleTest {
    private static final Logger LOG = LoggerFactory.getLogger(TALWorkedExampleTest.class);

    /**
     * The end of span 1; it started 110 ms earlier, and that start is the minute bucket every metric of it must
     * land in.
     */
    private static final long SPAN1_END = 1_700_000_000_500L;

    private ModuleServiceHolder coreProvider;
    private MeterSystem meterSystem;
    private FilterSpec filterSpec;
    private MockedStatic<MetricsStreamProcessor> mockedProcessor;

    private final List<AcceptableValue<?>> sent = new ArrayList<>();
    private final List<Metrics> traffic = new ArrayList<>();
    /**
     * The alias rows the span pairs built, prepared as the dispatcher would before storing them.
     */
    private final List<NetworkAddressAliasSetup> aliases = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        meterSystem = spy(new MeterSystem(moduleManager));
        coreProvider = stubCoreModule(moduleManager);

        final MetricsStreamProcessor processor = mock(MetricsStreamProcessor.class);
        mockedProcessor = Mockito.mockStatic(MetricsStreamProcessor.class);
        mockedProcessor.when(MetricsStreamProcessor::getInstance).thenReturn(processor);
        doNothing().when(processor).create(
            any(), (StreamDefinition) any(), any(), any());
        doAnswer(invocation -> {
            traffic.add(invocation.getArgument(0));
            return null;
        }).when(processor).in(any());
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(meterSystem).doStreamingCalculation(any());

        final NamingControl namingControl = new NamingControl(70, 70, 150, new EndpointNameGrouping());
        MeterEntity.setNamingControl(namingControl);
        // CoreModuleProvider hands both apdex implementations the thresholds from service-apdex-threshold.yml;
        // here the shipped default, 500 ms, is enough.
        ApdexFunction.setDICT(name -> 500);
        final SourceReceiver sourceReceiver = mock(SourceReceiver.class);
        doAnswer(invocation -> {
            final ISource source = invocation.getArgument(0);
            source.prepare();
            aliases.add((NetworkAddressAliasSetup) source);
            return null;
        }).when(sourceReceiver).receive(any());
        filterSpec = new FilterSpec(moduleManager, new TraceAnalyzerModuleConfig(),
            meterSystem, namingControl, sourceReceiver);
    }

    /**
     * {@code component()} resolves names through the core's component catalog, so the relation's component id is
     * only real when the catalog is reachable. Stubbed with the three ids the demo's spans need.
     *
     * @return the core module's service holder, for the services a later step of the example hands the runtime
     */
    private static ModuleServiceHolder stubCoreModule(final ModuleManager moduleManager) {
        final IComponentLibraryCatalogService catalog = mock(IComponentLibraryCatalogService.class);
        Mockito.when(catalog.getComponentIdIgnoreCase("grpc")).thenReturn(23);
        Mockito.when(catalog.getComponentIdIgnoreCase("http")).thenReturn(49);
        Mockito.when(catalog.getComponentIdIgnoreCase("postgresql")).thenReturn(22);
        final ModuleServiceHolder provider = mock(ModuleServiceHolder.class);
        Mockito.when(provider.getService(IComponentLibraryCatalogService.class)).thenReturn(catalog);
        final ModuleProviderHolder module = mock(ModuleProviderHolder.class);
        Mockito.when(module.provider()).thenReturn(provider);
        Mockito.when(moduleManager.find(CoreModule.NAME)).thenReturn(module);
        return provider;
    }

    /**
     * The alias rows reach {@link NetworkAddressAliasCache} through storage and the core's 10-second cache timer,
     * neither of which runs here; this is the reload, with the rows the span pairs built.
     */
    private static void reloadAliasCache(final ModuleServiceHolder coreProvider,
                                         final List<NetworkAddressAliasSetup> aliases) {
        final NetworkAddressAliasCache cache = mock(NetworkAddressAliasCache.class);
        for (final NetworkAddressAliasSetup setup : aliases) {
            final NetworkAddressAlias row = new NetworkAddressAlias();
            row.setAddress(setup.getAddress());
            row.setRepresentServiceId(setup.getRepresentServiceId());
            row.setRepresentServiceInstanceId(setup.getRepresentServiceInstanceId());
            Mockito.when(cache.get(setup.getAddress())).thenReturn(row);
        }
        Mockito.when(coreProvider.getService(NetworkAddressAliasCache.class)).thenReturn(cache);
    }

    @AfterEach
    void tearDown() {
        if (mockedProcessor != null) {
            mockedProcessor.close();
        }
    }

    @Test
    void theDemoTraceProducesTheDesignsMetrics() throws Exception {
        final TalExpression rule = compileShippedRule();

        runSpan1(rule);
        final int afterSpan1 = sent.size();
        runSpan2(rule);
        final int afterSpan2 = sent.size();
        runSpan3(rule);
        final int afterSpan3 = sent.size();
        runSpan4(rule);
        final int afterSpan4 = sent.size();
        // The next request from frontend, once the alias row the pair of spans 2 and 3 built has reached the cache;
        // the node that paired them knows it already, the others learn it from the cache.
        reloadAliasCache(coreProvider, aliases);
        runSpan2(rule);

        // Written to a file as well as logged: the table is the point of this test, and a captured log is not
        // readable from a CI run.
        final Path dump = Paths.get("target", "tal-worked-example.txt");
        Files.createDirectories(dump.getParent());
        Files.write(dump, render().getBytes(StandardCharsets.UTF_8));
        LOG.info("worked example written to {}\n{}", dump.toAbsolutePath(), render());

        // Span 1: a root SERVER span with no http.route and no instance id — five service metrics, four endpoint
        // metrics, no instance metrics for this fleet, and the edge from User.
        assertEquals(10, afterSpan1, "span 1 emits the service and endpoint blocks and the User edge");
        // Span 2: a CLIENT span — the three client-side relation metrics.
        assertEquals(3, afterSpan2 - afterSpan1, "span 2 emits the client relation blocks");
        // Span 3: the callee's own SERVER span, the same nine as span 1.
        assertEquals(9, afterSpan3 - afterSpan2, "span 3 emits the service and endpoint blocks");
        // Span 4: a database client span — the peer's own three metrics plus the three relation metrics.
        assertEquals(6, afterSpan4 - afterSpan3, "span 4 emits the database peer and the edge");
        assertEquals(
            List.of("otlp_database_access_cpm", "otlp_database_access_resp_time", "otlp_database_access_sla",
                "otlp_service_relation_client_cpm", "otlp_service_relation_client_resp_time",
                "otlp_service_relation_client_call_sla"),
            sent.subList(afterSpan3, afterSpan4).stream()
                .map(TALWorkedExampleTest::metricNameOf).collect(Collectors.toList()));
        assertTrue(traffic.stream().anyMatch(r -> describe(r).contains("pg:5432, OTLP_VIRTUAL_DATABASE")),
            "the conjectured database is listed in its own layer: " + traffic);

        assertEquals(
            List.of("otlp_service_cpm", "otlp_service_resp_time", "otlp_service_sla",
                "otlp_service_apdex", "otlp_service_percentile",
                "otlp_endpoint_cpm", "otlp_endpoint_resp_time", "otlp_endpoint_sla",
                "otlp_endpoint_percentile", "otlp_service_relation_server_cpm"),
            sent.subList(0, 10).stream().map(TALWorkedExampleTest::metricNameOf).collect(Collectors.toList()));

        // Every metric of span 1 lands in the minute its span started in, as the agent path buckets a call, not
        // the wall clock's.
        for (final AcceptableValue<?> value : sent.subList(0, 10)) {
            assertEquals(TimeBucket.getMinuteTimeBucket(SPAN1_END - 110), value.getTimeBucket());
        }

        // The design states span 1's 110 ms lands in bucket 250 of [10,25,50,100,250,...].
        assertTrue(fieldsOf(sent.get(4)).contains("250=1"), fieldsOf(sent.get(4)));

        // The root span's caller is outside the fleet: the edge comes from User, and User is a node of the
        // topology rather than a service in any layer's list.
        assertEquals("User (virtual) -> frontend", entityOf(sent.get(9)));
        assertTrue(traffic.stream().anyMatch(r -> describe(r).startsWith(
                "ServiceRelationServerSide{User (virtual) -> frontend, components")),
            "the server-side relation row names User as its source: " + traffic);
        assertTrue(traffic.stream().noneMatch(r -> describe(r).startsWith("ServiceTraffic{User,")),
            "no ServiceTraffic for User: " + traffic);
        // The first call names the peer by the address it dialed, under the deprecated spelling the demo emits:
        // the SERVER span it called has not arrived yet, so nothing has been learned for frontend and this address.
        assertTrue(entityOf(sent.get(afterSpan1)).contains("productcatalogservice:3550"),
            "the first client edge names the peer by its address: " + entityOf(sent.get(afterSpan1)));
        // Span 3 pairs with span 2, the waiting client, by its parent span id: one row, keyed by the caller and the
        // address it dialed. The root span 1 has no caller half, and the database call names no SERVER span.
        assertEquals(
            List.of("frontend->productcatalogservice:3550 = productcatalogservice"),
            aliases.stream().map(a -> a.getAddress() + " = " + serviceName(a.getRepresentServiceId()))
                   .collect(Collectors.toList()));
        // Once the alias row is in the cache, the same call lands on the callee's own node.
        assertEquals(3, sent.size() - afterSpan4, "the resolved call emits the client relation blocks");
        assertEquals("frontend -> productcatalogservice", entityOf(sent.get(afterSpan4)),
            "the resolved client edge names the service the pair learned");
        assertTrue(traffic.stream().anyMatch(r -> describe(r).startsWith(
                "ServiceRelationClientSide{frontend -> productcatalogservice, components")),
            "the resolved edge is a relation row of its own: " + traffic);

        // rpc.system=grpc resolves to the GRPC component, which is what draws the edge as a gRPC line.
        final ServiceRelationClientSideMetrics edge = traffic.stream()
            .filter(ServiceRelationClientSideMetrics.class::isInstance)
            .map(ServiceRelationClientSideMetrics.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no client-side relation was registered"));
        assertTrue(edge.getComponentIds().toString().contains("23"),
            "the edge carries the gRPC component id: " + edge.getComponentIds());
    }

    /**
     * The OpenTelemetry Java agent reports a blocking JMS receive as a CONSUMER span as long as the wait, which the
     * showcase's songs service turned into a 10-second "request" every poll. The rule drops the poll by its
     * operation, whatever its kind, and keeps the message that is actually handled.
     */
    @Test
    void aMessagingReceivePollIsNotARequest() throws Exception {
        final TalExpression rule = compileShippedRule("poll_example", "poll");

        run(rule, consumerSpan("queue-songs-ping receive", 10_079),
            new TestSpanInput().attribute("messaging.system", "jms")
                               .attribute("messaging.destination.name", "queue-songs-ping")
                               .attribute("messaging.operation", "receive"));
        assertEquals(0, sent.size(), "a receive poll emits nothing");

        run(rule, consumerSpan("queue-songs-ping process", 3),
            new TestSpanInput().attribute("messaging.system", "jms")
                               .attribute("messaging.destination.name", "queue-songs-ping")
                               .attribute("messaging.operation", "process"));
        assertTrue(sent.stream().map(TALWorkedExampleTest::metricNameOf).anyMatch("poll_service_cpm"::equals),
                   "a handled message is a request of the consumer");
    }

    /**
     * The showcase's songs service reaches an embedded H2 database and a JMS broker, neither of which has an address
     * in its spans; the rule names them by what the span does know, and files the queue as an endpoint of the
     * broker so the virtual layer's list stays one row per broker.
     */
    @Test
    void aPeerWithoutAnAddressIsNamedByWhatTheSpanKnows() throws Exception {
        final TalExpression rule = compileShippedRule("noaddr_example", "noaddr");

        run(rule, SpanMetadata.builder()
                              .service("songs").endpoint("SELECT mem-db.song")
                              .kind(SpanMetadata.KIND_CLIENT).statusCode(SpanMetadata.STATUS_UNSET).latency(1)
                              .startTime(SPAN1_END - 90).timestamp(SPAN1_END - 80)
                              .traceId("t3").spanId("d1").parentSpanId("p1").build(),
            new TestSpanInput().attribute("db.system", "h2").attribute("db.name", "mem-db")
                               .attribute("db.connection_string", "h2:mem:"));
        assertEquals(MeterEntity.newService("h2/mem-db", Layer.OTLP_VIRTUAL_DATABASE).id(),
                     entityIdOf(sentNamed("noaddr_database_access_cpm")), "the database is named by system and namespace");
        assertEquals(MeterEntity.newServiceRelation("songs", Layer.OTLP_GENERAL, "h2/mem-db",
                                                    Layer.OTLP_VIRTUAL_DATABASE, DetectPoint.CLIENT, 0).id(),
                     entityIdOf(sentNamed("noaddr_service_relation_client_cpm")), "and the edge lands on it");

        sent.clear();
        run(rule, SpanMetadata.builder()
                              .service("songs").endpoint("queue-songs-ping publish")
                              .kind(SpanMetadata.KIND_PRODUCER).statusCode(SpanMetadata.STATUS_UNSET).latency(1)
                              .startTime(SPAN1_END - 70).timestamp(SPAN1_END - 60)
                              .traceId("t3").spanId("m1").parentSpanId("p1").build(),
            new TestSpanInput().attribute("messaging.system", "jms")
                               .attribute("messaging.destination.name", "queue-songs-ping")
                               .attribute("messaging.operation", "publish"));
        assertEquals(MeterEntity.newService("jms", Layer.OTLP_VIRTUAL_MQ).id(),
                     entityIdOf(sentNamed("noaddr_mq_access_cpm")), "the broker is named by the messaging system");
        assertEquals(MeterEntity.newEndpoint("jms", "queue-songs-ping", Layer.OTLP_VIRTUAL_MQ).id(),
                     entityIdOf(sentNamed("noaddr_mq_destination_cpm")), "the queue is an endpoint of the broker");

        sent.clear();
        run(rule, consumerSpan("queue-songs-ping process", 2),
            new TestSpanInput().attribute("messaging.system", "jms")
                               .attribute("messaging.destination.name", "queue-songs-ping")
                               .attribute("messaging.operation", "process"));
        assertEquals(MeterEntity.newServiceRelation("jms", Layer.OTLP_VIRTUAL_MQ, "songs", Layer.OTLP_GENERAL,
                                                    DetectPoint.SERVER, 0).id(),
                     entityIdOf(sentNamed("noaddr_service_relation_server_cpm")),
                     "a handled message is a call from that same broker");
    }

    @Test
    void theCanaryRecipeOfThePracticesDocLandsEachCallOnItsOwnService() throws Exception {
        // docs/en/setup/backend/trace-analyzer-practices.md, "A canary routed by an HTTP header": the snippet goes
        // into a copy of otlp-general.yaml right after the peer is named and before prepareNetworkAlias.
        final String anchor = "// A CLIENT span to a peer that answers with a SERVER span of its own";
        final String shipped = ShippedRules.dsl("otlp-general");
        final int at = shipped.indexOf(anchor);
        assertTrue(at > 0 && shipped.indexOf(anchor, at + 1) < 0, "the CLIENT block's alias step moved");
        final int lineStart = shipped.lastIndexOf('\n', at) + 1;
        final String indent = shipped.substring(lineStart, at);
        final String canary = shipped.substring(0, lineStart)
            + indent + "if (peer && !isGateway(peer) && attr(\"http.request.header.x-canary\") == \"v2\") {\n"
            + indent + "  def peer = peer + \"(v2)\"\n"
            + indent + "}\n"
            + shipped.substring(lineStart);
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("canary_example");
        generator.setMetricPrefix("canary");
        generator.setInputType(TestSpanInput.class);
        generator.setSourceRef(ShippedRules.ref("otlp-general"));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        final TalExpression rule = generator.compile(canary);
        generator.createMetrics(meterSystem);

        // One call routed to the canary by its header, one to b; both dial b:8080.
        runCanaryCall(rule, "c1", "v2", "b-canary");
        runCanaryCall(rule, "c2", null, "b");
        assertEquals(List.of("a->b:8080(v2) = b-canary", "a->b:8080 = b"),
            aliases.stream().map(row -> row.getAddress() + " = " + serviceName(row.getRepresentServiceId()))
                   .collect(Collectors.toList()));

        reloadAliasCache(coreProvider, aliases);
        sent.clear();
        runCanaryCall(rule, "c3", "v2", null);
        runCanaryCall(rule, "c4", null, null);
        assertEquals(List.of(
                MeterEntity.newServiceRelation("a", Layer.OTLP_GENERAL, "b-canary", Layer.OTLP_GENERAL,
                                               DetectPoint.CLIENT, 49).id(),
                MeterEntity.newServiceRelation("a", Layer.OTLP_GENERAL, "b", Layer.OTLP_GENERAL,
                                               DetectPoint.CLIENT, 49).id()),
            sent.stream().filter(v -> "canary_service_relation_client_cpm".equals(metricNameOf(v)))
                .map(v -> {
                    try {
                        return entityIdOf(v);
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).collect(Collectors.toList()),
            "each call's edge lands on the service its header routed it to");

        // A canary call through a gateway listed in gateways.yml keeps the gateway's address, so it ends on the
        // gateway node as the other calls do, and nothing names the gateway after a backend.
        filterSpec.getAliasRegistrar().setGateways("ingress:80"::equals);
        sent.clear();
        aliases.clear();
        run(rule, SpanMetadata.builder()
                              .service("a").endpoint("GET /items")
                              .kind(SpanMetadata.KIND_CLIENT).statusCode(SpanMetadata.STATUS_UNSET).latency(3)
                              .startTime(SPAN1_END - 10).timestamp(SPAN1_END - 7)
                              .traceId("t-c5").spanId("c5").parentSpanId("p").build(),
            new TestSpanInput().attribute("server.address", "ingress").attribute("server.port", "80")
                               .attribute("http.request.header.x-canary", "v2"));
        assertEquals(List.of(
                MeterEntity.newServiceRelation("a", Layer.OTLP_GENERAL, "ingress:80", Layer.VIRTUAL_GATEWAY,
                                               DetectPoint.CLIENT, 49).id()),
            sent.stream().filter(v -> "canary_service_relation_client_cpm".equals(metricNameOf(v)))
                .map(v -> {
                    try {
                        return entityIdOf(v);
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).collect(Collectors.toList()),
            "the canary's call through the gateway ends on the gateway node");
        assertTrue(aliases.isEmpty(), "a gateway's address is never an alias: " + aliases);
    }

    @Test
    void theGatewayCanaryRecipeTagsOnlyTheGatewaysOwnCalls() throws Exception {
        // trace-analyzer-practices.md, "A canary": a traced gateway dials both versions at one address, a router
        // behind it splitting them; the rule puts the version on the gateway's own CLIENT spans only.
        final String anchor = "// A CLIENT span to a peer that answers with a SERVER span of its own";
        final String shipped = ShippedRules.dsl("otlp-general");
        final int at = shipped.indexOf(anchor);
        assertTrue(at > 0 && shipped.indexOf(anchor, at + 1) < 0, "the CLIENT block's alias step moved");
        final int lineStart = shipped.lastIndexOf('\n', at) + 1;
        final String indent = shipped.substring(lineStart, at);
        final String gatewayCanary = shipped.substring(0, lineStart)
            + indent + "if (peer && meta.service == \"edge\" && attr(\"http.request.header.x-canary\") == \"v2\") {\n"
            + indent + "  def peer = peer + \"(v2)\"\n"
            + indent + "}\n"
            + shipped.substring(lineStart);
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("gateway_canary_example");
        generator.setMetricPrefix("gwcanary");
        generator.setInputType(TestSpanInput.class);
        generator.setSourceRef(ShippedRules.ref("otlp-general"));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        final TalExpression rule = generator.compile(gatewayCanary);
        generator.createMetrics(meterSystem);

        runCall(rule, "edge", "g1", "v2", "b-canary");
        runCall(rule, "edge", "g2", null, "b");
        // Another caller sending the header is not the gateway: its peer stays as dialed.
        runCall(rule, "a", "g3", "v2", "b");
        assertEquals(List.of("edge->b:8080(v2) = b-canary", "edge->b:8080 = b", "a->b:8080 = b"),
            aliases.stream().map(row -> row.getAddress() + " = " + serviceName(row.getRepresentServiceId()))
                   .collect(Collectors.toList()));
    }

    private void runCall(final TalExpression rule, final String caller, final String spanId,
                         final String canaryHeader, final String callee) {
        final TestSpanInput client = new TestSpanInput().attribute("server.address", "b")
                                                        .attribute("server.port", "8080");
        if (canaryHeader != null) {
            client.attribute("http.request.header.x-canary", canaryHeader);
        }
        run(rule, SpanMetadata.builder()
                              .service(caller).endpoint("GET /items")
                              .kind(SpanMetadata.KIND_CLIENT).statusCode(SpanMetadata.STATUS_UNSET).latency(3)
                              .startTime(SPAN1_END - 10).timestamp(SPAN1_END - 7)
                              .traceId("t-" + spanId).spanId(spanId).parentSpanId("p").build(), client);
        run(rule, SpanMetadata.builder()
                              .service(callee).endpoint("GET /items")
                              .kind(SpanMetadata.KIND_SERVER).statusCode(SpanMetadata.STATUS_UNSET).latency(2)
                              .startTime(SPAN1_END - 9).timestamp(SPAN1_END - 7)
                              .traceId("t-" + spanId).spanId("s-" + spanId).parentSpanId(spanId).build(),
            new TestSpanInput().attribute("server.address", "b").attribute("server.port", "8080"));
    }

    /**
     * {@code a} calls {@code b:8080}; when {@code callee} is given, the SERVER span that answered follows.
     */
    private void runCanaryCall(final TalExpression rule, final String spanId, final String canaryHeader,
                               final String callee) {
        final TestSpanInput client = new TestSpanInput().attribute("server.address", "b")
                                                        .attribute("server.port", "8080");
        if (canaryHeader != null) {
            client.attribute("http.request.header.x-canary", canaryHeader);
        }
        run(rule, SpanMetadata.builder()
                              .service("a").endpoint("GET /items")
                              .kind(SpanMetadata.KIND_CLIENT).statusCode(SpanMetadata.STATUS_UNSET).latency(3)
                              .startTime(SPAN1_END - 10).timestamp(SPAN1_END - 7)
                              .traceId("t-" + spanId).spanId(spanId).parentSpanId("p").build(), client);
        if (callee != null) {
            run(rule, SpanMetadata.builder()
                                  .service(callee).endpoint("GET /items")
                                  .kind(SpanMetadata.KIND_SERVER).statusCode(SpanMetadata.STATUS_UNSET).latency(2)
                                  .startTime(SPAN1_END - 9).timestamp(SPAN1_END - 7)
                                  .traceId("t-" + spanId).spanId("s-" + spanId).parentSpanId(spanId).build(),
                new TestSpanInput().attribute("server.address", "b").attribute("server.port", "8080"));
        }
    }

    private static String entityIdOf(final AcceptableValue<?> value) throws Exception {
        return (String) value.getClass().getMethod("getEntityId").invoke(value);
    }

    private AcceptableValue<?> sentNamed(final String metric) {
        return sent.stream().filter(v -> metric.equals(metricNameOf(v))).findFirst()
                   .orElseThrow(() -> new AssertionError(metric + " was not emitted; got "
                       + sent.stream().map(TALWorkedExampleTest::metricNameOf).collect(Collectors.toList())));
    }

    private static SpanMetadata consumerSpan(final String name, final int latency) {
        return SpanMetadata.builder()
                           .service("songs").endpoint(name)
                           .kind(SpanMetadata.KIND_CONSUMER).statusCode(SpanMetadata.STATUS_UNSET).latency(latency)
                           .startTime(SPAN1_END - 90).timestamp(SPAN1_END - 80)
                           .traceId("t2").spanId("c1").parentSpanId("p1").build();
    }

    private void runSpan1(final TalExpression rule) {
        run(rule,
            SpanMetadata.builder()
                        .service("frontend").endpoint("HTTP GET").kind(SpanMetadata.KIND_SERVER)
                        .statusCode(SpanMetadata.STATUS_UNSET).latency(110)
                        .startTime(SPAN1_END - 110).timestamp(SPAN1_END)
                        .traceId("t1").spanId("s1").root(true).build(),
            new TestSpanInput().attribute("http.method", "GET")
                               .attribute("http.target", "/api/products")
                               .attribute("http.status_code", "200")
                               .attribute("net.host.name", "frontend")
                               .attribute("net.host.port", "8080"));
    }

    private void runSpan2(final TalExpression rule) {
        run(rule,
            SpanMetadata.builder()
                        .service("frontend").endpoint("grpc.oteldemo.ProductCatalogService/ListProducts")
                        .kind(SpanMetadata.KIND_CLIENT).statusCode(SpanMetadata.STATUS_UNSET).latency(9)
                        .startTime(SPAN1_END - 100).timestamp(SPAN1_END - 91)
                        .traceId("t1").spanId("s2").parentSpanId("s1").build(),
            new TestSpanInput().attribute("rpc.system", "grpc")
                               .attribute("rpc.service", "oteldemo.ProductCatalogService")
                               .attribute("rpc.method", "ListProducts")
                               .attribute("rpc.grpc.status_code", "0")
                               .attribute("net.peer.name", "productcatalogservice")
                               .attribute("net.peer.port", "3550"));
    }

    private void runSpan3(final TalExpression rule) {
        run(rule,
            SpanMetadata.builder()
                        .service("productcatalogservice")
                        .endpoint("oteldemo.ProductCatalogService/ListProducts")
                        .kind(SpanMetadata.KIND_SERVER).statusCode(SpanMetadata.STATUS_UNSET).latency(4)
                        .startTime(SPAN1_END - 99).timestamp(SPAN1_END - 95)
                        .traceId("t1").spanId("s3").parentSpanId("s2").build(),
            new TestSpanInput().attribute("rpc.system", "grpc")
                               .attribute("rpc.service", "oteldemo.ProductCatalogService")
                               .attribute("rpc.method", "ListProducts")
                               .attribute("rpc.grpc.status_code", "0")
                               // The `:authority` the caller sent, which is how the callee learns the name it was
                               // dialed by; the demo's Go server does not report it, a current SDK does.
                               .attribute("server.address", "productcatalogservice")
                               .attribute("server.port", "3550"));
    }

    /**
     * Not in the demo's three spans: a PostgreSQL call, so the conjectured peer shows what a virtual layer's
     * list now has to fill.
     */
    private void runSpan4(final TalExpression rule) {
        run(rule,
            SpanMetadata.builder()
                        .service("productcatalogservice").endpoint("SELECT products")
                        .kind(SpanMetadata.KIND_CLIENT).statusCode(SpanMetadata.STATUS_UNSET).latency(3)
                        .startTime(SPAN1_END - 98).timestamp(SPAN1_END - 95)
                        .traceId("t1").spanId("s4").parentSpanId("s3").build(),
            new TestSpanInput().attribute("db.system.name", "postgresql")
                               .attribute("db.namespace", "products")
                               .attribute("db.query.text", "SELECT * FROM products")
                               .attribute("server.address", "pg")
                               .attribute("server.port", "5432"));
    }

    private void run(final TalExpression rule, final SpanMetadata meta, final TestSpanInput input) {
        final ExecutionContext ctx = new ExecutionContext();
        ctx.setSourceTypeProvider(new TestSpanSourceTypeProvider());
        ctx.init(meta, input, Collections.emptyMap(), Collections.emptyMap());
        rule.execute(filterSpec, ctx);
        // What TraceAnalyzerServiceImpl does after the rules ran: a CLIENT span whose rule named its peer is the caller
        // half of a pair, a SERVER span whose rule asked for an alias the callee half, and the trace's owner composes
        // the address with that rule's buildNetworkAlias.
        if (!ctx.isAborted() && ctx.getNetworkAliasPeer() != null) {
            filterSpec.getSpanPairing().clientSeen(meta, ctx.getNetworkAliasPeer());
        }
        if (!ctx.isAborted() && ctx.isNetworkAliasRequested()) {
            filterSpec.getSpanPairing().setAddressComposer(
                (caller, callee) -> rule.networkAliasAddress(filterSpec, new ExecutionContext(), caller));
            filterSpec.getSpanPairing().serverSeen(meta, true, "OTLP_GENERAL", "otlp-general");
        }
    }

    private TalExpression compileShippedRule() throws Exception {
        return compileShippedRule("worked_example", "otlp");
    }

    /**
     * Javassist keeps a generated metric class for the whole JVM while the {@link MeterSystem} is new per test, so a
     * second compile of the same rule must register its metrics under another prefix.
     */
    private TalExpression compileShippedRule(final String classNameHint, final String metricPrefix) throws Exception {
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint(classNameHint);
        generator.setMetricPrefix(metricPrefix);
        generator.setInputType(TestSpanInput.class);
        generator.setSourceRef(ShippedRules.ref("otlp-general"));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        final TalExpression expression = generator.compile(ShippedRules.dsl("otlp-general"));
        generator.createMetrics(meterSystem);
        return expression;
    }

    // ==================== Rendering ====================

    /**
     * @return the metrics and traffic rows the three spans produced, as a table
     */
    private String render() {
        final StringBuilder out = new StringBuilder();
        out.append("METRICS (").append(sent.size()).append(")\n");
        out.append(String.format("%-46s %-52s %-14s %s%n", "metric", "entity", "bucket", "value"));
        for (final AcceptableValue<?> value : sent) {
            out.append(String.format("%-46s %-52s %-14d %s%n",
                metricNameOf(value), entityOf(value), value.getTimeBucket(), fieldsOf(value)));
        }
        out.append("\nTRAFFIC (").append(traffic.size()).append(")\n");
        final Map<String, Integer> rows = new LinkedHashMap<>();
        for (final Metrics row : traffic) {
            rows.merge(describe(row), 1, Integer::sum);
        }
        rows.forEach((k, v) -> out.append(String.format("  %-64s x%d%n", k, v)));
        out.append("\nALIASES (").append(aliases.size()).append(")\n");
        for (final NetworkAddressAliasSetup alias : aliases) {
            out.append("  ").append(alias.toJson()).append('\n');
        }
        return out.toString();
    }

    private static String describe(final Metrics row) {
        if (row instanceof ServiceTraffic) {
            final ServiceTraffic s = (ServiceTraffic) row;
            return "ServiceTraffic{" + s.getName() + ", " + s.getLayer() + "}";
        }
        if (row instanceof EndpointTraffic) {
            return "EndpointTraffic{" + ((EndpointTraffic) row).getName() + "}";
        }
        if (row instanceof ServiceRelationClientSideMetrics) {
            final ServiceRelationClientSideMetrics r = (ServiceRelationClientSideMetrics) row;
            return "ServiceRelationClientSide{" + serviceName(r.getSourceServiceId())
                + " -> " + serviceName(r.getDestServiceId()) + ", components " + r.getComponentIds() + "}";
        }
        if (row instanceof ServiceRelationServerSideMetrics) {
            final ServiceRelationServerSideMetrics r = (ServiceRelationServerSideMetrics) row;
            return "ServiceRelationServerSide{" + serviceName(r.getSourceServiceId())
                + " -> " + serviceName(r.getDestServiceId()) + ", components " + r.getComponentIds() + "}";
        }
        return row.getClass().getSimpleName();
    }

    private static String metricNameOf(final AcceptableValue<?> value) {
        // The generated metric class is named after the metric, with the shape MeterSystem gives it.
        final String simple = value.getClass().getSimpleName();
        return simple.replaceAll("Metrics$", "").replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String entityOf(final AcceptableValue<?> value) {
        final String entityId = read(value, "entityId");
        if (entityId == null) {
            return "?";
        }
        if (entityId.contains("-")) {
            final IDManager.ServiceID.ServiceRelationDefine relation =
                IDManager.ServiceID.analysisRelationId(entityId);
            return serviceName(relation.getSourceId()) + " -> " + serviceName(relation.getDestId());
        }
        if (entityId.contains("_")) {
            final IDManager.EndpointID.EndpointIDDefinition endpoint =
                IDManager.EndpointID.analysisId(entityId);
            return serviceName(endpoint.getServiceId()) + " / " + endpoint.getEndpointName();
        }
        return serviceName(entityId);
    }

    private static String serviceName(final String serviceId) {
        try {
            final IDManager.ServiceID.ServiceIDDefinition define = IDManager.ServiceID.analysisId(serviceId);
            return define.getName() + (define.isReal() ? "" : " (virtual)");
        } catch (final RuntimeException e) {
            return serviceId;
        }
    }

    /**
     * @return the accumulator fields the function filled, which is the data the minute row is calculated from
     */
    private static String fieldsOf(final AcceptableValue<?> value) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            for (final Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                final String name = field.getName();
                if (name.equals("entityId") || name.equals("serviceId") || name.startsWith("attr")
                        || name.equals("timeBucket") || name.startsWith("$")) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    final Object v = field.get(value);
                    if (v != null && !"0".equals(String.valueOf(v)) && !out.containsKey(name)) {
                        out.put(name, String.valueOf(v));
                    }
                } catch (final IllegalAccessException ignored) {
                    // a field the JVM will not show is not worth failing a rendering over
                }
            }
        }
        return out.toString();
    }

    private static String read(final AcceptableValue<?> value, final String fieldName) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return String.valueOf(field.get(value));
            } catch (final NoSuchFieldException | IllegalAccessException ignored) {
                continue;
            }
        }
        return null;
    }

}
