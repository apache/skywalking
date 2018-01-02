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

package org.apache.skywalking.apm.collector.analysis.register.provider.service;

import org.apache.skywalking.apm.collector.analysis.register.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.register.define.service.INetworkAddressIDService;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressIDService implements INetworkAddressIDService {

    private final Logger logger = LoggerFactory.getLogger(NetworkAddressIDService.class);

    private final ModuleManager moduleManager;
    private NetworkAddressCacheService networkAddressCacheService;
    private Graph<NetworkAddress> networkAddressGraph;

    public NetworkAddressIDService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private NetworkAddressCacheService getNetworkAddressCacheService() {
        if (ObjectUtils.isEmpty(networkAddressCacheService)) {
            networkAddressCacheService = moduleManager.find(CacheModule.NAME).getService(NetworkAddressCacheService.class);
        }
        return networkAddressCacheService;
    }

    private Graph<NetworkAddress> getNetworkAddressGraph() {
        if (ObjectUtils.isEmpty(networkAddressGraph)) {
            this.networkAddressGraph = GraphManager.INSTANCE.findGraph(GraphIdDefine.NETWORK_ADDRESS_NAME_REGISTER_GRAPH_ID, NetworkAddress.class);
        }
        return networkAddressGraph;
    }

    @Override public int getOrCreate(String networkAddress) {
        int addressId = getNetworkAddressCacheService().getAddressId(networkAddress);

        if (addressId == 0) {
            NetworkAddress newNetworkAddress = new NetworkAddress("0");
            newNetworkAddress.setNetworkAddress(networkAddress);
            newNetworkAddress.setAddressId(0);

            getNetworkAddressGraph().start(newNetworkAddress);
        }
        return addressId;
    }
}
