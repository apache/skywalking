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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.BulkConsumePool;
import org.apache.skywalking.apm.commons.datacarrier.consumer.ConsumerPoolFactory;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.data.MergableBufferedData;
import org.apache.skywalking.oap.server.core.analysis.data.ReadWriteSafeCache;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * MetricsPersistentWorker is an extension of {@link PersistenceWorker} and focuses on the Metrics data persistent.
 */
@Slf4j
public class MetricsPersistentWorker extends PersistenceWorker<Metrics> {
    private final Model model;
    private final Map<Metrics, Metrics> context;
    private final IMetricsDAO metricsDAO;
    private final Optional<AbstractWorker<Metrics>> nextAlarmWorker;
    private final Optional<AbstractWorker<ExportEvent>> nextExportWorker;
    private final DataCarrier<Metrics> dataCarrier;
    private final Optional<MetricsTransWorker> transWorker;
    private final boolean enableDatabaseSession;
    private final boolean supportUpdate;
    private CounterMetrics aggregationCounter;

    MetricsPersistentWorker(ModuleDefineHolder moduleDefineHolder, Model model, IMetricsDAO metricsDAO,
                            AbstractWorker<Metrics> nextAlarmWorker, AbstractWorker<ExportEvent> nextExportWorker,
                            MetricsTransWorker transWorker, boolean enableDatabaseSession, boolean supportUpdate) {
        super(moduleDefineHolder, new ReadWriteSafeCache<>(new MergableBufferedData(), new MergableBufferedData()));
        this.model = model;
        this.context = new HashMap<>(100);
        this.enableDatabaseSession = enableDatabaseSession;
        this.metricsDAO = metricsDAO;
        this.nextAlarmWorker = Optional.ofNullable(nextAlarmWorker);
        this.nextExportWorker = Optional.ofNullable(nextExportWorker);
        this.transWorker = Optional.ofNullable(transWorker);
        this.supportUpdate = supportUpdate;

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
        this.dataCarrier.consume(ConsumerPoolFactory.INSTANCE.get(name), new PersistentConsumer());

        MetricsCreator metricsCreator = moduleDefineHolder.find(TelemetryModule.NAME)
                                                          .provider()
                                                          .getService(MetricsCreator.class);
        aggregationCounter = metricsCreator.createCounter(
            "metrics_aggregation", "The number of rows in aggregation",
            new MetricsTag.Keys("metricName", "level", "dimensionality"),
            new MetricsTag.Values(model.getName(), "2", model.getDownsampling().getName())
        );
    }

    /**
     * Create the leaf MetricsPersistentWorker, no next step.
     */
    MetricsPersistentWorker(ModuleDefineHolder moduleDefineHolder, Model model, IMetricsDAO metricsDAO,
                            boolean enableDatabaseSession, boolean supportUpdate) {
        this(moduleDefineHolder, model, metricsDAO,
             null, null, null,
             enableDatabaseSession, supportUpdate
        );
    }

    /**
     * Accept all metrics data and push them into the queue for serial processing
     */
    @Override
    public void in(Metrics metrics) {
        aggregationCounter.inc();
        dataCarrier.produce(metrics);
    }

    @Override
    public void prepareBatch(Collection<Metrics> lastCollection, List<PrepareRequest> prepareRequests) {
        long start = System.currentTimeMillis();
        if (lastCollection.size() == 0) {
            return;
        }

        /*
         * Hard coded the max size. This is only the batch size of one metrics, too large number is meaningless.
         */
        int maxBatchGetSize = 2000;
        final int batchSize = Math.min(maxBatchGetSize, lastCollection.size());
        List<Metrics> metricsList = new ArrayList<>();
        for (Metrics data : lastCollection) {
            transWorker.ifPresent(metricsTransWorker -> metricsTransWorker.in(data));

            metricsList.add(data);

            if (metricsList.size() == batchSize) {
                flushDataToStorage(metricsList, prepareRequests);
            }
        }

        if (metricsList.size() > 0) {
            flushDataToStorage(metricsList, prepareRequests);
        }

        if (prepareRequests.size() > 0) {
            log.debug(
                "prepare batch requests for model {}, took time: {}, size: {}", model.getName(),
                System.currentTimeMillis() - start, prepareRequests.size()
            );
        }
    }

    private void flushDataToStorage(List<Metrics> metricsList,
                                    List<PrepareRequest> prepareRequests) {
        try {
            loadFromStorage(metricsList);

            for (Metrics metrics : metricsList) {
                Metrics cachedMetrics = context.get(metrics);
                if (cachedMetrics != null) {
                    /*
                     * If the metrics is not supportUpdate, defined through MetricsExtension#supportUpdate,
                     * then no merge and further process happens.
                     */
                    if (!supportUpdate) {
                        continue;
                    }
                    /*
                     * Merge metrics into cachedMetrics, change only happens inside cachedMetrics.
                     */
                    final boolean isAbandoned = !cachedMetrics.combine(metrics);
                    if (isAbandoned) {
                        continue;
                    }
                    cachedMetrics.calculate();
                    prepareRequests.add(metricsDAO.prepareBatchUpdate(model, cachedMetrics));
                    nextWorker(cachedMetrics);
                } else {
                    metrics.calculate();
                    prepareRequests.add(metricsDAO.prepareBatchInsert(model, metrics));
                    nextWorker(metrics);
                }

                /*
                 * The `metrics` should be not changed in all above process. Exporter is an async process.
                 */
                nextExportWorker.ifPresent(exportEvenWorker -> exportEvenWorker.in(
                    new ExportEvent(metrics, ExportEvent.EventType.INCREMENT)));
            }
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
        } finally {
            metricsList.clear();
        }
    }

    private void nextWorker(Metrics metrics) {
        nextAlarmWorker.ifPresent(nextAlarmWorker -> nextAlarmWorker.in(metrics));
        nextExportWorker.ifPresent(
            nextExportWorker -> nextExportWorker.in(new ExportEvent(metrics, ExportEvent.EventType.TOTAL)));
    }

    /**
     * Load data from the storage, if {@link #enableDatabaseSession} == true, only load data when the id doesn't exist.
     */
    private void loadFromStorage(List<Metrics> metrics) throws IOException {
        if (!enableDatabaseSession) {
            context.clear();
        }

        List<Metrics> noInCacheMetrics = metrics.stream()
                                                .filter(m -> !context.containsKey(m))
                                                .collect(Collectors.toList());
        if (!noInCacheMetrics.isEmpty()) {
            metricsDAO.multiGet(model, noInCacheMetrics).forEach(m -> context.put(m, m));
        }
    }

    @Override
    public void endOfRound(long tookTime) {
        if (enableDatabaseSession) {
            Iterator<Metrics> iterator = context.values().iterator();
            while (iterator.hasNext()) {
                Metrics metrics = iterator.next();
                metrics.extendSurvivalTime(tookTime);
                // 70,000ms means more than one minute.
                if (metrics.getSurvivalTime() > 70000) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Metrics queue processor, merge the received metrics if existing one with same ID(s) and time bucket.
     *
     * ID is declared through {@link Object#hashCode()} and {@link Object#equals(Object)} as usual.
     */
    private class PersistentConsumer implements IConsumer<Metrics> {
        @Override
        public void init() {

        }

        @Override
        public void consume(List<Metrics> data) {
            MetricsPersistentWorker.this.onWork(data);
        }

        @Override
        public void onError(List<Metrics> data, Throwable t) {
            log.error(t.getMessage(), t);
        }

        @Override
        public void onExit() {
        }
    }
}
