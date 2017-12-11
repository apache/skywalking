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

import org.apache.skywalking.apm.collector.agent.stream.IdAutoIncrement;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ServiceIdCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.storage.dao.IServiceNameRegisterDAO;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorker;
import org.apache.skywalking.apm.collector.stream.worker.base.WorkerException;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceNameRegisterSerialWorker extends AbstractLocalAsyncWorker<ServiceName, ServiceName> {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameRegisterSerialWorker.class);

    private final IServiceNameRegisterDAO serviceNameRegisterDAO;
    private final ServiceIdCacheService serviceIdCacheService;

    public ServiceNameRegisterSerialWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.serviceNameRegisterDAO = getModuleManager().find(StorageModule.NAME).getService(IServiceNameRegisterDAO.class);
        this.serviceIdCacheService = getModuleManager().find(CacheModule.NAME).getService(ServiceIdCacheService.class);
    }

    @Override public int id() {
        return 100;
    }

    @Override protected void onWork(ServiceName serviceName) throws WorkerException {
        logger.debug("register service name: {}, application id: {}", serviceName.getServiceName(), serviceName.getApplicationId());
        int serviceId = serviceIdCacheService.get(serviceName.getApplicationId(), serviceName.getServiceName());
        if (serviceId == 0) {
            ServiceName newServiceName;

            int min = serviceNameRegisterDAO.getMinServiceId();
            if (min == 0) {
                ServiceName noneServiceName = new ServiceName("1");
                noneServiceName.setApplicationId(0);
                noneServiceName.setServiceId(Const.NONE_SERVICE_ID);
                noneServiceName.setServiceName(Const.NONE_SERVICE_NAME);
                serviceNameRegisterDAO.save(noneServiceName);

                newServiceName = new ServiceName("-1");
                newServiceName.setApplicationId(serviceName.getApplicationId());
                newServiceName.setServiceId(-1);
                newServiceName.setServiceName(serviceName.getServiceName());
            } else {
                int max = serviceNameRegisterDAO.getMaxServiceId();
                serviceId = IdAutoIncrement.INSTANCE.increment(min, max);

                newServiceName = new ServiceName(String.valueOf(serviceId));
                newServiceName.setApplicationId(serviceName.getApplicationId());
                newServiceName.setServiceId(serviceId);
                newServiceName.setServiceName(serviceName.getServiceName());
            }
            serviceNameRegisterDAO.save(newServiceName);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceName, ServiceName, ServiceNameRegisterSerialWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<ServiceName> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public ServiceNameRegisterSerialWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceNameRegisterSerialWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
