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

package org.apache.skywalking.apm.collector.analysis.register.provider.register;

import org.apache.skywalking.apm.collector.analysis.register.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.*;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.register.INetworkAddressRegisterDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressRegisterSerialWorker extends AbstractLocalAsyncWorker<NetworkAddress, NetworkAddress> {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAddressRegisterSerialWorker.class);

    private final INetworkAddressRegisterDAO networkAddressRegisterDAO;
    private final NetworkAddressCacheService networkAddressCacheService;

    private NetworkAddressRegisterSerialWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.networkAddressRegisterDAO = getModuleManager().find(StorageModule.NAME).getService(INetworkAddressRegisterDAO.class);
        this.networkAddressCacheService = getModuleManager().find(CacheModule.NAME).getService(NetworkAddressCacheService.class);
    }

    @Override public int id() {
        return WorkerIdDefine.NETWORK_ADDRESS_REGISTER_SERIAL_WORKER;
    }

    @Override protected void onWork(NetworkAddress networkAddress) {
        if (logger.isDebugEnabled()) {
            logger.debug("register network address, address: {}", networkAddress.getNetworkAddress());
        }

        if (networkAddress.getAddressId() == 0) {
            int addressId = networkAddressCacheService.getAddressId(networkAddress.getNetworkAddress());

            if (addressId == 0) {
                NetworkAddress newNetworkAddress;
                int min = networkAddressRegisterDAO.getMinNetworkAddressId();
                if (min == 0) {
                    newNetworkAddress = new NetworkAddress();
                    newNetworkAddress.setId("-1");
                    newNetworkAddress.setAddressId(-1);
                    newNetworkAddress.setSrcSpanLayer(networkAddress.getSrcSpanLayer());
                    newNetworkAddress.setNetworkAddress(networkAddress.getNetworkAddress());
                } else {
                    int max = networkAddressRegisterDAO.getMaxNetworkAddressId();
                    addressId = IdAutoIncrement.INSTANCE.increment(min, max);

                    newNetworkAddress = new NetworkAddress();
                    newNetworkAddress.setId(String.valueOf(addressId));
                    newNetworkAddress.setAddressId(addressId);
                    newNetworkAddress.setSrcSpanLayer(networkAddress.getSrcSpanLayer());
                    newNetworkAddress.setNetworkAddress(networkAddress.getNetworkAddress());
                }
                networkAddressRegisterDAO.save(newNetworkAddress);
            }
        } else {
            networkAddressRegisterDAO.update(networkAddress.getId(), networkAddress.getSrcSpanLayer(), networkAddress.getServerType());
        }
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<NetworkAddress, NetworkAddress, NetworkAddressRegisterSerialWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public NetworkAddressRegisterSerialWorker workerInstance(ModuleManager moduleManager) {
            return new NetworkAddressRegisterSerialWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
