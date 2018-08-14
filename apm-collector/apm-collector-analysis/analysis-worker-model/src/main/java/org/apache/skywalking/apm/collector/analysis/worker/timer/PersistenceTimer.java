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

package org.apache.skywalking.apm.collector.analysis.worker.timer;

import java.util.*;
import java.util.concurrent.*;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.PersistenceWorker;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public enum PersistenceTimer {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(PersistenceTimer.class);

    private Boolean isStarted = false;
    private List<PersistenceWorker> persistenceWorkers = new LinkedList<>();
    private final Boolean debug;

    PersistenceTimer() {
        this.debug = System.getProperty("debug") != null;
    }

    public void start(ModuleManager moduleManager, List<PersistenceWorker> persistenceWorkers) {
        logger.info("persistence timer start");
        this.persistenceWorkers.addAll(persistenceWorkers);
        //TODO timer value config
//        final long timeInterval = EsConfig.Es.Persistence.Timer.VALUE * 1000;
        final long timeInterval = 3;
        IBatchDAO batchDAO = moduleManager.find(StorageModule.NAME).getService(IBatchDAO.class);

        if (!isStarted) {
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                new RunnableWithExceptionProtection(() -> extractDataAndSave(batchDAO, this.persistenceWorkers),
                    t -> logger.error("Extract data and save failure.", t)), 1, timeInterval, TimeUnit.SECONDS);

            this.isStarted = true;
        }
    }

    @SuppressWarnings("unchecked")
    private void extractDataAndSave(IBatchDAO batchDAO, List<PersistenceWorker> persistenceWorkers) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extract data and save");
        }

        long startTime = System.currentTimeMillis();
        try {
            List batchAllCollection = new LinkedList();
            persistenceWorkers.forEach((PersistenceWorker worker) -> {
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
            batchDAO.batchPersistence(batchAllCollection);
        } catch (Throwable e) {
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
