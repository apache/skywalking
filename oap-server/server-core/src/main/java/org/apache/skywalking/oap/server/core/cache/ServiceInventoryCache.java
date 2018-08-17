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

package org.apache.skywalking.oap.server.core.cache;

import com.google.common.cache.*;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.module.*;
import org.slf4j.*;

import static java.util.Objects.*;

/**
 * @author peng-yongsheng
 */
public class ServiceInventoryCache implements Service {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInventoryCache.class);

    private final Cache<String, Integer> serviceNameCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();
    private final Cache<String, Integer> addressIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();
    private final Cache<Integer, ServiceInventory> serviceIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();

    private final ModuleManager moduleManager;
    private IServiceInventoryCacheDAO cacheDAO;

    public ServiceInventoryCache(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IServiceInventoryCacheDAO getCacheDAO() {
        if (isNull(cacheDAO)) {
            this.cacheDAO = moduleManager.find(StorageModule.NAME).getService(IServiceInventoryCacheDAO.class);
        }
        return this.cacheDAO;
    }

    public int getServiceId(String serviceName) {
        int serviceId = 0;
        try {
            serviceId = serviceNameCache.get(ServiceInventory.buildId(serviceName), () -> getCacheDAO().getServiceId(serviceName));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceId == 0) {
            serviceId = getCacheDAO().getServiceId(serviceName);
            if (serviceId != 0) {
                serviceNameCache.put(ServiceInventory.buildId(serviceName), serviceId);
            }
        }
        return serviceId;
    }

    public int getServiceId(int addressId) {
        int serviceId = 0;
        try {
            serviceId = addressIdCache.get(ServiceInventory.buildId(addressId), () -> getCacheDAO().getServiceId(addressId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceId == 0) {
            serviceId = getCacheDAO().getServiceId(addressId);
            if (serviceId != 0) {
                addressIdCache.put(ServiceInventory.buildId(addressId), serviceId);
            }
        }
        return serviceId;
    }

    public ServiceInventory get(int serviceId) {
        ServiceInventory serviceInventory = null;
        try {
            serviceInventory = serviceIdCache.get(serviceId, () -> getCacheDAO().get(serviceId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (isNull(serviceInventory)) {
            serviceInventory = getCacheDAO().get(serviceId);
            if (nonNull(serviceInventory)) {
                serviceIdCache.put(serviceId, serviceInventory);
            }
        }
        return serviceInventory;
    }
}
