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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchQueueTest {

    @AfterEach
    public void cleanup() {
        BatchQueueManager.reset();
    }

    // --- Direct consumer mode ---

    @Test
    public void testDirectConsumerReceivesAllData() {
        final List<String> received = new CopyOnWriteArrayList<>();
        final BatchQueue<String> queue = BatchQueueManager.create("direct-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .consumer(data -> received.addAll(data))
                .bufferSize(1000)
                .build());

        for (int i = 0; i < 100; i++) {
            queue.produce("item-" + i);
        }

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> received.size() == 100);

        assertEquals(100, received.size());
    }

    @Test
    public void testDirectConsumerWithMultipleThreads() {
        final List<String> received = Collections.synchronizedList(new ArrayList<>());
        final BatchQueue<String> queue = BatchQueueManager.create("multi-thread-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(4))
                .partitions(PartitionPolicy.fixed(4))
                .consumer(data -> received.addAll(data))
                .bufferSize(1000)
                .build());

        for (int i = 0; i < 1000; i++) {
            queue.produce("item-" + i);
        }

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> received.size() == 1000);

        assertEquals(1000, received.size());
    }

    // --- Handler map dispatch ---

    static class MetricA {
        final String name;

        MetricA(final String name) {
            this.name = name;
        }
    }

    static class MetricB {
        final String name;

        MetricB(final String name) {
            this.name = name;
        }
    }

    @Test
    public void testHandlerMapDispatch() {
        final List<Object> receivedA = new CopyOnWriteArrayList<>();
        final List<Object> receivedB = new CopyOnWriteArrayList<>();

        final BatchQueue<Object> queue = BatchQueueManager.create("handler-map-test",
            BatchQueueConfig.<Object>builder()
                .threads(ThreadPolicy.fixed(1))
                .bufferSize(1000)
                .build());

        queue.addHandler(MetricA.class, data -> receivedA.addAll(data));
        queue.addHandler(MetricB.class, data -> receivedB.addAll(data));

        for (int i = 0; i < 50; i++) {
            queue.produce(new MetricA("a-" + i));
            queue.produce(new MetricB("b-" + i));
        }

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> receivedA.size() == 50 && receivedB.size() == 50);

        assertEquals(50, receivedA.size());
        assertEquals(50, receivedB.size());
    }

    // --- Partition assignment ---

    @Test
    public void testPartitionCountMatchesPolicy() {
        final BatchQueue<String> queue = BatchQueueManager.create("partition-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(2))
                .partitions(PartitionPolicy.fixed(8))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        assertEquals(8, queue.getPartitionCount());
        assertEquals(2, queue.getTaskCount());
    }

    @Test
    public void testThreadMultiplyPartitions() {
        final BatchQueue<String> queue = BatchQueueManager.create("multiply-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(3))
                .partitions(PartitionPolicy.threadMultiply(2))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        assertEquals(6, queue.getPartitionCount());
        assertEquals(3, queue.getTaskCount());
    }

    @Test
    public void testPartitionsLessThanThreadsReducesThreads() {
        // 2 partitions but 4 threads → should reduce to 2 tasks
        final BatchQueue<String> queue = BatchQueueManager.create("reduce-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(4))
                .partitions(PartitionPolicy.fixed(2))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        assertEquals(2, queue.getPartitionCount());
        assertEquals(2, queue.getTaskCount());
    }

    @Test
    public void testEachPartitionAssignedToExactlyOneTask() {
        final BatchQueue<String> queue = BatchQueueManager.create("assign-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(3))
                .partitions(PartitionPolicy.fixed(9))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        final int[][] assignments = queue.getAssignedPartitions();
        assertEquals(3, assignments.length);

        // Collect all assigned partitions
        final boolean[] assigned = new boolean[9];
        for (final int[] taskPartitions : assignments) {
            for (final int p : taskPartitions) {
                assertFalse(assigned[p], "Partition " + p + " assigned twice");
                assigned[p] = true;
            }
        }
        for (int i = 0; i < 9; i++) {
            assertTrue(assigned[i], "Partition " + i + " not assigned");
        }
    }

    @Test
    public void testAdaptivePartitionGrowsWithHandlers() {
        final BatchQueue<Object> queue = BatchQueueManager.create("adaptive-test",
            BatchQueueConfig.<Object>builder()
                .threads(ThreadPolicy.fixed(8))
                .partitions(PartitionPolicy.adaptive())
                .bufferSize(100)
                .build());

        // Initial: 8 partitions (threadCount)
        assertEquals(8, queue.getPartitionCount());

        // Register 500 handlers — threshold = 8*25 = 200, so 200 + 300/2 = 350
        for (int i = 0; i < 500; i++) {
            queue.addHandler(BenchmarkMetricTypes.CLASSES[i], data -> { });
        }

        assertEquals(350, queue.getPartitionCount());
    }

    @Test
    public void testAdaptiveBelowThresholdIs1to1() {
        final BatchQueue<Object> queue = BatchQueueManager.create("adaptive-below",
            BatchQueueConfig.<Object>builder()
                .threads(ThreadPolicy.fixed(8))
                .partitions(PartitionPolicy.adaptive())
                .bufferSize(100)
                .build());

        // Register 100 handlers — below threshold (200), so 1:1
        for (int i = 0; i < 100; i++) {
            queue.addHandler(BenchmarkMetricTypes.CLASSES[i], data -> { });
        }

        assertEquals(100, queue.getPartitionCount());
    }

    // --- Shared scheduler ---

    @Test
    public void testSharedSchedulerQueuesSharePool() {
        final List<String> received1 = new CopyOnWriteArrayList<>();
        final List<String> received2 = new CopyOnWriteArrayList<>();

        final BatchQueue<String> q1 = BatchQueueManager.create("shared-q1",
            BatchQueueConfig.<String>builder()
                .sharedScheduler("TEST_POOL", ThreadPolicy.fixed(2))
                .consumer(data -> received1.addAll(data))
                .bufferSize(1000)
                .build());

        final BatchQueue<String> q2 = BatchQueueManager.create("shared-q2",
            BatchQueueConfig.<String>builder()
                .sharedScheduler("TEST_POOL", ThreadPolicy.fixed(2))
                .consumer(data -> received2.addAll(data))
                .bufferSize(1000)
                .build());

        assertFalse(q1.isDedicatedScheduler());
        assertFalse(q2.isDedicatedScheduler());

        for (int i = 0; i < 50; i++) {
            q1.produce("a-" + i);
            q2.produce("b-" + i);
        }

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> received1.size() == 50 && received2.size() == 50);
    }

    @Test
    public void testDedicatedSchedulerIsOwned() {
        final BatchQueue<String> queue = BatchQueueManager.create("dedicated-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(2))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        assertTrue(queue.isDedicatedScheduler());
    }

    // --- Produce and buffer strategy ---

    @Test
    public void testIfPossibleDropsWhenFull() {
        final CountDownLatch blockLatch = new CountDownLatch(1);
        final BatchQueue<String> queue = BatchQueueManager.create("drop-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .strategy(BufferStrategy.IF_POSSIBLE)
                .consumer(data -> {
                    try {
                        blockLatch.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })
                .bufferSize(5)
                .build());

        // Produce enough to fill the buffer while consumer is blocked
        Awaitility.await().atMost(2, TimeUnit.SECONDS)
            .pollInterval(10, TimeUnit.MILLISECONDS)
            .until(() -> {
                queue.produce("x");
                return !queue.produce("overflow");
            });

        blockLatch.countDown();
    }

    @Test
    public void testProduceReturnsFalseWhenStopped() {
        final BatchQueue<String> queue = BatchQueueManager.create("stopped-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .consumer(data -> { })
                .bufferSize(100)
                .build());

        assertTrue(queue.produce("before-shutdown"));
        queue.shutdown();
        assertFalse(queue.produce("after-shutdown"));
    }

    // --- Shutdown final drain ---

    @Test
    public void testShutdownDrainsRemainingData() throws Exception {
        final List<String> received = new CopyOnWriteArrayList<>();
        final CountDownLatch blockLatch = new CountDownLatch(1);
        final AtomicInteger consumeCalls = new AtomicInteger(0);

        final BatchQueue<String> queue = BatchQueueManager.create("drain-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .consumer(data -> {
                    if (consumeCalls.getAndIncrement() == 0) {
                        try {
                            blockLatch.await(2, TimeUnit.SECONDS);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    received.addAll(data);
                })
                .bufferSize(10_000)
                .strategy(BufferStrategy.IF_POSSIBLE)
                .build());

        int produced = 0;
        for (int i = 0; i < 500; i++) {
            if (queue.produce("item-" + i)) {
                produced++;
            }
        }

        blockLatch.countDown();
        Thread.sleep(50);
        queue.shutdown();

        assertEquals(produced, received.size());
    }

    // --- Idle callback ---

    @Test
    public void testOnIdleCalledWhenEmpty() {
        final AtomicInteger idleCalls = new AtomicInteger(0);

        BatchQueueManager.create("idle-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .consumer(new HandlerConsumer<String>() {
                    @Override
                    public void consume(final List<String> data) {
                    }

                    @Override
                    public void onIdle() {
                        idleCalls.incrementAndGet();
                    }
                })
                .bufferSize(100)
                .build());

        Awaitility.await().atMost(3, TimeUnit.SECONDS)
            .until(() -> idleCalls.get() > 0);

        assertTrue(idleCalls.get() > 0);
    }

    @Test
    public void testHandlerMapOnIdleCalled() {
        final AtomicInteger idleA = new AtomicInteger(0);
        final AtomicInteger idleB = new AtomicInteger(0);

        final BatchQueue<Object> queue = BatchQueueManager.create("handler-idle-test",
            BatchQueueConfig.<Object>builder()
                .threads(ThreadPolicy.fixed(1))
                .bufferSize(100)
                .build());

        queue.addHandler(MetricA.class, new HandlerConsumer<Object>() {
            @Override
            public void consume(final List<Object> data) {
            }

            @Override
            public void onIdle() {
                idleA.incrementAndGet();
            }
        });
        queue.addHandler(MetricB.class, new HandlerConsumer<Object>() {
            @Override
            public void consume(final List<Object> data) {
            }

            @Override
            public void onIdle() {
                idleB.incrementAndGet();
            }
        });

        Awaitility.await().atMost(3, TimeUnit.SECONDS)
            .until(() -> idleA.get() > 0 && idleB.get() > 0);
    }

    // --- Error handler ---

    @Test
    public void testErrorHandlerCalled() {
        final AtomicInteger errorCount = new AtomicInteger(0);

        final BatchQueue<String> queue = BatchQueueManager.create("error-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .consumer(data -> {
                    throw new RuntimeException("intentional");
                })
                .errorHandler((data, t) -> errorCount.incrementAndGet())
                .bufferSize(100)
                .build());

        queue.produce("trigger-error");

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> errorCount.get() > 0);
    }

    // --- Adaptive backoff ---

    @Test
    public void testAdaptiveBackoffIncreasesDelay() throws Exception {
        final List<Long> idleTimestamps = new CopyOnWriteArrayList<>();

        BatchQueueManager.create("backoff-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .minIdleMs(10)
                .maxIdleMs(500)
                .consumer(new HandlerConsumer<String>() {
                    @Override
                    public void consume(final List<String> data) {
                    }

                    @Override
                    public void onIdle() {
                        idleTimestamps.add(System.currentTimeMillis());
                    }
                })
                .bufferSize(100)
                .build());

        Awaitility.await().atMost(10, TimeUnit.SECONDS)
            .until(() -> idleTimestamps.size() >= 8);

        final long earlyGap = idleTimestamps.get(1) - idleTimestamps.get(0);
        final long laterGap = idleTimestamps.get(idleTimestamps.size() - 1)
            - idleTimestamps.get(idleTimestamps.size() - 2);

        assertTrue(laterGap > earlyGap,
            "Later gap (" + laterGap + "ms) should be larger than early gap (" + earlyGap + "ms)");
    }

    @Test
    public void testBackoffResetsOnData() throws Exception {
        final AtomicInteger consumeCount = new AtomicInteger(0);
        final List<Long> idleTimestamps = new CopyOnWriteArrayList<>();

        final BatchQueue<String> queue = BatchQueueManager.create("backoff-reset-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .minIdleMs(10)
                .maxIdleMs(2000)
                .consumer(new HandlerConsumer<String>() {
                    @Override
                    public void consume(final List<String> data) {
                        consumeCount.addAndGet(data.size());
                    }

                    @Override
                    public void onIdle() {
                        idleTimestamps.add(System.currentTimeMillis());
                    }
                })
                .bufferSize(100)
                .build());

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> idleTimestamps.size() >= 5);

        final int beforeIdles = idleTimestamps.size();
        for (int i = 0; i < 10; i++) {
            queue.produce("reset-" + i);
        }

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> consumeCount.get() >= 10);

        final long postDataTime = System.currentTimeMillis();
        Awaitility.await().atMost(3, TimeUnit.SECONDS)
            .until(() -> {
                for (int i = beforeIdles; i < idleTimestamps.size(); i++) {
                    if (idleTimestamps.get(i) > postDataTime) {
                        return true;
                    }
                }
                return false;
            });
    }

}
