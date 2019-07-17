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

import java.util.*;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.*;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.data.*;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class MetricsAggregateWorker extends AbstractWorker<Metrics> {

    private static final Logger logger = LoggerFactory.getLogger(MetricsAggregateWorker.class);

    private AbstractWorker<Metrics> nextWorker;
    private final DataCarrier<Metrics> dataCarrier;
    private final MergeDataCache<Metrics> mergeDataCache;
    private CounterMetrics aggregationCounter;

    MetricsAggregateWorker(ModuleDefineHolder moduleDefineHolder, AbstractWorker<Metrics> nextWorker, String modelName) {
        super(moduleDefineHolder);
        this.nextWorker = nextWorker;
        this.mergeDataCache = new MergeDataCache<>();
        String name = "METRICS_L1_AGGREGATION";
        this.dataCarrier = new DataCarrier<>("MetricsAggregateWorker." + modelName, name, 2, 10000);

        BulkConsumePool.Creator creator = new BulkConsumePool.Creator(name, BulkConsumePool.Creator.recommendMaxSize() * 2, 20);
        try {
            ConsumerPoolFactory.INSTANCE.createIfAbsent(name, creator);
        } catch (Exception e) {
            throw new UnexpectedException(e.getMessage(), e);
        }
        this.dataCarrier.consume(ConsumerPoolFactory.INSTANCE.get(name), new AggregatorConsumer(this));

        MetricsCreator metricsCreator = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
        aggregationCounter = metricsCreator.createCounter("metrics_aggregation", "The number of rows in aggregation",
            new MetricsTag.Keys("metricName", "level", "dimensionality"), new MetricsTag.Values(modelName, "1", "min"));
    }

    @Override public final void in(Metrics metrics) {
        metrics.setEndOfBatchContext(new EndOfBatchContext(false));
        dataCarrier.produce(metrics);
    }

    private void onWork(Metrics metrics) {
        aggregationCounter.inc();
        aggregate(metrics);

        if (metrics.getEndOfBatchContext().isEndOfBatch()) {
            sendToNext();
        }
    }

    private void sendToNext() {
        mergeDataCache.switchPointer();
        while (mergeDataCache.getLast().isWriting()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }

        mergeDataCache.getLast().collection().forEach(data -> {
            if (logger.isDebugEnabled()) {
                logger.debug(data.toString());
            }

            nextWorker.in(data);
        });
        mergeDataCache.finishReadingLast();
    }

    private void aggregate(Metrics metrics) {
        mergeDataCache.writing();
        if (mergeDataCache.containsKey(metrics)) {
            mergeDataCache.get(metrics).combine(metrics);
        } else {
            mergeDataCache.put(metrics);
        }

        mergeDataCache.finishWriting();
    }

    private class AggregatorConsumer implements IConsumer<Metrics> {

        private final MetricsAggregateWorker aggregator;

        private AggregatorConsumer(MetricsAggregateWorker aggregator) {
            this.aggregator = aggregator;
        }

        @Override public void init() {

        }

        @Override public void consume(List<Metrics> data) {
            Iterator<Metrics> inputIterator = data.iterator();

            int i = 0;
            while (inputIterator.hasNext()) {
                Metrics metrics = inputIterator.next();
                i++;
                if (i == data.size()) {
                    metrics.getEndOfBatchContext().setEndOfBatch(true);
                }
                aggregator.onWork(metrics);
            }
        }

        @Override public void onError(List<Metrics> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }
}
