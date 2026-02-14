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
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.status.ServerStatusService;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
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
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * MetricsPersistentMinWorker is an extension of {@link MetricsPersistentWorker} and focuses on the
 * Minute Metrics data persistent.
 *
 * <p>All metric types (OAL and MAL) share a single {@link BatchQueue} with adaptive partitioning.
 * The {@code typeHash()} partition selector ensures same metric class lands on the same partition,
 * so each handler's {@link org.apache.skywalking.oap.server.core.analysis.data.ReadWriteSafeCache}
 * is only accessed by one drain thread.
 */
@Slf4j
public class MetricsPersistentMinWorker extends MetricsPersistentWorker {
    private static final String L2_QUEUE_NAME = "METRICS_L2_PERSISTENCE";
    private static final BatchQueueConfig<Metrics> L2_QUEUE_CONFIG =
        BatchQueueConfig.<Metrics>builder()
            .threads(ThreadPolicy.cpuCoresWithBase(1, 0.25))
            .partitions(PartitionPolicy.adaptive())
            .bufferSize(2_000)
            .strategy(BufferStrategy.BLOCKING)
            .minIdleMs(1)
            .maxIdleMs(50)
            .build();

    private static final int TOP_N = 10;
    /** slot label -> gauge instance. Keys: "total", "top1" .. "top10". */
    private static Map<String, GaugeMetrics> QUEUE_USAGE_GAUGE;

    private final BatchQueue<Metrics> l2Queue;

    /**
     * @since 9.4.0
     */
    private final ServerStatusService serverStatusService;

    // Not going to expose this as a configuration, only for testing purpose
    private final boolean isTestingTTL = "true".equalsIgnoreCase(System.getenv("TESTING_TTL"));

    MetricsPersistentMinWorker(ModuleDefineHolder moduleDefineHolder, Model model, IMetricsDAO metricsDAO,
                               AbstractWorker<Metrics> nextAlarmWorker, AbstractWorker<ExportEvent> nextExportWorker,
                               MetricsTransWorker transWorker, boolean supportUpdate,
                               long storageSessionTimeout, int metricsDataTTL, MetricStreamKind kind,
                               Class<? extends Metrics> metricsClass) {
        super(
            moduleDefineHolder, model, metricsDAO, nextAlarmWorker, nextExportWorker, transWorker, supportUpdate,
            storageSessionTimeout, metricsDataTTL, kind
        );

        this.l2Queue = BatchQueueManager.create(L2_QUEUE_NAME, L2_QUEUE_CONFIG);

        serverStatusService = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ServerStatusService.class);
        serverStatusService.registerWatcher(this);

        if (QUEUE_USAGE_GAUGE == null) {
            final MetricsCreator metricsCreator = moduleDefineHolder.find(TelemetryModule.NAME)
                                                                    .provider()
                                                                    .getService(MetricsCreator.class);
            final Map<String, GaugeMetrics> gauge = new LinkedHashMap<>();
            gauge.put("total", metricsCreator.createGauge(
                "metrics_aggregation_queue_used_percentage",
                "The percentage of queue used in L2 persistence.",
                new MetricsTag.Keys("level", "slot"),
                new MetricsTag.Values("2", "total")
            ));
            for (int i = 1; i <= TOP_N; i++) {
                gauge.put("top" + i, metricsCreator.createGauge(
                    "metrics_aggregation_queue_used_percentage",
                    "The percentage of queue used in L2 persistence.",
                    new MetricsTag.Keys("level", "slot"),
                    new MetricsTag.Values("2", "top" + i)
                ));
            }
            QUEUE_USAGE_GAUGE = gauge;
        }

        l2Queue.addHandler(metricsClass, new L2Handler());
    }

    /**
     * Accept all metrics data and push them into the queue for serial processing
     */
    @Override
    public void in(Metrics metrics) {
        final var isExpired = getMetricsDAO().isExpiredCache(getModel(), metrics, System.currentTimeMillis(), getMetricsDataTTL());
        if (isExpired && !isTestingTTL) {
            log.debug("Receiving expired metrics: {}, time: {}, ignored", metrics.id(), metrics.getTimeBucket());
            return;
        }
        getAggregationCounter().inc();
        l2Queue.produce(metrics);
    }

    private void updateQueueUsageGauges() {
        final Map<String, GaugeMetrics> gauge = QUEUE_USAGE_GAUGE;
        if (gauge == null) {
            return;
        }
        final BatchQueueStats stats = l2Queue.stats();
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

    private class L2Handler implements HandlerConsumer<Metrics> {
        @Override
        public void consume(List<Metrics> data) {
            updateQueueUsageGauges();
            onWork(data);
        }
    }
}
