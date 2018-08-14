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

package org.apache.skywalking.apm.collector.analysis.register.provider.register;

import org.apache.skywalking.apm.collector.analysis.register.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.*;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.*;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.register.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class InstanceRegisterSerialWorker extends AbstractLocalAsyncWorker<Instance, Instance> {

    private static final Logger logger = LoggerFactory.getLogger(InstanceRegisterSerialWorker.class);

    private final InstanceCacheService instanceCacheService;
    private final IInstanceRegisterDAO instanceRegisterDAO;

    private InstanceRegisterSerialWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.instanceCacheService = getModuleManager().find(CacheModule.NAME).getService(InstanceCacheService.class);
        this.instanceRegisterDAO = getModuleManager().find(StorageModule.NAME).getService(IInstanceRegisterDAO.class);
    }

    @Override public int id() {
        return WorkerIdDefine.INSTANCE_REGISTER_SERIAL_WORKER;
    }

    @Override protected void onWork(Instance instance) {
        if (logger.isDebugEnabled()) {
            logger.debug("register instance, application id: {}, agentUUID: {}", instance.getApplicationId(), instance.getAgentUUID());
        }

        int instanceId;
        if (BooleanUtils.valueToBoolean(instance.getIsAddress())) {
            instanceId = instanceCacheService.getInstanceIdByAddressId(instance.getApplicationId(), instance.getAddressId());
        } else {
            instanceId = instanceCacheService.getInstanceIdByAgentUUID(instance.getApplicationId(), instance.getAgentUUID());
        }

        if (instanceId == 0) {
            Instance newInstance;

            int min = instanceRegisterDAO.getMinInstanceId();
            int max = instanceRegisterDAO.getMaxInstanceId();
            if (min == 0 && max == 0) {
                Instance userInstance = new Instance();
                userInstance.setId(String.valueOf(Const.NONE_INSTANCE_ID));
                userInstance.setInstanceId(Const.NONE_INSTANCE_ID);
                userInstance.setApplicationId(Const.NONE_APPLICATION_ID);
                userInstance.setApplicationCode(Const.USER_CODE);
                userInstance.setAgentUUID(Const.USER_CODE);
                userInstance.setHeartBeatTime(System.currentTimeMillis());
                userInstance.setOsInfo(Const.EMPTY_STRING);
                userInstance.setRegisterTime(System.currentTimeMillis());
                userInstance.setAddressId(Const.NONE);
                userInstance.setIsAddress(BooleanUtils.FALSE);
                instanceRegisterDAO.save(userInstance);

                newInstance = new Instance();
                newInstance.setId("2");
                newInstance.setInstanceId(2);
                newInstance.setApplicationId(instance.getApplicationId());
                newInstance.setApplicationCode(instance.getApplicationCode());
                newInstance.setAgentUUID(instance.getAgentUUID());
                newInstance.setHeartBeatTime(instance.getHeartBeatTime());
                newInstance.setOsInfo(instance.getOsInfo());
                newInstance.setRegisterTime(instance.getRegisterTime());
                newInstance.setAddressId(instance.getAddressId());
                newInstance.setIsAddress(instance.getIsAddress());
            } else {
                newInstance = new Instance();
                newInstance.setId(String.valueOf(max + 1));
                newInstance.setInstanceId(max + 1);
                newInstance.setApplicationId(instance.getApplicationId());
                newInstance.setApplicationCode(instance.getApplicationCode());
                newInstance.setAgentUUID(instance.getAgentUUID());
                newInstance.setHeartBeatTime(instance.getHeartBeatTime());
                newInstance.setOsInfo(instance.getOsInfo());
                newInstance.setRegisterTime(instance.getRegisterTime());
                newInstance.setAddressId(instance.getAddressId());
                newInstance.setIsAddress(instance.getIsAddress());
            }
            instanceRegisterDAO.save(newInstance);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<Instance, Instance, InstanceRegisterSerialWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public InstanceRegisterSerialWorker workerInstance(ModuleManager moduleManager) {
            return new InstanceRegisterSerialWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
