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

package org.skywalking.apm.collector.agentregister.servicename;

import org.skywalking.apm.collector.agentregister.worker.servicename.ServiceNameRegisterRemoteWorker;
import org.skywalking.apm.collector.cache.dao.IServiceNameCacheDAO;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.define.register.ServiceNameDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceNameService {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameService.class);

    public int getOrCreate(int applicationId, String serviceName) {
        IServiceNameCacheDAO dao = (IServiceNameCacheDAO)DAOContainer.INSTANCE.get(IServiceNameCacheDAO.class.getName());
        int serviceId = dao.getServiceId(applicationId, serviceName);

        if (serviceId == 0) {
            StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
            ServiceNameDataDefine.ServiceName service = new ServiceNameDataDefine.ServiceName("0", serviceName, applicationId, 0);
            try {
                context.getClusterWorkerContext().lookup(ServiceNameRegisterRemoteWorker.WorkerRole.INSTANCE).tell(service);
            } catch (WorkerNotFoundException | WorkerInvokeException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return serviceId;
    }
}
