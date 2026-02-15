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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Throughput benchmark for BatchQueue with handler-map dispatch.
 *
 * <p>Simulates production OAP metrics aggregation: 500-2000 distinct metric types,
 * 32 gRPC producer threads, 8 consumer threads. Tests various partition strategies
 * from fixed small counts to 1:1 partition-per-type binding.
 *
 * <p>Run with: mvn test -pl oap-server/server-library/library-batch-queue
 *           -Dtest=BatchQueueBenchmark -DfailIfNoTests=false
 *
 * <h3>Reference results (Apple M3 Max, 128 GB RAM, macOS 26.2, JDK 17)</h3>
 *
 * <p><b>Fixed partitions (typeHash selector):</b>
 * <pre>
 * Partitions  BufSize   500 types (IF/BLK)      1000 types (IF/BLK)     2000 types (IF/BLK)
 * ---------- -------- ----------------------- ----------------------- -----------------------
 * fixed(16)   50,000   ~19.0M / ~12.3M         ~17.9M / ~17.1M         ~15.7M / ~15.2M
 * fixed(64)  500,000   ~17.0M / ~18.0M         ~17.7M / ~18.4M         ~17.7M / ~18.0M
 * fixed(128) 500,000   ~24.1M / ~23.2M         ~24.9M / ~26.1M         ~25.3M / ~24.8M
 * </pre>
 *
 * <p><b>Type-aware partitions (typeId selector, 50K buffer each):</b>
 * <pre>
 * Ratio       Partitions   500 types (IF/BLK)      1000 types (IF/BLK)     2000 types (IF/BLK)
 * ---------- ------------ ----------------------- ----------------------- -----------------------
 * 1:4         types/4      ~32.1M / ~29.9M         ~40.0M / ~40.5M         ~47.1M / ~50.7M
 * 1:2         types/2      ~38.2M / ~35.9M         ~49.6M / ~35.9M         ~52.6M / ~58.6M
 * adaptive    350/600/1100 ~45.7M / ~46.3M         ~50.5M / ~54.1M         ~64.0M / ~60.3M
 * 1:1         types        ~51.3M / ~54.4M         ~61.2M / ~62.5M         ~75.7M / ~67.4M
 * </pre>
 *
 * <p><b>DataCarrier baseline (N independent carriers, raw Long values, pool(8)):</b>
 * <pre>
 *   500 types:  ~33.4M IF_POSSIBLE / ~32.5M BLOCKING
 *  1000 types:  ~37.6M IF_POSSIBLE / ~36.0M BLOCKING
 *  2000 types:  ~38.0M IF_POSSIBLE / ~42.1M BLOCKING
 * </pre>
 *
 * <p><b>BatchQueue vs DataCarrier (IF_POSSIBLE):</b>
 * <pre>
 *              500 types   1000 types   2000 types
 * 1:4           -4%         +6%          +24%
 * 1:2          +14%        +32%          +38%
 * adaptive     +37%        +34%          +68%
 * 1:1          +53%        +63%          +99%
 * </pre>
 *
 * <p>All runs: 32 producers, fixed(8) threads, minIdleMs=1, maxIdleMs=50, 0% drop rate.
 * 2000 metric types generated at runtime via bytecode (see {@link BenchmarkMetricTypes}).
 * Adaptive policy: {@link PartitionPolicy#adaptive()} with threshold = threadCount * 25.
 * Below threshold: 1:1 (one partition per type). Above: excess at 1:2 ratio.
 */
@Slf4j
@SuppressWarnings("all")
public class BatchQueueBenchmark {

    private static final int WARMUP_SECONDS = 2;
    private static final int MEASURE_SECONDS = 5;
    private static final int PRODUCER_THREADS = 32;
    private static final ThreadPolicy THREADS = ThreadPolicy.fixed(8);

    @AfterEach
    public void cleanup() {
        BatchQueueManager.reset();
    }

    // ---- Fixed partitions, typeHash selector ----
    // fixed(16)  50K:  ~19.0M/~12.3M  ~17.9M/~17.1M  ~15.7M/~15.2M
    // fixed(64) 500K:  ~17.0M/~18.0M  ~17.7M/~18.4M  ~17.7M/~18.0M
    // fixed(128)500K:  ~24.1M/~23.2M  ~24.9M/~26.1M  ~25.3M/~24.8M

    @Test
    public void benchmark500Types() throws Exception {
        runBenchmark("500-types", 500, 16, 50_000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark1000Types() throws Exception {
        runBenchmark("1000-types", 1000, 16, 50_000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark2000Types() throws Exception {
        runBenchmark("2000-types", 2000, 16, 50_000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark500TypesBlocking() throws Exception {
        runBenchmark("500-types-blocking", 500, 16, 50_000, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark1000TypesBlocking() throws Exception {
        runBenchmark("1000-types-blocking", 1000, 16, 50_000, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark2000TypesBlocking() throws Exception {
        runBenchmark("2000-types-blocking", 2000, 16, 50_000, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark500Types_64p() throws Exception {
        runBenchmark("500-types-64p", 500, 64, 500_000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark1000Types_64p() throws Exception {
        runBenchmark("1000-types-64p", 1000, 64, 500_000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark2000Types_64p() throws Exception {
        runBenchmark("2000-types-64p", 2000, 64, 500_000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark500TypesBlocking_64p() throws Exception {
        runBenchmark("500-types-blocking-64p", 500, 64, 500_000, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark1000TypesBlocking_64p() throws Exception {
        runBenchmark("1000-types-blocking-64p", 1000, 64, 500_000, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark2000TypesBlocking_64p() throws Exception {
        runBenchmark("2000-types-blocking-64p", 2000, 64, 500_000, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark500Types_128p() throws Exception {
        runBenchmark("500-types-128p", 500, 128, 500_000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark1000Types_128p() throws Exception {
        runBenchmark("1000-types-128p", 1000, 128, 500_000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark2000Types_128p() throws Exception {
        runBenchmark("2000-types-128p", 2000, 128, 500_000, BufferStrategy.IF_POSSIBLE);
    }

    @Test
    public void benchmark500TypesBlocking_128p() throws Exception {
        runBenchmark("500-types-blocking-128p", 500, 128, 500_000, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark1000TypesBlocking_128p() throws Exception {
        runBenchmark("1000-types-blocking-128p", 1000, 128, 500_000, BufferStrategy.BLOCKING);
    }

    @Test
    public void benchmark2000TypesBlocking_128p() throws Exception {
        runBenchmark("2000-types-blocking-128p", 2000, 128, 500_000, BufferStrategy.BLOCKING);
    }

    // ---- Type-aware partitions, typeId selector, 50K buffer each ----
    // 1:4       types/4:       ~32.1M/~29.9M  ~40.0M/~40.5M  ~47.1M/~50.7M
    // 1:2       types/2:       ~38.2M/~35.9M  ~49.6M/~35.9M  ~52.6M/~58.6M
    // adaptive  350/600/1100:  ~45.7M/~46.3M  ~50.5M/~54.1M  ~64.0M/~60.3M
    // 1:1       types:         ~51.3M/~54.4M  ~61.2M/~62.5M  ~75.7M/~67.4M

    private static final PartitionSelector<BenchmarkMetricTypes.TypedMetric> TYPE_ID_SELECTOR =
        (data, count) -> data.typeId % count;

    @Test
    public void benchmark500Types_quarter() throws Exception {
        runBenchmark("500-types-quarter", 500, 125, 50_000,
            BufferStrategy.IF_POSSIBLE, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark1000Types_quarter() throws Exception {
        runBenchmark("1000-types-quarter", 1000, 250, 50_000,
            BufferStrategy.IF_POSSIBLE, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark2000Types_quarter() throws Exception {
        runBenchmark("2000-types-quarter", 2000, 500, 50_000,
            BufferStrategy.IF_POSSIBLE, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark500TypesBlocking_quarter() throws Exception {
        runBenchmark("500-types-blocking-quarter", 500, 125, 50_000,
            BufferStrategy.BLOCKING, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark1000TypesBlocking_quarter() throws Exception {
        runBenchmark("1000-types-blocking-quarter", 1000, 250, 50_000,
            BufferStrategy.BLOCKING, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark2000TypesBlocking_quarter() throws Exception {
        runBenchmark("2000-types-blocking-quarter", 2000, 500, 50_000,
            BufferStrategy.BLOCKING, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark500Types_half() throws Exception {
        runBenchmark("500-types-half", 500, 250, 50_000,
            BufferStrategy.IF_POSSIBLE, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark1000Types_half() throws Exception {
        runBenchmark("1000-types-half", 1000, 500, 50_000,
            BufferStrategy.IF_POSSIBLE, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark2000Types_half() throws Exception {
        runBenchmark("2000-types-half", 2000, 1000, 50_000,
            BufferStrategy.IF_POSSIBLE, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark500TypesBlocking_half() throws Exception {
        runBenchmark("500-types-blocking-half", 500, 250, 50_000,
            BufferStrategy.BLOCKING, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark1000TypesBlocking_half() throws Exception {
        runBenchmark("1000-types-blocking-half", 1000, 500, 50_000,
            BufferStrategy.BLOCKING, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark2000TypesBlocking_half() throws Exception {
        runBenchmark("2000-types-blocking-half", 2000, 1000, 50_000,
            BufferStrategy.BLOCKING, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark500Types_adaptive() throws Exception {
        runAdaptiveBenchmark("500-types-adaptive", 500, BufferStrategy.IF_POSSIBLE, false);
    }

    @Test
    public void benchmark1000Types_adaptive() throws Exception {
        runAdaptiveBenchmark("1000-types-adaptive", 1000, BufferStrategy.IF_POSSIBLE, false);
    }

    @Test
    public void benchmark2000Types_adaptive() throws Exception {
        runAdaptiveBenchmark("2000-types-adaptive", 2000, BufferStrategy.IF_POSSIBLE, false);
    }

    @Test
    public void benchmark500TypesBlocking_adaptive() throws Exception {
        runAdaptiveBenchmark("500-types-blocking-adaptive", 500, BufferStrategy.BLOCKING, false);
    }

    @Test
    public void benchmark1000TypesBlocking_adaptive() throws Exception {
        runAdaptiveBenchmark("1000-types-blocking-adaptive", 1000, BufferStrategy.BLOCKING, false);
    }

    @Test
    public void benchmark2000TypesBlocking_adaptive() throws Exception {
        runAdaptiveBenchmark("2000-types-blocking-adaptive", 2000, BufferStrategy.BLOCKING, false);
    }

    @Test
    public void benchmark500Types_1to1() throws Exception {
        runBenchmark("500-types-1to1", 500, 500, 50_000,
            BufferStrategy.IF_POSSIBLE, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark1000Types_1to1() throws Exception {
        runBenchmark("1000-types-1to1", 1000, 1000, 50_000,
            BufferStrategy.IF_POSSIBLE, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark2000Types_1to1() throws Exception {
        runBenchmark("2000-types-1to1", 2000, 2000, 50_000,
            BufferStrategy.IF_POSSIBLE, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark500TypesBlocking_1to1() throws Exception {
        runBenchmark("500-types-blocking-1to1", 500, 500, 50_000,
            BufferStrategy.BLOCKING, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark1000TypesBlocking_1to1() throws Exception {
        runBenchmark("1000-types-blocking-1to1", 1000, 1000, 50_000,
            BufferStrategy.BLOCKING, TYPE_ID_SELECTOR, false);
    }

    @Test
    public void benchmark2000TypesBlocking_1to1() throws Exception {
        runBenchmark("2000-types-blocking-1to1", 2000, 2000, 50_000,
            BufferStrategy.BLOCKING, TYPE_ID_SELECTOR, false);
    }

    /**
     * Run the same adaptive benchmark 3 times each (static vs rebalanced)
     * to distinguish real overhead from run-to-run variance.
     */
    @Test
    public void benchmarkRebalanceOverhead() throws Exception {
        log.info("\n========================================");
        log.info("  Rebalance overhead: 1000 types, BLOCKING");
        log.info("========================================\n");

        final int typeCount = 1000;
        final BufferStrategy strategy = BufferStrategy.BLOCKING;
        final int runs = 3;

        final double[] staticRates = new double[runs];
        final double[] rebalRates = new double[runs];

        for (int i = 0; i < runs; i++) {
            staticRates[i] = runAdaptiveBenchmark(
                "overhead-static-" + i, typeCount, strategy, false);
            cleanup();
            rebalRates[i] = runAdaptiveBenchmark(
                "overhead-rebal-" + i, typeCount, strategy, true);
            cleanup();
        }

        // Compute stats
        double staticSum = 0;
        double rebalSum = 0;
        for (int i = 0; i < runs; i++) {
            staticSum += staticRates[i];
            rebalSum += rebalRates[i];
        }
        final double staticAvg = staticSum / runs;
        final double rebalAvg = rebalSum / runs;

        double staticVarSum = 0;
        double rebalVarSum = 0;
        for (int i = 0; i < runs; i++) {
            staticVarSum += (staticRates[i] - staticAvg) * (staticRates[i] - staticAvg);
            rebalVarSum += (rebalRates[i] - rebalAvg) * (rebalRates[i] - rebalAvg);
        }
        final double staticStddev = Math.sqrt(staticVarSum / runs);
        final double rebalStddev = Math.sqrt(rebalVarSum / runs);

        log.info("\n--- OVERHEAD ANALYSIS ---");
        log.info("  Run   Static (items/s)   Rebalanced (items/s)");
        log.info("  ---   ----------------   --------------------");
        for (int i = 0; i < runs; i++) {
            log.info("   {}    {}          {}",
                i + 1,
                String.format("%16s", String.format("%,.0f", staticRates[i])),
                String.format("%16s", String.format("%,.0f", rebalRates[i])));
        }
        log.info("  Avg   {}          {}",
            String.format("%16s", String.format("%,.0f", staticAvg)),
            String.format("%16s", String.format("%,.0f", rebalAvg)));
        log.info("  Std   {}          {}",
            String.format("%16s", String.format("%,.0f", staticStddev)),
            String.format("%16s", String.format("%,.0f", rebalStddev)));
        final double delta = staticAvg > 0 ? (rebalAvg - staticAvg) / staticAvg * 100 : 0;
        final double noiseRange = staticAvg > 0 ? staticStddev / staticAvg * 100 : 0;
        log.info("  Delta:  {}%  (noise range: +/-{}%)",
            String.format("%+.1f", delta), String.format("%.1f", noiseRange));
        log.info("");
    }

    private double runAdaptiveBenchmark(final String label, final int typeCount,
                                        final BufferStrategy strategy,
                                        final boolean rebalance) throws Exception {
        // adaptive(): threshold = threadCount * 25 = 200
        //   500 types → 350p (200 + 300/2)
        //  1000 types → 600p (200 + 800/2)
        //  2000 types → 1100p (200 + 1800/2)
        final int partitionCount = PartitionPolicy.adaptive()
            .resolve(THREADS.resolve(), typeCount);
        return runBenchmark(label, typeCount, partitionCount, 50_000,
            strategy, TYPE_ID_SELECTOR, rebalance);
    }

    private double runBenchmark(final String label, final int typeCount,
                                final int partitionCount, final int bufferSize,
                                final BufferStrategy strategy) throws Exception {
        return runBenchmark(label, typeCount, partitionCount, bufferSize, strategy, null, false);
    }

    private double runBenchmark(final String label, final int typeCount,
                                final int partitionCount, final int bufferSize,
                                final BufferStrategy strategy,
                                final PartitionSelector<BenchmarkMetricTypes.TypedMetric> selector,
                                final boolean rebalance) throws Exception {
        final AtomicLong consumed = new AtomicLong(0);
        final PartitionPolicy partitions = PartitionPolicy.fixed(partitionCount);

        final BatchQueueConfig.BatchQueueConfigBuilder<BenchmarkMetricTypes.TypedMetric> configBuilder =
            BatchQueueConfig.<BenchmarkMetricTypes.TypedMetric>builder()
                .threads(THREADS)
                .partitions(partitions)
                .bufferSize(bufferSize)
                .strategy(strategy)
                .minIdleMs(1)
                .maxIdleMs(50);
        if (selector != null) {
            configBuilder.partitionSelector(selector);
        }
        if (rebalance) {
            configBuilder.balancer(DrainBalancer.throughputWeighted(), 2000);
        }

        final BatchQueue<BenchmarkMetricTypes.TypedMetric> queue = BatchQueueManager.create(
            "bench-" + label, configBuilder.build());

        for (int t = 0; t < typeCount; t++) {
            queue.addHandler(BenchmarkMetricTypes.CLASSES[t],
                (HandlerConsumer<BenchmarkMetricTypes.TypedMetric>) data ->
                    consumed.addAndGet(data.size()));
        }

        // Warmup
        final long warmupEnd = System.currentTimeMillis() + WARMUP_SECONDS * 1000L;
        runProducers(queue, typeCount, PRODUCER_THREADS, warmupEnd);
        Thread.sleep(200);
        consumed.set(0);

        // Measure
        final long measureStart = System.currentTimeMillis();
        final long measureEnd = measureStart + MEASURE_SECONDS * 1000L;
        final long produced = runProducers(queue, typeCount, PRODUCER_THREADS, measureEnd);
        final long measureDuration = System.currentTimeMillis() - measureStart;

        Thread.sleep(500);
        final long totalConsumed = consumed.get();
        final double consumeRate = totalConsumed * 1000.0 / measureDuration;

        log.info("\n=== BatchQueue Benchmark: {} ===\n"
                + "  Types:       {}\n"
                + "  Threads:     {}\n"
                + "  Partitions:  {}\n"
                + "  BufferSize:  {}\n"
                + "  Strategy:    {}\n"
                + "  Rebalance:   {}\n"
                + "  Producers:   {}\n"
                + "  Duration:    {} ms\n"
                + "  Produced:    {}\n"
                + "  Consumed:    {}\n"
                + "  Consume rate:  {} items/sec\n"
                + "  Drop rate:     {}%\n",
            label, typeCount, THREADS, partitions, bufferSize, strategy,
            rebalance, PRODUCER_THREADS,
            measureDuration,
            String.format("%,d", produced), String.format("%,d", totalConsumed),
            String.format("%,.0f", consumeRate),
            String.format("%.2f", produced > 0
                ? (produced - totalConsumed) * 100.0 / produced : 0));

        return consumeRate;
    }

    private long runProducers(final BatchQueue<BenchmarkMetricTypes.TypedMetric> queue,
                              final int typeCount, final int producerCount,
                              final long endTimeMs) throws InterruptedException {
        final AtomicLong totalProduced = new AtomicLong(0);
        final CountDownLatch done = new CountDownLatch(producerCount);

        for (int p = 0; p < producerCount; p++) {
            final int producerIndex = p;
            final Thread thread = new Thread(() -> {
                long count = 0;
                int typeIndex = producerIndex;
                while (System.currentTimeMillis() < endTimeMs) {
                    for (int batch = 0; batch < 100; batch++) {
                        final int type = typeIndex % typeCount;
                        if (queue.produce(BenchmarkMetricTypes.FACTORIES[type].create(count))) {
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
