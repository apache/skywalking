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

package org.apache.skywalking.oap.server.core.storage;

import java.util.*;
import java.util.concurrent.*;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.core.analysis.worker.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public enum PersistenceTimer {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(PersistenceTimer.class);

    private Boolean isStarted = false;
    private final Boolean debug;
    private CounterMetric errorCounter;
    private HistogramMetric prepareLatency;
    private HistogramMetric executeLatency;

    PersistenceTimer() {
        this.debug = System.getProperty("debug") != null;
    }

    public void start(ModuleManager moduleManager) {
        logger.info("persistence timer start");
        //TODO timer value config
//        final long timeInterval = EsConfig.Es.Persistence.Timer.VALUE * 1000;
        final long timeInterval = 3;
        IBatchDAO batchDAO = moduleManager.find(StorageModule.NAME).provider().getService(IBatchDAO.class);

        MetricCreator metricCreator = moduleManager.find(TelemetryModule.NAME).provider().getService(MetricCreator.class);
        errorCounter = metricCreator.createCounter("persistence_timer_bulk_error_count", "Error execution of the prepare stage in persistence timer",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
        prepareLatency = metricCreator.createHistogramMetric("persistence_timer_bulk_prepare_latency", "Latency of the prepare stage in persistence timer",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
        executeLatency = metricCreator.createHistogramMetric("persistence_timer_bulk_execute_latency", "Latency of the execute stage in persistence timer",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);

        if (!isStarted) {
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                new RunnableWithExceptionProtection(() -> extractDataAndSave(batchDAO),
                    t -> logger.error("Extract data and save failure.", t)), 1, timeInterval, TimeUnit.SECONDS);

            this.isStarted = true;
        }
    }

    @SuppressWarnings("unchecked")
    private void extractDataAndSave(IBatchDAO batchDAO) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extract data and save");
        }

        long startTime = System.currentTimeMillis();
        try {
            HistogramMetric.Timer timer = prepareLatency.createTimer();

            List batchAllCollection = new LinkedList();
            try {
                List<PersistenceWorker> persistenceWorkers = new ArrayList<>();
                persistenceWorkers.addAll(IndicatorProcess.INSTANCE.getPersistentWorkers());
                persistenceWorkers.addAll(RecordProcess.INSTANCE.getPersistentWorkers());
                persistenceWorkers.addAll(TopNProcess.INSTANCE.getPersistentWorkers());

                persistenceWorkers.forEach(worker -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("extract {} worker data and save", worker.getClass().getName());
                    }

                    if (worker.flushAndSwitch()) {
                        List<?> batchCollection = worker.buildBatchCollection();

                        if (logger.isDebugEnabled()) {
                            logger.debug("extract {} worker data size: {}", worker.getClass().getName(), batchCollection.size());
                        }
                        batchAllCollection.addAll(batchCollection);
                    }
                });

                if (debug) {
                    logger.info("build batch persistence duration: {} ms", System.currentTimeMillis() - startTime);
                }
            } finally {
                timer.finish();
            }

            HistogramMetric.Timer executeLatencyTimer = executeLatency.createTimer();
            try {
                batchDAO.batchPersistence(batchAllCollection);
            } finally {
                executeLatencyTimer.finish();
            }
        } catch (Throwable e) {
            errorCounter.inc();
            logger.error(e.getMessage(), e);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("persistence data save finish");
            }
        }

        if (debug) {
            logger.info("batch persistence duration: {} ms", System.currentTimeMillis() - startTime);
        }
    }
}
