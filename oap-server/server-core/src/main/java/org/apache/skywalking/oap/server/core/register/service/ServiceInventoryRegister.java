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

package org.apache.skywalking.oap.server.core.register.service;

import com.google.gson.JsonObject;
import java.util.Objects;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.register.worker.InventoryStreamProcessor;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.slf4j.*;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class ServiceInventoryRegister implements IServiceInventoryRegister {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInventoryRegister.class);

    private final ModuleDefineHolder moduleDefineHolder;
    private ServiceInventoryCache serviceInventoryCache;

    public ServiceInventoryRegister(ModuleDefineHolder moduleDefineHolder) {
        this.moduleDefineHolder = moduleDefineHolder;
    }

    private ServiceInventoryCache getServiceInventoryCache() {
        if (isNull(serviceInventoryCache)) {
            this.serviceInventoryCache = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        }
        return serviceInventoryCache;
    }

    @Override public int getOrCreate(String serviceName, JsonObject properties) {
        int serviceId = getServiceInventoryCache().getServiceId(serviceName);

        if (serviceId == Const.NONE) {
            ServiceInventory serviceInventory = new ServiceInventory();
            serviceInventory.setName(serviceName);
            serviceInventory.setAddressId(Const.NONE);
            serviceInventory.setIsAddress(BooleanUtils.FALSE);

            long now = System.currentTimeMillis();
            serviceInventory.setRegisterTime(now);
            serviceInventory.setHeartbeatTime(now);
            serviceInventory.setMappingServiceId(Const.NONE);
            serviceInventory.setLastUpdateTime(now);
            serviceInventory.setProperties(properties);

            InventoryStreamProcessor.getInstance().in(serviceInventory);
        }
        return serviceId;
    }

    @Override public int getOrCreate(int addressId, String serviceName, JsonObject properties) {
        int serviceId = getServiceInventoryCache().getServiceId(addressId);

        if (serviceId == Const.NONE) {
            ServiceInventory serviceInventory = new ServiceInventory();
            serviceInventory.setName(serviceName);
            serviceInventory.setAddressId(addressId);
            serviceInventory.setIsAddress(BooleanUtils.TRUE);

            long now = System.currentTimeMillis();
            serviceInventory.setRegisterTime(now);
            serviceInventory.setHeartbeatTime(now);
            serviceInventory.setLastUpdateTime(now);

            InventoryStreamProcessor.getInstance().in(serviceInventory);
        }
        return serviceId;
    }

    @Override public void update(int serviceId, NodeType nodeType, JsonObject properties) {
        ServiceInventory serviceInventory = getServiceInventoryCache().get(serviceId);
        if (Objects.nonNull(serviceInventory)) {
            if (properties != null || !compare(serviceInventory, nodeType)) {
                serviceInventory = serviceInventory.getClone();
                serviceInventory.setServiceNodeType(nodeType);
                serviceInventory.setProperties(properties);
                serviceInventory.setLastUpdateTime(System.currentTimeMillis());

                InventoryStreamProcessor.getInstance().in(serviceInventory);
            }
        } else {
            logger.warn("Service {} nodeType/properties update, but not found in storage.", serviceId);
        }
    }

    @Override public void heartbeat(int serviceId, long heartBeatTime) {
        ServiceInventory serviceInventory = getServiceInventoryCache().get(serviceId);
        if (Objects.nonNull(serviceInventory)) {
            serviceInventory = serviceInventory.getClone();
            serviceInventory.setHeartbeatTime(heartBeatTime);

            InventoryStreamProcessor.getInstance().in(serviceInventory);
        } else {
            logger.warn("Service {} heartbeat, but not found in storage.", serviceId);
        }
    }

    @Override public void updateMapping(int serviceId, int mappingServiceId) {
        ServiceInventory serviceInventory = getServiceInventoryCache().get(serviceId);
        if (Objects.nonNull(serviceInventory)) {
            serviceInventory = serviceInventory.getClone();
            serviceInventory.setMappingServiceId(mappingServiceId);
            serviceInventory.setLastUpdateTime(System.currentTimeMillis());

            InventoryStreamProcessor.getInstance().in(serviceInventory);
        } else {
            logger.warn("Service {} mapping update, but not found in storage.", serviceId);
        }
    }

    private boolean compare(ServiceInventory newServiceInventory, NodeType nodeType) {
        if (Objects.nonNull(newServiceInventory)) {
            return nodeType.equals(newServiceInventory.getServiceNodeType());
        }
        return true;
    }
}
