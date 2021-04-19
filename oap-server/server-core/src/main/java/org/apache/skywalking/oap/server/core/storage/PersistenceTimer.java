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

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.PersistenceWorker;
import org.apache.skywalking.oap.server.core.analysis.worker.TopNStreamProcessor;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public enum PersistenceTimer {
    INSTANCE;
    private Boolean isStarted = false;
    private final Boolean debug;
    private CounterMetrics errorCounter;
    private HistogramMetrics prepareLatency;
    private HistogramMetrics executeLatency;
    private long lastTime = System.currentTimeMillis();
    private final List<PrepareRequest> prepareRequests = new ArrayList<>(50000);
    private int syncOperationThreadsNum;
    private int maxSyncoperationNum;
    private ExecutorService executorService;

    PersistenceTimer() {
        this.debug = System.getProperty("debug") != null;
    }

    public void start(ModuleManager moduleManager, CoreModuleConfig moduleConfig) {
        log.info("persistence timer start");
        IBatchDAO batchDAO = moduleManager.find(StorageModule.NAME).provider().getService(IBatchDAO.class);

        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        errorCounter = metricsCreator.createCounter(
            "persistence_timer_bulk_error_count", "Error execution of the prepare stage in persistence timer",
            MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE
        );
        prepareLatency = metricsCreator.createHistogramMetric(
            "persistence_timer_bulk_prepare_latency", "Latency of the prepare stage in persistence timer",
            MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE
        );
        executeLatency = metricsCreator.createHistogramMetric(
            "persistence_timer_bulk_execute_latency", "Latency of the execute stage in persistence timer",
            MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE
        );
        syncOperationThreadsNum = moduleConfig.getSyncThreads();
        maxSyncoperationNum = moduleConfig.getMaxSyncOperationNum();
        executorService = Executors.newFixedThreadPool(syncOperationThreadsNum);
        if (!isStarted) {
            Executors.newSingleThreadScheduledExecutor()
                     .scheduleWithFixedDelay(
                         new RunnableWithExceptionProtection(() -> extractDataAndSave(batchDAO), t -> log
                             .error("Extract data and save failure.", t)), 5, moduleConfig.getPersistentPeriod(),
                         TimeUnit.SECONDS
                     );

            this.isStarted = true;
        }
    }

    private void extractDataAndSave(IBatchDAO batchDAO) {
        if (log.isDebugEnabled()) {
            log.debug("Extract data and save");
        }

        long startTime = System.currentTimeMillis();

        try {
            HistogramMetrics.Timer timer = prepareLatency.createTimer();

            try {
                List<PersistenceWorker> persistenceWorkers = new ArrayList<>();
                persistenceWorkers.addAll(TopNStreamProcessor.getInstance().getPersistentWorkers());
                persistenceWorkers.addAll(MetricsStreamProcessor.getInstance().getPersistentWorkers());

                persistenceWorkers.forEach(worker -> {
                    if (log.isDebugEnabled()) {
                        log.debug("extract {} worker data and save", worker.getClass().getName());
                    }

                    worker.buildBatchRequests(prepareRequests);

                    worker.endOfRound(System.currentTimeMillis() - lastTime);
                });

                if (debug) {
                    log.info("build batch persistence duration: {} ms", System.currentTimeMillis() - startTime);
                }
            } finally {
                timer.finish();
            }

            HistogramMetrics.Timer executeLatencyTimer = executeLatency.createTimer();
            try {
                List<List<PrepareRequest>> partitions = Lists.partition(prepareRequests, maxSyncoperationNum);
                CountDownLatch countDownLatch = new CountDownLatch(partitions.size());
                for (final List<PrepareRequest> partition : partitions) {
                    executorService.submit(() -> {
                        try {
                            if (CollectionUtils.isNotEmpty(partition)) {
                                batchDAO.synchronous(partition);
                            }
                        } catch (Throwable e) {
                            log.error(e.getMessage(), e);
                        } finally {
                            countDownLatch.countDown();
                        }
                    });
                }
                countDownLatch.await();
            } finally {
                executeLatencyTimer.finish();
            }
        } catch (Throwable e) {
            errorCounter.inc();
            log.error(e.getMessage(), e);
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("Persistence data save finish");
            }

            prepareRequests.clear();
            lastTime = System.currentTimeMillis();
        }

        if (debug) {
            log.info("Batch persistence duration: {} ms", System.currentTimeMillis() - startTime);
        }
    }
}
