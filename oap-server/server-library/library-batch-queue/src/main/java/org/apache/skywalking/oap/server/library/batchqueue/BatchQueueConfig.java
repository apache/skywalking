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

package org.apache.skywalking.oap.server.library.batchqueue;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BatchQueueConfig<T> {
    /**
     * Thread policy for the ScheduledExecutorService.
     */
    private ThreadPolicy threads;

    @Builder.Default
    private PartitionPolicy partitions = PartitionPolicy.fixed(1);

    /**
     * Partition selector for multi-partition queues. Determines which partition
     * a produced item is placed into. Ignored when the queue has only 1 partition.
     * Defaults to {@link PartitionSelector#typeHash()}.
     */
    @Builder.Default
    private PartitionSelector<T> partitionSelector = PartitionSelector.typeHash();

    @Builder.Default
    private int bufferSize = 10_000;

    @Builder.Default
    private BufferStrategy strategy = BufferStrategy.BLOCKING;

    /**
     * Direct consumer for the whole batch. When set, all drained data goes to this
     * handler without class-based grouping. Takes priority over handler map.
     */
    private HandlerConsumer<T> consumer;

    private QueueErrorHandler<T> errorHandler;

    @Builder.Default
    private long minIdleMs = 1;

    @Builder.Default
    private long maxIdleMs = 50;

    /**
     * How long {@code shutdown()} waits for in-flight consumer invocations to finish before
     * performing the final drain. Per-queue because the bound is set by the consumer's work:
     * a gRPC peer teardown runs on the cluster topology thread and cannot wait long, while a
     * queue whose consumer makes a multi-second remote call needs far more.
     *
     * On expiry the queue logs and proceeds with the final drain rather than interrupting the
     * consumer — no consumer is written to tolerate interruption mid-batch.
     */
    @Builder.Default
    private long shutdownTimeoutMs = 500;

    /**
     * Drain balancer for periodic rebalancing of partition-to-thread assignments.
     * Set via {@code .balancer(DrainBalancer, intervalMs)} on the builder.
     * When null (default), rebalancing is disabled.
     *
     * @see DrainBalancer#throughputWeighted()
     */
    private DrainBalancer balancer;

    /**
     * Rebalance interval in milliseconds. Set together with {@link #balancer}
     * via {@code .balancer(DrainBalancer, intervalMs)} on the builder.
     */
    private long rebalanceIntervalMs;

    void validate() {
        if (threads == null) {
            throw new IllegalArgumentException("threads must be set.");
        }
        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must be >= 1, got: " + bufferSize);
        }
        if (minIdleMs < 1) {
            throw new IllegalArgumentException("minIdleMs must be >= 1, got: " + minIdleMs);
        }
        if (maxIdleMs < minIdleMs) {
            throw new IllegalArgumentException(
                "maxIdleMs must be >= minIdleMs, got maxIdleMs=" + maxIdleMs + " minIdleMs=" + minIdleMs);
        }
        if (shutdownTimeoutMs < 0) {
            throw new IllegalArgumentException("shutdownTimeoutMs must be >= 0, got: " + shutdownTimeoutMs);
        }
    }

    /**
     * Builder customizations: convenience methods for setting paired fields together.
     */
    public static class BatchQueueConfigBuilder<T> {
        /**
         * Enable periodic drain rebalancing with the given strategy and interval.
         *
         * @param balancer rebalancing strategy (e.g. {@link DrainBalancer#throughputWeighted()})
         * @param intervalMs rebalance interval in milliseconds
         * @return this builder
         */
        public BatchQueueConfigBuilder<T> balancer(final DrainBalancer balancer, final long intervalMs) {
            this.balancer = balancer;
            this.rebalanceIntervalMs = intervalMs;
            return this;
        }
    }
}
