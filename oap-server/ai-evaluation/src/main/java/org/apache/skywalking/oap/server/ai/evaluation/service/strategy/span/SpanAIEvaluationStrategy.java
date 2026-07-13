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

package org.apache.skywalking.oap.server.ai.evaluation.service.strategy.span;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.context.GenAISemanticAttributes;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelProvider;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelResponse;
import org.apache.skywalking.oap.server.ai.evaluation.level.EvaluationLevelResolver;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationPlan;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationPlanner;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationPromptBuilder;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationResult;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationResultParser;
import org.apache.skywalking.oap.server.ai.evaluation.service.AIEvaluationMetricReporter;
import org.apache.skywalking.oap.server.ai.evaluation.service.strategy.AIEvaluationStrategy;
import org.apache.skywalking.oap.server.ai.evaluation.task.EvaluationTaskRegistry;
import org.apache.skywalking.oap.server.ai.evaluation.value.ValueType;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.genai.GenAIEvaluationRecord;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class SpanAIEvaluationStrategy implements AIEvaluationStrategy {
    private static final String CHAT_OPERATION = "chat";

    private final EvaluationTaskRegistry taskRegistry;
    private final EvaluationPlanner evaluationPlanner;
    private final EvaluationPromptBuilder promptBuilder;
    private final EvaluationResultParser resultParser;
    private final AIEvaluationMetricReporter metricReporter;
    private final NamingControl namingControl;
    private final EvaluationLevelResolver levelResolver;

    public SpanAIEvaluationStrategy(final EvaluationTaskRegistry taskRegistry,
                                    final EvaluationPlanner evaluationPlanner,
                                    final EvaluationPromptBuilder promptBuilder,
                                    final EvaluationResultParser resultParser,
                                    final AIEvaluationMetricReporter metricReporter,
                                    final NamingControl namingControl,
                                    final EvaluationLevelResolver levelResolver) {
        this.taskRegistry = taskRegistry;
        this.evaluationPlanner = evaluationPlanner;
        this.promptBuilder = promptBuilder;
        this.resultParser = resultParser;
        this.metricReporter = metricReporter;
        this.namingControl = namingControl;
        this.levelResolver = levelResolver;
    }

    @Override
    public boolean support(final AIEvaluationContext context) {
        return context != null && StringUtil.isNotEmpty(context.getTraceId())
                && StringUtil.isNotEmpty(context.getSpanId());
    }

    @Override
    public String taskId(final AIEvaluationContext context) {
        return context.getTraceId() + "-" + context.getSpanId();
    }

    @Override
    public void evaluate(final AIEvaluationContext context,
                         final JudgeModelProvider judgeModelProvider) throws IOException, InterruptedException {
        if (taskRegistry.isEmpty()) {
            log.debug("Skip GenAI span evaluation, no evaluation task configured, taskId: {}", taskId(context));
            return;
        }

        if (validLLMCallSpan(context)) {
            evaluateLLMCallSpan(context, judgeModelProvider);
        }
    }

    private void evaluateLLMCallSpan(final AIEvaluationContext context,
                                     final JudgeModelProvider judgeModelProvider)
            throws IOException, InterruptedException {
        final String judgeModel = judgeModelProvider.model();
        final List<EvaluationPlan> plans = evaluationPlanner.plan(context, taskRegistry.tasks());
        for (EvaluationPlan plan : plans) {
            final Optional<JudgeModelResponse> response = judgeModelProvider.judge(promptBuilder.build(plan));
            if (response.isEmpty()) {
                continue;
            }

            final JudgeModelResponse judgeResponse = response.get();
            final List<EvaluationResult> results = resultParser.parse(plan, judgeResponse.getContent());
            persistResults(context, plan, results, judgeModel);
        }
    }

    private void persistResults(final AIEvaluationContext context,
                                final EvaluationPlan plan,
                                final List<EvaluationResult> results,
                                final String judgeModel) {
        final long evaluationTime = System.currentTimeMillis();
        for (EvaluationResult result : results) {
            final String serviceId = IDManager.ServiceID.buildId(
                    namingControl.formatServiceName(context.getProviderName()),
                    Layer.VIRTUAL_GENAI.isNormal()
            );
            final GenAIEvaluationRecord record = new GenAIEvaluationRecord();
            record.setUniqueId(UUID.randomUUID().toString().replace("-", ""));
            record.setTraceId(context.getTraceId());
            record.setServiceId(serviceId);
            record.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(
                    serviceId,
                    namingControl.formatServiceName(context.getModelName())
            ));
            record.setSegmentId(context.getSegmentId());
            record.setSpanId(context.getSpanId());
            record.setSpanType(plan.getSpanType() == null ? "" : plan.getSpanType().name());
            record.setTaskName(result.getName());
            record.setValueType(result.getValueType() == null ? "" : result.getValueType().name());
            record.setValue(result.getValue());
            record.setEvaluationLevel(levelResolver.resolve(result.getValueType(), result.getValue()));
            record.setReason(result.getReason());
            record.setJudgeModel(judgeModel);
            record.setEvaluationTime(evaluationTime);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(evaluationTime));
            RecordStreamProcessor.getInstance().in(record);

            if (result.getValueType() == ValueType.SCORE) {
                metricReporter.reportScore(context, result, evaluationTime);
            }
        }
    }

    private static boolean validLLMCallSpan(final AIEvaluationContext context) {
        if (!CHAT_OPERATION.equalsIgnoreCase(operationName(context))) {
            return false;
        }

        final String inputMessages = context.getTags().get(GenAISemanticAttributes.INPUT_MESSAGES);
        final String outputMessages = context.getTags().get(GenAISemanticAttributes.OUTPUT_MESSAGES);
        if (StringUtil.isEmpty(inputMessages) || StringUtil.isEmpty(outputMessages)) {
            log.warn(
                    "Skip GenAI span evaluation, missing input or output messages,trace id :{}",
                    context.getTraceId()
            );
            return false;
        }
        return true;
    }

    private static String operationName(final AIEvaluationContext context) {
        return context.getTags().get(GenAISemanticAttributes.OPERATION_NAME);
    }

}
