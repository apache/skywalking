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

import java.io.IOException;
import java.util.*;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.*;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.data.MergeDataCache;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class MetricsPersistentWorker extends PersistenceWorker<Metrics, MergeDataCache<Metrics>> {

    private static final Logger logger = LoggerFactory.getLogger(MetricsPersistentWorker.class);

    private final Model model;
    private final Map<Metrics, Metrics> databaseSession;
    private final MergeDataCache<Metrics> mergeDataCache;
    private final IMetricsDAO metricsDAO;
    private final AbstractWorker<Metrics> nextAlarmWorker;
    private final AbstractWorker<ExportEvent> nextExportWorker;
    private final DataCarrier<Metrics> dataCarrier;
    private final boolean enableDatabaseSession;

    MetricsPersistentWorker(ModuleDefineHolder moduleDefineHolder, Model model, IMetricsDAO metricsDAO, AbstractWorker<Metrics> nextAlarmWorker,
        AbstractWorker<ExportEvent> nextExportWorker, boolean enableDatabaseSession) {
        super(moduleDefineHolder);
        this.model = model;
        this.databaseSession = new HashMap<>(100);
        this.enableDatabaseSession = enableDatabaseSession;
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
        cacheData(metrics);
    }

    @Override public void in(Metrics metrics) {
        dataCarrier.produce(metrics);
    }

    @Override public MergeDataCache<Metrics> getCache() {
        return mergeDataCache;
    }

    @Override public void prepareBatch(Collection<Metrics> lastCollection, List<PrepareRequest> prepareRequests) {
        long start = System.currentTimeMillis();

        int i = 0;
        int batchGetSize = 2000;
        Metrics[] metrics = null;
        for (Metrics data : lastCollection) {
            if (Objects.nonNull(nextExportWorker)) {
                ExportEvent event = new ExportEvent(data, ExportEvent.EventType.INCREMENT);
                nextExportWorker.in(event);
            }

            int mod = i % batchGetSize;
            if (mod == 0) {
                int residual = lastCollection.size() - i;
                if (residual >= batchGetSize) {
                    metrics = new Metrics[batchGetSize];
                } else {
                    metrics = new Metrics[residual];
                }
            }
            metrics[mod] = data;

            if (mod == metrics.length - 1) {
                try {
                    syncStorageToCache(metrics);

                    for (Metrics metric : metrics) {
                        Metrics cacheMetric = databaseSession.get(metric);
                        if (cacheMetric != null) {
                            cacheMetric.combine(metric);
                            cacheMetric.calculate();
                            prepareRequests.add(metricsDAO.prepareBatchUpdate(model, cacheMetric));
                            nextWorker(cacheMetric);
                        } else {
                            prepareRequests.add(metricsDAO.prepareBatchInsert(model, metric));
                            nextWorker(metric);
                        }
                    }
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }

            i++;
        }

        if (prepareRequests.size() > 0) {
            logger.debug("prepare batch requests for model {}, took time: {}", model.getName(), System.currentTimeMillis() - start);
        }
    }

    private void nextWorker(Metrics metric) {
        if (Objects.nonNull(nextAlarmWorker)) {
            nextAlarmWorker.in(metric);
        }
        if (Objects.nonNull(nextExportWorker)) {
            ExportEvent event = new ExportEvent(metric, ExportEvent.EventType.TOTAL);
            nextExportWorker.in(event);
        }
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

    private void syncStorageToCache(Metrics[] metrics) throws IOException {
        if (!enableDatabaseSession) {
            databaseSession.clear();
        }

        List<String> notInCacheIds = new ArrayList<>();
        for (Metrics metric : metrics) {
            if (!databaseSession.containsKey(metric)) {
                notInCacheIds.add(metric.id());
            }
        }

        if (notInCacheIds.size() > 0) {
            List<Metrics> metricsList = metricsDAO.multiGet(model, notInCacheIds);
            for (Metrics metric : metricsList) {
                databaseSession.put(metric, metric);
            }
        }
    }

    @Override public void endOfRound(long tookTime) {
        if (enableDatabaseSession) {
            Iterator<Metrics> iterator = databaseSession.values().iterator();
            while (iterator.hasNext()) {
                Metrics metrics = iterator.next();
                metrics.setSurvivalTime(tookTime + metrics.getSurvivalTime());
                if (metrics.getSurvivalTime() > 70000) {
                    iterator.remove();
                }
            }
        }
    }

    private class PersistentConsumer implements IConsumer<Metrics> {

        private final MetricsPersistentWorker persistent;

        private PersistentConsumer(MetricsPersistentWorker persistent) {
            this.persistent = persistent;
        }

        @Override public void init() {

        }

        @Override public void consume(List<Metrics> data) {
            data.forEach(persistent::onWork);
        }

        @Override public void onError(List<Metrics> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }
}
