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

package org.apache.skywalking.oap.server.ai.evaluation.listener;

import java.util.Map;
import org.apache.skywalking.oap.server.ai.evaluation.AIEvaluationModule;
import org.apache.skywalking.oap.server.ai.evaluation.AIEvaluationSamplingConfig;
import org.apache.skywalking.oap.server.ai.evaluation.AIEvaluationTALFunctions;
import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.service.IAIEvaluationService;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.source.GenAIEvaluationSample;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenAIEvaluationSampleDispatcherTest {
    @AfterEach
    void unbind() {
        GenAIEvaluationSampleDispatcher.bind(null);
    }

    @Test
    void theSampleReachesTheEvaluationServiceAsTheRuleSetIt() {
        final IAIEvaluationService evaluationService = mock(IAIEvaluationService.class);
        GenAIEvaluationSampleDispatcher.bind(evaluationService);
        final GenAIEvaluationSample sample = new GenAIEvaluationSample();
        sample.setTraceId("t1");
        sample.setTraceRefType(GenAITraceRefType.SKYWALKING_NATIVE);
        sample.setSegmentId("s1");
        sample.setSpanIndex(3);
        sample.setServiceName("svc");
        sample.setServiceInstanceName("inst");
        sample.setOperationName("chat");
        sample.setProviderName("openai");
        sample.setModelName("gpt-4o");
        sample.setStartTimeMillis(100L);
        sample.setEndTimeMillis(250L);
        sample.setError(true);
        sample.setTags(Map.of("gen_ai.response.model", "gpt-4o"));
        new GenAIEvaluationSampleDispatcher().dispatch(sample);

        final ArgumentCaptor<AIEvaluationContext> sampled = ArgumentCaptor.forClass(AIEvaluationContext.class);
        verify(evaluationService).sample(sampled.capture());
        final AIEvaluationContext context = sampled.getValue();
        assertEquals("t1", context.getTraceId());
        assertEquals(GenAITraceRefType.SKYWALKING_NATIVE, context.getTraceRefType());
        assertEquals("s1", context.getSegmentId());
        assertEquals(3, context.getSpanIndex());
        assertEquals("svc", context.getServiceName());
        assertEquals("inst", context.getServiceInstanceName());
        assertEquals("chat", context.getOperationName());
        assertEquals("openai", context.getProviderName());
        assertEquals("gpt-4o", context.getModelName());
        assertEquals(100L, context.getStartTimeMillis());
        assertEquals(250L, context.getEndTimeMillis());
        assertTrue(context.isError());
        assertEquals(Map.of("gen_ai.response.model", "gpt-4o"), context.getTags());
    }

    @Test
    void anOtlpSampleNamesItsSpanByIdAndNoSegment() {
        final IAIEvaluationService evaluationService = mock(IAIEvaluationService.class);
        GenAIEvaluationSampleDispatcher.bind(evaluationService);
        final GenAIEvaluationSample sample = new GenAIEvaluationSample();
        sample.setTraceId("t2");
        sample.setTraceRefType(GenAITraceRefType.OTLP);
        sample.setSpanId("00f067aa0ba902b7");
        sample.setServiceName("shop::chatbot");
        new GenAIEvaluationSampleDispatcher().dispatch(sample);

        final ArgumentCaptor<AIEvaluationContext> sampled = ArgumentCaptor.forClass(AIEvaluationContext.class);
        verify(evaluationService).sample(sampled.capture());
        final AIEvaluationContext context = sampled.getValue();
        assertEquals(GenAITraceRefType.OTLP, context.getTraceRefType());
        assertEquals("00f067aa0ba902b7", context.getSpanId());
        assertNull(context.getSpanIndex(), "an OTLP span has no index in a segment");
        assertNull(context.getSegmentId());
        final String json = sample.toJson();
        assertTrue(json.contains("\"traceRefType\":\"OTLP\"") && json.contains("\"spanId\":\"00f067aa0ba902b7\"")
            && json.contains("\"spanIndex\":null"), json);
    }

    @Test
    void aSampleWithoutTagsGetsTheEmptyMapAndNothingBoundTakesNothing() {
        new GenAIEvaluationSampleDispatcher().dispatch(new GenAIEvaluationSample());
        final IAIEvaluationService evaluationService = mock(IAIEvaluationService.class);
        GenAIEvaluationSampleDispatcher.bind(evaluationService);
        new GenAIEvaluationSampleDispatcher().dispatch(new GenAIEvaluationSample());
        final ArgumentCaptor<AIEvaluationContext> sampled = ArgumentCaptor.forClass(AIEvaluationContext.class);
        verify(evaluationService).sample(sampled.capture());
        assertTrue(sampled.getValue().getTags().isEmpty());
    }

    @Test
    void theFunctionsReadTheSamplingDecisionsOfTheModule() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder module = mock(ModuleProviderHolder.class);
        when(module.provider()).thenReturn(services);
        when(moduleManager.find(AIEvaluationModule.NAME)).thenReturn(module);
        when(services.getService(AIEvaluationSamplingConfig.class)).thenReturn(new AIEvaluationSamplingConfig(true, 1500));
        final AIEvaluationTALFunctions functions = new AIEvaluationTALFunctions();
        assertEquals(AIEvaluationModule.NAME, functions.module());
        assertTrue(functions.supportsInputType(TALFunctionProvider.SW_NATIVE_INPUT_TYPE));
        assertTrue(functions.supportsInputType("otlp"));
        functions.bind(moduleManager);
        assertTrue(functions.evaluationActive());
        assertEquals(1500, functions.evaluationSampleRate());
    }
}
