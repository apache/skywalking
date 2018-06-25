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

import com.google.common.cache.*;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.IServiceNameCacheDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.slf4j.*;

import static java.util.Objects.*;

/**
 * @author peng-yongsheng
 */
public class ServiceNameCacheGuavaService implements ServiceNameCacheService {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameCacheGuavaService.class);

    private final Cache<Integer, ServiceName> serviceCache = CacheBuilder.newBuilder().maximumSize(1000000).build();

    private final ModuleManager moduleManager;
    private IServiceNameCacheDAO serviceNameCacheDAO;

    public ServiceNameCacheGuavaService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IServiceNameCacheDAO getServiceNameCacheDAO() {
        if (isNull(serviceNameCacheDAO)) {
            this.serviceNameCacheDAO = moduleManager.find(StorageModule.NAME).getService(IServiceNameCacheDAO.class);
        }
        return this.serviceNameCacheDAO;
    }

    public ServiceName get(int serviceId) {
        ServiceName serviceName = null;
        try {
            serviceName = serviceCache.get(serviceId, () -> getServiceNameCacheDAO().get(serviceId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (isNull(serviceName)) {
            serviceName = getServiceNameCacheDAO().get(serviceId);
            if (nonNull(serviceName)) {
                serviceCache.put(serviceId, serviceName);
            } else {
                logger.warn("Service id {} is not in cache and persistent storage.", serviceId);
            }
        }

        return serviceName;
    }
}
