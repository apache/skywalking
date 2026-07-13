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

package org.apache.skywalking.oap.server.ai.evaluation.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelProvider;
import org.apache.skywalking.oap.server.ai.evaluation.service.sample.AIEvaluationSamplingPolicy;
import org.apache.skywalking.oap.server.ai.evaluation.service.strategy.AIEvaluationStrategy;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
public class AIEvaluationService implements IAIEvaluationService {
    private final AIEvaluationSamplingPolicy samplingPolicy;
    private final JudgeModelProvider judgeModelProvider;
    private volatile List<AIEvaluationStrategy> strategies;
    private final Set<String> pendingTaskIds = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor evaluationExecutor =
        new ThreadPoolExecutor(
            4,
            4,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100)
        );

    public AIEvaluationService(final AIEvaluationSamplingPolicy samplingPolicy,
                               final JudgeModelProvider judgeModelProvider) {
        this.samplingPolicy = samplingPolicy;
        this.judgeModelProvider = judgeModelProvider;
        this.strategies = List.of();
    }

    public void setStrategies(final List<AIEvaluationStrategy> strategies) {
        this.strategies = strategies == null ? List.of() : List.copyOf(strategies);
    }

    @Override
    public boolean shouldSample(final String traceId) {
        return StringUtil.isNotEmpty(traceId) && samplingPolicy.shouldSample(traceId);
    }

    @Override
    public void sample(final AIEvaluationContext context) {
        if (context == null || StringUtil.isEmpty(context.getTraceId())) {
            return;
        }

        final AIEvaluationStrategy strategy = findStrategy(context);
        if (strategy == null) {
            return;
        }

        final String taskId = strategy.taskId(context);
        if (!pendingTaskIds.add(taskId)) {
            return;
        }

        try {
            evaluationExecutor.execute(() -> {
                try {
                    evaluate(context, strategy, taskId);
                } finally {
                    pendingTaskIds.remove(taskId);
                }
            });
        } catch (RejectedExecutionException e) {
            pendingTaskIds.remove(taskId);
            log.warn("GenAI span evaluation task rejected, taskId: {}", taskId, e);
        }
    }

    private void evaluate(final AIEvaluationContext context,
                          final AIEvaluationStrategy strategy,
                          final String taskId) {
        try {
            strategy.evaluate(context, judgeModelProvider);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("GenAI evaluation interrupted, taskId: {}", taskId, e);
        } catch (Exception e) {
            log.error("GenAI evaluation failed, taskId: {}", taskId, e);
        }
    }

    private AIEvaluationStrategy findStrategy(final AIEvaluationContext context) {
        for (AIEvaluationStrategy strategy : strategies) {
            if (strategy.support(context)) {
                return strategy;
            }
        }
        return null;
    }
}
