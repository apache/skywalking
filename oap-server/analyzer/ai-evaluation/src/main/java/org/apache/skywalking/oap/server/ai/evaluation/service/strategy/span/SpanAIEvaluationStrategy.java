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
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelException;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelException.Reason;
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
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.genai.GenAIEvaluationRecord;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.library.util.genai.GenAISemanticAttributes;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;

import java.io.IOException;
import java.util.List;

@Slf4j
public class SpanAIEvaluationStrategy implements AIEvaluationStrategy {
    private final EvaluationTaskRegistry taskRegistry;
    private final EvaluationPlanner evaluationPlanner;
    private final EvaluationPromptBuilder promptBuilder;
    private final EvaluationResultParser resultParser;
    private final AIEvaluationMetricReporter metricReporter;
    private final NamingControl namingControl;
    private final EvaluationLevelResolver levelResolver;
    private final CounterMetrics incompleteSpanCounter;

    public SpanAIEvaluationStrategy(final EvaluationTaskRegistry taskRegistry,
                                    final EvaluationPlanner evaluationPlanner,
                                    final EvaluationPromptBuilder promptBuilder,
                                    final EvaluationResultParser resultParser,
                                    final AIEvaluationMetricReporter metricReporter,
                                    final NamingControl namingControl,
                                    final EvaluationLevelResolver levelResolver,
                                    final CounterMetrics incompleteSpanCounter) {
        this.taskRegistry = taskRegistry;
        this.evaluationPlanner = evaluationPlanner;
        this.promptBuilder = promptBuilder;
        this.resultParser = resultParser;
        this.metricReporter = metricReporter;
        this.namingControl = namingControl;
        this.levelResolver = levelResolver;
        this.incompleteSpanCounter = incompleteSpanCounter;
    }

    @Override
    public boolean support(final AIEvaluationContext context) {
        if (context == null || taskRegistry.isEmpty()) {
            return false;
        }
        if (context.getTags() == null) {
            incompleteSpanCounter.inc();
            return false;
        }

        if (!completeLLMCallSpan(context)) {
            incompleteSpanCounter.inc();
            return false;
        }
        return true;
    }

    @Override
    public String taskId(final AIEvaluationContext context) {
        if (context.getTraceRefType() == GenAITraceRefType.SKYWALKING_NATIVE) {
            return context.getTraceId() + "-" + context.getSegmentId() + "-" + context.getSpanIndex();
        }
        return context.getTraceId() + "-" + context.getSpanId();
    }

    @Override
    public void evaluate(final AIEvaluationContext context,
                         final JudgeModelProvider judgeModelProvider) throws IOException, InterruptedException {
        evaluateLLMCallSpan(context, judgeModelProvider);
    }

    private void evaluateLLMCallSpan(final AIEvaluationContext context,
                                     final JudgeModelProvider judgeModelProvider)
            throws IOException, InterruptedException {
        final String judgeModel = judgeModelProvider.model();
        final List<EvaluationPlan> plans = evaluationPlanner.plan(context, taskRegistry.tasks());
        for (EvaluationPlan plan : plans) {
            final JudgeModelResponse judgeResponse = judgeModelProvider.judge(promptBuilder.build(plan));
            final List<EvaluationResult> results;
            try {
                results = resultParser.parse(plan, judgeResponse.getContent());
            } catch (RuntimeException e) {
                throw new JudgeModelException(Reason.INVALID_RESPONSE, "Judge returned invalid evaluation JSON.", e);
            }
            if (results.isEmpty()) {
                throw new JudgeModelException(
                    Reason.INVALID_RESPONSE, "Judge response contains no valid evaluation result."
                );
            }
            persistResults(context, results, judgeModel);
        }
    }

    private void persistResults(final AIEvaluationContext context,
                                final List<EvaluationResult> results,
                                final String judgeModel) {
        final long evaluationTime = context.getEndTimeMillis();
        for (EvaluationResult result : results) {
            if (result.getValueType() == null) {
                log.warn("Skip GenAI evaluation result without value type, task: {}", result.getName());
                continue;
            }
            final GenAIEvaluationRecord record = GenAIEvaluationRecord.create(
                namingControl.formatServiceName(context.getProviderName()),
                namingControl.formatInstanceName(context.getModelName())
            );
            record.setUniqueId(GenAIEvaluationRecord.toUniqueId(taskId(context), result.getName(), evaluationTime));
            record.setTraceId(context.getTraceId());
            record.setServiceName(namingControl.formatServiceName(context.getServiceName()));
            record.setOperationName(operationName(context));
            record.setRefType(context.getTraceRefType().name());
            record.setSegmentId(context.getSegmentId());
            record.setSpanIndex(context.getSpanIndex());
            record.setSpanId(context.getSpanId());
            record.setTaskName(result.getName());
            record.setValueType(result.getValueType().name());
            if (result.getValueType() == ValueType.SCORE || result.getValueType() == ValueType.BOOLEAN) {
                record.setEvalNumberValue(GenAIEvaluationRecord.toScoreValuePpm(numericValue(result.getValue())));
            } else {
                record.setEvalStringValue(result.getValue());
            }
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

    private static boolean completeLLMCallSpan(final AIEvaluationContext context) {
        if (StringUtil.isBlank(context.getTraceId())
            || context.getTraceRefType() == null
            || StringUtil.isBlank(context.getServiceName())
            || StringUtil.isBlank(context.getProviderName())
            || StringUtil.isBlank(context.getModelName())) {
            return false;
        }

        if (context.getTraceRefType() == GenAITraceRefType.SKYWALKING_NATIVE) {
            if (StringUtil.isBlank(context.getSegmentId()) || context.getSpanIndex() == null) {
                return false;
            }
        } else if (context.getTraceRefType() != GenAITraceRefType.OTLP
            || StringUtil.isBlank(context.getSpanId())) {
            return false;
        }

        final String inputMessages = context.getTags().get(GenAISemanticAttributes.INPUT_MESSAGES);
        final String outputMessages = context.getTags().get(GenAISemanticAttributes.OUTPUT_MESSAGES);
        return StringUtil.isNotBlank(inputMessages) && StringUtil.isNotBlank(outputMessages);
    }

    private static String operationName(final AIEvaluationContext context) {
        return context.getTags().get(GenAISemanticAttributes.OPERATION_NAME);
    }

    private static Double numericValue(final String value) {
        if ("true".equalsIgnoreCase(value)) {
            return 1D;
        }
        if ("false".equalsIgnoreCase(value)) {
            return 0D;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
