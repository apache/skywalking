/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.library.kubernetes;

import lombok.extern.slf4j.Slf4j;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A lane of the reads of the {@link LookupCache}s, run one at a time, so a backlog never turns into concurrent requests
 * to the API server, whether the executor runs a virtual thread per task or not. Reads refused as the lane is full,
 * possible only when its size is limited, are summarized at WARN.
 */
@Slf4j
final class SerialReader {
    private static final FailureSummaryLogger REFUSALS = new FailureSummaryLogger(
        "Kubernetes metadata read queueing",
        "a refused key is queued again by a later lookup, and cached data stays available until it expires",
        log::warn, log::info
    );

    private final Executor executor;
    private final String lane;
    private final FailureSummaryLogger refusals;
    private final Queue<Runnable> queued = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * @param lane names the lane in the summary of refused reads, such as {@code waiting read queue}
     */
    SerialReader(final Executor executor, final String lane) {
        this(executor, lane, REFUSALS);
    }

    SerialReader(final Executor executor, final String lane, final FailureSummaryLogger refusals) {
        this.executor = executor;
        this.lane = lane;
        this.refusals = refusals;
    }

    /**
     * @param maxQueued the most reads waiting at once, or a negative number for no limit
     * @return false when {@code read} is not queued, as {@code maxQueued} reads are waiting
     */
    boolean submit(final Runnable read, final int maxQueued) {
        int current;
        do {
            current = size.get();
            if (maxQueued >= 0 && current >= maxQueued) {
                refusals.recordFailure(lane, "full size=" + maxQueued);
                return false;
            }
        }
        while (!size.compareAndSet(current, current + 1));
        if (maxQueued >= 0) {
            refusals.recordSuccess(lane);
        }
        queued.add(read);
        startRunning();
        return true;
    }

    private void startRunning() {
        if (running.compareAndSet(false, true)) {
            executor.execute(this::runQueued);
        }
    }

    private void runQueued() {
        try {
            for (Runnable read = queued.poll(); read != null; read = queued.poll()) {
                size.decrementAndGet();
                read.run();
            }
        } finally {
            running.set(false);
            // A read queued after the last poll and before running was cleared would wait for the next submit.
            if (!queued.isEmpty()) {
                startRunning();
            }
        }
    }
}
