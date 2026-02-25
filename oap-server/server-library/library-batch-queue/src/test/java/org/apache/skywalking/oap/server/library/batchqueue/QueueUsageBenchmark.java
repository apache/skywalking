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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Queue usage benchmark — validates {@link BatchQueueStats} metrics under realistic
 * backpressure by adding simulated processing cost in consumers.
 *
 * <p>Unlike {@link BatchQueueBenchmark} which uses no-op consumers (queue stays near 0%),
 * this benchmark adds per-item CPU work via busy-spin to simulate real handler cost
 * (metrics merging, serialization). This creates genuine backpressure so the queue
 * fills up and the usage percentage becomes meaningful.
 *
 * <p>Run with: mvn test -pl oap-server/server-library/library-batch-queue
 *           -Dtest=QueueUsageBenchmark -DfailIfNoTests=false
 */
@Slf4j
@SuppressWarnings("all")
public class QueueUsageBenchmark {

    private static final int WARMUP_SECONDS = 2;
    private static final int MEASURE_SECONDS = 5;
    private static final int PRODUCER_THREADS = 32;
    private static final ThreadPolicy THREADS = ThreadPolicy.fixed(8);
    private static final int TOP_N = 3;

    private static final PartitionSelector<BenchmarkMetricTypes.TypedMetric> TYPE_ID_SELECTOR =
        (data, count) -> data.typeId % count;

    @AfterEach
    public void cleanup() {
        BatchQueueManager.reset();
    }

    /**
     * Light consumer cost (~200ns per item). Drain threads mostly keep up,
     * but usage should rise above 0% to validate the metric under mild load.
     */
    @Test
    public void usageUnderLightLoad() throws Exception {
        runUsageBenchmark("light-load", 500, BufferStrategy.IF_POSSIBLE, 200);
    }

    /**
     * Medium consumer cost (~500ns per item). Creates moderate backpressure —
     * queue usage should reach 5-30%.
     */
    @Test
    public void usageUnderMediumLoad() throws Exception {
        runUsageBenchmark("medium-load", 500, BufferStrategy.IF_POSSIBLE, 500);
    }

    /**
     * Heavy consumer cost (~1μs per item). Creates strong backpressure —
     * queue fills significantly. IF_POSSIBLE may drop items.
     */
    @Test
    public void usageUnderHeavyLoad() throws Exception {
        runUsageBenchmark("heavy-load", 500, BufferStrategy.IF_POSSIBLE, 1000);
    }

    /**
     * Heavy load with BLOCKING strategy — producers block when queue is full,
     * no data loss. Usage should be high and sustained.
     */
    @Test
    public void usageUnderHeavyLoadBlocking() throws Exception {
        runUsageBenchmark("heavy-load-blocking", 500, BufferStrategy.BLOCKING, 1000);
    }

    /**
     * Medium load with 1000 types — more partitions, validates per-partition
     * usage tracking at scale.
     */
    @Test
    public void usageUnderMediumLoad1000Types() throws Exception {
        runUsageBenchmark("medium-load-1000t", 1000, BufferStrategy.IF_POSSIBLE, 500);
    }

    /**
     * Heavy load with BLOCKING and 1000 types.
     */
    @Test
    public void usageUnderHeavyLoadBlocking1000Types() throws Exception {
        runUsageBenchmark("heavy-load-blocking-1000t", 1000, BufferStrategy.BLOCKING, 1000);
    }

    private void runUsageBenchmark(final String label, final int typeCount,
                                   final BufferStrategy strategy,
                                   final long consumeNanosPerItem) throws Exception {
        final int partitionCount = PartitionPolicy.adaptive()
            .resolve(THREADS.resolve(), typeCount);
        final PartitionPolicy partitions = PartitionPolicy.fixed(partitionCount);
        final AtomicLong consumed = new AtomicLong(0);

        final BatchQueueConfig.BatchQueueConfigBuilder<BenchmarkMetricTypes.TypedMetric> configBuilder =
            BatchQueueConfig.<BenchmarkMetricTypes.TypedMetric>builder()
                .threads(THREADS)
                .partitions(partitions)
                .bufferSize(50_000)
                .strategy(strategy)
                .partitionSelector(TYPE_ID_SELECTOR)
                .minIdleMs(1)
                .maxIdleMs(50);

        final BatchQueue<BenchmarkMetricTypes.TypedMetric> queue = BatchQueueManager.create(
            "usage-" + label, configBuilder.build());

        for (int t = 0; t < typeCount; t++) {
            queue.addHandler(BenchmarkMetricTypes.CLASSES[t],
                (HandlerConsumer<BenchmarkMetricTypes.TypedMetric>) data -> {
                    for (int i = 0; i < data.size(); i++) {
                        busySpin(consumeNanosPerItem);
                    }
                    consumed.addAndGet(data.size());
                });
        }

        // Warmup — wait until every produced item is consumed before measurement.
        // Comparing consumed count against produced count is more precise than polling
        // queue size, because queue.stats().totalUsed()==0 can be true while a drain
        // thread is still inside the consumer callback (items dequeued but not yet counted).
        final long warmupEnd = System.currentTimeMillis() + WARMUP_SECONDS * 1000L;
        final long warmupProduced = runProducers(queue, typeCount, PRODUCER_THREADS, warmupEnd);
        final CountDownLatch warmupDrained = new CountDownLatch(1);
        final Thread warmupWaiter = new Thread(() -> {
            while (consumed.get() < warmupProduced) {
                try {
                    Thread.sleep(50);
                } catch (final InterruptedException e) {
                    break;
                }
            }
            warmupDrained.countDown();
        });
        warmupWaiter.setDaemon(true);
        warmupWaiter.start();
        warmupDrained.await(30, TimeUnit.SECONDS);
        consumed.set(0);

        // Measure — sample queue usage every 500ms
        final int sampleInterval = 500;
        final int maxSamples = MEASURE_SECONDS * 1000 / sampleInterval;
        final double[] totalUsageSamples = new double[maxSamples];
        final double[] topPartitionSamples = new double[maxSamples];
        final int[][] topNIndexSamples = new int[maxSamples][TOP_N];
        final double[][] topNUsageSamples = new double[maxSamples][TOP_N];
        final AtomicLong samplesTaken = new AtomicLong(0);

        final long measureStart = System.currentTimeMillis();
        final long measureEnd = measureStart + MEASURE_SECONDS * 1000L;

        final Thread sampler = new Thread(() -> {
            for (int s = 0; s < maxSamples; s++) {
                try {
                    Thread.sleep(sampleInterval);
                } catch (final InterruptedException e) {
                    break;
                }
                final BatchQueueStats stats = queue.stats();
                totalUsageSamples[s] = stats.totalUsedPercentage();
                final List<BatchQueueStats.PartitionUsage> top = stats.topN(TOP_N);
                topPartitionSamples[s] = top.isEmpty() ? 0 : top.get(0).getUsedPercentage();
                for (int i = 0; i < TOP_N && i < top.size(); i++) {
                    topNIndexSamples[s][i] = top.get(i).getPartitionIndex();
                    topNUsageSamples[s][i] = top.get(i).getUsedPercentage();
                }
                samplesTaken.incrementAndGet();
            }
        });
        sampler.setName("UsageSampler");
        sampler.setDaemon(true);
        sampler.start();

        final long produced = runProducers(queue, typeCount, PRODUCER_THREADS, measureEnd);
        final long measureDuration = System.currentTimeMillis() - measureStart;

        sampler.join(2000);

        // Let drain threads catch up for final snapshot
        Thread.sleep(1000);
        final long totalConsumed = consumed.get();
        final double consumeRate = totalConsumed * 1000.0 / measureDuration;

        // Compute usage stats
        final int samples = (int) samplesTaken.get();
        double usageSum = 0;
        double usageMin = Double.MAX_VALUE;
        double usageMax = 0;
        double topSum = 0;
        double topMax = 0;
        for (int s = 0; s < samples; s++) {
            usageSum += totalUsageSamples[s];
            usageMin = Math.min(usageMin, totalUsageSamples[s]);
            usageMax = Math.max(usageMax, totalUsageSamples[s]);
            topSum += topPartitionSamples[s];
            topMax = Math.max(topMax, topPartitionSamples[s]);
        }
        if (samples == 0) {
            usageMin = 0;
        }
        final double usageAvg = samples > 0 ? usageSum / samples : 0;
        final double topAvg = samples > 0 ? topSum / samples : 0;

        final BatchQueueStats finalStats = queue.stats();

        // Build the output
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("\n=== Queue Usage Benchmark: %s ===\n", label));
        sb.append(String.format("  Types:           %d\n", typeCount));
        sb.append(String.format("  Threads:         %s\n", THREADS));
        sb.append(String.format("  Partitions:      %s\n", partitions));
        sb.append(String.format("  BufferSize:      %d\n", 50_000));
        sb.append(String.format("  Strategy:        %s\n", strategy));
        sb.append(String.format("  Consumer cost:   %d ns/item\n", consumeNanosPerItem));
        sb.append(String.format("  Producers:       %d\n", PRODUCER_THREADS));
        sb.append(String.format("  Duration:        %d ms\n", measureDuration));
        sb.append(String.format("  Produced:        %s\n", String.format("%,d", produced)));
        sb.append(String.format("  Consumed:        %s\n", String.format("%,d", totalConsumed)));
        sb.append(String.format("  Consume rate:    %s items/sec\n", String.format("%,.0f", consumeRate)));
        sb.append(String.format("  Drop rate:       %s%%\n", String.format("%.2f",
            produced > 0 ? (produced - totalConsumed) * 100.0 / produced : 0)));
        sb.append("\n  --- Queue Usage (sampled every 500ms) ---\n");
        sb.append(String.format("  Samples:         %d\n", samples));
        sb.append(String.format("  Total usage:     min=%.1f%%, avg=%.1f%%, max=%.1f%%\n",
            usageMin, usageAvg, usageMax));
        sb.append(String.format("  Top partition:   avg=%.1f%%, max=%.1f%%\n", topAvg, topMax));
        sb.append(String.format("  Final snapshot:  totalUsed=%s/%s (%.1f%%)\n",
            String.format("%,d", finalStats.totalUsed()),
            String.format("%,d", finalStats.totalCapacity()),
            finalStats.totalUsedPercentage()));

        // Per-sample timeline
        sb.append("\n  --- Usage Timeline ---\n");
        sb.append("  Sample   Total%   Top1 partition (index: usage%)\n");
        sb.append("  ------   ------   ------------------------------\n");
        for (int s = 0; s < samples; s++) {
            sb.append(String.format("  %4dms   %5.1f%%   ", (s + 1) * sampleInterval, totalUsageSamples[s]));
            for (int i = 0; i < TOP_N; i++) {
                if (topNUsageSamples[s][i] > 0) {
                    sb.append(String.format("p%d:%.1f%%  ", topNIndexSamples[s][i], topNUsageSamples[s][i]));
                }
            }
            sb.append("\n");
        }

        log.info(sb.toString());
    }

    /**
     * Busy-spin for approximately the given nanoseconds.
     * More accurate than Thread.sleep for sub-microsecond delays.
     */
    private static void busySpin(final long nanos) {
        final long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            // spin
        }
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

        done.await(MEASURE_SECONDS + WARMUP_SECONDS + 10, TimeUnit.SECONDS);
        return totalProduced.get();
    }
}
