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
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.slf4j.*;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class ServiceInstanceInventoryCache implements Service {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceInventoryCache.class);

    private final ServiceInstanceInventory userServiceInstance;
    private final Cache<Integer, ServiceInstanceInventory> serviceInstanceIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final Cache<String, Integer> serviceInstanceNameCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final Cache<String, Integer> addressIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final ModuleManager moduleManager;
    private IServiceInstanceInventoryCacheDAO cacheDAO;

    public ServiceInstanceInventoryCache(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;

        this.userServiceInstance = new ServiceInstanceInventory();
        this.userServiceInstance.setSequence(Const.USER_INSTANCE_ID);
        this.userServiceInstance.setName(Const.USER_CODE);
        this.userServiceInstance.setServiceId(Const.USER_SERVICE_ID);
        this.userServiceInstance.setIsAddress(BooleanUtils.FALSE);
    }

    private IServiceInstanceInventoryCacheDAO getCacheDAO() {
        if (isNull(cacheDAO)) {
            this.cacheDAO = moduleManager.find(StorageModule.NAME).provider().getService(IServiceInstanceInventoryCacheDAO.class);
        }
        return this.cacheDAO;
    }

    public ServiceInstanceInventory get(int serviceInstanceId) {
        if (Const.USER_INSTANCE_ID == serviceInstanceId) {
            return userServiceInstance;
        }

        ServiceInstanceInventory serviceInstanceInventory = serviceInstanceIdCache.getIfPresent(serviceInstanceId);

        if (Objects.isNull(serviceInstanceInventory)) {
            serviceInstanceInventory = getCacheDAO().get(serviceInstanceId);
            if (Objects.nonNull(serviceInstanceInventory)) {
                serviceInstanceIdCache.put(serviceInstanceId, serviceInstanceInventory);
            }
        }
        return serviceInstanceInventory;
    }

    public int getServiceInstanceId(int serviceId, String uuid) {
        Integer serviceInstanceId = serviceInstanceNameCache.getIfPresent(ServiceInstanceInventory.buildId(serviceId, uuid));

        if (Objects.isNull(serviceInstanceId) || serviceInstanceId == Const.NONE) {
            serviceInstanceId = getCacheDAO().getServiceInstanceId(serviceId, uuid);
            if (serviceId != Const.NONE) {
                serviceInstanceNameCache.put(ServiceInstanceInventory.buildId(serviceId, uuid), serviceInstanceId);
            }
        }
        return serviceInstanceId;
    }

    public int getServiceInstanceId(int serviceId, int addressId) {
        Integer serviceInstanceId = addressIdCache.getIfPresent(ServiceInstanceInventory.buildId(serviceId, addressId));

        if (Objects.isNull(serviceInstanceId) || serviceInstanceId == Const.NONE) {
            serviceInstanceId = getCacheDAO().getServiceInstanceId(serviceId, addressId);
            if (serviceId != Const.NONE) {
                addressIdCache.put(ServiceInstanceInventory.buildId(serviceId, addressId), serviceInstanceId);
            }
        }
        return serviceInstanceId;
    }
}
