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

package org.apache.skywalking.apm.collector.cache.caffeine.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.IServiceNameCacheDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public class ServiceNameCacheCaffeineService implements ServiceNameCacheService {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameCacheCaffeineService.class);

    private final Cache<Integer, ServiceName> serviceCache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).initialCapacity(100).maximumSize(10000).build();

    private final ModuleManager moduleManager;
    private IServiceNameCacheDAO serviceNameCacheDAO;

    public ServiceNameCacheCaffeineService(ModuleManager moduleManager) {
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
            serviceName = serviceCache.get(serviceId, key -> getServiceNameCacheDAO().get(key));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (isNull(serviceName)) {
            serviceName = getServiceNameCacheDAO().get(serviceId);
            if (nonNull(serviceName)) {
                serviceCache.put(serviceId, serviceName);
            }
        }

        return serviceName;
    }
}
