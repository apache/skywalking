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
     * Thread policy for a dedicated ScheduledExecutorService.
     * When set, the queue creates its own scheduler.
     * When null, sharedSchedulerName/sharedSchedulerThreads must be set.
     */
    private ThreadPolicy threads;

    /**
     * Shared scheduler name. Set via the builder method
     * {@code sharedScheduler(name, threads)}.
     */
    private String sharedSchedulerName;

    /**
     * Thread policy for the shared scheduler. Set together with sharedSchedulerName.
     */
    private ThreadPolicy sharedSchedulerThreads;

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
        final boolean hasDedicated = threads != null;
        final boolean hasShared = sharedSchedulerName != null;
        if (hasDedicated == hasShared) {
            throw new IllegalArgumentException(
                "Exactly one of threads or sharedScheduler must be set. " +
                    "threads=" + threads + ", sharedSchedulerName=" + sharedSchedulerName);
        }
        if (hasShared && sharedSchedulerThreads == null) {
            throw new IllegalArgumentException(
                "sharedSchedulerThreads must be set when sharedSchedulerName is set");
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
    }

    /**
     * Builder customizations: convenience methods for setting paired fields together.
     */
    public static class BatchQueueConfigBuilder<T> {
        /**
         * Configure the queue to use a shared scheduler instead of a dedicated one.
         *
         * @param name the shared scheduler name (queues with the same name share a pool)
         * @param threads the thread policy for the shared scheduler
         * @return this builder
         */
        public BatchQueueConfigBuilder<T> sharedScheduler(final String name, final ThreadPolicy threads) {
            this.sharedSchedulerName = name;
            this.sharedSchedulerThreads = threads;
            return this;
        }

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
