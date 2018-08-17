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
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.module.*;
import org.slf4j.*;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class ServiceInstanceInventoryCache implements Service {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceInventoryCache.class);

    private final Cache<Integer, Integer> serviceInstanceIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final Cache<String, Integer> serviceInstanceNameCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final Cache<String, Integer> addressIdCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final ModuleManager moduleManager;
    private IServiceInstanceInventoryCacheDAO cacheDAO;

    public ServiceInstanceInventoryCache(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IServiceInstanceInventoryCacheDAO getCacheDAO() {
        if (isNull(cacheDAO)) {
            this.cacheDAO = moduleManager.find(CoreModule.NAME).getService(IServiceInstanceInventoryCacheDAO.class);
        }
        return this.cacheDAO;
    }

    public int getServiceId(int serviceInstanceId) {
        int serviceId = Const.NONE;
        try {
            serviceId = serviceInstanceIdCache.get(serviceInstanceId, () -> getCacheDAO().getServiceId(serviceInstanceId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceId == Const.NONE) {
            serviceId = getCacheDAO().getServiceId(serviceInstanceId);
            if (serviceId != Const.NONE) {
                serviceInstanceIdCache.put(serviceInstanceId, serviceId);
            }
        }
        return serviceId;
    }

    public int getServiceInstanceId(int serviceId, String serviceInstanceName) {
        int serviceInstanceId = Const.NONE;
        try {
            serviceInstanceId = serviceInstanceNameCache.get(ServiceInstanceInventory.buildId(serviceId, serviceInstanceName), () -> getCacheDAO().getServiceInstanceId(serviceId, serviceInstanceName));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceInstanceId == Const.NONE) {
            serviceInstanceId = getCacheDAO().getServiceInstanceId(serviceId, serviceInstanceName);
            if (serviceId != Const.NONE) {
                serviceInstanceNameCache.put(ServiceInstanceInventory.buildId(serviceId, serviceInstanceName), serviceInstanceId);
            }
        }
        return serviceInstanceId;
    }

    public int getServiceInstanceId(int serviceId, int addressId) {
        int serviceInstanceId = Const.NONE;
        try {
            serviceInstanceId = addressIdCache.get(ServiceInstanceInventory.buildId(serviceId, addressId), () -> getCacheDAO().getServiceInstanceId(serviceId, addressId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (serviceInstanceId == Const.NONE) {
            serviceInstanceId = getCacheDAO().getServiceInstanceId(serviceId, addressId);
            if (serviceId != Const.NONE) {
                addressIdCache.put(ServiceInstanceInventory.buildId(serviceId, addressId), serviceInstanceId);
            }
        }
        return serviceInstanceId;
    }
}
