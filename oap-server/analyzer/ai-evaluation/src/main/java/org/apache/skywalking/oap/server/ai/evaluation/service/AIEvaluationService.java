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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelProvider;
import org.apache.skywalking.oap.server.ai.evaluation.service.sample.AIEvaluationSamplingPolicy;
import org.apache.skywalking.oap.server.ai.evaluation.service.strategy.AIEvaluationStrategy;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueue;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueConfig;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueManager;
import org.apache.skywalking.oap.server.library.batchqueue.BufferStrategy;
import org.apache.skywalking.oap.server.library.batchqueue.PartitionPolicy;
import org.apache.skywalking.oap.server.library.batchqueue.ThreadPolicy;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;

@Slf4j
public class AIEvaluationService implements IAIEvaluationService {
    private final AIEvaluationSamplingPolicy samplingPolicy;
    private final JudgeModelProvider judgeModelProvider;
    private volatile List<AIEvaluationStrategy> strategies;
    private volatile CounterMetrics capacityDroppedCounter;
    private volatile CounterMetrics incompleteSpanCounter;
    private final Set<String> pendingTaskIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger nextPartition = new AtomicInteger();
    private final BatchQueue<PendingEvaluation> evaluationQueue;

    public AIEvaluationService(final AIEvaluationSamplingPolicy samplingPolicy,
                               final JudgeModelProvider judgeModelProvider,
                               final int bufferSize,
                               final int consumerThreads) {
        this.samplingPolicy = samplingPolicy;
        this.judgeModelProvider = judgeModelProvider;
        this.evaluationQueue = BatchQueueManager.create(
            "AI_EVALUATION",
            BatchQueueConfig.<PendingEvaluation>builder()
                            .threads(ThreadPolicy.fixed(consumerThreads))
                            .partitions(PartitionPolicy.fixed(consumerThreads))
                            .bufferSize(perPartitionBufferSize(bufferSize, consumerThreads))
                            .strategy(BufferStrategy.IF_POSSIBLE)
                            .partitionSelector((task, partitionCount) ->
                                Math.floorMod(nextPartition.getAndIncrement(), partitionCount))
                            .consumer(this::consume)
                            .build()
        );
        this.strategies = List.of();
    }

    public void setStrategies(final List<AIEvaluationStrategy> strategies) {
        this.strategies = strategies == null ? List.of() : List.copyOf(strategies);
    }

    public void setDroppedCounters(final CounterMetrics capacityDroppedCounter,
                                   final CounterMetrics incompleteSpanCounter) {
        this.capacityDroppedCounter = capacityDroppedCounter;
        this.incompleteSpanCounter = incompleteSpanCounter;
    }

    @Override
    public boolean shouldSample(final String traceId) {
        return StringUtil.isNotEmpty(traceId) && samplingPolicy.shouldSample(traceId);
    }

    @Override
    public void sample(final AIEvaluationContext context) {
        if (context == null || StringUtil.isEmpty(context.getTraceId())) {
            increment(incompleteSpanCounter);
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

        if (!evaluationQueue.produce(new PendingEvaluation(context, strategy, taskId))) {
            pendingTaskIds.remove(taskId);
            increment(capacityDroppedCounter);
        }
    }

    private void consume(final List<PendingEvaluation> tasks) {
        for (final PendingEvaluation task : tasks) {
            try {
                evaluate(task.context, task.strategy, task.taskId);
            } finally {
                pendingTaskIds.remove(task.taskId);
            }
        }
    }

    private static int perPartitionBufferSize(final int bufferSize, final int consumerThreads) {
        return bufferSize / consumerThreads + (bufferSize % consumerThreads == 0 ? 0 : 1);
    }

    private static void increment(final CounterMetrics counter) {
        if (counter != null) {
            counter.inc();
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

    private static final class PendingEvaluation {
        private final AIEvaluationContext context;
        private final AIEvaluationStrategy strategy;
        private final String taskId;

        private PendingEvaluation(final AIEvaluationContext context,
                                  final AIEvaluationStrategy strategy,
                                  final String taskId) {
            this.context = context;
            this.strategy = strategy;
            this.taskId = taskId;
        }
    }
}
