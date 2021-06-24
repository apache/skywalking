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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
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
    Boolean isStarted = false;
    private final Boolean debug;
    private CounterMetrics errorCounter;
    private HistogramMetrics prepareLatency;
    private HistogramMetrics executeLatency;
    private HistogramMetrics allLatency;
    private long lastTime = System.currentTimeMillis();
    private int syncOperationThreadsNum;
    private int maxSyncoperationNum;
    private ExecutorService executorService;
    private ExecutorService prepareExecutorService;

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
        allLatency = metricsCreator.createHistogramMetric(
            "persistence_timer_bulk_all_latency", "Latency of the all stage in persistence timer",
            MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE
        );

        syncOperationThreadsNum = moduleConfig.getSyncThreads();
        maxSyncoperationNum = moduleConfig.getMaxSyncOperationNum();
        executorService = Executors.newFixedThreadPool(syncOperationThreadsNum);
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

    @VisibleForTesting
    void extractDataAndSave(IBatchDAO batchDAO) {

        if (log.isDebugEnabled()) {
            log.debug("Extract data and save");
        }

        long startTime = System.currentTimeMillis();
        HistogramMetrics.Timer allTimer = allLatency.createTimer();
        // Use `stop` as a control signal to make fail-fast in the persistence process.
        AtomicBoolean stop = new AtomicBoolean(false);

        BlockingBatchQueue<PrepareRequest> prepareQueue = new BlockingBatchQueue(this.maxSyncoperationNum);
        try {
            List<PersistenceWorker<? extends StorageData>> persistenceWorkers = new ArrayList<>();
            persistenceWorkers.addAll(TopNStreamProcessor.getInstance().getPersistentWorkers());
            persistenceWorkers.addAll(MetricsStreamProcessor.getInstance().getPersistentWorkers());

            // CountDownLatch makes sure all prepare threads done eventually.
            CountDownLatch prepareStageCountDownLatch = new CountDownLatch(persistenceWorkers.size());

            /*
                Here we use `this.prepareRequests` as a FIFO queue, for a producer-consumer model.
                The prepareExecutorService is for making the executable requests ready, and batchExecutorService consumes from the queue to flush.
                When the number of metrics produced reaches maxSyncoperationNum or the prepare stage is done,
                the data would flush into the storage.

                When the consumer ends or an exception occurs in the middle, the entire process is completed.
             */

            persistenceWorkers.forEach(worker -> {
                prepareExecutorService.submit(() -> {
                    if (stop.get()) {
                        prepareStageCountDownLatch.countDown();
                        return;
                    }

                    HistogramMetrics.Timer timer = prepareLatency.createTimer();
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("extract {} worker data and save", worker.getClass().getName());
                        }
                        List<PrepareRequest> innerPrepareRequests = new ArrayList<>(5000);
                        worker.buildBatchRequests(innerPrepareRequests);
                        prepareQueue.putMany(innerPrepareRequests);
                        worker.endOfRound(System.currentTimeMillis() - lastTime);
                    } finally {
                        timer.finish();
                        prepareStageCountDownLatch.countDown();
                    }
                });
            });

            List<Future<?>> batchFutures = new ArrayList<>();
            for (int i = 0; i < syncOperationThreadsNum; i++) {
                Future<?> batchFuture = executorService.submit(() -> {
                    // consume the metrics
                    while (!stop.get()) {
                        List<PrepareRequest> partition = prepareQueue.popMany();
                        if (partition.isEmpty()) {
                            break;
                        }
                        HistogramMetrics.Timer executeLatencyTimer = executeLatency.createTimer();
                        try {
                            if (CollectionUtils.isNotEmpty(partition)) {
                                batchDAO.synchronous(partition);
                            }
                        } catch (Throwable e) {
                            log.error(e.getMessage(), e);
                        } finally {
                            executeLatencyTimer.finish();
                        }
                    }
                    return null;
                });
                batchFutures.add(batchFuture);
            }

            // Wait for prepare stage is done.
            prepareStageCountDownLatch.await();
            prepareQueue.noFurtherAppending();
            // Wait for batch stage is done.
            for (Future<?> result : batchFutures) {
                result.get();
            }

        } catch (Throwable e) {
            errorCounter.inc();
            log.error(e.getMessage(), e);
        } finally {

            if (log.isDebugEnabled()) {
                log.debug("Persistence data save finish");
            }

            stop.set(true);
            allTimer.finish();
            lastTime = System.currentTimeMillis();
        }

        if (debug) {
            log.info("Batch persistence duration: {} ms", System.currentTimeMillis() - startTime);
        }
    }

    @RequiredArgsConstructor
    private static class BlockingBatchQueue<E> {

        @Getter
        private final int maxBatchSize;

        @Getter
        private boolean inAppendingMode = true;

        private final List<E> elementData = new ArrayList<>(50000);

        public void putMany(List<E> elements) {
            synchronized (elementData) {
                elementData.addAll(elements);
                if (elementData.size() >= maxBatchSize) {
                    elementData.notify();
                }
            }
        }

        public List<E> pop() throws InterruptedException {
            synchronized (elementData) {
                while (this.elementData.size() < maxBatchSize && inAppendingMode) {
                    elementData.wait(1000);
                }
                if (CollectionUtils.isEmpty(elementData)) {
                    return Collections.EMPTY_LIST;
                }
                List<E> sublist = this.elementData.subList(
                    0, Math.min(maxBatchSize, this.elementData.size()));
                List<E> partition = new ArrayList<>(sublist);
                sublist.clear();
                return partition;
            }
        }

        public void noFurtherAppending() {
            synchronized (elementData) {
                inAppendingMode = false;
                elementData.notify();
            }
        }
    }

}
