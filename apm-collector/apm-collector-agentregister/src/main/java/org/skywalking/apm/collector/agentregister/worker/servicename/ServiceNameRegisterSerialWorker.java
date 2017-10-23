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

package org.skywalking.apm.collector.agentregister.worker.servicename;

import org.skywalking.apm.collector.agentregister.worker.IdAutoIncrement;
import org.skywalking.apm.collector.agentregister.worker.servicename.dao.IServiceNameDAO;
import org.skywalking.apm.collector.cache.dao.IServiceNameCacheDAO;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.register.ServiceNameDataDefine;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.selector.ForeverFirstSelector;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceNameRegisterSerialWorker extends AbstractLocalAsyncWorker {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameRegisterSerialWorker.class);

    public ServiceNameRegisterSerialWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected void onWork(Object message) throws WorkerException {
        if (message instanceof ServiceNameDataDefine.ServiceName) {
            ServiceNameDataDefine.ServiceName serviceName = (ServiceNameDataDefine.ServiceName)message;
            logger.debug("register service name: {}, application id: {}", serviceName.getServiceName(), serviceName.getApplicationId());

            IServiceNameCacheDAO cacheDao = (IServiceNameCacheDAO)DAOContainer.INSTANCE.get(IServiceNameCacheDAO.class.getName());
            int serviceId = cacheDao.getServiceId(serviceName.getApplicationId(), serviceName.getServiceName());

            IServiceNameDAO dao = (IServiceNameDAO)DAOContainer.INSTANCE.get(IServiceNameDAO.class.getName());
            if (serviceId == 0) {
                int min = dao.getMinServiceId();
                if (min == 0) {
                    ServiceNameDataDefine.ServiceName noneServiceName = new ServiceNameDataDefine.ServiceName("1", Const.NONE_SERVICE_NAME, 0, Const.NONE_SERVICE_ID);
                    dao.save(noneServiceName);

                    serviceName.setServiceId(-1);
                    serviceName.setId("-1");
                } else {
                    int max = dao.getMaxServiceId();
                    serviceId = IdAutoIncrement.INSTANCE.increment(min, max);
                    serviceName.setId(String.valueOf(serviceId));
                    serviceName.setServiceId(serviceId);
                }
                dao.save(serviceName);
            }
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceNameRegisterSerialWorker> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public ServiceNameRegisterSerialWorker workerInstance(ClusterWorkerContext clusterContext) {
            return new ServiceNameRegisterSerialWorker(role(), clusterContext);
        }

        @Override public int queueSize() {
            return 256;
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ServiceNameRegisterSerialWorker.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new ForeverFirstSelector();
        }

        @Override public DataDefine dataDefine() {
            return new ServiceNameDataDefine();
        }
    }
}
