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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.TracesData;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.apdex.ApdexFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALClassGenerator;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalExpression;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * The shipped {@code otlp-general} rule over a trace recorded from the OpenTelemetry Demo, one {@code GET /api/cart}
 * through {@code frontend-web}, {@code frontend-proxy}, {@code frontend} and {@code cart}, fed as the receiver
 * feeds it: the real {@code otlp} input type, resource and scope attributes as string maps, and
 * {@link SpanMetadata} built the way {@code OpenTelemetryTraceHandler} builds it.
 *
 * <p>Two passes over the same eight spans: a cold one, where the span pairing learns each caller and peer as its
 * callee's SERVER span arrives, and a warm one, where the alias rows of the first pass are in the cache. The fixture
 * is the original OTLP/JSON with the host, OS, process, container and browser attributes removed, along with the
 * copies of the resource attributes the demo's collector puts on every span; neither the rule nor this test reads
 * them.
 */
class ShippedRuleOverRecordedTraceTest {
    private static final String FIXTURE = "tal/otlp-trace-get-api-cart.json";
    private static final Path RULES = Paths.get("../../server-starter/src/main/resources/tal/otlp-general.yaml");

    private ModuleServiceHolder coreProvider;
    private MeterSystem meterSystem;
    private FilterSpec filterSpec;
    private MockedStatic<MetricsStreamProcessor> mockedProcessor;
    private final List<AcceptableValue<?>> sent = new ArrayList<>();
    private final List<Metrics> traffic = new ArrayList<>();
    private final List<NetworkAddressAliasSetup> aliases = new ArrayList<>();
    private final StringBuilder report = new StringBuilder();

    @BeforeEach
    void setUp() throws Exception {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final IComponentLibraryCatalogService catalog = mock(IComponentLibraryCatalogService.class);
        Mockito.when(catalog.getComponentIdIgnoreCase("grpc")).thenReturn(23);
        Mockito.when(catalog.getComponentIdIgnoreCase("http")).thenReturn(49);
        coreProvider = mock(ModuleServiceHolder.class);
        Mockito.when(coreProvider.getService(IComponentLibraryCatalogService.class)).thenReturn(catalog);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        Mockito.when(coreModule.provider()).thenReturn(coreProvider);
        Mockito.when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);

        meterSystem = spy(new MeterSystem(moduleManager));
        final MetricsStreamProcessor processor = mock(MetricsStreamProcessor.class);
        mockedProcessor = Mockito.mockStatic(MetricsStreamProcessor.class);
        mockedProcessor.when(MetricsStreamProcessor::getInstance).thenReturn(processor);
        doNothing().when(processor).create(any(), (StreamDefinition) any(), any(), any());
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

    @AfterEach
    void tearDown() throws Exception {
        if (mockedProcessor != null) {
            mockedProcessor.close();
        }
        final Path dump = Paths.get("target", "otlp-trace-rule-run.txt");
        Files.createDirectories(dump.getParent());
        Files.write(dump, report.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void theRecordedTraceProducesTheEdgesAliasesAndMetricsOfTheDesign() throws Exception {
        final TalExpression rule = compileShippedRule();
        final List<AnalyzedSpan> spans = loadSpans();
        assertEquals(8, spans.size());
        assertEquals(
            List.of("frontend-web CLIENT", "frontend INTERNAL", "frontend SERVER", "frontend SERVER",
                "frontend CLIENT", "frontend-proxy SERVER", "frontend-proxy CLIENT", "cart SERVER"),
            spans.stream().map(s -> s.meta.getService() + " " + s.meta.getKind()).collect(Collectors.toList()),
            "the spans in start order, as the receiver would analyze them");

        // ---- Pass 1: cold. No alias row is in the cache yet.
        report.append("=== PASS 1: cold ===\n");
        runAll(rule, spans);
        assertEquals(List.of(
                "frontend-web -> frontend-proxy:8080 [49]",
                "frontend -> cart:7070 [23]",
                "frontend-proxy -> 172.19.0.19:8080 [49]"),
            clientEdges(), "a peer is named only once its row reaches the alias cache, so the cold pass keeps every "
                + "peer's address");
        assertTrue(serverEdges().isEmpty(), "no root SERVER span, so no User edge: " + serverEdges());
        // Each hop is learned from its own two spans. frontend's SERVER span reports the proxy's address,
        // frontend-proxy:8080, as its server.address; it is never registered, since its caller is Envoy's CLIENT
        // span, which dialed 172.19.0.19:8080.
        assertEquals(List.of(
                "frontend-web->frontend-proxy:8080 = frontend-proxy",
                "frontend-proxy->172.19.0.19:8080 = frontend",
                "frontend->cart:7070 = cart"),
            aliasRows());
        assertMetricCounts(spans);

        // ---- Pass 2: warm. The alias rows the first pass built are in the cache.
        loadAliasCache();
        sent.clear();
        traffic.clear();
        aliases.clear();
        report.append("\n=== PASS 2: warm ===\n");
        runAll(rule, spans);
        assertEquals(List.of(
                "frontend-web -> frontend-proxy [49]",
                "frontend -> cart [23]",
                "frontend-proxy -> frontend [49]"),
            clientEdges(), "every edge lands on the service that answered it, the proxy hop included");
        // Every matched pair is pushed again; NetworkAddressAlias#combine skips the write of an unchanged mapping.
        assertEquals(List.of(
                "frontend-web->frontend-proxy:8080 = frontend-proxy",
                "frontend-proxy->172.19.0.19:8080 = frontend",
                "frontend->cart:7070 = cart"),
            aliasRows());
        assertMetricCounts(spans);
    }

    /**
     * Nine metrics per SERVER span without an instance id, twelve with one, three per CLIENT span with a peer,
     * none for INTERNAL spans; the same in both passes, since resolution changes names, not counts. Next.js's
     * SERVER span, nested under frontend's HTTP SERVER span of the same request, counts nothing.
     */
    private void assertMetricCounts(final List<AnalyzedSpan> spans) {
        assertEquals(List.of(3, 0, 9, 0, 3, 9, 3, 12),
            spans.stream().map(s -> s.metrics).collect(Collectors.toList()),
            "metrics emitted per span, in start order");
        assertTrue(sent.stream().map(ShippedRuleOverRecordedTraceTest::entityOf).anyMatch(e -> e.equals("frontend / GET /api/cart")),
            "the endpoint is the route, read through the real otlp provider");
        assertTrue(sent.stream().map(ShippedRuleOverRecordedTraceTest::entityOf)
                .anyMatch(e -> e.equals("cart / POST /oteldemo.CartService/GetCart")),
            "the .NET server's HTTP span names its endpoint by route too");
        assertTrue(traffic.stream().noneMatch(r -> describe(r).startsWith("ServiceTraffic{User")));
    }

    private void runAll(final TalExpression rule, final List<AnalyzedSpan> spans) {
        int i = 0;
        for (final AnalyzedSpan s : spans) {
            final int before = sent.size();
            final ExecutionContext ctx = new ExecutionContext();
            ctx.setSourceTypeProvider(new OtlpTALSourceTypeProvider());
            ctx.init(s.meta, s.span, s.resourceTags, s.scopeTags);
            rule.execute(filterSpec, ctx);
            // What TraceAnalyzerServiceImpl does after the rules ran: the CLIENT span of a rule that named its peer is
            // the caller half of a pair, the SERVER span of a rule that asked for an alias the callee half, composed
            // on the trace's owner by that rule's buildNetworkAlias.
            if (!ctx.isAborted() && ctx.getNetworkAliasPeer() != null) {
                filterSpec.getSpanPairing().clientSeen(s.meta, ctx.getNetworkAliasPeer());
            }
            if (!ctx.isAborted() && ctx.isNetworkAliasRequested()) {
                filterSpec.getSpanPairing().setAddressComposer(
                    (caller, callee) -> rule.networkAliasAddress(filterSpec, new ExecutionContext(), caller));
                filterSpec.getSpanPairing().serverSeen(s.meta, true, "OTLP_GENERAL", "otlp-general");
            }
            s.metrics = sent.size() - before;
            report.append(String.format("#%d %s %s \"%s\": %d metrics%n", ++i, s.meta.getService(), s.meta.getKind(),
                s.meta.getEndpoint(), s.metrics));
        }
        traffic.forEach(r -> report.append("  traffic ").append(describe(r)).append('\n'));
        aliases.forEach(a -> report.append("  alias   ").append(a.getAddress()).append(" = ")
                                   .append(serviceName(a.getRepresentServiceId())).append('\n'));
    }

    /**
     * The core's cache timer would load the rows of pass 1 from storage; this is that reload.
     */
    private void loadAliasCache() {
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

    private TalExpression compileShippedRule() throws Exception {
        final Map<String, Object> doc = new Yaml().load(Files.newInputStream(RULES));
        String dsl = null;
        for (final Object r : (List<?>) doc.get("rules")) {
            if ("otlp-general".equals(((Map<?, ?>) r).get("name"))) {
                dsl = String.valueOf(((Map<?, ?>) r).get("dsl"));
            }
        }
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("recorded_trace");
        generator.setMetricPrefix(String.valueOf(doc.get("metricPrefix")));
        generator.setInputType(Span.class);
        generator.setSourceRef(DslSourceRef.ofRule("tal/otlp-general.yaml", 29));
        final TalExpression expression = generator.compile(dsl);
        generator.createMetrics(meterSystem);
        return expression;
    }

    private static final class AnalyzedSpan {
        SpanMetadata meta;
        Span span;
        Map<String, String> resourceTags;
        Map<String, String> scopeTags;
        int metrics;
    }

    /**
     * The fixture's spans in start order, each with the metadata and attribute maps the receiver hands the
     * analyzer.
     */
    private static List<AnalyzedSpan> loadSpans() throws Exception {
        final TracesData.Builder builder = TracesData.newBuilder();
        try (InputStream in = ShippedRuleOverRecordedTraceTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
            // The receiver's own conversion, so the fixture is read the way an OTLP/JSON export is.
            JsonFormat.parser().ignoringUnknownFields().merge(
                OTLPValues.hexIdsToBase64(new String(in.readAllBytes(), StandardCharsets.UTF_8)), builder);
        }
        final List<AnalyzedSpan> out = new ArrayList<>();
        for (final ResourceSpans rs : builder.getResourceSpansList()) {
            final Map<String, String> resourceTags = OTLPValues.toStringMap(rs.getResource().getAttributesList());
            final String service = resourceTags.getOrDefault("service.name", "");
            for (final ScopeSpans ss : rs.getScopeSpansList()) {
                for (final Span span : ss.getSpansList()) {
                    final String parent = OTLPValues.hexId(span.getParentSpanId());
                    final AnalyzedSpan a = new AnalyzedSpan();
                    a.span = span;
                    a.resourceTags = resourceTags;
                    a.scopeTags = OTLPValues.toStringMap(ss.getScope().getAttributesList());
                    a.meta = SpanMetadata.builder()
                        .service(service).serviceInstance(resourceTags.getOrDefault("service.instance.id", ""))
                        .endpoint(span.getName()).layer(resourceTags.getOrDefault("service.layer", ""))
                        .kind(SpanMetadata.normalizeKind(span.getKind().name()))
                        .statusCode(SpanMetadata.normalizeStatusCode(span.getStatus().getCode().name()))
                        .statusMessage(span.getStatus().getMessage())
                        .startTime(TimeUnit.NANOSECONDS.toMillis(span.getStartTimeUnixNano()))
                        .timestamp(TimeUnit.NANOSECONDS.toMillis(span.getEndTimeUnixNano()))
                        .latency(SpanMetadata.latencyMillis(span.getStartTimeUnixNano(), span.getEndTimeUnixNano()))
                        .traceId(OTLPValues.hexId(span.getTraceId())).spanId(OTLPValues.hexId(span.getSpanId()))
                        .parentSpanId(parent).root(parent.isEmpty())
                        .parentLocation(OpenTelemetryTraceHandler.parentLocation(parent, span.getFlags()))
                        .scopeName(ss.getScope().getName()).scopeVersion(ss.getScope().getVersion())
                        .build();
                    out.add(a);
                }
            }
        }
        out.sort((x, y) -> Long.compare(x.span.getStartTimeUnixNano(), y.span.getStartTimeUnixNano()));
        return out;
    }

    // ==================== Reading back what was emitted ====================

    private List<String> clientEdges() {
        return traffic.stream().filter(ServiceRelationClientSideMetrics.class::isInstance)
                      .map(ServiceRelationClientSideMetrics.class::cast)
                      .map(r -> serviceName(r.getSourceServiceId()) + " -> " + serviceName(r.getDestServiceId())
                          + " " + r.getComponentIds().toString().replace("IntList(data=", "").replace(")", ""))
                      .collect(Collectors.toList());
    }

    private List<String> serverEdges() {
        return traffic.stream().filter(ServiceRelationServerSideMetrics.class::isInstance)
                      .map(ServiceRelationServerSideMetrics.class::cast)
                      .map(r -> serviceName(r.getSourceServiceId()) + " -> " + serviceName(r.getDestServiceId()))
                      .collect(Collectors.toList());
    }

    private List<String> aliasRows() {
        return aliases.stream().map(a -> a.getAddress() + " = " + serviceName(a.getRepresentServiceId()))
                      .collect(Collectors.toList());
    }

    private static String describe(final Metrics row) {
        if (row instanceof ServiceTraffic) {
            return "ServiceTraffic{" + ((ServiceTraffic) row).getName() + ", " + ((ServiceTraffic) row).getLayer() + "}";
        }
        if (row instanceof ServiceRelationClientSideMetrics) {
            final ServiceRelationClientSideMetrics r = (ServiceRelationClientSideMetrics) row;
            return "ServiceRelationClientSide{" + serviceName(r.getSourceServiceId()) + " -> "
                + serviceName(r.getDestServiceId()) + "}";
        }
        return row.getClass().getSimpleName();
    }

    private static String entityOf(final AcceptableValue<?> value) {
        final String entityId = read(value, "entityId");
        if (entityId == null) {
            return "?";
        }
        if (entityId.contains("-")) {
            final IDManager.ServiceID.ServiceRelationDefine relation = IDManager.ServiceID.analysisRelationId(entityId);
            return serviceName(relation.getSourceId()) + " -> " + serviceName(relation.getDestId());
        }
        if (entityId.contains("_")) {
            try {
                final IDManager.EndpointID.EndpointIDDefinition endpoint = IDManager.EndpointID.analysisId(entityId);
                return serviceName(endpoint.getServiceId()) + " / " + endpoint.getEndpointName();
            } catch (final RuntimeException e) {
                final IDManager.ServiceInstanceID.InstanceIDDefinition inst = IDManager.ServiceInstanceID.analysisId(entityId);
                return serviceName(inst.getServiceId()) + " @ " + inst.getName();
            }
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
