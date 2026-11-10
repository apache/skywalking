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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.GenAIEvaluationSample;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.core.trace.SpanAnalysisResult;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.ShippedRules;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALFunctionRegistry;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALMetricShapes;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TALTestModules;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalDSL;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener.SpanFilterListener;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.SwNativeTALSourceTypeProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shipped {@code otlp-ai-evaluation} rule run among a span's other rules, as the analysis runs it: the sample is
 * taken whatever the span's storage verdict, once per remembered span, and a failure to hand it over costs only the
 * rule that failed.
 */
class OtlpAIEvaluationAnalysisTest {
    private static final String MODEL = "gpt-4.1-mini";

    private final List<ISource> sent = new ArrayList<>();
    private ModuleManager moduleManager;
    private SourceReceiver sourceReceiver;
    private SpanFilterListener.Factory factory;
    private TALFunctionRegistry functions;
    private TalDSL evaluation;

    @BeforeAll
    static void declareScope() {
        if (DefaultScopeDefine.classOf("GenAIEvaluationSample") == null) {
            new DefaultScopeDefine.Listener().notify(GenAIEvaluationSample.class);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        sourceReceiver = mock(SourceReceiver.class);
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(sourceReceiver).receive(any());
        TALTestModules.stubServices(moduleManager, coreProvider, mock(MeterSystem.class),
            new NamingControl(70, 70, 150, new EndpointNameGrouping()), sourceReceiver);
        factory = mock(SpanFilterListener.Factory.class);
        when(factory.hasRules()).thenReturn(true);

        functions = new TALFunctionRegistry(List.of(new TestEvaluationSettings()));
        functions.bind(null, List.of(new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider()));
        final TALConfig shipped = ShippedRules.configs("otlp-ai-evaluation").getRules().get(0);
        evaluation = compile(shipped.getDsl(), shipped.getName(),
            ShippedRules.ref("otlp-ai-evaluation", shipped.getName()));
    }

    @Test
    void aSpanAnotherRuleKeepsOutOfStorageIsStillEvaluated() {
        // The shipped files run otlp-general before otlp-ai-evaluation; its persist {} vote does not stop the next
        // rule.
        given(stub(ctx -> ctx.votePersist(false)), binding(evaluation));

        final SpanAnalysisResult result = analyze(new TraceAnalyzerServiceImpl(factory, new SpanPairing(),
            new SeenSpans(0, 1)));

        assertFalse(result.isPersist(), "the span is not stored");
        assertEquals(1, sent.size(), "but it is sampled: " + sent);
        assertInstanceOf(GenAIEvaluationSample.class, sent.get(0));
    }

    @Test
    void aSpanSentAgainIsEvaluatedOnceWhileTheNodeRemembersIt() {
        given(binding(evaluation));
        final TraceAnalyzerServiceImpl remembering = new TraceAnalyzerServiceImpl(factory, new SpanPairing(),
            new SeenSpans(60, 1000));
        analyze(remembering);
        analyze(remembering);
        assertEquals(1, sent.size(), "the second delivery reaches no rule: " + sent);

        // dedupWindow 0, the default, remembers nothing: each delivery is evaluated.
        final TraceAnalyzerServiceImpl forgetting = new TraceAnalyzerServiceImpl(factory, new SpanPairing(),
            new SeenSpans(0, 1));
        analyze(forgetting);
        analyze(forgetting);
        assertEquals(3, sent.size(), sent.toString());
    }

    @Test
    void aSampleTheReceiverRejectsFailsOnlyTheEvaluationRule() {
        Mockito.doThrow(new IllegalStateException("evaluation queue closed")).when(sourceReceiver).receive(any());
        given(binding(evaluation), stub(ctx -> ctx.addTag("still", "ran")));

        final SpanAnalysisResult result = analyze(new TraceAnalyzerServiceImpl(factory, new SpanPairing(),
            new SeenSpans(0, 1)));

        assertTrue(result.isPersist(), "the span is stored as if the rule had not run");
        assertEquals(Map.of("still", "ran"), result.getAdditionalTags(), "the rules after it still run");
    }

    @Test
    void aRuleWhoseSourceFailsHasItsPersistVoteIgnored() throws Exception {
        // The vote is cast before the source statement; the dispatch failure surfaces from the rule, so its vote is
        // not counted.
        final TalDSL dropsThenSends = compile("filter {\n"
            + "  persist { dropper {} }\n"
            + "  extractor {\n"
            + "    source GenAIEvaluationSample {\n"
            + "      traceId meta.traceId\n"
            + "      spanId meta.spanId\n"
            + "      timeBucket recordTimeBucket(meta.startTime)\n"
            + "    }\n"
            + "  }\n"
            + "}", "drops_then_sends", DslSourceRef.ofRule("tal/test-drops-then-sends.yaml", 1));
        given(binding(dropsThenSends));

        assertFalse(analyze(new TraceAnalyzerServiceImpl(factory, new SpanPairing(), new SeenSpans(0, 1)))
            .isPersist(), "the vote counts when the source is handed over");
        assertEquals(1, sent.size(), sent.toString());

        Mockito.doThrow(new IllegalStateException("dispatcher failed")).when(sourceReceiver).receive(any());
        assertTrue(analyze(new TraceAnalyzerServiceImpl(factory, new SpanPairing(), new SeenSpans(0, 1)))
            .isPersist(), "the failed rule's drop vote is not counted");
    }

    private TalDSL compile(final String dsl, final String name, final DslSourceRef ref) throws Exception {
        return TalDSL.of(moduleManager, new TraceAnalyzerModuleConfig(), dsl, Span.class, "otlp", functions, name,
            null, ref, new TALMetricShapes(), null, null);
    }

    private SpanAnalysisResult analyze(final TraceAnalyzerServiceImpl service) {
        final SpanMetadata meta = SpanMetadata.builder()
                                              .service("chatbot")
                                              .layer(Layer.OTLP_GENERAL.name())
                                              .kind(SpanMetadata.KIND_CLIENT)
                                              .statusCode(SpanMetadata.STATUS_UNSET)
                                              .startTime(1_700_000_000_123L)
                                              .timestamp(1_700_000_001_987L)
                                              .traceId("4bf92f3577b34da6a3ce929d0e0e4736")
                                              .spanId("00f067aa0ba902b7")
                                              .traceStorage("otlp")
                                              .build();
        final Span span = Span.newBuilder()
                              .setName("chat " + MODEL)
                              .setStartTimeUnixNano(1_700_000_000_123_456_789L)
                              .setEndTimeUnixNano(1_700_000_001_987_654_321L)
                              .addAttributes(KeyValue.newBuilder().setKey("gen_ai.response.model")
                                                     .setValue(AnyValue.newBuilder().setStringValue(MODEL)))
                              .build();
        return service.doAnalysis(meta, span, Collections.emptyMap(), Collections.emptyMap());
    }

    private void given(final SpanFilterListener.RuleBinding... bindings) {
        when(factory.rulesFor(any(), any())).thenReturn(List.of(bindings));
    }

    private static SpanFilterListener.RuleBinding binding(final TalDSL dsl) {
        return new SpanFilterListener.RuleBinding(dsl, new OtlpTALSourceTypeProvider());
    }

    /**
     * Another rule of the span's layer, standing in for compiled DSL.
     */
    private static SpanFilterListener.RuleBinding stub(final Consumer<ExecutionContext> body) {
        final TalDSL dsl = mock(TalDSL.class);
        doAnswer(invocation -> {
            body.accept(invocation.getArgument(0));
            return null;
        }).when(dsl).evaluate(any());
        return new SpanFilterListener.RuleBinding(dsl, new OtlpTALSourceTypeProvider());
    }

    /**
     * {@code ai-evaluation}'s sampling settings, which this module's tests do not load: active, every trace sampled.
     */
    public static final class TestEvaluationSettings implements TALFunctionProvider {
        @Override
        public String module() {
            return "ai-evaluation";
        }

        @Override
        public boolean supportsInputType(final String inputType) {
            return true;
        }

        public boolean evaluationActive() {
            return true;
        }

        public int evaluationSampleRate() {
            return 1_000_000;
        }
    }
}
