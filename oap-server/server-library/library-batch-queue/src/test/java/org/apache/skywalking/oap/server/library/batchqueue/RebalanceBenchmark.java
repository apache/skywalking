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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Benchmark comparing throughput with and without partition rebalancing
 * under skewed load simulating OAP L2 persistence.
 *
 * <h3>Scenario: L2 entity-count-driven imbalance</h3>
 * After L1 merge, each metric type produces one item per unique entity per minute.
 * Endpoint-scoped metrics see many more entities than service-scoped metrics:
 * <pre>
 *   endpoint-level OAL:  ~24 entities (6 endpoints × 4 services)
 *   instance-level OAL:  ~8 entities  (2 instances × 4 services)
 *   service-level OAL:   ~4 entities  (4 services)
 *   MAL / cold:          ~1 entity    (periodic scrape)
 * </pre>
 *
 * With {@code typeHash()} partition selection and round-robin thread assignment,
 * some threads get more endpoint-scoped types by chance, creating load imbalance.
 * The throughput-weighted rebalancer fixes this by reassigning partitions based
 * on observed throughput.
 *
 * <h3>What this benchmark measures</h3>
 * <ol>
 *   <li><b>Static vs rebalanced throughput:</b> total consumed items/sec with
 *       BLOCKING strategy and simulated consumer work (~500ns/item). With imbalance,
 *       producers block on full partitions of the overloaded thread; rebalancing
 *       equalizes thread load and reduces blocking.</li>
 *   <li><b>Rebalance stability:</b> once throughput distribution is stable, the
 *       rebalancer should converge to a fixed assignment. Measured by sampling
 *       per-thread load ratio over multiple intervals.</li>
 * </ol>
 *
 * <h3>Results (4 drain threads, 16 producers, 100 types, 500 LCG iters/item)</h3>
 * <pre>
 *                     Static          Rebalanced
 *   Throughput:    7,211,794         8,729,310  items/sec
 *   Load ratio:       1.30x             1.04x  (max/min thread)
 *   Improvement:                       +21.0%
 * </pre>
 *
 * <h3>Stability (20 sec, sampled every 2 sec after initial rebalance)</h3>
 * <pre>
 *   Interval    Throughput      Ratio
 *    0- 2s     8,915,955       1.00x
 *    2- 4s     8,956,595       1.01x
 *    4- 6s     8,934,778       1.00x
 *    6- 8s     8,838,461       1.01x
 *    8-10s     8,887,092       1.00x
 *   10-12s     8,844,614       1.00x
 *   12-14s     8,877,651       1.00x
 *   14-16s     8,851,595       1.01x
 *   16-18s     8,639,045       1.01x
 *   18-20s     8,708,210       1.01x
 *   Stable: YES (avg ratio 1.01x)
 * </pre>
 *
 * <p>Run with: {@code mvn test -pl oap-server/server-library/library-batch-queue
 *              -Dtest=RebalanceBenchmark -DfailIfNoTests=false}
 */
@Slf4j
@SuppressWarnings("all")
public class RebalanceBenchmark {

    // --- Configuration ---

    /** Number of metric types. */
    private static final int TYPE_COUNT = 100;

    /** Throughput tiers (simulate entity count variance after L1 merge). */
    private static final int ENDPOINT_TYPES = 20;   // types 0-19,  weight 24
    private static final int INSTANCE_TYPES = 30;    // types 20-49, weight 8
    private static final int SERVICE_TYPES  = 20;    // types 50-69, weight 4
    private static final int COLD_TYPES     = 30;    // types 70-99, weight 1

    private static final int ENDPOINT_WEIGHT = 24;
    private static final int INSTANCE_WEIGHT = 8;
    private static final int SERVICE_WEIGHT  = 4;
    private static final int COLD_WEIGHT     = 1;

    /** Consumer busy-spin iterations per item. Tuned so drain threads are the bottleneck. */
    private static final int WORK_ITERATIONS = 500;

    private static final int DRAIN_THREADS = 4;
    private static final int PRODUCER_THREADS = 16;
    private static final int BUFFER_SIZE = 2000;
    private static final int WARMUP_SECONDS = 3;
    private static final int MEASURE_SECONDS = 10;

    /** Rebalance interval for rebalanced scenario. */
    private static final long REBALANCE_INTERVAL_MS = 2000;

    /** Weighted type selection array. Producers cycle through this to create skewed throughput. */
    private static final int[] WEIGHTED_TYPES = buildWeightedTypes();

    /** Volatile sink to prevent JIT from optimizing away consumer work. */
    private volatile long sink;

    @AfterEach
    public void cleanup() {
        BatchQueueManager.reset();
    }

    /**
     * Compare throughput: static round-robin assignment vs rebalanced.
     */
    @Test
    public void benchmarkStaticVsRebalanced() throws Exception {
        // Print workload description
        final int totalWeight = ENDPOINT_TYPES * ENDPOINT_WEIGHT
            + INSTANCE_TYPES * INSTANCE_WEIGHT
            + SERVICE_TYPES * SERVICE_WEIGHT
            + COLD_TYPES * COLD_WEIGHT;
        log.info("\n========================================");
        log.info("  RebalanceBenchmark: Static vs Rebalanced");
        log.info("========================================");
        log.info("Types: {} ({} endpoint@{}x, {} instance@{}x, {} service@{}x, {} cold@{}x)",
            TYPE_COUNT,
            ENDPOINT_TYPES, ENDPOINT_WEIGHT,
            INSTANCE_TYPES, INSTANCE_WEIGHT,
            SERVICE_TYPES, SERVICE_WEIGHT,
            COLD_TYPES, COLD_WEIGHT);
        log.info("Total weighted units per cycle: {}", totalWeight);
        log.info("Drain threads: {}, Producers: {}, Buffer: {}, Work: {} iters/item",
            DRAIN_THREADS, PRODUCER_THREADS, BUFFER_SIZE, WORK_ITERATIONS);
        log.info("");

        // Show initial type-to-thread distribution (based on typeHash)
        printTypeDistribution();

        // Run static scenario
        log.info("--- Running STATIC scenario ({} sec) ---", MEASURE_SECONDS);
        final ScenarioResult staticResult = runScenario("static", false);
        cleanup();

        // Run rebalanced scenario
        log.info("--- Running REBALANCED scenario ({} sec) ---", MEASURE_SECONDS);
        final ScenarioResult rebalancedResult = runScenario("rebalanced", true);

        // Comparison
        log.info("\n--- COMPARISON ---");
        log.info("                    Static          Rebalanced");
        log.info("  Throughput:   {}      {}  items/sec",
            String.format("%12s", String.format("%,.0f", staticResult.throughputPerSec)),
            String.format("%12s", String.format("%,.0f", rebalancedResult.throughputPerSec)));
        log.info("  Load ratio:      {}x          {}x  (max/min thread)",
            String.format("%.2f", staticResult.loadRatio),
            String.format("%.2f", rebalancedResult.loadRatio));
        if (staticResult.throughputPerSec > 0) {
            final double improvement = (rebalancedResult.throughputPerSec - staticResult.throughputPerSec)
                / staticResult.throughputPerSec * 100;
            log.info("  Improvement:  {}%", String.format("%+.1f", improvement));
        }
        log.info("");
    }

    /**
     * Verify rebalance stability: once the throughput distribution is stable,
     * the per-thread load ratio should converge and stay low across intervals.
     */
    @Test
    public void benchmarkRebalanceStability() throws Exception {
        log.info("\n========================================");
        log.info("  RebalanceBenchmark: Stability Check");
        log.info("========================================");

        final ConcurrentHashMap<Long, AtomicLong> perThreadConsumed = new ConcurrentHashMap<>();
        final AtomicLong totalConsumed = new AtomicLong(0);

        final BatchQueue<BenchmarkMetricTypes.TypedMetric> queue = createQueue("stability", true);
        registerHandlers(queue, perThreadConsumed, totalConsumed);

        // Warmup
        log.info("Warming up ({} sec)...", WARMUP_SECONDS);
        final long warmupEnd = System.currentTimeMillis() + WARMUP_SECONDS * 1000L;
        runProducers(queue, warmupEnd);
        Thread.sleep(500);
        resetCounters(perThreadConsumed, totalConsumed);

        // Measure stability over multiple intervals
        final int totalSeconds = 20;
        final int sampleIntervalSec = 2;
        final int samples = totalSeconds / sampleIntervalSec;

        log.info("Measuring stability ({} sec, sample every {} sec)...\n", totalSeconds, sampleIntervalSec);
        log.info("  Interval    Throughput      Ratio    Per-thread consumed");
        log.info("  --------    ----------      -----    -------------------");

        final long measureEnd = System.currentTimeMillis() + totalSeconds * 1000L;

        // Start producers in background
        final CountDownLatch producersDone = new CountDownLatch(1);
        final Thread producerManager = new Thread(() -> {
            try {
                runProducers(queue, measureEnd);
            } catch (final Exception e) {
                log.error("Producer error", e);
            } finally {
                producersDone.countDown();
            }
        });
        producerManager.setDaemon(true);
        producerManager.start();

        // Sample at each interval
        final List<Double> ratios = new ArrayList<>();
        for (int s = 0; s < samples; s++) {
            // Snapshot current values
            final Map<Long, Long> before = snapshotCounters(perThreadConsumed);
            final long consumedBefore = totalConsumed.get();

            Thread.sleep(sampleIntervalSec * 1000L);

            // Compute deltas
            final Map<Long, Long> after = snapshotCounters(perThreadConsumed);
            final long consumedAfter = totalConsumed.get();
            final long intervalConsumed = consumedAfter - consumedBefore;
            final double throughput = intervalConsumed * 1000.0 / (sampleIntervalSec * 1000);

            // Per-thread deltas
            final List<Long> threadDeltas = new ArrayList<>();
            for (final Map.Entry<Long, Long> entry : after.entrySet()) {
                final long delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0L);
                threadDeltas.add(delta);
            }

            long maxDelta = 0;
            long minDelta = Long.MAX_VALUE;
            for (final long d : threadDeltas) {
                if (d > maxDelta) {
                    maxDelta = d;
                }
                if (d < minDelta) {
                    minDelta = d;
                }
            }
            final double ratio = minDelta > 0 ? (double) maxDelta / minDelta : 0;
            ratios.add(ratio);

            log.info("  {}-{}s    {}    {}x    {}",
                String.format("%3d", s * sampleIntervalSec),
                String.format("%3d", (s + 1) * sampleIntervalSec),
                String.format("%10s", String.format("%,.0f", throughput)),
                String.format("%5.2f", ratio),
                formatThreadDeltas(threadDeltas));
        }

        producersDone.await(5, TimeUnit.SECONDS);

        // Summary
        log.info("");
        final double firstRatio = ratios.get(0);
        final double avgLaterRatio = ratios.subList(Math.min(2, ratios.size()), ratios.size())
            .stream().mapToDouble(d -> d).average().orElse(0);
        log.info("  First interval ratio:     {}x", String.format("%.2f", firstRatio));
        log.info("  Avg later ratio (4s+):    {}x", String.format("%.2f", avgLaterRatio));
        log.info("  Stable: {}", avgLaterRatio < 1.3 ? "YES" : "NO (ratio > 1.3)");
        log.info("");
    }

    // ========== Helpers ==========

    private BatchQueue<BenchmarkMetricTypes.TypedMetric> createQueue(
            final String label, final boolean withBalancer) {
        final BatchQueueConfig.BatchQueueConfigBuilder<BenchmarkMetricTypes.TypedMetric> builder =
            BatchQueueConfig.<BenchmarkMetricTypes.TypedMetric>builder()
                .threads(ThreadPolicy.fixed(DRAIN_THREADS))
                .partitions(PartitionPolicy.adaptive())
                .bufferSize(BUFFER_SIZE)
                .strategy(BufferStrategy.BLOCKING)
                .minIdleMs(1)
                .maxIdleMs(50);
        if (withBalancer) {
            builder.balancer(DrainBalancer.throughputWeighted(), REBALANCE_INTERVAL_MS);
        }
        return BatchQueueManager.create("rebal-" + label, builder.build());
    }

    private void registerHandlers(
            final BatchQueue<BenchmarkMetricTypes.TypedMetric> queue,
            final ConcurrentHashMap<Long, AtomicLong> perThreadConsumed,
            final AtomicLong totalConsumed) {
        for (int t = 0; t < TYPE_COUNT; t++) {
            queue.addHandler(BenchmarkMetricTypes.CLASSES[t],
                (HandlerConsumer<BenchmarkMetricTypes.TypedMetric>) data -> {
                    // Track per-thread consumed
                    final long tid = Thread.currentThread().getId();
                    perThreadConsumed.computeIfAbsent(tid, k -> new AtomicLong())
                        .addAndGet(data.size());
                    totalConsumed.addAndGet(data.size());

                    // Simulate consumer work per item.
                    // Uses item data to prevent JIT from collapsing the loop.
                    long s = tid;
                    for (int i = 0; i < data.size(); i++) {
                        long v = data.get(i).value;
                        for (int w = 0; w < WORK_ITERATIONS; w++) {
                            v = v * 6364136223846793005L + s;
                        }
                        s += v;
                    }
                    sink = s;
                });
        }
    }

    private ScenarioResult runScenario(final String label, final boolean rebalance) throws Exception {
        final ConcurrentHashMap<Long, AtomicLong> perThreadConsumed = new ConcurrentHashMap<>();
        final AtomicLong totalConsumed = new AtomicLong(0);

        final BatchQueue<BenchmarkMetricTypes.TypedMetric> queue = createQueue(label, rebalance);
        registerHandlers(queue, perThreadConsumed, totalConsumed);

        // Warmup
        final long warmupEnd = System.currentTimeMillis() + WARMUP_SECONDS * 1000L;
        runProducers(queue, warmupEnd);
        Thread.sleep(500);
        resetCounters(perThreadConsumed, totalConsumed);

        // Measure
        final long measureStart = System.currentTimeMillis();
        final long measureEnd = measureStart + MEASURE_SECONDS * 1000L;
        runProducers(queue, measureEnd);
        final long elapsed = System.currentTimeMillis() - measureStart;
        Thread.sleep(500);

        final long total = totalConsumed.get();
        final double throughput = total * 1000.0 / elapsed;

        // Per-thread stats
        long maxThread = 0;
        long minThread = Long.MAX_VALUE;
        final StringBuilder threadDetail = new StringBuilder();
        for (final Map.Entry<Long, AtomicLong> entry : perThreadConsumed.entrySet()) {
            final long val = entry.getValue().get();
            if (val > maxThread) {
                maxThread = val;
            }
            if (val < minThread) {
                minThread = val;
            }
            if (threadDetail.length() > 0) {
                threadDetail.append(", ");
            }
            threadDetail.append(String.format("%,d", val));
        }
        final double ratio = minThread > 0 ? (double) maxThread / minThread : 0;

        log.info("  {} result: {} items/sec, ratio={}x, threads=[{}]",
            label,
            String.format("%,.0f", throughput),
            String.format("%.2f", ratio),
            threadDetail);

        return new ScenarioResult(throughput, ratio);
    }

    /**
     * Produce items with weighted type distribution until endTimeMs.
     * Each producer cycles through {@link #WEIGHTED_TYPES}, producing one item per slot.
     */
    private void runProducers(
            final BatchQueue<BenchmarkMetricTypes.TypedMetric> queue,
            final long endTimeMs) throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(PRODUCER_THREADS);

        for (int p = 0; p < PRODUCER_THREADS; p++) {
            final int producerIndex = p;
            final Thread thread = new Thread(() -> {
                long count = 0;
                // Start at different offset per producer to spread contention
                int slotIndex = producerIndex * (WEIGHTED_TYPES.length / PRODUCER_THREADS);
                while (System.currentTimeMillis() < endTimeMs) {
                    for (int batch = 0; batch < 50; batch++) {
                        final int typeId = WEIGHTED_TYPES[slotIndex % WEIGHTED_TYPES.length];
                        queue.produce(BenchmarkMetricTypes.FACTORIES[typeId].create(count));
                        count++;
                        slotIndex++;
                    }
                }
                done.countDown();
            });
            thread.setName("RebalProducer-" + producerIndex);
            thread.setDaemon(true);
            thread.start();
        }

        done.await(MEASURE_SECONDS + WARMUP_SECONDS + 30, TimeUnit.SECONDS);
    }

    /**
     * Show the initial type-to-thread mapping based on typeHash and round-robin.
     * This reveals the static imbalance before rebalancing.
     */
    private void printTypeDistribution() {
        // Resolve partition count using adaptive policy
        final int partitionCount = PartitionPolicy.adaptive().resolve(DRAIN_THREADS, TYPE_COUNT);
        final int[][] assignments = buildAssignments(DRAIN_THREADS, partitionCount);

        // Map partition -> thread
        final int[] partitionToThread = new int[partitionCount];
        for (int t = 0; t < assignments.length; t++) {
            for (final int p : assignments[t]) {
                partitionToThread[p] = t;
            }
        }

        // Compute per-thread weighted load
        final long[] threadLoad = new long[DRAIN_THREADS];
        final int[] threadTypeCount = new int[DRAIN_THREADS];
        final int[] threadEndpointTypes = new int[DRAIN_THREADS];
        for (int typeId = 0; typeId < TYPE_COUNT; typeId++) {
            final int hash = BenchmarkMetricTypes.CLASSES[typeId].hashCode() & 0x7FFFFFFF;
            final int partition = hash % partitionCount;
            final int thread = partitionToThread[partition];
            threadLoad[thread] += weightOf(typeId);
            threadTypeCount[thread]++;
            if (typeId < ENDPOINT_TYPES) {
                threadEndpointTypes[thread]++;
            }
        }

        final long maxLoad = Arrays.stream(threadLoad).max().orElse(0);
        final long minLoad = Arrays.stream(threadLoad).min().orElse(0);
        final double ratio = minLoad > 0 ? (double) maxLoad / minLoad : 0;

        log.info("Initial type distribution (typeHash + round-robin):");
        for (int t = 0; t < DRAIN_THREADS; t++) {
            log.info("  Thread {}: {} types ({} endpoint), weighted load = {}",
                t, threadTypeCount[t], threadEndpointTypes[t], threadLoad[t]);
        }
        log.info("  Static load ratio: {}x (max={}, min={})\n",
            String.format("%.2f", ratio), maxLoad, minLoad);
    }

    private static int weightOf(final int typeId) {
        if (typeId < ENDPOINT_TYPES) {
            return ENDPOINT_WEIGHT;
        }
        if (typeId < ENDPOINT_TYPES + INSTANCE_TYPES) {
            return INSTANCE_WEIGHT;
        }
        if (typeId < ENDPOINT_TYPES + INSTANCE_TYPES + SERVICE_TYPES) {
            return SERVICE_WEIGHT;
        }
        return COLD_WEIGHT;
    }

    /**
     * Build a weighted type selection array. Producers cycle through this to
     * produce items at rates proportional to each type's entity count.
     */
    private static int[] buildWeightedTypes() {
        final List<Integer> slots = new ArrayList<>();
        for (int t = 0; t < TYPE_COUNT; t++) {
            final int weight = weightOf(t);
            for (int w = 0; w < weight; w++) {
                slots.add(t);
            }
        }
        // Shuffle to avoid sequential clustering of hot types in the produce cycle
        java.util.Collections.shuffle(slots, new java.util.Random(42));
        return slots.stream().mapToInt(i -> i).toArray();
    }

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

    private static Map<Long, Long> snapshotCounters(
            final ConcurrentHashMap<Long, AtomicLong> perThread) {
        final Map<Long, Long> snapshot = new TreeMap<>();
        for (final Map.Entry<Long, AtomicLong> entry : perThread.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }

    private static void resetCounters(
            final ConcurrentHashMap<Long, AtomicLong> perThread, final AtomicLong total) {
        for (final AtomicLong v : perThread.values()) {
            v.set(0);
        }
        total.set(0);
    }

    private static String formatThreadDeltas(final List<Long> deltas) {
        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < deltas.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("%,d", deltas.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static class ScenarioResult {
        final double throughputPerSec;
        final double loadRatio;

        ScenarioResult(final double throughputPerSec, final double loadRatio) {
            this.throughputPerSec = throughputPerSec;
            this.loadRatio = loadRatio;
        }
    }
}
