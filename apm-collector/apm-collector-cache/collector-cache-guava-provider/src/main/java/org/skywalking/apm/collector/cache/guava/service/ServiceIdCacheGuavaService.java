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

package org.skywalking.apm.collector.cache.guava.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.skywalking.apm.collector.cache.service.ServiceIdCacheService;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.dao.IServiceNameCacheDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceIdCacheGuavaService implements ServiceIdCacheService {

    private final Logger logger = LoggerFactory.getLogger(ServiceIdCacheGuavaService.class);

    private final Cache<String, Integer> serviceIdCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private final IServiceNameCacheDAO serviceNameCacheDAO;

    public ServiceIdCacheGuavaService(ModuleManager moduleManager) {
        this.serviceNameCacheDAO = moduleManager.find(StorageModule.NAME).getService(IServiceNameCacheDAO.class);
    }

    public int get(int applicationId, String serviceName) {
        int serviceId = 0;
        try {
            serviceId = serviceIdCache.get(applicationId + Const.ID_SPLIT + serviceName, () -> serviceNameCacheDAO.getServiceId(applicationId, serviceName));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceId == 0) {
            serviceId = serviceNameCacheDAO.getServiceId(applicationId, serviceName);
            if (serviceId != 0) {
                serviceIdCache.put(applicationId + Const.ID_SPLIT + serviceName, serviceId);
            }
        }
        return serviceId;
    }
}
