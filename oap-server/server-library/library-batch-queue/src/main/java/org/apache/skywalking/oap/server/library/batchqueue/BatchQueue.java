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
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;
import lombok.AccessLevel;
import lombok.Getter;
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
 *   +-- queue stopped?  --&gt; return false
 *   |
 *   +-- N == 1?  --&gt; partition[0]  (skip selector)
 *   +-- N &gt; 1?  --&gt; partition[selector.select(data, N)]
 *   |
 *   +-- BLOCKING strategy?
 *   |     yes --&gt; ArrayBlockingQueue.put(data)   // blocks until space available
 *   |     no  --&gt; ArrayBlockingQueue.offer(data) // returns false if full (drop)
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
 *         |     yes --&gt; consecutiveIdleCycles++, notify onIdle(), break
 *         |     no  --&gt; consecutiveIdleCycles = 0, dispatch(combined)
 *         |              |
 *         |              +-- single consumer set?
 *         |              |     yes --&gt; consumer.consume(batch)
 *         |              |     no  --&gt; group by item.getClass()
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
 * <h3>Use case examples</h3>
 * <pre>
 * dedicated fixed(1), partitions=1, one consumer  --&gt; I/O queue (gRPC, Kafka, JDBC)
 * dedicated fixed(1), partitions=1, many handlers --&gt; TopN (all types share 1 thread)
 * dedicated cpuCores(1.0), adaptive(),
 *           many handlers                         --&gt; metrics aggregation
 * </pre>
 */
@Slf4j
public class BatchQueue<T> {
    private static final int UNOWNED = -1;

    @Getter
    private final String name;
    private final BatchQueueConfig<T> config;

    /** The thread pool that executes drain tasks. */
    private final ScheduledExecutorService scheduler;

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
     *
     * <p>Layout: {@code partitions[partitionIndex]} is an {@link ArrayBlockingQueue}
     * holding items routed to that partition. Length equals the resolved partition count.
     *
     * <p>Example — 8 partitions, bufferSize=2000, 5 metric types:
     * <pre>
     *   TypeA.hashCode() % 8 = 2  --&gt;  partitions[2].put(typeA_item)
     *   TypeB.hashCode() % 8 = 5  --&gt;  partitions[5].put(typeB_item)
     *   TypeC.hashCode() % 8 = 2  --&gt;  partitions[2].put(typeC_item)  (same partition as TypeA)
     *   TypeD.hashCode() % 8 = 7  --&gt;  partitions[7].put(typeD_item)
     *   TypeE.hashCode() % 8 = 0  --&gt;  partitions[0].put(typeE_item)
     * </pre>
     */
    private volatile ArrayBlockingQueue<T>[] partitions;

    /**
     * Per-task partition assignments. Each drain task drains only its assigned partitions.
     * Built by round-robin initially, rebuilt by the rebalancer when partitions are moved.
     * Volatile for visibility when partitions grow via {@link #addHandler} or rebalance.
     *
     * <p>Example — 8 partitions, 4 tasks, initial round-robin:
     * <pre>
     *   assignedPartitions[0] = {0, 4}   -- task 0 drains partitions 0,4
     *   assignedPartitions[1] = {1, 5}   -- task 1 drains partitions 1,5
     *   assignedPartitions[2] = {2, 6}   -- task 2 drains partitions 2,6
     *   assignedPartitions[3] = {3, 7}   -- task 3 drains partitions 3,7
     * </pre>
     * After rebalancing moves partition 4 from task 0 to task 2 (to equalize load):
     * <pre>
     *   assignedPartitions[0] = {0}      -- task 0 lost partition 4
     *   assignedPartitions[1] = {1, 5}   -- unchanged
     *   assignedPartitions[2] = {2, 6, 4}-- task 2 gained partition 4
     *   assignedPartitions[3] = {3, 7}   -- unchanged
     * </pre>
     */
    @Getter(AccessLevel.PACKAGE)
    private volatile int[][] assignedPartitions;

    /**
     * Per-task count of consecutive idle cycles (no data drained).
     * Used for adaptive exponential backoff in {@link #scheduleDrain}.
     *
     * <p>Example — 4 tasks, task 0 busy, task 3 idle for 5 cycles:
     * <pre>
     *   consecutiveIdleCycles = {0, 0, 1, 5}
     *   delay for task 0: minIdleMs * 2^0 = 1ms  (data flowing)
     *   delay for task 3: minIdleMs * 2^5 = 32ms (backing off, capped at maxIdleMs)
     * </pre>
     */
    private final int[] consecutiveIdleCycles;

    /** Set to false on {@link #shutdown()} to stop drain loops and reject new data. */
    /**
     * Whether the queue is currently accepting produces and running drain loops.
     *
     * @return true if the queue is running
     */
    @Getter
    private volatile boolean running;

    // ---- Rebalancing fields (only allocated when enableRebalancing() is called) ----

    /**
     * Per-partition produce counter, incremented in {@link #produce} before {@code put/offer}.
     * The rebalancer snapshots and resets all counters each interval to measure throughput.
     * Null until {@link #enableRebalancing} is called.
     *
     * <p>Example — 8 partitions, after 2 seconds of production with skewed types:
     * <pre>
     *   partitionThroughput = [1200, 300, 4800, 150, 3600, 300, 900, 2400]
     *                           ^                ^         ^
     *                      TypeE(1x)     TypeA+C(24x)  TypeD(12x)
     * </pre>
     * The rebalancer snapshots these values and resets all to 0:
     * <pre>
     *   snapshot             = [1200, 300, 4800, 150, 3600, 300, 900, 2400]
     *   partitionThroughput  = [   0,   0,    0,   0,    0,   0,   0,    0]  (reset)
     * </pre>
     */
    private volatile AtomicLongArray partitionThroughput;

    /**
     * Partition-to-task ownership map, authoritative source for which task drains which
     * partition. The drain loop checks this before draining each partition, skipping
     * partitions it no longer owns. Null until {@link #enableRebalancing} is called.
     *
     * <p>Example — 8 partitions, 4 tasks, initial round-robin ownership:
     * <pre>
     *   partitionOwner = [0, 1, 2, 3, 0, 1, 2, 3]
     *                     ^           ^
     *               partition 0    partition 4
     *               owned by       owned by
     *               task 0         task 0
     * </pre>
     * During two-phase handoff (moving partition 4 from task 0 to task 2):
     * <pre>
     *   Phase 1 — revoke:  partitionOwner = [0, 1, 2, 3, -1, 1, 2, 3]
     *                                                      ^
     *                                                  UNOWNED (-1)
     *                                                  task 0 skips it on next drain
     *
     *   (rebalancer waits for task 0's cycleCount to advance)
     *
     *   Phase 2 — assign:  partitionOwner = [0, 1, 2, 3, 2, 1, 2, 3]
     *                                                     ^
     *                                                 now owned by task 2
     * </pre>
     */
    private volatile AtomicIntegerArray partitionOwner;

    /**
     * Per-task drain cycle counter, monotonically increasing. Incremented once in the
     * {@code finally} block of {@link #drainLoop} when the entire scheduled invocation
     * exits — which may have drained and dispatched multiple batches in its inner
     * {@code while} loop before finding an empty drain.
     *
     * <p>The rebalancer uses this as a fence: after revoking a partition from a task,
     * it waits for that task's cycle count to advance, which proves the task has exited
     * {@code drainLoop} and will not touch the revoked partition again until the next
     * scheduled invocation (which will re-read the updated {@code partitionOwner}).
     * Null until {@link #enableRebalancing} is called.
     *
     * <p>Example — 4 tasks, rebalancer revoking partition 4 from task 0:
     * <pre>
     *   cycleCount = [142, 138, 145, 140]
     *                  ^
     *            snapshot task 0's count = 142
     *
     *   task 0 is still inside drainLoop:
     *     while(running) { drain --&gt; dispatch --&gt; drain --&gt; empty --&gt; break }
     *     finally { cycleCount[0]++ }      // increments once for the whole invocation
     *     scheduleDrain(0)                 // next invocation re-reads partitionOwner
     *
     *   cycleCount = [143, 138, 145, 140]
     *                  ^
     *            143 &gt; 142 --&gt; task 0 exited drainLoop, safe to reassign
     * </pre>
     */
    private volatile AtomicLongArray cycleCount;

    /** Whether rebalancing is active. Gates all hot-path additions. */
    @Getter(AccessLevel.PACKAGE)
    private volatile boolean rebalancingEnabled;

    @SuppressWarnings("unchecked")
    BatchQueue(final String name, final BatchQueueConfig<T> config) {
        this.name = name;
        this.config = config;
        this.partitionSelector = config.getPartitionSelector();
        this.handlerMap = new ConcurrentHashMap<>();
        this.warnedUnregisteredTypes = ConcurrentHashMap.newKeySet();

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
        this.taskCount = threadCount;
        this.assignedPartitions = buildAssignments(threadCount, partitionCount);

        this.consecutiveIdleCycles = new int[taskCount];
        this.running = true;
        // Kick off the drain loop for each task
        for (int t = 0; t < taskCount; t++) {
            scheduleDrain(t);
        }

        // Enable rebalancing if configured
        if (config.getBalancer() != null && config.getRebalanceIntervalMs() > 0) {
            enableRebalancing(config.getRebalanceIntervalMs());
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
     *
     * @param type the class of items to route to this handler
     * @param handler the consumer that processes batches of the given type
     */
    @SuppressWarnings("unchecked")
    public void addHandler(final Class<? extends T> type, final HandlerConsumer<T> handler) {
        handlerMap.put(type, handler);

        final int newPartitionCount = config.getPartitions()
            .resolve(resolvedThreadCount, handlerMap.size());
        final ArrayBlockingQueue<T>[] currentPartitions = this.partitions;
        if (newPartitionCount > currentPartitions.length) {
            final int oldCount = currentPartitions.length;
            final ArrayBlockingQueue<T>[] grown = new ArrayBlockingQueue[newPartitionCount];
            System.arraycopy(currentPartitions, 0, grown, 0, oldCount);
            for (int i = oldCount; i < newPartitionCount; i++) {
                grown[i] = new ArrayBlockingQueue<>(config.getBufferSize());
            }

            if (rebalancingEnabled) {
                // Grow atomic arrays to cover new partitions
                final AtomicLongArray newThroughput = new AtomicLongArray(newPartitionCount);
                for (int i = 0; i < oldCount; i++) {
                    newThroughput.set(i, partitionThroughput.get(i));
                }
                this.partitionThroughput = newThroughput;

                final AtomicIntegerArray newOwner = new AtomicIntegerArray(newPartitionCount);
                for (int i = 0; i < oldCount; i++) {
                    newOwner.set(i, partitionOwner.get(i));
                }
                // Assign new partitions round-robin
                for (int i = oldCount; i < newPartitionCount; i++) {
                    newOwner.set(i, i % taskCount);
                }
                this.partitionOwner = newOwner;
            }

            // Volatile writes — drain loop threads see the new assignments on next cycle
            this.assignedPartitions = buildAssignments(taskCount, newPartitionCount);
            this.partitions = grown;
        }
    }

    /**
     * Initialize rebalancing infrastructure and schedule periodic rebalance task.
     * Called from constructor when {@code .balancer(DrainBalancer, intervalMs)} is
     * configured. Silently returns if {@code taskCount <= 1} (nothing to rebalance).
     */
    private void enableRebalancing(final long intervalMs) {
        if (taskCount <= 1) {
            return;
        }

        final int partitionCount = partitions.length;

        // Allocate atomic arrays
        this.partitionThroughput = new AtomicLongArray(partitionCount);
        this.cycleCount = new AtomicLongArray(taskCount);

        // Initialize ownership from current assignedPartitions
        final AtomicIntegerArray owner = new AtomicIntegerArray(partitionCount);
        // Default all to UNOWNED, then set from assignments
        for (int p = 0; p < partitionCount; p++) {
            owner.set(p, UNOWNED);
        }
        final int[][] currentAssignments = this.assignedPartitions;
        for (int t = 0; t < currentAssignments.length; t++) {
            for (final int p : currentAssignments[t]) {
                owner.set(p, t);
            }
        }
        this.partitionOwner = owner;

        // Enable the flag — gates hot-path additions in produce() and drainLoop()
        this.rebalancingEnabled = true;

        // Schedule periodic rebalancing on the queue's scheduler
        scheduler.scheduleAtFixedRate(
            this::rebalance, intervalMs, intervalMs, TimeUnit.MILLISECONDS
        );

        log.info("BatchQueue[{}]: rebalancing enabled, interval={}ms, tasks={}, partitions={}",
            name, intervalMs, taskCount, partitionCount);
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
     * @param data the item to enqueue
     * @return true if data was accepted, false if dropped or queue is stopped
     */
    public boolean produce(final T data) {
        if (!running) {
            return false;
        }
        final ArrayBlockingQueue<T>[] currentPartitions = this.partitions;
        final int index = currentPartitions.length == 1
            ? 0 : partitionSelector.select(data, currentPartitions.length);
        // Increment throughput counter BEFORE put/offer so the rebalancer sees
        // true produce demand, not the drain-throttled rate. With BLOCKING, the
        // post-put counter would reflect drain rate (already equalized by
        // backpressure), hiding the imbalance the rebalancer needs to detect.
        if (rebalancingEnabled) {
            partitionThroughput.incrementAndGet(index);
        }
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
        final boolean checkOwnership = rebalancingEnabled;
        try {
            while (running) {
                // Drain all assigned partitions into one batch
                final List<T> combined = new ArrayList<>();
                for (final int partitionIndex : myPartitions) {
                    if (partitionIndex < currentPartitions.length) {
                        // Skip partitions revoked by the rebalancer
                        if (checkOwnership && partitionOwner.get(partitionIndex) != taskIndex) {
                            continue;
                        }
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
            // Bump cycle count so the rebalancer knows this task finished its dispatch
            if (checkOwnership) {
                cycleCount.incrementAndGet(taskIndex);
            }
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
     * Periodic rebalance task. Snapshots throughput counters, runs LPT assignment,
     * and performs two-phase handoff for moved partitions.
     */
    private void rebalance() {
        if (!running) {
            return;
        }
        try {
            final int partitionCount = partitions.length;
            final AtomicLongArray throughput = this.partitionThroughput;
            final AtomicIntegerArray owner = this.partitionOwner;

            // Step 1: Snapshot and reset throughput counters
            final long[] snapshot = new long[partitionCount];
            for (int p = 0; p < partitionCount; p++) {
                snapshot[p] = throughput.getAndSet(p, 0);
            }

            // Step 2: Snapshot current ownership
            final int[] currentOwner = new int[partitionCount];
            for (int p = 0; p < partitionCount; p++) {
                currentOwner[p] = owner.get(p);
            }

            // Log per-thread load before balancing (debug)
            if (log.isDebugEnabled()) {
                final long[] threadLoad = new long[taskCount];
                for (int p = 0; p < partitionCount; p++) {
                    final int t = currentOwner[p];
                    if (t >= 0 && t < taskCount) {
                        threadLoad[t] += snapshot[p];
                    }
                }
                final StringBuilder sb = new StringBuilder();
                for (int t = 0; t < taskCount; t++) {
                    if (t > 0) {
                        sb.append(", ");
                    }
                    sb.append(threadLoad[t]);
                }
                log.debug("BatchQueue[{}]: rebalance check — thread loads: [{}]", name, sb);
            }

            // Step 3: Delegate to the configured balancer
            final int[] newOwner = config.getBalancer().assign(snapshot, currentOwner, taskCount);
            if (newOwner == null) {
                return; // Balancer decided to skip this cycle
            }

            // Step 4: Diff — find partitions that changed owner
            final List<int[]> moves = new ArrayList<>(); // [partition, oldTask, newTask]
            for (int p = 0; p < partitionCount; p++) {
                final int oldTask = currentOwner[p];
                final int newTask = newOwner[p];
                if (oldTask != newTask && oldTask >= 0 && newTask >= 0) {
                    moves.add(new int[]{p, oldTask, newTask});
                }
            }

            if (moves.isEmpty()) {
                return;
            }

            // Step 5: Two-phase handoff
            // Phase 1 — Revoke: set all moved partitions to UNOWNED
            final long[] cycleSnapshots = new long[taskCount];
            final boolean[] needWait = new boolean[taskCount];
            for (final int[] move : moves) {
                owner.set(move[0], UNOWNED);
                needWait[move[1]] = true;
            }

            // Snapshot cycle counts for old owners that lost partitions
            for (int t = 0; t < taskCount; t++) {
                if (needWait[t]) {
                    cycleSnapshots[t] = cycleCount.get(t);
                }
            }

            // Wait for all old owners to complete their current drain+dispatch cycle
            for (int t = 0; t < taskCount; t++) {
                if (needWait[t]) {
                    final long snap = cycleSnapshots[t];
                    while (cycleCount.get(t) <= snap && running) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    }
                }
            }

            // Phase 2 — Assign: set new owners
            for (final int[] move : moves) {
                owner.set(move[0], move[2]);
            }

            // Rebuild assignedPartitions from ownership array
            this.assignedPartitions = buildAssignmentsFromOwner(owner, taskCount, partitionCount);

            log.info("BatchQueue[{}]: rebalanced {} partitions", name, moves.size());
        } catch (final Throwable t) {
            log.error("BatchQueue[{}]: rebalance error", name, t);
        }
    }

    /**
     * Build partition-to-task assignments from the ownership array.
     */
    private static int[][] buildAssignmentsFromOwner(
            final AtomicIntegerArray owner, final int taskCount, final int partitionCount) {
        final List<List<Integer>> assignment = new ArrayList<>();
        for (int t = 0; t < taskCount; t++) {
            assignment.add(new ArrayList<>());
        }
        for (int p = 0; p < partitionCount; p++) {
            final int t = owner.get(p);
            if (t >= 0 && t < taskCount) {
                assignment.get(t).add(p);
            }
        }
        final int[][] result = new int[taskCount][];
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
     * Stop the queue: reject new produces, perform a final drain of all partitions,
     * and shut down the scheduler.
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
        scheduler.shutdown();
    }

    int getPartitionCount() {
        return partitions.length;
    }

    int getTaskCount() {
        return assignedPartitions.length;
    }

    /**
     * Take a point-in-time snapshot of queue usage across all partitions.
     *
     * @return a stats snapshot containing per-partition usage and capacity
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
