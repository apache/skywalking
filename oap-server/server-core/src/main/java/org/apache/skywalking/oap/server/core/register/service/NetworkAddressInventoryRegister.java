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
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.register.worker.InventoryProcess;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressInventoryRegister implements INetworkAddressInventoryRegister {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAddressInventoryRegister.class);

    private final ModuleManager moduleManager;
    private ServiceInventoryCache serviceInventoryCache;
    private NetworkAddressInventoryCache networkAddressInventoryCache;
    private IServiceInventoryRegister serviceInventoryRegister;
    private IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;

    public NetworkAddressInventoryRegister(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ServiceInventoryCache getServiceInventoryCache() {
        if (isNull(serviceInventoryCache)) {
            this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        }
        return this.serviceInventoryCache;
    }

    private NetworkAddressInventoryCache getNetworkAddressInventoryCache() {
        if (isNull(networkAddressInventoryCache)) {
            this.networkAddressInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(NetworkAddressInventoryCache.class);
        }
        return this.networkAddressInventoryCache;
    }

    private IServiceInventoryRegister getServiceInventoryRegister() {
        if (isNull(serviceInventoryRegister)) {
            this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
        }
        return this.serviceInventoryRegister;
    }

    private IServiceInstanceInventoryRegister getServiceInstanceInventoryRegister() {
        if (isNull(serviceInstanceInventoryRegister)) {
            this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
        }
        return this.serviceInstanceInventoryRegister;
    }

    @Override public int getOrCreate(String networkAddress, JsonObject properties) {
        int addressId = getNetworkAddressInventoryCache().getAddressId(networkAddress);

        if (addressId != Const.NONE) {
            int serviceId = getServiceInventoryRegister().getOrCreate(addressId, networkAddress, properties);

            if (serviceId != Const.NONE) {
                int serviceInstanceId = getServiceInstanceInventoryRegister().getOrCreate(serviceId, addressId, System.currentTimeMillis());

                if (serviceInstanceId != Const.NONE) {
                    return addressId;
                }
            }
        } else {
            NetworkAddressInventory newNetworkAddress = new NetworkAddressInventory();
            newNetworkAddress.setName(networkAddress);

            long now = System.currentTimeMillis();
            newNetworkAddress.setRegisterTime(now);
            newNetworkAddress.setHeartbeatTime(now);

            InventoryProcess.INSTANCE.in(newNetworkAddress);
        }

        return Const.NONE;
    }

    @Override public int get(String networkAddress) {
        return getNetworkAddressInventoryCache().getAddressId(networkAddress);
    }

    @Override public void heartbeat(int addressId, long heartBeatTime) {
        NetworkAddressInventory networkAddress = getNetworkAddressInventoryCache().get(addressId);
        if (Objects.nonNull(networkAddress)) {
            networkAddress = networkAddress.getClone();
            networkAddress.setHeartbeatTime(heartBeatTime);

            InventoryProcess.INSTANCE.in(networkAddress);
        } else {
            logger.warn("Network getAddress {} heartbeat, but not found in storage.", addressId);
        }
    }

    @Override public void update(int addressId, NodeType nodeType) {
        NetworkAddressInventory networkAddress = getNetworkAddressInventoryCache().get(addressId);

        if (!this.compare(networkAddress, nodeType)) {
            NetworkAddressInventory newNetworkAddress = networkAddress.getClone();
            newNetworkAddress.setNetworkAddressNodeType(nodeType);
            newNetworkAddress.setHeartbeatTime(System.currentTimeMillis());

            InventoryProcess.INSTANCE.in(newNetworkAddress);
        }
    }

    private boolean compare(NetworkAddressInventory newNetworkAddress, NodeType nodeType) {
        if (Objects.nonNull(newNetworkAddress)) {
            return nodeType.equals(newNetworkAddress.getNetworkAddressNodeType());
        }
        return true;
    }
}
