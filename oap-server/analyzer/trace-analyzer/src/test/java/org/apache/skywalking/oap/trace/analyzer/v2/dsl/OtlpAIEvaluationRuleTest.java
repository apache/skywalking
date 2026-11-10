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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.GenAIEvaluationSample;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.util.genai.GenAIModelMatcher;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.trace.analyzer.v2.ShippedRules;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALClassGenerator;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALFunctionRegistry;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.SwNativeTALSourceTypeProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shipped {@code otlp-ai-evaluation} rule over OTLP spans: which spans it samples for evaluation, and the
 * {@code GenAIEvaluationSample} it sends, field by field.
 */
class OtlpAIEvaluationRuleTest {
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String MODEL = "gpt-4.1-mini";
    private static final long START_NANOS = 1_700_000_000_123_456_789L;
    private static final long END_NANOS = 1_700_000_001_987_654_321L;

    private final List<ISource> sent = new ArrayList<>();
    private FilterSpec filterSpec;
    private TalExpression rule;

    @BeforeAll
    static void declareScope() {
        if (DefaultScopeDefine.classOf("GenAIEvaluationSample") == null) {
            new DefaultScopeDefine.Listener().notify(GenAIEvaluationSample.class);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        TestEvaluationFunctions.ACTIVE = true;
        TestEvaluationFunctions.SAMPLE_RATE = 1_000_000;
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        final SourceReceiver sourceReceiver = mock(SourceReceiver.class);
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(sourceReceiver).receive(any());
        filterSpec = TALTestModules.filterSpec(moduleManager, coreProvider, mock(MeterSystem.class),
            new NamingControl(12, 70, 150, new EndpointNameGrouping()), sourceReceiver);

        final TALFunctionRegistry functions = new TALFunctionRegistry(List.of(new TestEvaluationFunctions()));
        functions.bind(null, List.of(new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider()));
        final TALConfig shipped = ShippedRules.configs("otlp-ai-evaluation").getRules().get(0);
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("otlp_ai_evaluation");
        generator.setInputType(Span.class);
        generator.setInputAlias("otlp");
        generator.setFunctionRegistry(functions);
        generator.setSourceRef(ShippedRules.ref("otlp-ai-evaluation", shipped.getName()));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        rule = generator.compile(shipped.getDsl());
    }

    @Test
    void aGenAISpanIsSampledWithItsAttributesAndTheSpansOwnTimes() {
        run(meta("shop::chatbot", "ERROR", "otlp"), span(
            attribute("gen_ai.response.model", MODEL), attribute("gen_ai.provider.name", "openai"),
            attribute("gen_ai.operation.name", "chat"), attribute("gen_ai.input.messages", "[{\"role\":\"user\"}]"),
            attribute("gen_ai.output.messages", ""), attribute("http.route", "/chat")));

        final GenAIEvaluationSample sample = onlySample();
        assertEquals(TRACE_ID, sample.getTraceId());
        assertEquals(GenAITraceRefType.OTLP, sample.getTraceRefType());
        assertEquals(SPAN_ID, sample.getSpanId());
        assertNull(sample.getSpanIndex(), "an OTLP span is named by its id");
        assertNull(sample.getSegmentId());
        assertEquals("shop::chatbo", sample.getServiceName(), "through the naming rules, here a 12-character limit");
        assertEquals("chat " + MODEL, sample.getOperationName());
        assertEquals("openai", sample.getProviderName());
        assertEquals(MODEL, sample.getModelName());
        assertEquals(1_700_000_000_123L, sample.getStartTimeMillis());
        assertEquals(1_700_000_001_987L, sample.getEndTimeMillis());
        assertTrue(sample.isError());
        assertEquals(Map.of("gen_ai.response.model", MODEL, "gen_ai.provider.name", "openai",
            "gen_ai.operation.name", "chat", "gen_ai.input.messages", "[{\"role\":\"user\"}]", "error", "true"),
            sample.getTags(), "the attributes the judge reads, an empty one left out, and the error status");
        assertEquals(TimeBucket.getRecordTimeBucket(1_700_000_000_123L), sample.getTimeBucket());
    }

    @Test
    void theProviderIsTheDeclaredOneThenTheLegacySystemThenTheModelsMatch() {
        run(meta("svc", "OK", "otlp"), span(attribute("gen_ai.response.model", MODEL),
            attribute("gen_ai.system", "azure"), attribute("error", "boom")));
        final GenAIEvaluationSample legacy = onlySample();
        assertEquals("azure", legacy.getProviderName());
        assertEquals(false, legacy.isError(), "only the span status sets the error");
        assertEquals("boom", legacy.getTags().get("error"));

        run(meta("svc", "UNSET", "otlp"), span(attribute("gen_ai.response.model", MODEL)));
        assertEquals(GenAIModelMatcher.getInstance().match(MODEL).getProvider(), onlySample().getProviderName());
    }

    @Test
    void onlySampledGenAISpansOfNativelyStoredTracesAreSent() {
        final Span genAI = span(attribute("gen_ai.response.model", MODEL));
        run(meta("svc", "OK", "zipkin"), genAI);
        assertTrue(sent.isEmpty(), "a span stored as a Zipkin span is sampled on the Zipkin path");
        run(meta("svc", "OK", "otlp"), span(attribute("gen_ai.request.model", MODEL)));
        assertTrue(sent.isEmpty(), "a span that names no answering model is no GenAI span");
        run(meta("svc", "OK", "otlp"), span(attribute("gen_ai.response.model", "  ")));
        assertTrue(sent.isEmpty(), "a blank model names none");
        run(SpanMetadata.builder().service("svc").traceStorage("otlp").build(), genAI);
        assertTrue(sent.isEmpty(), "a span without a trace id is not sampled");
        TestEvaluationFunctions.ACTIVE = false;
        run(meta("svc", "OK", "otlp"), genAI);
        assertTrue(sent.isEmpty(), "an inactive evaluation samples nothing");
        TestEvaluationFunctions.ACTIVE = true;
        TestEvaluationFunctions.SAMPLE_RATE = 0;
        run(meta("svc", "OK", "otlp"), genAI);
        assertTrue(sent.isEmpty(), "a sample rate of 0 samples nothing");
        TestEvaluationFunctions.SAMPLE_RATE = 1_000_000;
        run(meta("svc", "OK", "otlp"), genAI);
        onlySample();
    }

    private void run(final SpanMetadata meta, final Span span) {
        final ExecutionContext ctx = new ExecutionContext();
        ctx.setSourceTypeProvider(new OtlpTALSourceTypeProvider());
        ctx.init(meta, span, Collections.emptyMap(), Collections.emptyMap());
        rule.execute(filterSpec, ctx);
    }

    private GenAIEvaluationSample onlySample() {
        assertEquals(1, sent.size(), "one sample: " + sent);
        return (GenAIEvaluationSample) sent.remove(0);
    }

    private static SpanMetadata meta(final String service, final String statusCode, final String traceStorage) {
        return SpanMetadata.builder().service(service).layer("OTLP_GENERAL").kind(SpanMetadata.KIND_CLIENT)
                           .statusCode(statusCode).startTime(1_700_000_000_123L).timestamp(1_700_000_001_987L)
                           .traceId(TRACE_ID).spanId(SPAN_ID).traceStorage(traceStorage).build();
    }

    private static Span span(final KeyValue... attributes) {
        return Span.newBuilder().setName("chat " + MODEL).setStartTimeUnixNano(START_NANOS)
                   .setEndTimeUnixNano(END_NANOS).setStatus(Status.newBuilder().build())
                   .addAllAttributes(List.of(attributes)).build();
    }

    private static KeyValue attribute(final String key, final String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }

    /**
     * {@code ai-evaluation}'s sampling settings, which this module's tests do not load.
     */
    public static final class TestEvaluationFunctions implements TALFunctionProvider {
        static boolean ACTIVE;
        static int SAMPLE_RATE;

        @Override
        public String module() {
            return "ai-evaluation";
        }

        @Override
        public boolean supportsInputType(final String inputType) {
            return true;
        }

        public boolean evaluationActive() {
            return ACTIVE;
        }

        public int evaluationSampleRate() {
            return SAMPLE_RATE;
        }
    }
}
