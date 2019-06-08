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
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.slf4j.*;

import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public class MetricsPersistentWorker extends PersistenceWorker<Metrics, MergeDataCache<Metrics>> {

    private static final Logger logger = LoggerFactory.getLogger(MetricsPersistentWorker.class);

    private final Model model;
    private final MergeDataCache<Metrics> mergeDataCache;
    private final IMetricsDAO metricsDAO;
    private final AbstractWorker<Metrics> nextAlarmWorker;
    private final AbstractWorker<ExportEvent> nextExportWorker;
    private final DataCarrier<Metrics> dataCarrier;

    MetricsPersistentWorker(ModuleDefineHolder moduleDefineHolder, Model model, int batchSize,
        IMetricsDAO metricsDAO, AbstractWorker<Metrics> nextAlarmWorker,
        AbstractWorker<ExportEvent> nextExportWorker) {
        super(moduleDefineHolder, batchSize);
        this.model = model;
        this.mergeDataCache = new MergeDataCache<>();
        this.metricsDAO = metricsDAO;
        this.nextAlarmWorker = nextAlarmWorker;
        this.nextExportWorker = nextExportWorker;

        String name = "METRICS_L2_AGGREGATION";
        int size = BulkConsumePool.Creator.recommendMaxSize() / 8;
        if (size == 0) {
            size = 1;
        }
        BulkConsumePool.Creator creator = new BulkConsumePool.Creator(name, size, 20);
        try {
            ConsumerPoolFactory.INSTANCE.createIfAbsent(name, creator);
        } catch (Exception e) {
            throw new UnexpectedException(e.getMessage(), e);
        }

        this.dataCarrier = new DataCarrier<>("MetricsPersistentWorker." + model.getName(), name, 1, 2000);
        this.dataCarrier.consume(ConsumerPoolFactory.INSTANCE.get(name), new PersistentConsumer(this));
    }

    @Override void onWork(Metrics metrics) {
        super.onWork(metrics);
    }

    @Override public void in(Metrics metrics) {
        metrics.setEndOfBatchContext(new EndOfBatchContext(false));
        dataCarrier.produce(metrics);
    }

    @Override public MergeDataCache<Metrics> getCache() {
        return mergeDataCache;
    }

    public boolean flushAndSwitch() {
        boolean isSwitch;
        try {
            if (isSwitch = getCache().trySwitchPointer()) {
                getCache().switchPointer();
            }
        } finally {
            getCache().trySwitchPointerFinally();
        }
        return isSwitch;
    }

    @Override public List<Object> prepareBatch(MergeDataCache<Metrics> cache) {
        List<Object> batchCollection = new LinkedList<>();
        cache.getLast().collection().forEach(data -> {
            if (Objects.nonNull(nextExportWorker)) {
                ExportEvent event = new ExportEvent(data, ExportEvent.EventType.INCREMENT);
                nextExportWorker.in(event);
            }

            Metrics dbData = null;
            try {
                dbData = metricsDAO.get(model, data);
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
            }
            try {
                if (nonNull(dbData)) {
                    data.combine(dbData);
                    data.calculate();

                    batchCollection.add(metricsDAO.prepareBatchUpdate(model, data));
                } else {
                    batchCollection.add(metricsDAO.prepareBatchInsert(model, data));
                }

                if (Objects.nonNull(nextAlarmWorker)) {
                    nextAlarmWorker.in(data);
                }
                if (Objects.nonNull(nextExportWorker)) {
                    ExportEvent event = new ExportEvent(data, ExportEvent.EventType.TOTAL);
                    nextExportWorker.in(event);
                }
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
            }
        });

        return batchCollection;
    }

    @Override public void cacheData(Metrics input) {
        mergeDataCache.writing();
        if (mergeDataCache.containsKey(input)) {
            Metrics metrics = mergeDataCache.get(input);
            metrics.combine(input);
            metrics.calculate();
        } else {
            input.calculate();
            mergeDataCache.put(input);
        }

        mergeDataCache.finishWriting();
    }

    private class PersistentConsumer implements IConsumer<Metrics> {

        private final MetricsPersistentWorker persistent;

        private PersistentConsumer(MetricsPersistentWorker persistent) {
            this.persistent = persistent;
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
                persistent.onWork(metrics);
            }
        }

        @Override public void onError(List<Metrics> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }
}
