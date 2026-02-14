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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * A partitioned, self-draining queue with type-based dispatch.
 *
 * <h3>Usage</h3>
 * <pre>
 * BatchQueue queue = BatchQueueManager.create(name, config);
 * queue.addHandler(TypeA.class, handlerA);   // register metric types
 * queue.addHandler(TypeB.class, handlerB);   // partitions grow adaptively
 * queue.produce(data);                        // data flows immediately
 * </pre>
 *
 * <p>For adaptive partition policies, each {@link #addHandler} call recalculates
 * the partition count from the current number of registered handlers, growing the
 * partition array as needed. The thread count is resolved at construction time
 * and remains fixed.
 *
 * <h3>Produce workflow</h3>
 * <pre>
 * produce(data)
 *   |
 *   +-- queue stopped?  --> return false
 *   |
 *   +-- N == 1?  --> partition[0]  (skip selector)
 *   +-- N &gt; 1?  --> partition[selector.select(data, N)]
 *   |
 *   +-- BLOCKING strategy?
 *   |     yes --> ArrayBlockingQueue.put(data)   // blocks until space available
 *   |     no  --> ArrayBlockingQueue.offer(data) // returns false if full (drop)
 *   |
 *   +-- return true/false
 * </pre>
 *
 * <h3>Consume workflow (drain loop, runs on scheduler threads)</h3>
 * <pre>
 * scheduleDrain(taskIndex)                  // schedule with adaptive backoff delay
 *   |
 *   +-- scheduler.schedule(drainLoop, delay)
 *         |
 *         +-- drainTo(combined) from each assigned partition
 *         |
 *         +-- combined is empty?
 *         |     yes --> consecutiveIdleCycles++, notify onIdle(), break
 *         |     no  --> consecutiveIdleCycles = 0, dispatch(combined)
 *         |              |
 *         |              +-- single consumer set?
 *         |              |     yes --> consumer.consume(batch)
 *         |              |     no  --> group by item.getClass()
 *         |              |              for each (class, subBatch):
 *         |              |                handlerMap.get(class).consume(subBatch)
 *         |              |
 *         |              +-- loop back to drainTo (drain until empty)
 *         |
 *         +-- finally: scheduleDrain(taskIndex) // re-schedule self
 * </pre>
 *
 * <h3>Adaptive backoff</h3>
 * Delay doubles on each consecutive idle cycle: {@code minIdleMs * 2^idleCount},
 * capped at {@code maxIdleMs}. Resets to {@code minIdleMs} on first non-empty drain.
 *
 * <h3>Scheduler modes</h3>
 * <ul>
 *   <li><b>Dedicated</b> — this queue owns its own ScheduledThreadPool. Each thread
 *       is assigned a fixed subset of partitions (round-robin). Thread count and
 *       partition count are configured independently.</li>
 *   <li><b>Shared</b> — the queue borrows a scheduler from {@link BatchQueueManager},
 *       shared with other queues. Only 1 drain task is submitted (drains all partitions).
 *       The shared scheduler is reference-counted and shut down when the last queue
 *       releases it.</li>
 * </ul>
 *
 * <h3>Use case examples</h3>
 * <pre>
 * shared scheduler, partitions=1, one consumer    --> I/O queue (gRPC, Kafka, JDBC)
 * dedicated fixed(1), partitions=1, many handlers --> TopN (all types share 1 thread)
 * dedicated cpuCores(1.0), adaptive(),
 *           many handlers                         --> metrics aggregation
 * </pre>
 */
@Slf4j
public class BatchQueue<T> {
    private final String name;
    private final BatchQueueConfig<T> config;

    /** The thread pool that executes drain tasks. Either dedicated or shared. */
    private final ScheduledExecutorService scheduler;

    /** True if this queue owns the scheduler and should shut it down. */
    private final boolean dedicatedScheduler;

    /** Non-null only for shared schedulers; used to release the ref count on shutdown. */
    private final String sharedSchedulerName;

    /**
     * Cached partition selector from config. Only used when {@code partitions.length > 1};
     * single-partition queues bypass the selector entirely.
     */
    private final PartitionSelector<T> partitionSelector;

    /**
     * Type-based handler registry. When no single consumer is configured,
     * drained items are grouped by {@code item.getClass()} and dispatched
     * to the matching handler. Allows multiple metric/record types to share
     * one queue while each type has its own processing logic.
     */
    private final ConcurrentHashMap<Class<?>, HandlerConsumer<T>> handlerMap;

    /**
     * Tracks unregistered types that have already been warned about,
     * to avoid flooding the log with repeated errors.
     */
    private final Set<Class<?>> warnedUnregisteredTypes;

    /** Resolved thread count, stored for adaptive partition recalculation. */
    private final int resolvedThreadCount;

    /** Number of drain tasks (equals thread count for dedicated, 1 for shared). */
    private final int taskCount;

    /**
     * Partitions. Producers select a partition via {@link PartitionSelector}.
     * For adaptive policies, this array grows via {@link #addHandler} as handlers
     * are registered. Volatile for visibility to drain loop threads.
     */
    private volatile ArrayBlockingQueue<T>[] partitions;

    /**
     * Which partitions each drain task is responsible for.
     * Volatile for visibility when partitions grow via {@link #addHandler}.
     */
    private volatile int[][] assignedPartitions;

    /**
     * Per-task count of consecutive idle cycles (no data drained).
     * Used for adaptive exponential backoff in {@link #scheduleDrain}.
     */
    private final int[] consecutiveIdleCycles;

    /** Set to false on {@link #shutdown()} to stop drain loops and reject new data. */
    private volatile boolean running;

    @SuppressWarnings("unchecked")
    BatchQueue(final String name, final BatchQueueConfig<T> config) {
        this.name = name;
        this.config = config;
        this.partitionSelector = config.getPartitionSelector();
        this.handlerMap = new ConcurrentHashMap<>();
        this.warnedUnregisteredTypes = ConcurrentHashMap.newKeySet();

        if (config.getSharedSchedulerName() != null) {
            // ---- Shared scheduler mode ----
            final ScheduledExecutorService sharedScheduler =
                BatchQueueManager.getOrCreateSharedScheduler(
                    config.getSharedSchedulerName(), config.getSharedSchedulerThreads());

            this.resolvedThreadCount = 1;
            final int partitionCount = config.getPartitions().resolve(1, 0);
            this.partitions = new ArrayBlockingQueue[partitionCount];
            for (int i = 0; i < partitions.length; i++) {
                partitions[i] = new ArrayBlockingQueue<>(config.getBufferSize());
            }

            this.scheduler = sharedScheduler;
            this.dedicatedScheduler = false;
            this.sharedSchedulerName = config.getSharedSchedulerName();
            this.taskCount = 1;
            this.assignedPartitions = buildAssignments(1, partitionCount);
        } else {
            // ---- Dedicated scheduler mode ----
            int threadCount = config.getThreads().resolve();
            this.resolvedThreadCount = threadCount;

            // For adaptive with 0 handlers, resolve returns threadCount (sensible initial).
            // For fixed/threadMultiply, resolve returns the configured count.
            final int partitionCount = config.getPartitions().resolve(threadCount, 0);

            if (partitionCount < threadCount) {
                log.warn("BatchQueue[{}]: partitions({}) < threads({}), reducing threads to {}",
                    name, partitionCount, threadCount, partitionCount);
                threadCount = partitionCount;
            }

            this.partitions = new ArrayBlockingQueue[partitionCount];
            for (int i = 0; i < partitions.length; i++) {
                partitions[i] = new ArrayBlockingQueue<>(config.getBufferSize());
            }

            this.scheduler = Executors.newScheduledThreadPool(threadCount, r -> {
                final Thread t = new Thread(r);
                t.setName("BatchQueue-" + name + "-" + t.getId());
                t.setDaemon(true);
                return t;
            });
            this.dedicatedScheduler = true;
            this.sharedSchedulerName = null;
            this.taskCount = threadCount;
            this.assignedPartitions = buildAssignments(threadCount, partitionCount);
        }

        this.consecutiveIdleCycles = new int[taskCount];
        this.running = true;
        // Kick off the drain loop for each task
        for (int t = 0; t < taskCount; t++) {
            scheduleDrain(t);
        }
    }

    /**
     * Build round-robin partition-to-task assignments.
     */
    private static int[][] buildAssignments(final int taskCount, final int partitionCount) {
        final int[][] result = new int[taskCount][];
        final List<List<Integer>> assignment = new ArrayList<>();
        for (int t = 0; t < taskCount; t++) {
            assignment.add(new ArrayList<>());
        }
        for (int p = 0; p < partitionCount; p++) {
            assignment.get(p % taskCount).add(p);
        }
        for (int t = 0; t < taskCount; t++) {
            final List<Integer> parts = assignment.get(t);
            result[t] = new int[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                result[t][i] = parts.get(i);
            }
        }
        return result;
    }

    /**
     * Schedule the next drain for the given task. The delay uses adaptive exponential
     * backoff: {@code minIdleMs * 2^consecutiveIdleCycles}, capped at maxIdleMs.
     * When data is flowing, consecutiveIdleCycles is 0, so delay = minIdleMs.
     */
    private void scheduleDrain(final int taskIndex) {
        if (!running) {
            return;
        }
        final int idleCount = consecutiveIdleCycles[taskIndex];
        final long delay = Math.min(
            config.getMinIdleMs() * (1L << Math.min(idleCount, 20)),
            config.getMaxIdleMs()
        );
        try {
            scheduler.schedule(() -> drainLoop(taskIndex), delay, TimeUnit.MILLISECONDS);
        } catch (final Exception e) {
            if (running) {
                log.error("BatchQueue[{}]: failed to schedule drain task", name, e);
            }
        }
    }

    /**
     * Register a type-based handler. Items whose {@code getClass()} matches the given
     * type will be batched together and dispatched to this handler.
     *
     * <p>For adaptive partition policies, adding a handler recalculates the partition
     * count and grows the partition array if needed. For non-adaptive policies the
     * resolved count never changes, so this is a no-op beyond the registration.
     * Drain loop threads pick up new partitions on their next cycle via volatile reads.
     */
    @SuppressWarnings("unchecked")
    public void addHandler(final Class<? extends T> type, final HandlerConsumer<T> handler) {
        handlerMap.put(type, handler);

        final int newPartitionCount = config.getPartitions()
            .resolve(resolvedThreadCount, handlerMap.size());
        final ArrayBlockingQueue<T>[] currentPartitions = this.partitions;
        if (newPartitionCount > currentPartitions.length) {
            final ArrayBlockingQueue<T>[] grown = new ArrayBlockingQueue[newPartitionCount];
            System.arraycopy(currentPartitions, 0, grown, 0, currentPartitions.length);
            for (int i = currentPartitions.length; i < newPartitionCount; i++) {
                grown[i] = new ArrayBlockingQueue<>(config.getBufferSize());
            }
            // Volatile writes — drain loop threads see the new assignments on next cycle
            this.assignedPartitions = buildAssignments(taskCount, newPartitionCount);
            this.partitions = grown;
        }
    }

    /**
     * Produce data into a partition selected by the configured {@link PartitionSelector}.
     *
     * <p>Single-partition queues bypass the selector entirely (always index 0).
     * Multi-partition queues default to {@link PartitionSelector#typeHash()}, which
     * uses {@code data.getClass().hashCode()} so all items of the same type land on
     * the same partition — the HashMap grouping in {@link #dispatch} becomes
     * effectively a no-op.
     *
     * <p>Behavior depends on {@link BufferStrategy}:
     * <ul>
     *   <li>BLOCKING — blocks the caller until space is available</li>
     *   <li>IF_POSSIBLE — returns false immediately if the partition is full (data dropped)</li>
     * </ul>
     *
     * @return true if data was accepted, false if dropped or queue is stopped
     */
    public boolean produce(final T data) {
        if (!running) {
            return false;
        }
        final ArrayBlockingQueue<T>[] currentPartitions = this.partitions;
        final int index = currentPartitions.length == 1
            ? 0 : partitionSelector.select(data, currentPartitions.length);
        if (config.getStrategy() == BufferStrategy.BLOCKING) {
            try {
                currentPartitions[index].put(data);
                return true;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } else {
            return currentPartitions[index].offer(data);
        }
    }

    /**
     * Core drain loop. Runs on a scheduler thread. Drains all assigned partitions
     * into a single list, then dispatches. Loops until partitions are empty,
     * then breaks to re-schedule with backoff.
     *
     * <p>Reads volatile references to {@code partitions} and {@code assignedPartitions}
     * at the start of each cycle, so it picks up new partitions added via
     * {@link #addHandler} on the next iteration.
     */
    void drainLoop(final int taskIndex) {
        final ArrayBlockingQueue<T>[] currentPartitions = this.partitions;
        final int[] myPartitions = this.assignedPartitions[taskIndex];
        try {
            while (running) {
                // Drain all assigned partitions into one batch
                final List<T> combined = new ArrayList<>();
                for (final int partitionIndex : myPartitions) {
                    if (partitionIndex < currentPartitions.length) {
                        currentPartitions[partitionIndex].drainTo(combined);
                    }
                }

                if (combined.isEmpty()) {
                    // Nothing to drain — increase backoff and notify idle
                    consecutiveIdleCycles[taskIndex]++;
                    notifyIdle();
                    break;
                }

                // Data found — reset backoff and dispatch
                consecutiveIdleCycles[taskIndex] = 0;
                dispatch(combined);
            }
        } catch (final Throwable t) {
            log.error("BatchQueue[{}]: drain loop error", name, t);
        } finally {
            // Always re-schedule unless shutdown
            if (running) {
                scheduleDrain(taskIndex);
            }
        }
    }

    /**
     * Dispatch a batch to the appropriate consumer(s).
     *
     * <p>Two modes:
     * <ol>
     *   <li><b>Single consumer</b> (config.consumer != null): the entire batch goes
     *       to one consumer, regardless of item types.</li>
     *   <li><b>Handler map</b>: items are grouped by {@code item.getClass()}, then
     *       each group is dispatched to its registered handler. Unregistered types
     *       are logged as errors and dropped.</li>
     * </ol>
     */
    private void dispatch(final List<T> batch) {
        if (config.getConsumer() != null) {
            try {
                config.getConsumer().consume(batch);
            } catch (final Throwable t) {
                handleError(batch, t);
            }
            return;
        }

        // Group by concrete class, then dispatch each group to its handler
        final Map<Class<?>, List<T>> grouped = new HashMap<>();
        for (final T item : batch) {
            grouped.computeIfAbsent(item.getClass(), k -> new ArrayList<>()).add(item);
        }

        for (final Map.Entry<Class<?>, List<T>> entry : grouped.entrySet()) {
            final HandlerConsumer<T> handler = handlerMap.get(entry.getKey());
            if (handler != null) {
                try {
                    handler.consume(entry.getValue());
                } catch (final Throwable t) {
                    handleError(entry.getValue(), t);
                }
            } else {
                if (warnedUnregisteredTypes.add(entry.getKey())) {
                    log.error("BatchQueue[{}]: no handler for type {}, {} items abandoned",
                        name, entry.getKey().getName(), entry.getValue().size());
                }
            }
        }
    }

    /**
     * Notify consumer/handlers that a drain cycle found no data.
     * Useful for flush-on-idle semantics (e.g. flush partial batches to storage).
     */
    private void notifyIdle() {
        if (config.getConsumer() != null) {
            try {
                config.getConsumer().onIdle();
            } catch (final Throwable t) {
                log.error("BatchQueue[{}]: onIdle error in consumer", name, t);
            }
        } else {
            for (final HandlerConsumer<T> handler : handlerMap.values()) {
                try {
                    handler.onIdle();
                } catch (final Throwable t) {
                    log.error("BatchQueue[{}]: onIdle error in handler", name, t);
                }
            }
        }
    }

    private void handleError(final List<T> data, final Throwable t) {
        if (config.getErrorHandler() != null) {
            try {
                config.getErrorHandler().onError(data, t);
            } catch (final Throwable inner) {
                log.error("BatchQueue[{}]: error handler threw", name, inner);
            }
        } else {
            log.error("BatchQueue[{}]: unhandled dispatch error", name, t);
        }
    }

    /**
     * Stop the queue: reject new produces, perform a final drain of all partitions,
     * and release the scheduler (dedicated: shutdown; shared: decrement ref count).
     */
    void shutdown() {
        running = false;
        // Final drain — flush any remaining data to consumers
        final ArrayBlockingQueue<T>[] currentPartitions = this.partitions;
        final List<T> combined = new ArrayList<>();
        for (final ArrayBlockingQueue<T> partition : currentPartitions) {
            partition.drainTo(combined);
        }
        if (!combined.isEmpty()) {
            dispatch(combined);
        }
        if (dedicatedScheduler) {
            scheduler.shutdown();
        } else if (sharedSchedulerName != null) {
            BatchQueueManager.releaseSharedScheduler(sharedSchedulerName);
        }
    }

    public String getName() {
        return name;
    }

    public boolean isRunning() {
        return running;
    }

    int getPartitionCount() {
        return partitions.length;
    }

    int getTaskCount() {
        return assignedPartitions.length;
    }

    int[][] getAssignedPartitions() {
        return assignedPartitions;
    }

    boolean isDedicatedScheduler() {
        return dedicatedScheduler;
    }

    /**
     * Take a point-in-time snapshot of queue usage across all partitions.
     */
    public BatchQueueStats stats() {
        final ArrayBlockingQueue<T>[] currentPartitions = this.partitions;
        final int[] used = new int[currentPartitions.length];
        for (int i = 0; i < currentPartitions.length; i++) {
            used[i] = currentPartitions[i].size();
        }
        return new BatchQueueStats(currentPartitions.length, config.getBufferSize(), used);
    }
}
