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

package org.skywalking.apm.collector.agent.stream.worker.register;

import org.skywalking.apm.collector.cache.CacheServiceManager;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.storage.dao.IInstanceStreamDAO;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.storage.table.register.Instance;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.base.WorkerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceRegisterSerialWorker extends AbstractLocalAsyncWorker<Instance, Instance> {

    private final Logger logger = LoggerFactory.getLogger(InstanceRegisterSerialWorker.class);

    public InstanceRegisterSerialWorker(DAOService daoService, CacheServiceManager cacheServiceManager) {
        super(daoService, cacheServiceManager);
    }

    @Override public int id() {
        return 0;
    }

    @Override protected void onWork(Instance instance) throws WorkerException {
        logger.debug("register instance, application id: {}, agentUUID: {}", instance.getApplicationId(), instance.getAgentUUID());
        int instanceId = getCacheServiceManager().getInstanceCacheService().getInstanceId(instance.getApplicationId(), instance.getAgentUUID());
        if (instanceId == 0) {
            IInstanceStreamDAO dao = (IInstanceStreamDAO)getDaoService().get(IInstanceStreamDAO.class);
            Instance newInstance;

            int min = dao.getMinInstanceId();
            int max = dao.getMaxInstanceId();
            if (min == 0 && max == 0) {
                newInstance = new Instance("1");
                newInstance.setInstanceId(1);
                newInstance.setApplicationId(instance.getApplicationId());
                newInstance.setAgentUUID(instance.getAgentUUID());
                newInstance.setHeartBeatTime(instance.getHeartBeatTime());
                newInstance.setOsInfo(instance.getOsInfo());
                newInstance.setRegisterTime(instance.getRegisterTime());
            } else {
                newInstance = new Instance(String.valueOf(max + 1));
                newInstance.setInstanceId(max + 1);
                newInstance.setApplicationId(instance.getApplicationId());
                newInstance.setAgentUUID(instance.getAgentUUID());
                newInstance.setHeartBeatTime(instance.getHeartBeatTime());
                newInstance.setOsInfo(instance.getOsInfo());
                newInstance.setRegisterTime(instance.getRegisterTime());
            }
            dao.save(newInstance);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<Instance, Instance, InstanceRegisterSerialWorker> {

        public Factory(DAOService daoService, CacheServiceManager cacheServiceManager,
            QueueCreatorService<Instance> queueCreatorService) {
            super(daoService, cacheServiceManager, queueCreatorService);
        }

        @Override
        public InstanceRegisterSerialWorker workerInstance(DAOService daoService,
            CacheServiceManager cacheServiceManager) {
            return new InstanceRegisterSerialWorker(getDaoService(), getCacheServiceManager());
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
