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

import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.service.IAIEvaluationService;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.source.GenAIEvaluationSample;

/**
 * Hands a {@link GenAIEvaluationSample}, which the TAL rules {@code native-ai-evaluation} and {@code otlp-ai-evaluation}
 * send for a sampled GenAI span, to {@link IAIEvaluationService#sample}, which queues it for the judge; nothing is
 * stored here.
 */
public class GenAIEvaluationSampleDispatcher implements SourceDispatcher<GenAIEvaluationSample> {
    /**
     * Set by the ai-evaluation provider when it registers the service. The source receiver creates dispatchers by
     * class scanning with no constructor arguments, so a dispatcher reaches what it hands to statically, as the core
     * dispatchers reach their stream processors. Null while ai-evaluation is not loaded, when no rule sends the
     * source: the rule file requires the module.
     */
    private static volatile IAIEvaluationService EVALUATION_SERVICE;

    /**
     * @param service the evaluation service the ai-evaluation provider registered, active or inactive
     */
    public static void bind(final IAIEvaluationService service) {
        EVALUATION_SERVICE = service;
    }

    @Override
    public void dispatch(final GenAIEvaluationSample sample) {
        final IAIEvaluationService service = EVALUATION_SERVICE;
        if (service == null) {
            return;
        }
        final AIEvaluationContext.AIEvaluationContextBuilder context = AIEvaluationContext.builder()
            .traceId(sample.getTraceId())
            .traceRefType(sample.getTraceRefType())
            .segmentId(sample.getSegmentId())
            .spanIndex(sample.getSpanIndex())
            .spanId(sample.getSpanId())
            .serviceName(sample.getServiceName())
            .serviceInstanceName(sample.getServiceInstanceName())
            .operationName(sample.getOperationName())
            .providerName(sample.getProviderName())
            .modelName(sample.getModelName())
            .startTimeMillis(sample.getStartTimeMillis())
            .endTimeMillis(sample.getEndTimeMillis())
            .error(sample.isError());
        if (sample.getTags() != null) {
            context.tags(sample.getTags());
        }
        service.sample(context.build());
    }
}
