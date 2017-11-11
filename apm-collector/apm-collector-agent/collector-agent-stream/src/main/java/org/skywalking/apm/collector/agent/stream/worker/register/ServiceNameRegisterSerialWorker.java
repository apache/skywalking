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

import org.skywalking.apm.collector.agent.stream.IdAutoIncrement;
import org.skywalking.apm.collector.cache.CacheServiceManager;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.storage.dao.IServiceNameStreamDAO;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.storage.table.register.ServiceName;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.base.WorkerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceNameRegisterSerialWorker extends AbstractLocalAsyncWorker<ServiceName, ServiceName> {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameRegisterSerialWorker.class);

    public ServiceNameRegisterSerialWorker(DAOService daoService, CacheServiceManager cacheServiceManager) {
        super(daoService, cacheServiceManager);
    }

    @Override public int id() {
        return 0;
    }

    @Override protected void onWork(ServiceName serviceName) throws WorkerException {
        logger.debug("register service name: {}, application id: {}", serviceName.getServiceName(), serviceName.getApplicationId());
        int serviceId = getCacheServiceManager().getServiceIdCacheService().get(serviceName.getApplicationId(), serviceName.getServiceName());
        if (serviceId == 0) {
            IServiceNameStreamDAO dao = (IServiceNameStreamDAO)getDaoService().get(IServiceNameStreamDAO.class);
            ServiceName newServiceName;

            int min = dao.getMinServiceId();
            if (min == 0) {
                ServiceName noneServiceName = new ServiceName("1");
                noneServiceName.setApplicationId(0);
                noneServiceName.setServiceId(Const.NONE_SERVICE_ID);
                noneServiceName.setServiceName(Const.NONE_SERVICE_NAME);
                dao.save(noneServiceName);

                newServiceName = new ServiceName("-1");
                newServiceName.setApplicationId(serviceName.getApplicationId());
                newServiceName.setServiceId(-1);
                newServiceName.setServiceName(serviceName.getServiceName());
            } else {
                int max = dao.getMaxServiceId();
                serviceId = IdAutoIncrement.INSTANCE.increment(min, max);

                newServiceName = new ServiceName(String.valueOf(serviceId));
                newServiceName.setApplicationId(serviceName.getApplicationId());
                newServiceName.setServiceId(serviceId);
                newServiceName.setServiceName(serviceName.getServiceName());
            }
            dao.save(newServiceName);
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceName, ServiceName, ServiceNameRegisterSerialWorker> {
        public Factory(DAOService daoService, CacheServiceManager cacheServiceManager,
            QueueCreatorService<ServiceName> queueCreatorService) {
            super(daoService, cacheServiceManager, queueCreatorService);
        }

        @Override public ServiceNameRegisterSerialWorker workerInstance(DAOService daoService,
            CacheServiceManager cacheServiceManager) {
            return new ServiceNameRegisterSerialWorker(getDaoService(), getCacheServiceManager());
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
