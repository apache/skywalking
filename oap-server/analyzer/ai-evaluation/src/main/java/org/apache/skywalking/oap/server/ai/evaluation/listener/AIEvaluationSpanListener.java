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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.ai.evaluation.AIEvaluationModule;
import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.service.IAIEvaluationService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.trace.OTLPSpanReader;
import org.apache.skywalking.oap.server.core.trace.SpanListener;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.library.util.genai.GenAIContextResolver;
import org.apache.skywalking.oap.server.library.util.genai.GenAISemanticAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AIEvaluationSpanListener implements SpanListener {
    private static final String ERROR_TAG = "error";
    private static final String STATUS_CODE_ERROR = "STATUS_CODE_ERROR";
    private static final long MILLIS_PER_MICRO = 1_000L;
    private static final String[] REQUIRED_TAG_KEYS = new String[]{
            GenAISemanticAttributes.RESPONSE_MODEL,
            GenAISemanticAttributes.PROVIDER_NAME,
            GenAISemanticAttributes.SYSTEM_NAME,
            GenAISemanticAttributes.OPERATION_NAME,
            GenAISemanticAttributes.INPUT_MESSAGES,
            GenAISemanticAttributes.OUTPUT_MESSAGES,
            ERROR_TAG
    };

    private IAIEvaluationService evaluationService;
    private NamingControl namingControl;

    @Override
    public String[] requiredModules() {
        return new String[]{
                AIEvaluationModule.NAME,
                CoreModule.NAME
        };
    }

    @Override
    public void init(final ModuleManager moduleManager) {
        evaluationService = moduleManager.find(AIEvaluationModule.NAME)
                .provider()
                .getService(IAIEvaluationService.class);
        namingControl = moduleManager.find(CoreModule.NAME)
                .provider()
                .getService(NamingControl.class);
    }

    @Override
    public SpanListenerResult onZipkinSpan(final ZipkinSpan span) {
        final Map<String, String> tags = toRequiredTagMap(span.getTags());
        if (!isGenAISpan(tags)) {
            return SpanListenerResult.CONTINUE;
        }
        if (!shouldSample(span.getTraceId())) {
            return SpanListenerResult.CONTINUE;
        }
        sample(GenAITraceRefType.ZIPKIN,
                span.getTraceId(),
                span.getSpanId(),
                span.getLocalEndpointServiceName(),
                span.getName(),
                span.getTimestampMillis(),
                span.getTimestampMillis() + span.getDuration() / MILLIS_PER_MICRO,
                tags,
                "true".equalsIgnoreCase(tags.get(ERROR_TAG)));
        return SpanListenerResult.CONTINUE;
    }

    /**
     * The same sampling over a natively stored OTLP span, which never becomes a Zipkin span. The {@code error}
     * tag the Zipkin conversion writes is derived from the span status here, so the evaluation context reads the
     * same on both paths.
     */
    @Override
    public SpanListenerResult onNativeOTLPSpan(final OTLPSpanReader span,
                                               final String serviceName,
                                               final Map<String, String> resourceAttributes,
                                               final String scopeName,
                                               final String scopeVersion) {
        final Map<String, String> attributes = span.attributes();
        final Map<String, String> tags = new HashMap<>();
        for (String requiredKey : REQUIRED_TAG_KEYS) {
            final String value = attributes.get(requiredKey);
            if (StringUtil.isNotEmpty(value)) {
                tags.put(requiredKey, value);
            }
        }
        final boolean error = STATUS_CODE_ERROR.equals(span.statusCode());
        if (error) {
            tags.put(ERROR_TAG, "true");
        }
        if (!isGenAISpan(tags)) {
            return SpanListenerResult.CONTINUE;
        }
        if (!shouldSample(span.traceId())) {
            return SpanListenerResult.CONTINUE;
        }
        sample(GenAITraceRefType.OTLP,
                span.traceId(),
                span.spanId(),
                // Through the naming rules, as the stored span's service is, so the record joins back to it.
                namingControl.formatServiceName(serviceName),
                span.spanName(),
                TimeUnit.NANOSECONDS.toMillis(span.startTimeNanos()),
                TimeUnit.NANOSECONDS.toMillis(span.endTimeNanos()),
                tags,
                error);
        return SpanListenerResult.CONTINUE;
    }

    private void sample(final GenAITraceRefType traceRefType,
                        final String traceId,
                        final String spanId,
                        final String serviceName,
                        final String operationName,
                        final long startTimeMillis,
                        final long endTimeMillis,
                        final Map<String, String> tags,
                        final boolean error) {
        final GenAIContextResolver.Result genAIContext = GenAIContextResolver.resolve(tags);
        evaluationService.sample(AIEvaluationContext.builder()
                .traceId(traceId)
                .traceRefType(traceRefType)
                .spanId(spanId)
                .serviceName(serviceName)
                .operationName(operationName)
                .providerName(genAIContext.getProviderName())
                .modelName(genAIContext.getModelName())
                .startTimeMillis(startTimeMillis)
                .endTimeMillis(endTimeMillis)
                .error(error)
                .tags(tags)
                .build());
    }

    private boolean shouldSample(final String traceId) {
        return evaluationService.shouldSample(traceId);
    }

    private static boolean isGenAISpan(final Map<String, String> tags) {
        return StringUtil.isNotBlank(tags.get(GenAISemanticAttributes.RESPONSE_MODEL));
    }

    private static Map<String, String> toRequiredTagMap(final JsonObject tags) {
        final Map<String, String> result = new HashMap<>();
        if (tags == null) {
            return result;
        }
        for (String requiredKey : REQUIRED_TAG_KEYS) {
            final JsonElement value = tags.get(requiredKey);
            if (value != null && !value.isJsonNull()) {
                final String tagValue = value.getAsString();
                if (!tagValue.isEmpty()) {
                    result.put(requiredKey, tagValue);
                }
            }
        }
        return result;
    }
}
