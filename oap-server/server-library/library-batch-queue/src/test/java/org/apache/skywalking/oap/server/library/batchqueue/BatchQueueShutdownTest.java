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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchQueueShutdownTest {

    @AfterEach
    public void cleanup() {
        BatchQueueManager.reset();
    }

    /**
     * shutdown() must not run the final drain while a drain loop is still inside consume().
     * Before the fix the caller dispatched immediately, so the handler saw two concurrent
     * invocations — the invariant workers such as MetricsAggregateWorker rely on.
     */
    @Test
    public void testShutdownWaitsForInFlightConsumer() throws Exception {
        final CountDownLatch consumerEntered = new CountDownLatch(1);
        final AtomicInteger concurrentConsumers = new AtomicInteger();
        final AtomicInteger maxConcurrentConsumers = new AtomicInteger();

        final BatchQueue<String> queue = BatchQueueManager.create("shutdown-wait-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .partitions(PartitionPolicy.fixed(1))
                .bufferSize(100)
                .shutdownTimeoutMs(5_000)
                .consumer(batch -> {
                    maxConcurrentConsumers.accumulateAndGet(
                        concurrentConsumers.incrementAndGet(), Math::max);
                    try {
                        consumerEntered.countDown();
                        Thread.sleep(300);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        concurrentConsumers.decrementAndGet();
                    }
                })
                .build());

        queue.produce("first");
        assertTrue(consumerEntered.await(5, TimeUnit.SECONDS), "consumer never started");
        // Queued behind the consumer that is currently sleeping, so it is still buffered
        // when shutdown() begins.
        queue.produce("second");

        queue.shutdown();

        assertEquals(0, concurrentConsumers.get(), "a consumer was still running after shutdown returned");
        assertEquals(1, maxConcurrentConsumers.get(), "consumer was invoked concurrently during shutdown");
    }

    /**
     * The final drain must still flush whatever is left in the partitions once the loops
     * have stopped.
     */
    @Test
    public void testShutdownFinalDrainFlushesRemainder() {
        final List<String> consumed = new CopyOnWriteArrayList<>();

        final BatchQueue<String> queue = BatchQueueManager.create("shutdown-drain-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .partitions(PartitionPolicy.fixed(1))
                .bufferSize(100)
                .minIdleMs(10_000).maxIdleMs(10_000)   // keep the drain loop asleep
                .shutdownTimeoutMs(2_000)
                .consumer(consumed::addAll)
                .build());

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                  .until(() -> queue.produce("a") && queue.produce("b"));

        final long start = System.nanoTime();
        queue.shutdown();
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(consumed.containsAll(List.of("a", "b")), "final drain lost data: " + consumed);
        // A drain task parked on a long idle backoff must not hold up termination: the scheduler
        // drops pending delayed tasks, so shutdown returns well inside its timeout.
        assertTrue(elapsedMs < 2_000,
                   "shutdown waited out its timeout instead of terminating: " + elapsedMs + "ms");
    }

    /**
     * A rebalancing queue must still reach termination. On a virtual-thread scheduler the
     * periodic task is one submission whose loop exits only on interrupt, so shutdown() has
     * to cancel it explicitly or awaitTermination can never succeed.
     */
    @Test
    public void testShutdownTerminatesWithRebalancingEnabled() {
        final BatchQueue<String> queue = BatchQueueManager.create("shutdown-rebalance-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(2))
                .partitions(PartitionPolicy.fixed(4))
                .balancer(DrainBalancer.throughputWeighted(), 50)
                .bufferSize(100)
                .shutdownTimeoutMs(5_000)
                .consumer(batch -> {
                })
                .build());

        queue.produce("x");
        final long start = System.nanoTime();
        queue.shutdown();
        final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMs < 5_000,
                   "shutdown hit the timeout, the periodic rebalance task was not cancelled: "
                       + elapsedMs + "ms");
    }

    /**
     * A timeout must not reintroduce the race. awaitTermination is only a courtesy wait for
     * orderly exit; the exclusive dispatch lock is what guarantees the consumer is never entered
     * from two threads. Reproduces with a consumer far slower than the timeout.
     */
    @Test
    public void testTimeoutStillSerialisesTheFinalDispatch() throws Exception {
        final CountDownLatch consumerEntered = new CountDownLatch(1);
        final AtomicInteger concurrentConsumers = new AtomicInteger();
        final AtomicInteger maxConcurrentConsumers = new AtomicInteger();

        final BatchQueue<String> queue = BatchQueueManager.create("shutdown-timeout-race-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .partitions(PartitionPolicy.fixed(1))
                .bufferSize(100)
                .shutdownTimeoutMs(50)          // far shorter than the consumer below
                .consumer(batch -> {
                    maxConcurrentConsumers.accumulateAndGet(
                        concurrentConsumers.incrementAndGet(), Math::max);
                    try {
                        consumerEntered.countDown();
                        Thread.sleep(500);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        concurrentConsumers.decrementAndGet();
                    }
                })
                .build());

        queue.produce("first");
        assertTrue(consumerEntered.await(5, TimeUnit.SECONDS), "consumer never started");
        queue.produce("second");

        queue.shutdown();

        assertEquals(1, maxConcurrentConsumers.get(),
                     "final dispatch raced an in-flight consumer after the wait timed out");
    }

    /**
     * A caller that loses the shutdown CAS must not return before the winner has finished, or
     * shutdownAll() reports completion while a consumer is still running.
     */
    @Test
    public void testLosingShutdownCallerAwaitsCompletion() throws Exception {
        final CountDownLatch consumerEntered = new CountDownLatch(1);
        final AtomicInteger activeConsumers = new AtomicInteger();

        final BatchQueue<String> queue = BatchQueueManager.create("shutdown-share-completion-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .partitions(PartitionPolicy.fixed(1))
                .bufferSize(100)
                .shutdownTimeoutMs(5_000)
                .consumer(batch -> {
                    activeConsumers.incrementAndGet();
                    try {
                        consumerEntered.countDown();
                        Thread.sleep(400);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        activeConsumers.decrementAndGet();
                    }
                })
                .build());

        queue.produce("first");
        assertTrue(consumerEntered.await(5, TimeUnit.SECONDS), "consumer never started");

        final Thread winner = new Thread(queue::shutdown);
        winner.start();
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(queue::isShutdownStarted);

        // The loser returns from shutdown(); by then nothing may still be consuming.
        queue.shutdown();
        assertEquals(0, activeConsumers.get(),
                     "the losing shutdown caller returned while a consumer was still running");
        winner.join(10_000);
    }

    /**
     * onIdle() runs on the drain thread and touches the same worker state as consume() — L1's
     * onIdle() calls flush(). It must therefore be inside the dispatch lock, or shutdown's final
     * dispatch can run concurrently with it once the courtesy wait expires.
     */
    @Test
    public void testIdleNotificationIsSerialisedAgainstTheFinalDispatch() throws Exception {
        final CountDownLatch idleEntered = new CountDownLatch(1);
        final AtomicInteger concurrentCallbacks = new AtomicInteger();
        final AtomicInteger maxConcurrentCallbacks = new AtomicInteger();

        final HandlerConsumer<String> handler = new HandlerConsumer<>() {
            @Override
            public void consume(final List<String> data) {
                enter();
                try {
                    Thread.sleep(50);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    concurrentCallbacks.decrementAndGet();
                }
            }

            @Override
            public void onIdle() {
                enter();
                try {
                    idleEntered.countDown();
                    Thread.sleep(500);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    concurrentCallbacks.decrementAndGet();
                }
            }

            private void enter() {
                maxConcurrentCallbacks.accumulateAndGet(concurrentCallbacks.incrementAndGet(), Math::max);
            }
        };

        final BatchQueue<String> queue = BatchQueueManager.create("shutdown-onidle-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .partitions(PartitionPolicy.fixed(1))
                .bufferSize(100)
                .shutdownTimeoutMs(50)          // far shorter than the onIdle above
                .build());
        queue.addHandler(String.class, handler);

        assertTrue(idleEntered.await(5, TimeUnit.SECONDS), "onIdle never ran");
        queue.produce("queued-behind-idle");

        queue.shutdown();

        assertEquals(1, maxConcurrentCallbacks.get(),
                     "final dispatch ran concurrently with onIdle() after the wait timed out");
    }

    /**
     * The winner can exceed shutdownTimeoutMs — it waits on the write lock for as long as the
     * consumer runs — so a losing caller must await actual completion, not a fixed bound.
     */
    @Test
    public void testLosingCallerWaitsOutASlowWinner() throws Exception {
        final CountDownLatch consumerEntered = new CountDownLatch(1);
        final AtomicInteger activeConsumers = new AtomicInteger();

        final BatchQueue<String> queue = BatchQueueManager.create("shutdown-slow-winner-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.fixed(1))
                .partitions(PartitionPolicy.fixed(1))
                .bufferSize(100)
                .shutdownTimeoutMs(50)          // winner's courtesy wait expires long before...
                .consumer(batch -> {
                    activeConsumers.incrementAndGet();
                    try {
                        consumerEntered.countDown();
                        Thread.sleep(1_500);    // ...this consumer finishes
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        activeConsumers.decrementAndGet();
                    }
                })
                .build());

        queue.produce("first");
        assertTrue(consumerEntered.await(5, TimeUnit.SECONDS), "consumer never started");

        final Thread winner = new Thread(queue::shutdown);
        winner.start();
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(queue::isShutdownStarted);

        final long loserStart = System.nanoTime();
        queue.shutdown();   // loser
        final long loserWaitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - loserStart);

        assertEquals(0, activeConsumers.get(),
                     "the losing caller returned while a consumer was still running");
        // The winner's courtesy wait is 50ms but the consumer runs 600ms, so a loser bounded by
        // shutdownTimeoutMs returns in ~50ms. Deliberately NOT asserting on winner.isAlive():
        // the latch fires inside doShutdown's finally, so the winner thread is still unwinding
        // for a moment afterwards — that is thread teardown, not the contract.
        assertTrue(loserWaitedMs >= 100,
                   "the losing caller did not wait for the winner, returned after " + loserWaitedMs + "ms");
        winner.join(10_000);
    }

    /**
     * An IO-bound queue behaves identically to a fixed one — the substrate differs, the
     * semantics do not. Runs on virtual threads where available, platform threads otherwise.
     */
    @Test
    public void testIoBoundQueueDrainsAndShutsDown() {
        final List<String> consumed = new CopyOnWriteArrayList<>();

        final BatchQueue<String> queue = BatchQueueManager.create("io-bound-test",
            BatchQueueConfig.<String>builder()
                .threads(ThreadPolicy.ioBound(4))
                .partitions(PartitionPolicy.fixed(4))
                .bufferSize(10)
                .shutdownTimeoutMs(5_000)
                .consumer(consumed::addAll)
                .build());

        for (int i = 0; i < 20; i++) {
            queue.produce("item-" + i);
        }
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> consumed.size() == 20);

        queue.shutdown();
        assertEquals(20, consumed.size());
    }
}
