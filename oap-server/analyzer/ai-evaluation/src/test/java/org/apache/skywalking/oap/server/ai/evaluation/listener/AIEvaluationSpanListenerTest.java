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

import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.ai.evaluation.AIEvaluationModule;
import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.service.IAIEvaluationService;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.util.genai.GenAISemanticAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIEvaluationSpanListenerTest {
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String MODEL = "gpt-4.1-mini";

    private IAIEvaluationService evaluationService;
    private AIEvaluationSpanListener listener;

    @BeforeEach
    void setUp() {
        evaluationService = mock(IAIEvaluationService.class);
        when(evaluationService.shouldSample(anyString())).thenReturn(true);
        final ModuleServiceHolder provider = mock(ModuleServiceHolder.class);
        when(provider.getService(IAIEvaluationService.class)).thenReturn(evaluationService);
        final ModuleProviderHolder module = mock(ModuleProviderHolder.class);
        when(module.provider()).thenReturn(provider);
        final ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(AIEvaluationModule.NAME)).thenReturn(module);
        listener = new AIEvaluationSpanListener();
        listener.init(moduleManager);
    }

    @Test
    void recordsAZipkinSpanAsStoredInZipkin() {
        final JsonObject tags = new JsonObject();
        tags.addProperty(GenAISemanticAttributes.RESPONSE_MODEL, MODEL);
        final ZipkinSpan span = new ZipkinSpan();
        span.setTraceId(TRACE_ID);
        span.setSpanId(SPAN_ID);
        span.setTags(tags);

        listener.onZipkinSpan(span);

        final AIEvaluationContext context = sampledContext();
        assertEquals(GenAITraceRefType.ZIPKIN, context.getTraceRefType());
        assertEquals(SPAN_ID, context.getSpanId());
    }

    private AIEvaluationContext sampledContext() {
        final ArgumentCaptor<AIEvaluationContext> captor = ArgumentCaptor.forClass(AIEvaluationContext.class);
        verify(evaluationService).sample(captor.capture());
        return captor.getValue();
    }
}
