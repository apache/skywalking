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

import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.worker.InventoryProcess;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.slf4j.*;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class ServiceInstanceInventoryRegister implements IServiceInstanceInventoryRegister {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceInventoryRegister.class);

    private final ModuleManager moduleManager;
    private IServiceInstanceInventoryCacheDAO cacheDAO;

    public ServiceInstanceInventoryRegister(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IServiceInstanceInventoryCacheDAO getCacheDAO() {
        if (isNull(cacheDAO)) {
            cacheDAO = moduleManager.find(StorageModule.NAME).getService(IServiceInstanceInventoryCacheDAO.class);
        }
        return cacheDAO;
    }

    @Override public int getOrCreate(int serviceId, String serviceInstanceName, long registerTime,
        ServiceInstanceInventory.AgentOsInfo osInfo) {
        if (logger.isDebugEnabled()) {
            logger.debug("Get or create service instance by service instance name, service id: {}, service instance name: {}, registerTime: {}", serviceId, serviceInstanceName, registerTime);
        }

        int serviceInstanceId = getCacheDAO().getServiceInstanceId(serviceId, serviceInstanceName);

        if (serviceInstanceId == Const.NONE) {
            ServiceInstanceInventory serviceInstanceInventory = new ServiceInstanceInventory();
            serviceInstanceInventory.setServiceId(serviceId);
            serviceInstanceInventory.setName(serviceInstanceName);
            serviceInstanceInventory.setIsAddress(BooleanUtils.FALSE);
            serviceInstanceInventory.setAddressId(Const.NONE);

            serviceInstanceInventory.setRegisterTime(registerTime);
            serviceInstanceInventory.setHeartbeatTime(registerTime);

            serviceInstanceInventory.setOsName(osInfo.getOsName());
            serviceInstanceInventory.setHostName(osInfo.getHostname());
            serviceInstanceInventory.setProcessNo(osInfo.getProcessNo());
            serviceInstanceInventory.setIpv4s(ServiceInstanceInventory.AgentOsInfo.ipv4sSerialize(osInfo.getIpv4s()));

            InventoryProcess.INSTANCE.in(serviceInstanceInventory);
        }
        return serviceInstanceId;
    }

    @Override public int getOrCreate(int serviceId, int addressId, long registerTime) {
        if (logger.isDebugEnabled()) {
            logger.debug("get or create service instance by address id, service id: {}, address id: {}, registerTime: {}", serviceId, addressId, registerTime);
        }

        int serviceInstanceId = getCacheDAO().getServiceInstanceId(serviceId, addressId);

        if (serviceInstanceId == Const.NONE) {
            ServiceInstanceInventory serviceInstanceInventory = new ServiceInstanceInventory();
            serviceInstanceInventory.setServiceId(serviceId);
            serviceInstanceInventory.setName(Const.EMPTY_STRING);
            serviceInstanceInventory.setIsAddress(BooleanUtils.TRUE);
            serviceInstanceInventory.setAddressId(addressId);

            serviceInstanceInventory.setRegisterTime(registerTime);
            serviceInstanceInventory.setHeartbeatTime(registerTime);

            InventoryProcess.INSTANCE.in(serviceInstanceInventory);
        }
        return serviceInstanceId;
    }
}
