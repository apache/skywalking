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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.data.MergableBufferedData;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueue;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueConfig;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueManager;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueStats;
import org.apache.skywalking.oap.server.library.batchqueue.BufferStrategy;
import org.apache.skywalking.oap.server.library.batchqueue.HandlerConsumer;
import org.apache.skywalking.oap.server.library.batchqueue.PartitionPolicy;
import org.apache.skywalking.oap.server.library.batchqueue.ThreadPolicy;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * MetricsAggregateWorker provides an in-memory metrics merging capability. This aggregation is called L1 aggregation,
 * it merges the data just after the receiver analysis. The metrics belonging to the same entity, metrics type and time
 * bucket, the L1 aggregation will merge them into one metrics object to reduce the unnecessary memory and network
 * payload.
 *
 * <p>All metric types (OAL and MAL) share a single {@link BatchQueue} with adaptive partitioning.
 * The {@code typeHash()} partition selector ensures same metric class lands on the same partition,
 * so each handler's {@link MergableBufferedData} is only accessed by one drain thread.
 */
@Slf4j
public class MetricsAggregateWorker extends AbstractWorker<Metrics> {
    private static final String L1_QUEUE_NAME = "METRICS_L1_AGGREGATION";
    private static final BatchQueueConfig<Metrics> L1_QUEUE_CONFIG =
        BatchQueueConfig.<Metrics>builder()
            .threads(ThreadPolicy.cpuCores(1.0))
            .partitions(PartitionPolicy.adaptive())
            .bufferSize(20_000)
            .strategy(BufferStrategy.IF_POSSIBLE)
            .minIdleMs(1)
            .maxIdleMs(50)
            .build();

    private static final int TOP_N = 10;
    /** slot label -> gauge instance. Keys: "total", "top1" .. "top10". */
    private static Map<String, GaugeMetrics> QUEUE_USAGE_GAUGE;

    private final BatchQueue<Metrics> l1Queue;
    private final long l1FlushPeriod;
    private final AbstractWorker<Metrics> nextWorker;
    private final MergableBufferedData<Metrics> mergeDataCache;
    private final CounterMetrics abandonCounter;
    private final CounterMetrics aggregationCounter;
    private long lastSendTime = 0;

    MetricsAggregateWorker(final ModuleDefineHolder moduleDefineHolder,
                           final AbstractWorker<Metrics> nextWorker,
                           final String modelName,
                           final long l1FlushPeriod,
                           final Class<? extends Metrics> metricsClass) {
        super(moduleDefineHolder);
        this.nextWorker = nextWorker;
        this.mergeDataCache = new MergableBufferedData<>();
        this.l1FlushPeriod = l1FlushPeriod;
        this.l1Queue = BatchQueueManager.create(L1_QUEUE_NAME, L1_QUEUE_CONFIG);

        final MetricsCreator metricsCreator = moduleDefineHolder.find(TelemetryModule.NAME)
                                                                .provider()
                                                                .getService(MetricsCreator.class);
        abandonCounter = metricsCreator.createCounter(
            "metrics_aggregator_abandon", "The abandon number of rows received in aggregation.",
            new MetricsTag.Keys("metricName", "level", "dimensionality"),
            new MetricsTag.Values(modelName, "1", "minute")
        );
        aggregationCounter = metricsCreator.createCounter(
            "metrics_aggregation", "The number of rows in aggregation.",
            new MetricsTag.Keys("metricName", "level", "dimensionality"),
            new MetricsTag.Values(modelName, "1", "minute")
        );

        if (QUEUE_USAGE_GAUGE == null) {
            final Map<String, GaugeMetrics> gauge = new LinkedHashMap<>();
            gauge.put("total", metricsCreator.createGauge(
                "metrics_aggregation_queue_used_percentage",
                "The percentage of queue used in L1 aggregation.",
                new MetricsTag.Keys("level", "slot"),
                new MetricsTag.Values("1", "total")
            ));
            for (int i = 1; i <= TOP_N; i++) {
                gauge.put("top" + i, metricsCreator.createGauge(
                    "metrics_aggregation_queue_used_percentage",
                    "The percentage of queue used in L1 aggregation.",
                    new MetricsTag.Keys("level", "slot"),
                    new MetricsTag.Values("1", "top" + i)
                ));
            }
            QUEUE_USAGE_GAUGE = gauge;
        }

        l1Queue.addHandler(metricsClass, new L1Handler());
    }

    @Override
    public void in(final Metrics metrics) {
        if (!l1Queue.produce(metrics)) {
            abandonCounter.inc();
        }
    }

    private void onWork(final List<Metrics> metricsList) {
        for (final Metrics metrics : metricsList) {
            aggregationCounter.inc();
            mergeDataCache.accept(metrics);
        }
        updateQueueUsageGauges();
        flush();
    }

    private void updateQueueUsageGauges() {
        final Map<String, GaugeMetrics> gauge = QUEUE_USAGE_GAUGE;
        if (gauge == null) {
            return;
        }
        final BatchQueueStats stats = l1Queue.stats();
        gauge.get("total").setValue(stats.totalUsedPercentage());
        final List<BatchQueueStats.PartitionUsage> topPartitions = stats.topN(TOP_N);
        for (int i = 1; i <= TOP_N; i++) {
            if (i <= topPartitions.size()) {
                gauge.get("top" + i).setValue(topPartitions.get(i - 1).getUsedPercentage());
            } else {
                gauge.get("top" + i).setValue(0);
            }
        }
    }

    private void flush() {
        final long currentTime = System.currentTimeMillis();
        if (currentTime - lastSendTime > l1FlushPeriod) {
            mergeDataCache.read().forEach(nextWorker::in);
            lastSendTime = currentTime;
        }
    }

    private class L1Handler implements HandlerConsumer<Metrics> {
        @Override
        public void consume(final List<Metrics> data) {
            onWork(data);
        }

        @Override
        public void onIdle() {
            flush();
        }
    }
}
