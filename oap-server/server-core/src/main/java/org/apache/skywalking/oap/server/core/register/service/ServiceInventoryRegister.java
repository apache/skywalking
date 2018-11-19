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

import java.util.Objects;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.register.worker.InventoryProcess;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.slf4j.*;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class ServiceInventoryRegister implements IServiceInventoryRegister {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInventoryRegister.class);

    private final ModuleManager moduleManager;
    private ServiceInventoryCache serviceInventoryCache;

    public ServiceInventoryRegister(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ServiceInventoryCache getServiceInventoryCache() {
        if (isNull(serviceInventoryCache)) {
            this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        }
        return serviceInventoryCache;
    }

    @Override public int getOrCreate(String serviceName) {
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
            serviceInventory.setMappingLastUpdateTime(now);

            InventoryProcess.INSTANCE.in(serviceInventory);
        }
        return serviceId;
    }

    @Override public int getOrCreate(int addressId, String serviceName) {
        int serviceId = getServiceInventoryCache().getServiceId(addressId);

        if (serviceId == Const.NONE) {
            ServiceInventory serviceInventory = new ServiceInventory();
            serviceInventory.setName(serviceName);
            serviceInventory.setAddressId(addressId);
            serviceInventory.setIsAddress(BooleanUtils.TRUE);

            long now = System.currentTimeMillis();
            serviceInventory.setRegisterTime(now);
            serviceInventory.setHeartbeatTime(now);
            serviceInventory.setMappingLastUpdateTime(now);

            InventoryProcess.INSTANCE.in(serviceInventory);
        }
        return serviceId;
    }

    @Override public void heartbeat(int serviceId, long heartBeatTime) {
        ServiceInventory serviceInventory = getServiceInventoryCache().get(serviceId);
        if (Objects.nonNull(serviceInventory)) {
            serviceInventory.setHeartbeatTime(heartBeatTime);

            InventoryProcess.INSTANCE.in(serviceInventory);
        } else {
            logger.warn("Service {} heartbeat, but not found in storage.");
        }
    }

    @Override public void updateMapping(int serviceId, int mappingServiceId) {
        ServiceInventory serviceInventory = getServiceInventoryCache().get(serviceId);
        if (Objects.nonNull(serviceInventory)) {
            serviceInventory = serviceInventory.getClone();
            serviceInventory.setMappingServiceId(mappingServiceId);
            serviceInventory.setMappingLastUpdateTime(System.currentTimeMillis());

            InventoryProcess.INSTANCE.in(serviceInventory);
        } else {
            logger.warn("Service {} mapping update, but not found in storage.");
        }
    }
}
