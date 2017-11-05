/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.stream.timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.stream.worker.base.WorkerException;
import org.skywalking.apm.collector.stream.worker.impl.FlushAndSwitch;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorkerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class PersistenceTimer {

    private final Logger logger = LoggerFactory.getLogger(PersistenceTimer.class);

    public void start(DAOService daoService) {
        logger.info("persistence timer start");
        //TODO timer value config
//        final long timeInterval = EsConfig.Es.Persistence.Timer.VALUE * 1000;
        final long timeInterval = 3;
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> extractDataAndSave(daoService), 1, timeInterval, TimeUnit.SECONDS);
    }

    private void extractDataAndSave(DAOService daoService) {
        try {
            List<PersistenceWorker> workers = PersistenceWorkerContainer.INSTANCE.getPersistenceWorkers();
            List batchAllCollection = new ArrayList<>();
            workers.forEach((PersistenceWorker worker) -> {
                logger.debug("extract {} worker data and save", worker.getRole().roleName());
                try {
                    worker.allocateJob(new FlushAndSwitch());
                    List<?> batchCollection = worker.buildBatchCollection();
                    logger.debug("extract {} worker data size: {}", worker.getRole().roleName(), batchCollection.size());
                    batchAllCollection.addAll(batchCollection);
                } catch (WorkerException e) {
                    logger.error(e.getMessage(), e);
                }
            });

            IBatchDAO dao = (IBatchDAO)daoService.get(IBatchDAO.class);
            dao.batchPersistence(batchAllCollection);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            logger.debug("persistence data save finish");
        }
    }
}
