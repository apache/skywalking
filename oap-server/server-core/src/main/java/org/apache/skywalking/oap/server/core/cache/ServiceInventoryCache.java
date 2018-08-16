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

    private final Cache<String, Integer> idCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();
    private final Cache<Integer, ServiceInventory> sequenceCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();
    private final Cache<Integer, Integer> addressIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();

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

    public int get(String serviceName) {
        int serviceId = 0;
        try {
            serviceId = idCache.get(serviceName, () -> getCacheDAO().get(serviceName));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceId == 0) {
            serviceId = getCacheDAO().get(serviceName);
            if (serviceId != 0) {
                idCache.put(serviceName, serviceId);
            }
        }
        return serviceId;
    }

    public ServiceInventory get(int serviceId) {
        ServiceInventory serviceInventory = null;
        try {
            serviceInventory = sequenceCache.get(serviceId, () -> getCacheDAO().get(serviceId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (isNull(serviceInventory)) {
            serviceInventory = getCacheDAO().get(serviceId);
            if (nonNull(serviceInventory)) {
                sequenceCache.put(serviceId, serviceInventory);
            }
        }
        return serviceInventory;
    }

    public int getServiceIdByAddressId(int addressId) {
        int serviceId = 0;
        try {
            serviceId = addressIdCache.get(addressId, () -> getCacheDAO().getServiceIdByAddressId(addressId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceId == 0) {
            serviceId = getCacheDAO().getServiceIdByAddressId(addressId);
            if (serviceId != 0) {
                addressIdCache.put(addressId, serviceId);
            }
        }
        return serviceId;
    }
}
