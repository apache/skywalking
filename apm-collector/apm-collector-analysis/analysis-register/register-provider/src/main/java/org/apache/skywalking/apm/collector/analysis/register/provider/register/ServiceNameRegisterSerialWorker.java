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
import org.apache.skywalking.apm.collector.cache.service.ServiceIdCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.register.IServiceNameRegisterDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceNameRegisterSerialWorker extends AbstractLocalAsyncWorker<ServiceName, ServiceName> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNameRegisterSerialWorker.class);

    private final IServiceNameRegisterDAO serviceNameRegisterDAO;
    private final ServiceIdCacheService serviceIdCacheService;

    private ServiceNameRegisterSerialWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.serviceNameRegisterDAO = getModuleManager().find(StorageModule.NAME).getService(IServiceNameRegisterDAO.class);
        this.serviceIdCacheService = getModuleManager().find(CacheModule.NAME).getService(ServiceIdCacheService.class);
    }

    @Override public int id() {
        return WorkerIdDefine.SERVICE_NAME_REGISTER_SERIAL_WORKER;
    }

    @Override protected void onWork(ServiceName serviceName) {
        if (logger.isDebugEnabled()) {
            logger.debug("register service name: {}, application id: {}", serviceName.getServiceName(), serviceName.getApplicationId());
        }

        int serviceId = serviceIdCacheService.get(serviceName.getApplicationId(), serviceName.getSrcSpanType(), serviceName.getServiceName());
        if (serviceId == 0) {
            long now = System.currentTimeMillis();
            ServiceName newServiceName;

            int min = serviceNameRegisterDAO.getMinServiceId();
            if (min == 0) {
                ServiceName noneServiceName = new ServiceName();
                noneServiceName.setId("1");
                noneServiceName.setApplicationId(Const.NONE_APPLICATION_ID);
                noneServiceName.setServiceId(Const.NONE_SERVICE_ID);
                noneServiceName.setServiceName(Const.NONE_SERVICE_NAME);
                noneServiceName.setSrcSpanType(Const.SPAN_TYPE_VIRTUAL);
                noneServiceName.setRegisterTime(now);
                noneServiceName.setHeartBeatTime(now);
                serviceNameRegisterDAO.save(noneServiceName);

                newServiceName = new ServiceName();
                newServiceName.setId("-1");
                newServiceName.setApplicationId(serviceName.getApplicationId());
                newServiceName.setServiceId(-1);
                newServiceName.setSrcSpanType(serviceName.getSrcSpanType());
                newServiceName.setServiceName(serviceName.getServiceName());
                newServiceName.setRegisterTime(now);
                newServiceName.setHeartBeatTime(now);
            } else {
                int max = serviceNameRegisterDAO.getMaxServiceId();
                serviceId = IdAutoIncrement.INSTANCE.increment(min, max);

                newServiceName = new ServiceName();
                newServiceName.setId(String.valueOf(serviceId));
                newServiceName.setApplicationId(serviceName.getApplicationId());
                newServiceName.setServiceId(serviceId);
                newServiceName.setSrcSpanType(serviceName.getSrcSpanType());
                newServiceName.setServiceName(serviceName.getServiceName());
                newServiceName.setRegisterTime(now);
                newServiceName.setHeartBeatTime(now);
            }
            serviceNameRegisterDAO.save(newServiceName);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceName, ServiceName, ServiceNameRegisterSerialWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ServiceNameRegisterSerialWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceNameRegisterSerialWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
