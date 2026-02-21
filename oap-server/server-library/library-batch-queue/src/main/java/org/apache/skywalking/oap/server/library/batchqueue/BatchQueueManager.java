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
import lombok.extern.slf4j.Slf4j;

/**
 * Global registry for batch queues.
 * Thread-safe. Queues are created by name and shared across modules.
 *
 * <p>Internal maps:
 * <pre>
 * QUEUES: queueName -&gt; BatchQueue instance
 * </pre>
 */
@Slf4j
public class BatchQueueManager {
    /**
     * queueName -&gt; BatchQueue instance. Each queue has a unique name.
     */
    private static final ConcurrentHashMap<String, BatchQueue<?>> QUEUES = new ConcurrentHashMap<>();

    /**
     * Create a new queue with the given name and config. Throws if a queue with the same name
     * already exists. Use {@link #getOrCreate} when multiple callers share a single queue.
     */
    public static <T> BatchQueue<T> create(final String name, final BatchQueueConfig<T> config) {
        config.validate();
        final BatchQueue<T> queue = new BatchQueue<>(name, config);
        final BatchQueue<?> existing = QUEUES.putIfAbsent(name, queue);
        if (existing != null) {
            queue.shutdown();
            throw new IllegalStateException(
                "BatchQueue [" + name + "] already exists. Each queue name must be unique.");
        }
        return queue;
    }

    /**
     * Get an existing queue or create a new one. The first caller creates the queue;
     * subsequent callers with the same name get the existing instance (config is ignored
     * for them). Thread-safe via CAS on the internal map.
     */
    @SuppressWarnings("unchecked")
    public static <T> BatchQueue<T> getOrCreate(final String name, final BatchQueueConfig<T> config) {
        final BatchQueue<?> existing = QUEUES.get(name);
        if (existing != null) {
            return (BatchQueue<T>) existing;
        }
        config.validate();
        final BatchQueue<T> queue = new BatchQueue<>(name, config);
        final BatchQueue<?> prev = QUEUES.putIfAbsent(name, queue);
        if (prev != null) {
            queue.shutdown();
            return (BatchQueue<T>) prev;
        }
        return queue;
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
     * Shutdown all queues. Called during OAP server shutdown.
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
    }

    /**
     * Reset for testing purposes only.
     */
    static void reset() {
        shutdownAll();
    }
}
