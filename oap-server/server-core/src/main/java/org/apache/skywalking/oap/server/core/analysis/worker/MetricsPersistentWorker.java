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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.data.MergableBufferedData;
import org.apache.skywalking.oap.server.core.analysis.data.ReadWriteSafeCache;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.status.ServerStatusService;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.datacarrier.DataCarrier;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.BulkConsumePool;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.ConsumerPoolFactory;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.IConsumer;
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
    /**
     * The counter of MetricsPersistentWorker instance, to calculate session timeout offset.
     */
    private static long SESSION_TIMEOUT_OFFSITE_COUNTER = 0;

    private final Model model;
    private final MetricsSessionCache sessionCache;
    private final IMetricsDAO metricsDAO;
    private final Optional<AbstractWorker<Metrics>> nextAlarmWorker;
    private final Optional<AbstractWorker<ExportEvent>> nextExportWorker;
    private final DataCarrier<Metrics> dataCarrier;
    private final Optional<MetricsTransWorker> transWorker;
    private final boolean supportUpdate;
    /**
     * The counter of L2 aggregation.
     */
    private CounterMetrics aggregationCounter;
    /**
     * The counter of metrics reading from Database.
     */
    private CounterMetrics readMetricsCounter;
    /**
     * The counter of metrics cached in-memory.
     */
    private CounterMetrics cachedMetricsCounter;
    /**
     * The counter for the round of persistent.
     */
    private int persistentCounter;
    /**
     * The mod value to control persistent. The MetricsPersistentWorker is driven by the {@link
     * org.apache.skywalking.oap.server.core.storage.PersistenceTimer}. The down sampling level workers only execute in
     * every {@link #persistentMod} periods. And minute level workers execute every time.
     */
    private int persistentMod;
    /**
     * @since 8.7.0 TTL settings from {@link org.apache.skywalking.oap.server.core.CoreModuleConfig#getMetricsDataTTL()}
     */
    private int metricsDataTTL;
    /**
     * @since 9.4.0
     */
    private final ServerStatusService serverStatusService;
    /**
     * The time bucket is 0 or in minute dimensionality of the system in the latest stability status.
     *
     * @since 9.4.0
     */
    private long timeOfLatestStabilitySts = 0;

    MetricsPersistentWorker(ModuleDefineHolder moduleDefineHolder, Model model, IMetricsDAO metricsDAO,
                            AbstractWorker<Metrics> nextAlarmWorker, AbstractWorker<ExportEvent> nextExportWorker,
                            MetricsTransWorker transWorker, boolean supportUpdate,
                            long storageSessionTimeout, int metricsDataTTL, MetricStreamKind kind) {
        super(moduleDefineHolder, new ReadWriteSafeCache<>(new MergableBufferedData(), new MergableBufferedData()));
        this.model = model;
        this.sessionCache = new MetricsSessionCache(storageSessionTimeout);
        this.metricsDAO = metricsDAO;
        this.nextAlarmWorker = Optional.ofNullable(nextAlarmWorker);
        this.nextExportWorker = Optional.ofNullable(nextExportWorker);
        this.transWorker = Optional.ofNullable(transWorker);
        this.supportUpdate = supportUpdate;
        this.persistentCounter = 0;
        this.persistentMod = 1;
        this.metricsDataTTL = metricsDataTTL;

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
        aggregationCounter = metricsCreator.createCounter(
            "metrics_aggregation", "The number of rows in aggregation",
            new MetricsTag.Keys("metricName", "level", "dimensionality"),
            new MetricsTag.Values(model.getName(), "2", model.getDownsampling().getName())
        );
        readMetricsCounter = metricsCreator.createCounter(
            "metrics_persistent_cache", "The counter of metrics status, new or cached.",
            new MetricsTag.Keys("status"), new MetricsTag.Values("new")
        );
        cachedMetricsCounter = metricsCreator.createCounter(
            "metrics_persistent_cache", "The counter of metrics status, new or cached.",
            new MetricsTag.Keys("status"), new MetricsTag.Values("cached")
        );
        SESSION_TIMEOUT_OFFSITE_COUNTER++;
        serverStatusService = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ServerStatusService.class);
    }

    /**
     * Create the leaf and down-sampling MetricsPersistentWorker, no next step.
     */
    MetricsPersistentWorker(ModuleDefineHolder moduleDefineHolder,
                            Model model,
                            IMetricsDAO metricsDAO,
                            boolean supportUpdate,
                            long storageSessionTimeout,
                            int metricsDataTTL,
                            MetricStreamKind kind) {
        this(moduleDefineHolder, model, metricsDAO,
             null, null, null,
             supportUpdate, storageSessionTimeout, metricsDataTTL, kind
        );
        // For a down-sampling metrics, we prolong the session timeout for 4 times, nearly 5 minutes.
        // And add offset according to worker creation sequence, to avoid context clear overlap,
        // eventually optimize load of IDs reading.
        sessionCache.setTimeoutThreshold(storageSessionTimeout * 4 + SESSION_TIMEOUT_OFFSITE_COUNTER * 200);
        // Set cache mode in normal mode for high dimensionality metrics, such as hour/day metrics.
        // The down sampling level worker executes every 4 periods.
        this.persistentMod = 4;
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
    public List<PrepareRequest> buildBatchRequests() {
        if (persistentCounter++ % persistentMod != 0) {
            return Collections.emptyList();
        }

        final List<Metrics> lastCollection = getCache().read();

        long start = System.currentTimeMillis();
        if (lastCollection.size() == 0) {
            return Collections.emptyList();
        }

        if (model.getDownsampling().equals(DownSampling.Minute)) {
            timeOfLatestStabilitySts = TimeBucket.getMinuteTimeBucket(
                serverStatusService.getBootingStatus().getUptime());
        }

        /*
         * Hard coded the max size. This only affect the multiIDRead if the data doesn't hit the cache.
         */
        int maxBatchGetSize = 2000;
        final int batchSize = Math.min(maxBatchGetSize, lastCollection.size());
        List<Metrics> metricsList = new ArrayList<>();
        List<PrepareRequest> prepareRequests = new ArrayList<>(lastCollection.size());
        for (Metrics data : lastCollection) {
            transWorker.ifPresent(metricsTransWorker -> metricsTransWorker.in(data));

            metricsList.add(data);

            if (metricsList.size() == batchSize) {
                prepareFlushDataToStorage(metricsList, prepareRequests);
            }
        }

        if (metricsList.size() > 0) {
            prepareFlushDataToStorage(metricsList, prepareRequests);
        }

        if (prepareRequests.size() > 0) {
            log.debug(
                "prepare batch requests for model {}, took time: {}, size: {}", model.getName(),
                System.currentTimeMillis() - start, prepareRequests.size()
            );
        }
        return prepareRequests;
    }

    /**
     * Build given prepareRequests to prepare database flush
     *
     * @param metricsList     the metrics in the last read from the in-memory aggregated cache.
     * @param prepareRequests the results for final execution.
     */
    private void prepareFlushDataToStorage(List<Metrics> metricsList,
                                           List<PrepareRequest> prepareRequests) {
        try {
            loadFromStorage(metricsList);

            long timestamp = System.currentTimeMillis();
            for (Metrics metrics : metricsList) {
                Metrics cachedMetrics = sessionCache.get(metrics);
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
                    prepareRequests.add(
                        metricsDAO.prepareBatchUpdate(
                            model,
                            cachedMetrics,
                            new SessionCacheCallback(sessionCache, cachedMetrics)
                        ));
                    nextWorker(cachedMetrics);
                    cachedMetrics.setLastUpdateTimestamp(timestamp);
                } else {
                    metrics.calculate();
                    prepareRequests.add(
                        metricsDAO.prepareBatchInsert(
                            model,
                            metrics,
                            new SessionCacheCallback(sessionCache, metrics)
                        ));
                    nextWorker(metrics);
                    metrics.setLastUpdateTimestamp(timestamp);
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
     * Load data from the storage, only load data when the id doesn't exist.
     */
    private void loadFromStorage(List<Metrics> metrics) {
        final long currentTimeMillis = System.currentTimeMillis();
        try {
            List<Metrics> notInCacheMetrics =
                metrics.stream()
                       .filter(m -> {
                           final Metrics cachedValue = requireInitialization(m);
                           // the metric is tagged `not in cache`.
                           if (cachedValue == null) {
                               return true;
                           }
                           // The metric is in the cache, but still we have to check
                           // whether the cache is expired due to TTL.
                           // This is a cache-DB inconsistent case:
                           // Metrics keep coming due to traffic, but the entity in the
                           // database has been removed due to TTL.
                           if (!model.isTimeRelativeID() && supportUpdate) {
                               // Mostly all updatable metadata level metrics are required to do this check.

                               if (metricsDAO.isExpiredCache(model, cachedValue, currentTimeMillis, metricsDataTTL)) {
                                   // The expired metrics should be removed from the context and tagged `not in cache` directly.
                                   sessionCache.remove(m);
                                   return true;
                               }
                           }

                           return false;
                       })
                       .collect(Collectors.toList());

            readMetricsCounter.inc(notInCacheMetrics.size());
            cachedMetricsCounter.inc(metrics.size() - notInCacheMetrics.size());
            if (notInCacheMetrics.isEmpty()) {
                return;
            }

            metricsDAO.multiGet(model, notInCacheMetrics).forEach(m -> sessionCache.put(m));
        } catch (final Exception e) {
            log.error("Failed to load metrics for merging", e);
        }
    }

    @Override
    public void endOfRound() {
        sessionCache.removeExpired();
    }

    /**
     * Check the metrics whether in the cache, and whether the worker should go further to load from database.
     *
     * @param metrics the metrics in the streaming process.
     * @return metrics in cache or null if try to read the metrics from the database.
     */
    private Metrics requireInitialization(Metrics metrics) {
        final Metrics cached = sessionCache.get(metrics);

        // All cached metrics, it at least had been written once.
        if (cached != null) {
            return cached;
        }

        // When
        // (1) the time bucket of the server's latest stability status is provided
        //     1.1 the OAP has booted successfully
        //     1.2 the current dimensionality is in minute.
        // (2) the metrics are from the time after the timeOfLatestStabilitySts
        // (3) the metrics don't exist in the cache
        // the kernel should NOT try to load it from the database.
        //
        // Notice, about condition (2),
        // for the specific minute of booted successfully, the metrics are expected to load from database when
        // it doesn't exist in the cache.
        if (timeOfLatestStabilitySts > 0 &&
            metrics.getTimeBucket() > timeOfLatestStabilitySts
            && cached == null) {
            // Return metrics as input to avoid reading from database.
            sessionCache.put(metrics);
            return metrics;
        }

        return null;
    }

    /**
     * Metrics queue processor, merge the received metrics if existing one with same ID(s) and time bucket.
     *
     * ID is declared through {@link Object#hashCode()} and {@link Object#equals(Object)} as usual.
     */
    private class PersistentConsumer implements IConsumer<Metrics> {
        @Override
        public void init(final Properties properties) {

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
