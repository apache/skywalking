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

import java.util.Iterator;
import java.util.List;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.*;
import org.apache.skywalking.oap.server.core.analysis.data.EndOfBatchContext;
import org.apache.skywalking.oap.server.core.analysis.data.MergeDataCache;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class IndicatorAggregateWorker extends AbstractWorker<Indicator> {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorAggregateWorker.class);

    private AbstractWorker<Indicator> nextWorker;
    private final DataCarrier<Indicator> dataCarrier;
    private final MergeDataCache<Indicator> mergeDataCache;
    private int messageNum;
    private final String modelName;
    private CounterMetric aggregationCounter;

    IndicatorAggregateWorker(ModuleManager moduleManager, int workerId, AbstractWorker<Indicator> nextWorker, String modelName) {
        super(workerId);
        this.modelName = modelName;
        this.nextWorker = nextWorker;
        this.mergeDataCache = new MergeDataCache<>();
        this.dataCarrier = new DataCarrier<>("IndicatorAggregateWorker." + modelName, 1, 10000);

        this.dataCarrier.consume(ConsumerPoolFactory.DEFAULT_POOL, new AggregatorConsumer(this));

        MetricCreator metricCreator = moduleManager.find(TelemetryModule.NAME).provider().getService(MetricCreator.class);
        aggregationCounter = metricCreator.createCounter("indicator_aggregation", "The number of rows in aggregation",
            new MetricTag.Keys("metricName", "level", "dimensionality"), new MetricTag.Values(modelName, "1", "min"));
    }

    @Override public final void in(Indicator indicator) {
        indicator.setEndOfBatchContext(new EndOfBatchContext(false));
        dataCarrier.produce(indicator);
    }

    private void onWork(Indicator indicator) {
        aggregationCounter.inc();
        messageNum++;
        aggregate(indicator);

        if (messageNum >= 1000 || indicator.getEndOfBatchContext().isEndOfBatch()) {
            sendToNext();
            messageNum = 0;
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

    private void aggregate(Indicator indicator) {
        mergeDataCache.writing();
        if (mergeDataCache.containsKey(indicator)) {
            mergeDataCache.get(indicator).combine(indicator);
        } else {
            mergeDataCache.put(indicator);
        }

        mergeDataCache.finishWriting();
    }

    private class AggregatorConsumer implements IConsumer<Indicator> {

        private final IndicatorAggregateWorker aggregator;

        private AggregatorConsumer(IndicatorAggregateWorker aggregator) {
            this.aggregator = aggregator;
        }

        @Override public void init() {

        }

        @Override public void consume(List<Indicator> data) {
            Iterator<Indicator> inputIterator = data.iterator();

            int i = 0;
            while (inputIterator.hasNext()) {
                Indicator indicator = inputIterator.next();
                i++;
                if (i == data.size()) {
                    indicator.getEndOfBatchContext().setEndOfBatch(true);
                }
                aggregator.onWork(indicator);
            }
        }

        @Override public void onError(List<Indicator> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }
}
