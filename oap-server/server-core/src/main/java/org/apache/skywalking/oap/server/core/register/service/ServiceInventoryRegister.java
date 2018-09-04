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

import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.register.worker.InventoryProcess;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class ServiceInventoryRegister implements IServiceInventoryRegister {

    private final ModuleManager moduleManager;
    private ServiceInventoryCache serviceInventoryCache;

    public ServiceInventoryRegister(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ServiceInventoryCache getServiceInventoryCache() {
        if (isNull(serviceInventoryCache)) {
            this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInventoryCache.class);
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

            InventoryProcess.INSTANCE.in(serviceInventory);
        }
        return serviceId;
    }

    @Override public int getOrCreate(int addressId) {
        int serviceId = getServiceInventoryCache().getServiceId(addressId);

        if (serviceId == Const.NONE) {
            ServiceInventory serviceInventory = new ServiceInventory();
            serviceInventory.setName(Const.EMPTY_STRING);
            serviceInventory.setAddressId(addressId);
            serviceInventory.setIsAddress(BooleanUtils.TRUE);

            long now = System.currentTimeMillis();
            serviceInventory.setRegisterTime(now);
            serviceInventory.setHeartbeatTime(now);

            InventoryProcess.INSTANCE.in(serviceInventory);
        }
        return serviceId;
    }
}
