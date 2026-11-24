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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl.debug;

import io.opentelemetry.proto.trace.v1.Span;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.trace.NativeTraceAnalysisConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.SearchableTracesTagsWatcher;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.dsl.Catalog;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.dsl.debug.DSLDebugCodegenSwitch;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALFunctionRegistry;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALMetricShapes;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TALTestModules;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalDSL;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeRuleRunner;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeSegmentState;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SeenSpans;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SpanPairing;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.TraceAnalyzerServiceImpl;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener.SpanFilterListener;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.SwNativeTALSourceTypeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The probes a rule compiled with dsl-debugging injection on reports, in the order a run reaches them, through the
 * runners that open and close the records: a native segment, span by span and then its build phase, and an OTLP span
 * with the network-alias half its run asked for.
 */
class TALDebugProbeTest {
    private final List<ISource> sent = new ArrayList<>();
    private ModuleManager moduleManager;
    private TALFunctionRegistry functions;

    @BeforeAll
    static void declareScopes() {
        if (DefaultScopeDefine.classOf(ServiceMeta.class.getSimpleName()) == null) {
            new DefaultScopeDefine.Listener().notify(ServiceMeta.class);
        }
    }

    @BeforeEach
    void setUp() {
        DSLDebugCodegenSwitch.enableInjection();
        moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        final SourceReceiver sourceReceiver = mock(SourceReceiver.class);
        doAnswer(invocation -> sent.add(invocation.getArgument(0))).when(sourceReceiver).receive(any());
        TALTestModules.stubServices(moduleManager, coreProvider, mock(MeterSystem.class),
            new NamingControl(70, 70, 150, new EndpointNameGrouping()), sourceReceiver);
        final SearchableTracesTagsWatcher searchable = mock(SearchableTracesTagsWatcher.class);
        when(searchable.getSearchableTags()).thenReturn(Set.of());
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getSearchableTracesTags()).thenReturn(searchable);
        when(coreProvider.getService(ConfigService.class)).thenReturn(configService);
        final ModuleServiceHolder analyzerProvider = mock(ModuleServiceHolder.class);
        when(analyzerProvider.getService(NativeTraceAnalysisConfig.class))
            .thenReturn(mock(NativeTraceAnalysisConfig.class));
        final ModuleProviderHolder analyzerModule = mock(ModuleProviderHolder.class);
        when(analyzerModule.provider()).thenReturn(analyzerProvider);
        when(moduleManager.find(AnalyzerModule.NAME)).thenReturn(analyzerModule);
        when(moduleManager.has(AnalyzerModule.NAME)).thenReturn(true);
        functions = new TALFunctionRegistry(List.of());
        functions.bind(moduleManager, List.of(new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider()));
    }

    @AfterEach
    void tearDown() {
        DSLDebugCodegenSwitch.resetInjection();
    }

    @Test
    void aNativeSegmentReportsEachSpanThenTheBuildsOnTheSpansThatQueuedThem() throws Exception {
        final TalDSL rule = nativeRule("filter {\n"
            + "  first {\n"
            + "    segmentDef owner = segment().service\n"
            + "  }\n"
            + "  if (span.spanType == \"Entry\") {\n"
            + "    def op = span.operationName\n"
            + "    extractor {\n"
            + "      build { source ServiceMeta { name owner + \":\" + op timeBucket 1 } }\n"
            + "    }\n"
            + "  }\n"
            + "}");
        assertArrayEquals(new String[] {"owner"}, rule.getExpression().segmentDefNames());
        final TestCallRecorder recorder = bind(rule.getExpression().debugHolder());
        final SwNativeRuleRunner runner = new SwNativeRuleRunner(moduleManager, new SwNativeTALSourceTypeProvider());
        runner.loadRules(List.of(rule));

        runner.send(SegmentObject.newBuilder().setService("svc").setTraceId("t1").setTraceSegmentId("seg1")
                                 .addSpans(span(0, "op0", SpanType.Entry))
                                 .addSpans(span(1, "op1", SpanType.Exit))
                                 .build());

        assertEquals(List.of(
            "beginSegment svc",
            "beginSpan 0",
            "step first block 2 first {",
            "step first segmentDef 3 svc",
            // first {}'s state, kept should a later rule fail the segment
            "endSpan 0",
            "beginSpan 0",
            "condition filter 5 true",
            "step filter def 6 op0",
            "step extractor block 7 extractor {",
            "step extractor build 8 0",
            "endSpan 0",
            "beginSpan 1",
            "condition filter 5 false",
            "endSpan 1",
            "beginBuild 0",
            "step build block 8 build { source ServiceMeta { name owner + \":\" + op timeBucket 1 } }",
            "source build 8 ServiceMeta",
            "endSpan 0",
            "endSegment"), recorder.calls);
        assertEquals(1, sent.size(), "the probes change nothing the rule sends");
    }

    @Test
    void anOtlpSpanReportsItsRunAndTheHalfItAskedFor() throws Exception {
        final TalDSL rule = otlpRule("filter {\n"
            + "  if (meta.kind == \"SERVER\") {\n"
            + "    abort {}\n"
            + "  }\n"
            + "  extractor {\n"
            + "    service \"renamed\"\n"
            + "    tag \"k\": \"v\"\n"
            + "    prepareNetworkAlias \"backend:8080\"\n"
            + "  }\n"
            + "}");
        final TestCallRecorder recorder = bind(rule.getExpression().debugHolder());
        final TraceAnalyzerServiceImpl service = service(rule);

        service.doAnalysis(meta("s1", SpanMetadata.KIND_CLIENT), Span.getDefaultInstance(),
            Collections.emptyMap(), Collections.emptyMap());
        service.doAnalysis(meta("s2", SpanMetadata.KIND_SERVER), Span.getDefaultInstance(),
            Collections.emptyMap(), Collections.emptyMap());

        assertEquals(List.of(
            "beginSpan s1",
            "condition filter 2 false",
            "step extractor block 5 extractor {",
            "step extractor service 6 renamed",
            "step extractor tag 7 [k, v]",
            "step extractor prepareNetworkAlias 8 backend:8080",
            "pairing caller sent backend:8080",
            "endSpan s1",
            "beginSpan s2",
            "condition filter 2 true",
            "step filter abort 3 null",
            "endSpan s2"), recorder.calls);
    }

    @Test
    void anIfReportsTheOperandsItEvaluatedBeforeItsResult() throws Exception {
        final TalDSL rule = otlpRule("filter {\n"
            + "  if (meta.kind == \"CLIENT\" && meta.service == \"svc\") {\n"
            + "    def paired = true\n"
            + "  }\n"
            + "  if (!hasAttr(\"k\") && meta.latency as Long >= 0) {\n"
            + "    abort {}\n"
            + "  }\n"
            + "}");
        final List<String> calls = new ArrayList<>();
        final TALDebugRecorder recorder = mock(TALDebugRecorder.class);
        doAnswer(invocation -> calls.add("operand " + invocation.getArgument(1) + " = " + invocation.getArgument(2)))
            .when(recorder).operand(any(), any(), any());
        doAnswer(invocation -> calls.add("condition " + invocation.getArgument(2) + " " + invocation.getArgument(4)))
            .when(recorder).condition(any(), any(), anyInt(), any(), anyBoolean());
        rule.getExpression().debugHolder().addRecorder(recorder);
        final TraceAnalyzerServiceImpl service = service(rule);

        service.doAnalysis(meta("s1", SpanMetadata.KIND_SERVER), Span.getDefaultInstance(),
            Collections.emptyMap(), Collections.emptyMap());
        service.doAnalysis(meta("s2", SpanMetadata.KIND_CLIENT), Span.getDefaultInstance(),
            Collections.emptyMap(), Collections.emptyMap());

        assertEquals(List.of(
            // `&&` stops at the first false operand, so the second is neither evaluated nor reported
            "operand meta.kind = SERVER",
            "condition 2 false",
            "operand hasAttr(\"k\") = false",
            "operand meta.latency as Long = 0",
            "condition 5 true",
            "operand meta.kind = CLIENT",
            "operand meta.service = svc",
            "condition 2 true",
            "operand hasAttr(\"k\") = false",
            "operand meta.latency as Long = 0",
            "condition 5 true"), calls);
    }

    @Test
    void aParenthesizedLiteralIsNoOperandAndComparesWithALayer() throws Exception {
        final TalDSL rule = otlpRule("filter {\n"
            + "  if (meta.layer as Layer == (\"OTLP_GENERAL\") && meta.kind == (\"SERVER\")) {\n"
            + "    def paired = true\n"
            + "  }\n"
            + "}");
        final List<String> calls = new ArrayList<>();
        final TALDebugRecorder recorder = mock(TALDebugRecorder.class);
        doAnswer(invocation -> calls.add("operand " + invocation.getArgument(1) + " = " + invocation.getArgument(2)))
            .when(recorder).operand(any(), any(), any());
        doAnswer(invocation -> calls.add("condition " + invocation.getArgument(2) + " " + invocation.getArgument(4)))
            .when(recorder).condition(any(), any(), anyInt(), any(), anyBoolean());
        rule.getExpression().debugHolder().addRecorder(recorder);

        service(rule).doAnalysis(meta("s1", SpanMetadata.KIND_SERVER), Span.getDefaultInstance(),
            Collections.emptyMap(), Collections.emptyMap());

        assertEquals(List.of(
            "operand meta.layer as Layer = OTLP_GENERAL",
            "operand meta.kind = SERVER",
            "condition 2 true"), calls);
    }

    @Test
    void aRuleCompiledWithoutInjectionCarriesNoHolder() throws Exception {
        DSLDebugCodegenSwitch.resetInjection();
        final TalDSL rule = otlpRule("filter { extractor { service \"x\" } }");
        assertNull(rule.getExpression().debugHolder());
        assertEquals(0, rule.getExpression().segmentDefNames().length);
    }

    // ==================== Harness ====================

    private TraceAnalyzerServiceImpl service(final TalDSL rule) {
        final SpanFilterListener.Factory factory = mock(SpanFilterListener.Factory.class);
        when(factory.hasRules()).thenReturn(true);
        when(factory.rulesFor(any(), any()))
            .thenReturn(List.of(new SpanFilterListener.RuleBinding(rule, new OtlpTALSourceTypeProvider())));
        return new TraceAnalyzerServiceImpl(factory, new SpanPairing(), new SeenSpans(0, 1));
    }

    private TalDSL nativeRule(final String dsl) throws Exception {
        return TalDSL.of(moduleManager, new TraceAnalyzerModuleConfig(), dsl, SpanObject.class,
            TALFunctionProvider.SW_NATIVE_INPUT_TYPE, functions, "probe_native_" + Math.abs(dsl.hashCode()), null,
            DslSourceRef.ofRule("tal/test-probes.yaml", 1), new TALMetricShapes(), null, null);
    }

    private TalDSL otlpRule(final String dsl) throws Exception {
        return TalDSL.of(moduleManager, new TraceAnalyzerModuleConfig(), dsl, Span.class, "otlp", functions,
            "probe_otlp_" + Math.abs(dsl.hashCode()) + (DSLDebugCodegenSwitch.isInjectionEnabled() ? "" : "_off"),
            null, DslSourceRef.ofRule("tal/test-probes.yaml", 1), new TALMetricShapes(), null, null);
    }

    private static TestCallRecorder bind(final GateHolder holder) {
        assertNotNull(holder, "the rule carries a capture binding");
        final TestCallRecorder recorder = new TestCallRecorder();
        holder.addRecorder(recorder);
        return recorder;
    }

    private static SpanObject span(final int spanId, final String operationName, final SpanType type) {
        return SpanObject.newBuilder().setSpanId(spanId).setParentSpanId(spanId == 0 ? -1 : 0)
                         .setOperationName(operationName).setSpanType(type)
                         .setStartTime(1_700_000_000_000L).setEndTime(1_700_000_000_010L).build();
    }

    private static SpanMetadata meta(final String spanId, final String kind) {
        return SpanMetadata.builder().service("svc").layer(Layer.OTLP_GENERAL.name()).kind(kind)
                           .traceId("t1").spanId(spanId).parentSpanId("p1").build();
    }

    /**
     * Writes each probe call as one line, the span it is on named by the span id.
     */
    private static final class TestCallRecorder implements TALDebugRecorder {
        private final List<String> calls = new ArrayList<>();

        @Override
        public String sessionId() {
            return "test";
        }

        @Override
        public RuleKey ruleKey() {
            return new RuleKey(Catalog.TAL, "test-probes", "test");
        }

        @Override
        public boolean matches(final RuleKey candidate) {
            return true;
        }

        @Override
        public boolean isCaptured() {
            return false;
        }

        @Override
        public void beginSegment(final SwNativeSegmentState segment) {
            calls.add("beginSegment " + segment.getSegmentObject().getService());
        }

        @Override
        public void beginSpan(final ExecutionContext ctx, final Map<String, String> resource,
                              final Map<String, String> scope) {
            calls.add("beginSpan " + spanId(ctx));
        }

        @Override
        public void beginBuild(final Object span, final int index) {
            calls.add("beginBuild " + ((SpanObject) span).getSpanId());
        }

        @Override
        public void endSpan(final ExecutionContext ctx) {
            calls.add("endSpan " + spanId(ctx));
        }

        @Override
        public void endSegment() {
            calls.add("endSegment");
        }

        @Override
        public void failed(final ExecutionContext ctx, final Throwable failure) {
            calls.add("failed " + failure.getMessage());
        }

        @Override
        public void pairing(final String role, final String outcome, final String peer, final String offeredBy) {
            calls.add("pairing " + role + " " + outcome + " " + peer + (offeredBy == null ? "" : " " + offeredBy));
        }

        @Override
        public void step(final ExecutionContext ctx, final String stage, final String kind, final int line,
                         final String text, final Object value) {
            final Object shown = "block".equals(kind) ? text
                : value instanceof Object[] ? List.of((Object[]) value) : value;
            calls.add("step " + stage + " " + kind + " " + line + " " + shown);
        }

        @Override
        public void condition(final ExecutionContext ctx, final String stage, final int line, final String text,
                              final boolean result) {
            calls.add("condition " + stage + " " + line + " " + result);
        }

        @Override
        public void source(final ExecutionContext ctx, final String stage, final int line, final String text,
                           final ISource source) {
            calls.add("source " + stage + " " + line + " " + DefaultScopeDefine.nameOf(source.scope()));
        }

        @Override
        public void metric(final ExecutionContext ctx, final String stage, final int line, final String text,
                           final String metric, final MeterEntity entity, final Object observation,
                           final long timestamp) {
            calls.add("metric " + metric);
        }

        @Override
        public void metricSkipped(final ExecutionContext ctx, final String stage, final int line, final String text,
                                  final String metric, final String reason) {
            calls.add("metricSkipped " + metric + " " + reason);
        }

        private static String spanId(final ExecutionContext ctx) {
            return ctx.getInput() instanceof SpanObject
                ? String.valueOf(((SpanObject) ctx.getInput()).getSpanId()) : ctx.getMeta().getSpanId();
        }
    }
}
