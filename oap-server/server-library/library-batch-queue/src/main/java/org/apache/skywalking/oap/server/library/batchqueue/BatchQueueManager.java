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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Global registry for batch queues and shared schedulers.
 * Thread-safe. Queues are created by name and shared across modules.
 *
 * <p>Shared schedulers are created lazily on first queue reference — no separate
 * setup step needed. This eliminates startup ordering dependencies.
 *
 * <p>Shared schedulers are reference-counted: each queue that uses a shared scheduler
 * increments the count on creation and decrements on shutdown. When the count reaches
 * zero, the scheduler is shut down automatically.
 *
 * <p>Internal maps:
 * <pre>
 * QUEUES:                     queueName     -> BatchQueue instance
 * SHARED_SCHEDULERS:          schedulerName -> ScheduledExecutorService
 * SHARED_SCHEDULER_POLICIES:  schedulerName -> ThreadPolicy (first-wins)
 * SHARED_SCHEDULER_REF_COUNTS: schedulerName -> AtomicInteger (reference count)
 * </pre>
 */
@Slf4j
public class BatchQueueManager {
    /**
     * queueName -> BatchQueue instance. Each queue has a unique name.
     */
    private static final ConcurrentHashMap<String, BatchQueue<?>> QUEUES = new ConcurrentHashMap<>();
    /**
     * schedulerName -> ScheduledExecutorService. Multiple queues can share one scheduler by
     * referencing the same scheduler name in their config.
     */
    private static final ConcurrentHashMap<String, ScheduledExecutorService> SHARED_SCHEDULERS =
        new ConcurrentHashMap<>();
    /**
     * schedulerName -> ThreadPolicy. Tracks the first-wins policy for each shared scheduler
     * to detect mismatched configs.
     */
    private static final ConcurrentHashMap<String, ThreadPolicy> SHARED_SCHEDULER_POLICIES =
        new ConcurrentHashMap<>();
    /**
     * schedulerName -> reference count. Incremented when a queue acquires the scheduler,
     * decremented when a queue releases it. Scheduler is shut down when count reaches 0.
     */
    private static final ConcurrentHashMap<String, AtomicInteger> SHARED_SCHEDULER_REF_COUNTS =
        new ConcurrentHashMap<>();

    /**
     * Get or create a shared scheduler and increment its reference count.
     * Called internally by BatchQueue constructor.
     * First call creates the scheduler; subsequent calls reuse it.
     * If ThreadPolicy differs from the first creator, logs a warning (first one wins).
     */
    static ScheduledExecutorService getOrCreateSharedScheduler(final String name,
                                                                final ThreadPolicy threads) {
        SHARED_SCHEDULER_POLICIES.compute(name, (k, existing) -> {
            if (existing != null) {
                if (!existing.toString().equals(threads.toString())) {
                    log.warn("Shared scheduler [{}]: ThreadPolicy mismatch. "
                            + "Existing={}, requested={}. Using existing.",
                        name, existing, threads);
                }
                return existing;
            }
            return threads;
        });

        SHARED_SCHEDULER_REF_COUNTS.computeIfAbsent(name, k -> new AtomicInteger(0)).incrementAndGet();

        return SHARED_SCHEDULERS.computeIfAbsent(name, k -> {
            final int threadCount = threads.resolve();
            log.info("Creating shared scheduler [{}] with {} threads ({})",
                name, threadCount, threads);
            return Executors.newScheduledThreadPool(threadCount, r -> {
                final Thread t = new Thread(r);
                t.setName("SharedScheduler-" + name + "-" + t.getId());
                t.setDaemon(true);
                return t;
            });
        });
    }

    /**
     * Decrement the reference count for a shared scheduler.
     * When the count reaches zero, the scheduler is shut down and removed.
     */
    static void releaseSharedScheduler(final String name) {
        final AtomicInteger refCount = SHARED_SCHEDULER_REF_COUNTS.get(name);
        if (refCount == null) {
            return;
        }
        if (refCount.decrementAndGet() <= 0) {
            SHARED_SCHEDULER_REF_COUNTS.remove(name);
            SHARED_SCHEDULER_POLICIES.remove(name);
            final ScheduledExecutorService scheduler = SHARED_SCHEDULERS.remove(name);
            if (scheduler != null) {
                log.info("Shutting down shared scheduler [{}] (ref count reached 0)", name);
                scheduler.shutdown();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> BatchQueue<T> create(final String name, final BatchQueueConfig<T> config) {
        config.validate();
        return (BatchQueue<T>) QUEUES.computeIfAbsent(name, k -> new BatchQueue<>(name, config));
    }

    @SuppressWarnings("unchecked")
    public static <T> BatchQueue<T> get(final String name) {
        return (BatchQueue<T>) QUEUES.get(name);
    }

    public static void shutdown(final String name) {
        final BatchQueue<?> queue = QUEUES.remove(name);
        if (queue != null) {
            queue.shutdown();
        }
    }

    /**
     * Shutdown all queues and all shared schedulers. Called during OAP server shutdown.
     */
    public static void shutdownAll() {
        final List<BatchQueue<?>> allQueues = new ArrayList<>(QUEUES.values());
        QUEUES.clear();

        for (final BatchQueue<?> queue : allQueues) {
            try {
                queue.shutdown();
            } catch (final Throwable t) {
                log.error("Error shutting down queue: {}", queue.getName(), t);
            }
        }

        for (final ScheduledExecutorService scheduler : SHARED_SCHEDULERS.values()) {
            try {
                scheduler.shutdown();
            } catch (final Throwable t) {
                log.error("Error shutting down shared scheduler", t);
            }
        }
        SHARED_SCHEDULERS.clear();
        SHARED_SCHEDULER_POLICIES.clear();
        SHARED_SCHEDULER_REF_COUNTS.clear();
    }

    /**
     * Reset for testing purposes only.
     */
    static void reset() {
        shutdownAll();
    }
}
