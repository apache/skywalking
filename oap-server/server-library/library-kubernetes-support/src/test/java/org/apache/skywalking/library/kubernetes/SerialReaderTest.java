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

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SerialReaderTest {
    private final Queue<Runnable> executor = new ArrayDeque<>();
    private final AtomicLong millis = new AtomicLong(TimeUnit.HOURS.toMillis(1));
    private final List<String> warnings = new ArrayList<>();
    private final List<String> recoveries = new ArrayList<>();
    private final FailureSummaryLogger refusals = new FailureSummaryLogger(
        "Kubernetes metadata read queueing",
        "a refused key is queued again by a later lookup, and cached data stays available until it expires",
        warnings::add, recoveries::add, millis::get, (task, delayMillis) -> {
            if (delayMillis == 0) {
                task.run();
            }
        });
    private final SerialReader reader = new SerialReader(executor::add, "waiting read queue", refusals);
    private final List<String> done = new ArrayList<>();

    @Test
    public void runsTheQueuedReadsOneAtATimeInOrder() {
        reader.submit(() -> done.add("a"), -1);
        reader.submit(() -> done.add("b"), -1);
        reader.submit(() -> done.add("c"), -1);

        // One task drains the queue, so the reads never run concurrently.
        assertEquals(1, executor.size());
        executor.poll().run();
        assertEquals(List.of("a", "b", "c"), done);

        reader.submit(() -> done.add("d"), -1);
        assertEquals(1, executor.size());
        executor.poll().run();
        assertEquals(List.of("a", "b", "c", "d"), done);
    }

    @Test
    public void refusesAReadWhileTheQueueIsFull() {
        assertTrue(reader.submit(() -> done.add("a"), 2));
        assertTrue(reader.submit(() -> done.add("b"), 2));
        assertFalse(reader.submit(() -> done.add("c"), 2));

        executor.poll().run();
        assertTrue(reader.submit(() -> done.add("c"), 2));
        executor.poll().run();
        assertEquals(List.of("a", "b", "c"), done);
    }

    @Test
    public void summarizesTheReadsRefusedAsTheQueueIsFullAndTheirEnd() {
        reader.submit(() -> done.add("a"), 1);
        assertFalse(reader.submit(() -> done.add("b"), 1));
        assertFalse(reader.submit(() -> done.add("c"), 1));
        millis.addAndGet(TimeUnit.MINUTES.toMillis(1));
        refusals.logIfDue();
        assertEquals(List.of(
            "Kubernetes metadata read queueing failed for 1 target in the last 1 minute, a refused key is queued "
                + "again by a later lookup, and cached data stays available until it expires: waiting read queue full "
                + "size=1 count=2"),
                     warnings);

        executor.poll().run();
        assertTrue(reader.submit(() -> done.add("d"), 1));
        assertEquals(List.of("Kubernetes metadata read queueing recovered."), recoveries);
    }

    @Test
    public void anUnlimitedQueueNeverRefuses() {
        for (int i = 0; i < 100; i++) {
            assertTrue(reader.submit(() -> done.add("x"), -1));
        }
        millis.addAndGet(TimeUnit.MINUTES.toMillis(1));
        refusals.logIfDue();
        assertTrue(warnings.isEmpty());
    }
}
