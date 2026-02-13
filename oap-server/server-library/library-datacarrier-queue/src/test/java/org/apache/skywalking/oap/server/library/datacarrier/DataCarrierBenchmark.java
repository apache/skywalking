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

package org.apache.skywalking.oap.server.library.datacarrier;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.BulkConsumePool;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.IConsumer;
import org.junit.jupiter.api.Test;

/**
 * Throughput benchmark for DataCarrier as a baseline for BatchQueue comparison.
 *
 * <p>Simulates the real production pattern: N DataCarrier instances (one per
 * metric type), each with 1 channel, all sharing a {@link BulkConsumePool}
 * with 8 consumer threads. 32 producer threads simulate gRPC connections.
 *
 * <p>Run with: mvn test -pl oap-server/server-library/library-datacarrier-queue
 *           -Dtest=DataCarrierBenchmark -DfailIfNoTests=false
 *
 * <h3>Reference results (Apple M3 Max, 128 GB RAM, macOS 26.2, JDK 17)</h3>
 * <pre>
 * Types  Producers  Pool threads    IF_POSSIBLE       BLOCKING
 * ------ --------- -------------- ------------- -------------
 *   500      32      pool(8)       ~33,400,000   ~32,500,000
 *  1000      32      pool(8)       ~37,600,000   ~36,000,000
 *  2000      32      pool(8)       ~38,000,000   ~42,100,000
 *
 * All runs: 1 channel per carrier, bufferSize=50,000, consumeCycle=1ms, 0% drop rate.
 * </pre>
 *
 * <p><b>BatchQueue comparison (type-aware partitions, typed objects):</b>
 * <pre>
 *              500 types   1000 types   2000 types
 * 1:4           -4%         +6%          +24%
 * 1:2          +14%        +32%          +38%
 * adaptive     +37%        +34%          +68%
 * 1:1          +53%        +63%          +99%
 * </pre>
 *
 * <p>BatchQueue adaptive() = threshold(threadCount * 25), 1:1 below, 1:2
 * above. Consistently outperforms DataCarrier across all type counts.
 * See BatchQueueBenchmark for full details.
 */
@SuppressWarnings("all")
public class DataCarrierBenchmark {

    private static final int WARMUP_SECONDS = 2;
    private static final int MEASURE_SECONDS = 5;
    private static final int PRODUCER_THREADS = 32;
    private static final int POOL_THREADS = 8;
    private static final int BUFFER_SIZE = 50_000;

    @Test
    public void benchmark500Types() throws Exception {
        runSharedPoolBenchmark("500-types", 500, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark1000Types() throws Exception {
        runSharedPoolBenchmark("1000-types", 1000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark2000Types() throws Exception {
        runSharedPoolBenchmark("2000-types", 2000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark500TypesBlocking() throws Exception {
        runSharedPoolBenchmark("500-types-blocking", 500, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark1000TypesBlocking() throws Exception {
        runSharedPoolBenchmark("1000-types-blocking", 1000, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark2000TypesBlocking() throws Exception {
        runSharedPoolBenchmark("2000-types-blocking", 2000, BufferStrategy.BLOCKING);
    }

    private void runSharedPoolBenchmark(final String label, final int typeCount,
                                        final BufferStrategy strategy) throws Exception {
        final AtomicLong consumed = new AtomicLong(0);

        final BulkConsumePool pool = new BulkConsumePool(
            "bench-pool", POOL_THREADS, 1, false);

        final DataCarrier<Long>[] carriers = new DataCarrier[typeCount];
        for (int i = 0; i < typeCount; i++) {
            carriers[i] = new DataCarrier<>(
                "type-" + i, "bench", 1, BUFFER_SIZE, strategy);
            carriers[i].consume(pool, new IConsumer<Long>() {
                @Override
                public void consume(final List<Long> data) {
                    consumed.addAndGet(data.size());
                }

                @Override
                public void onError(final List<Long> data, final Throwable t) {
                    t.printStackTrace();
                }
            });
        }

        // Warmup
        final long warmupEnd = System.currentTimeMillis() + WARMUP_SECONDS * 1000L;
        runProducers(carriers, warmupEnd);
        Thread.sleep(200);
        consumed.set(0);

        // Measure
        final long measureStart = System.currentTimeMillis();
        final long measureEnd = measureStart + MEASURE_SECONDS * 1000L;
        final long produced = runProducers(carriers, measureEnd);
        final long measureDuration = System.currentTimeMillis() - measureStart;

        Thread.sleep(500);
        final long totalConsumed = consumed.get();

        pool.close(null);

        System.out.printf("%n=== DataCarrier Benchmark: %s ===%n"
                + "  Types:       %d (1 DataCarrier per type, 1 channel each)%n"
                + "  Pool threads:%d%n"
                + "  Strategy:    %s%n"
                + "  Producers:   %d%n"
                + "  Duration:    %d ms%n"
                + "  Produced:    %,d%n"
                + "  Consumed:    %,d%n"
                + "  Consume rate:  %,.0f items/sec%n"
                + "  Drop rate:     %.2f%%%n",
            label, typeCount, POOL_THREADS, strategy,
            PRODUCER_THREADS, measureDuration,
            produced, totalConsumed,
            totalConsumed * 1000.0 / measureDuration,
            produced > 0 ? (produced - totalConsumed) * 100.0 / produced : 0);
    }

    private long runProducers(final DataCarrier<Long>[] carriers,
                              final long endTimeMs) throws InterruptedException {
        final int carrierCount = carriers.length;
        final AtomicLong totalProduced = new AtomicLong(0);
        final CountDownLatch done = new CountDownLatch(PRODUCER_THREADS);

        for (int p = 0; p < PRODUCER_THREADS; p++) {
            final int producerIndex = p;
            final Thread thread = new Thread(() -> {
                long count = 0;
                int typeIndex = producerIndex;
                while (System.currentTimeMillis() < endTimeMs) {
                    for (int batch = 0; batch < 100; batch++) {
                        final int type = typeIndex % carrierCount;
                        if (carriers[type].produce(count)) {
                            count++;
                        }
                        typeIndex++;
                    }
                }
                totalProduced.addAndGet(count);
                done.countDown();
            });
            thread.setName("Producer-" + producerIndex);
            thread.setDaemon(true);
            thread.start();
        }

        done.await(MEASURE_SECONDS + 10, TimeUnit.SECONDS);
        return totalProduced.get();
    }
}
