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
import java.util.Objects;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.slf4j.*;

import static java.util.Objects.*;

/**
 * @author peng-yongsheng
 */
public class ServiceInventoryCache implements Service {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInventoryCache.class);

    private final ServiceInventory userService;
    private final Cache<String, Integer> serviceNameCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();
    private final Cache<String, Integer> addressIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();
    private final Cache<Integer, ServiceInventory> serviceIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(1000).build();

    private final ModuleManager moduleManager;
    private IServiceInventoryCacheDAO cacheDAO;

    public ServiceInventoryCache(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;

        this.userService = new ServiceInventory();
        this.userService.setSequence(Const.USER_SERVICE_ID);
        this.userService.setName(Const.USER_CODE);
        this.userService.setIsAddress(BooleanUtils.FALSE);
    }

    private IServiceInventoryCacheDAO getCacheDAO() {
        if (isNull(cacheDAO)) {
            this.cacheDAO = moduleManager.find(StorageModule.NAME).provider().getService(IServiceInventoryCacheDAO.class);
        }
        return this.cacheDAO;
    }

    public int getServiceId(String serviceName) {
        Integer serviceId = serviceNameCache.getIfPresent(ServiceInventory.buildId(serviceName));

        if (Objects.isNull(serviceId) || serviceId == Const.NONE) {
            serviceId = getCacheDAO().getServiceId(serviceName);
            if (serviceId != Const.NONE) {
                serviceNameCache.put(ServiceInventory.buildId(serviceName), serviceId);
            }
        }
        return serviceId;
    }

    public int getServiceId(int addressId) {
        Integer serviceId = addressIdCache.getIfPresent(ServiceInventory.buildId(addressId));

        if (Objects.isNull(serviceId) || serviceId == Const.NONE) {
            serviceId = getCacheDAO().getServiceId(addressId);
            if (serviceId != Const.NONE) {
                addressIdCache.put(ServiceInventory.buildId(addressId), serviceId);
            }
        }
        return serviceId;
    }

    public ServiceInventory get(int serviceId) {
        if (logger.isDebugEnabled()) {
            logger.debug("Get service by id {} from cache", serviceId);
        }

        if (Const.USER_SERVICE_ID == serviceId) {
            return userService;
        }

        ServiceInventory serviceInventory = serviceIdCache.getIfPresent(serviceId);

        if (isNull(serviceInventory)) {
            serviceInventory = getCacheDAO().get(serviceId);
            if (nonNull(serviceInventory)) {
                serviceIdCache.put(serviceId, serviceInventory);
            }
        }

        if (logger.isDebugEnabled()) {
            if (Objects.isNull(serviceInventory)) {
                logger.debug("service id {} not find in cache.", serviceId);
            }
        }

        return serviceInventory;
    }
}
