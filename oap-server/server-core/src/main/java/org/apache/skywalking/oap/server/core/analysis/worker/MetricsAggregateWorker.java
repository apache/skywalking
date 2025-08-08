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
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.data.MergableBufferedData;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.datacarrier.DataCarrier;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.QueueBuffer;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.BulkConsumePool;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.ConsumerPoolFactory;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.IConsumer;
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
 */
@Slf4j
public class MetricsAggregateWorker extends AbstractWorker<Metrics> {
    public final long l1FlushPeriod;
    private AbstractWorker<Metrics> nextWorker;
    private final DataCarrier<Metrics> dataCarrier;
    private final MergableBufferedData<Metrics> mergeDataCache;
    private CounterMetrics abandonCounter;
    private CounterMetrics aggregationCounter;
    private GaugeMetrics queuePercentageGauge;
    private long lastSendTime = 0;
    private final MetricStreamKind kind;
    private final int queueTotalSize;

    MetricsAggregateWorker(ModuleDefineHolder moduleDefineHolder,
                           AbstractWorker<Metrics> nextWorker,
                           String modelName,
                           long l1FlushPeriod,
                           MetricStreamKind kind) {
        super(moduleDefineHolder);
        this.nextWorker = nextWorker;
        this.mergeDataCache = new MergableBufferedData();
        this.kind = kind;
        String name = "METRICS_L1_AGGREGATION";
        int queueChannelSize = 2;
        int queueBufferSize = 10_000;
        if (MetricStreamKind.MAL == kind) {
            // In MAL meter streaming, the load of data flow is much less as they are statistics already,
            // but in OAL sources, they are raw data.
            // Set the buffer(size of queue) as 1/20 to reduce unnecessary resource costs.
            queueChannelSize = 1;
            queueBufferSize = 1_000;
        }
        this.dataCarrier = new DataCarrier<>(
            "MetricsAggregateWorker." + modelName, name, queueChannelSize, queueBufferSize, BufferStrategy.IF_POSSIBLE);

        BulkConsumePool.Creator creator = new BulkConsumePool.Creator(
            name, BulkConsumePool.Creator.recommendMaxSize() * 2, 200);
        try {
            ConsumerPoolFactory.INSTANCE.createIfAbsent(name, creator);
        } catch (Exception e) {
            throw new UnexpectedException(e.getMessage(), e);
        }
        this.dataCarrier.consume(ConsumerPoolFactory.INSTANCE.get(name), new AggregatorConsumer());

        MetricsCreator metricsCreator = moduleDefineHolder.find(TelemetryModule.NAME)
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
        queuePercentageGauge = metricsCreator.createGauge(
            "metrics_aggregation_queue_used_percentage", "The percentage of queue used in aggregation.",
            new MetricsTag.Keys("metricName", "level", "kind"),
            new MetricsTag.Values(modelName, "1", kind.name())
        );
        this.l1FlushPeriod = l1FlushPeriod;
        queueTotalSize = Arrays.stream(dataCarrier.getChannels().getBufferChannels())
                               .mapToInt(QueueBuffer::getBufferSize)
                               .sum();
    }

    /**
     * MetricsAggregateWorker#in operation does include enqueue only
     */
    @Override
    public final void in(Metrics metrics) {
        if (!dataCarrier.produce(metrics)) {
            abandonCounter.inc();
        }
    }

    /**
     * Dequeue consuming. According to {@link IConsumer#consume(List)}, this is a serial operation for every work
     * instance.
     *
     * @param metricsList from the queue.
     */
    private void onWork(List<Metrics> metricsList) {
        metricsList.forEach(metrics -> {
            aggregationCounter.inc();
            mergeDataCache.accept(metrics);
        });

        flush();
    }

    private void flush() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSendTime > l1FlushPeriod) {
            mergeDataCache.read().forEach(
                data -> {
                    nextWorker.in(data);
                }
            );
            lastSendTime = currentTime;
        }
    }

    private class AggregatorConsumer implements IConsumer<Metrics> {
        @Override
        public void consume(List<Metrics> data) {
            queuePercentageGauge.setValue(Math.round(100 * (double) data.size() / queueTotalSize));
            MetricsAggregateWorker.this.onWork(data);
        }

        @Override
        public void onError(List<Metrics> data, Throwable t) {
            log.error(t.getMessage(), t);
        }

        @Override
        public void nothingToConsume() {
            flush();
        }
    }
}