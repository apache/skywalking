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


package org.apache.skywalking.apm.collector.agent.stream.worker.register;

import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorker;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.stream.worker.base.WorkerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceRegisterSerialWorker extends AbstractLocalAsyncWorker<Instance, Instance> {

    private final Logger logger = LoggerFactory.getLogger(InstanceRegisterSerialWorker.class);

    private final InstanceCacheService instanceCacheService;
    private final IInstanceRegisterDAO instanceRegisterDAO;

    public InstanceRegisterSerialWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.instanceCacheService = getModuleManager().find(CacheModule.NAME).getService(InstanceCacheService.class);
        this.instanceRegisterDAO = getModuleManager().find(StorageModule.NAME).getService(IInstanceRegisterDAO.class);
    }

    @Override public int id() {
        return 102;
    }

    @Override protected void onWork(Instance instance) throws WorkerException {
        logger.debug("register instance, application id: {}, agentUUID: {}", instance.getApplicationId(), instance.getAgentUUID());
        int instanceId = instanceCacheService.getInstanceId(instance.getApplicationId(), instance.getAgentUUID());
        if (instanceId == 0) {
            Instance newInstance;

            int min = instanceRegisterDAO.getMinInstanceId();
            int max = instanceRegisterDAO.getMaxInstanceId();
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
            instanceRegisterDAO.save(newInstance);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<Instance, Instance, InstanceRegisterSerialWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<Instance> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public InstanceRegisterSerialWorker workerInstance(ModuleManager moduleManager) {
            return new InstanceRegisterSerialWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
