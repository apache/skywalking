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

import com.google.common.annotations.VisibleForTesting;
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
    @VisibleForTesting
    boolean isStarted = false;
    private CounterMetrics errorCounter;
    private HistogramMetrics prepareLatency;
    private HistogramMetrics executeLatency;
    private HistogramMetrics allLatency;
    private ExecutorService prepareExecutorService;

    PersistenceTimer() {
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
        allLatency = metricsCreator.createHistogramMetric(
            "persistence_timer_bulk_all_latency", "Latency of the all stage in persistence timer",
            MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE
        );

        prepareExecutorService = Executors.newFixedThreadPool(moduleConfig.getPrepareThreads());
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

        try (HistogramMetrics.Timer allTimer = allLatency.createTimer()) {
            List<PersistenceWorker<? extends StorageData>> persistenceWorkers = new ArrayList<>();
            persistenceWorkers.addAll(TopNStreamProcessor.getInstance().getPersistentWorkers());
            persistenceWorkers.addAll(MetricsStreamProcessor.getInstance().getPersistentWorkers());

            CountDownLatch countDownLatch = new CountDownLatch(persistenceWorkers.size());
            persistenceWorkers.forEach(worker -> {
                prepareExecutorService.submit(() -> {
                    List<PrepareRequest> innerPrepareRequests = null;
                    try {
                        // Prepare stage
                        try (HistogramMetrics.Timer timer = prepareLatency.createTimer()) {
                            if (log.isDebugEnabled()) {
                                log.debug("extract {} worker data and save", worker.getClass().getName());
                            }

                            innerPrepareRequests = worker.buildBatchRequests();

                            worker.endOfRound();
                        } catch (Throwable e) {
                            log.error(e.getMessage(), e);
                        }

                        // Execution stage
                        try (HistogramMetrics.Timer executeLatencyTimer = executeLatency.createTimer()) {
                            if (CollectionUtils.isNotEmpty(innerPrepareRequests)) {
                                batchDAO.flush(innerPrepareRequests);
                            }
                        } catch (Throwable e) {
                            log.error(e.getMessage(), e);
                        }
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            });

            countDownLatch.await();
        } catch (Throwable e) {
            errorCounter.inc();
            log.error(e.getMessage(), e);
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("Persistence data save finish");
            }
        }

        log.debug("Batch persistence duration: {} ms", System.currentTimeMillis() - startTime);
    }
}
