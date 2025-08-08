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

import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.status.ServerStatusService;
import org.apache.skywalking.oap.server.core.status.ServerStatusWatcher;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.datacarrier.DataCarrier;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.QueueBuffer;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.BulkConsumePool;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.ConsumerPoolFactory;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * MetricsPersistentWorkerMin is an extension of {@link MetricsPersistentWorker} and focuses on the Minute Metrics data persistent.
 */
@Slf4j
public class MetricsPersistentWorkerMin extends MetricsPersistentWorker implements ServerStatusWatcher {
    private final DataCarrier<Metrics> dataCarrier;

    /**
     * The percentage of queue used in aggregation
     */
    private final GaugeMetrics queuePercentageGauge;

    /**
     * @since 9.4.0
     */
    private final ServerStatusService serverStatusService;

    // Not going to expose this as a configuration, only for testing purpose
    private final boolean isTestingTTL = "true".equalsIgnoreCase(System.getenv("TESTING_TTL"));

    MetricsPersistentWorkerMin(ModuleDefineHolder moduleDefineHolder, Model model, IMetricsDAO metricsDAO,
                               AbstractWorker<Metrics> nextAlarmWorker, AbstractWorker<ExportEvent> nextExportWorker,
                               MetricsTransWorker transWorker, boolean supportUpdate,
                               long storageSessionTimeout, int metricsDataTTL, MetricStreamKind kind) {
        super(
            moduleDefineHolder, model, metricsDAO, nextAlarmWorker, nextExportWorker, transWorker, supportUpdate,
            storageSessionTimeout, metricsDataTTL, kind
        );

        String name = "METRICS_L2_AGGREGATION";
        int size = BulkConsumePool.Creator.recommendMaxSize() / 8;
        if (size == 0) {
            size = 1;
        }
        BulkConsumePool.Creator creator = new BulkConsumePool.Creator(name, size, 200);
        try {
            ConsumerPoolFactory.INSTANCE.createIfAbsent(name, creator);
        } catch (Exception e) {
            throw new UnexpectedException(e.getMessage(), e);
        }

        int bufferSize = 2000;
        if (MetricStreamKind.MAL == kind) {
            // In MAL meter streaming, the load of data flow is much less as they are statistics already,
            // but in OAL sources, they are raw data.
            // Set the buffer(size of queue) as 1/2 to reduce unnecessary resource costs.
            bufferSize = 1000;
        }
        this.dataCarrier = new DataCarrier<>("MetricsPersistentWorker." + model.getName(), name, 1, bufferSize);
        this.dataCarrier.consume(ConsumerPoolFactory.INSTANCE.get(name), new PersistentConsumer());

        MetricsCreator metricsCreator = moduleDefineHolder.find(TelemetryModule.NAME)
                                                          .provider()
                                                          .getService(MetricsCreator.class);
        queuePercentageGauge = metricsCreator.createGauge(
            "metrics_aggregation_queue_used_percentage", "The percentage of queue used in aggregation.",
            new MetricsTag.Keys("metricName", "level", "kind"),
            new MetricsTag.Values(model.getName(), "2", kind.name())
        );
        serverStatusService = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ServerStatusService.class);
        serverStatusService.registerWatcher(this);
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
        dataCarrier.produce(metrics);
    }

    /**
     * Metrics queue processor, merge the received metrics if existing one with same ID(s) and time bucket.
     *
     * ID is declared through {@link Object#hashCode()} and {@link Object#equals(Object)} as usual.
     */
    private class PersistentConsumer implements IConsumer<Metrics> {
        @Override
        public void consume(List<Metrics> data) {
            int queueBufferSize = Arrays.stream(dataCarrier.getChannels().getBufferChannels())
                                        .mapToInt(QueueBuffer::getBufferSize)
                                        .sum();
            int queueSize = data.size();

            queuePercentageGauge.setValue(Math.round(100 * (double) queueSize / queueBufferSize));
            MetricsPersistentWorkerMin.this.onWork(data);
        }

        @Override
        public void onError(List<Metrics> data, Throwable t) {
            log.error(t.getMessage(), t);
        }
    }
}
