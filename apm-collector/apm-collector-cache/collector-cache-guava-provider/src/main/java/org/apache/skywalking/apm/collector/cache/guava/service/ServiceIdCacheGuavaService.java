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

package org.apache.skywalking.apm.collector.cache.guava.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.skywalking.apm.collector.cache.service.ServiceIdCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.IServiceNameCacheDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class ServiceIdCacheGuavaService implements ServiceIdCacheService {

    private final Logger logger = LoggerFactory.getLogger(ServiceIdCacheGuavaService.class);

    private final Cache<String, Integer> serviceIdCache = CacheBuilder.newBuilder().maximumSize(1000000).build();

    private final ModuleManager moduleManager;
    private IServiceNameCacheDAO serviceNameCacheDAO;

    public ServiceIdCacheGuavaService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IServiceNameCacheDAO getServiceNameCacheDAO() {
        if (isNull(serviceNameCacheDAO)) {
            this.serviceNameCacheDAO = moduleManager.find(StorageModule.NAME).getService(IServiceNameCacheDAO.class);
        }
        return this.serviceNameCacheDAO;
    }

    @Override public int get(int applicationId, int srcSpanType, String serviceName) {
        int serviceId = 0;
        String id = applicationId + Const.ID_SPLIT + srcSpanType + Const.ID_SPLIT + serviceName;
        try {
            serviceId = serviceIdCache.get(id, () -> getServiceNameCacheDAO().getServiceId(applicationId, srcSpanType, serviceName));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceId == 0) {
            serviceId = getServiceNameCacheDAO().getServiceId(applicationId, srcSpanType, serviceName);
            if (serviceId != 0) {
                serviceIdCache.put(id, serviceId);
            }
        }
        return serviceId;
    }
}
