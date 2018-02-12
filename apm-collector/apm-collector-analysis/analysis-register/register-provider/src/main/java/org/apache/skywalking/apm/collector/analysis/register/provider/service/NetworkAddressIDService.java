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

import org.apache.skywalking.apm.collector.analysis.register.define.AnalysisRegisterModule;
import org.apache.skywalking.apm.collector.analysis.register.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IApplicationIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IInstanceIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.INetworkAddressIDService;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressIDService implements INetworkAddressIDService {

    private final ModuleManager moduleManager;
    private NetworkAddressCacheService networkAddressCacheService;
    private IApplicationIDService applicationIDService;
    private IInstanceIDService instanceIDService;
    private Graph<NetworkAddress> networkAddressGraph;

    public NetworkAddressIDService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private NetworkAddressCacheService getNetworkAddressCacheService() {
        if (ObjectUtils.isEmpty(networkAddressCacheService)) {
            this.networkAddressCacheService = moduleManager.find(CacheModule.NAME).getService(NetworkAddressCacheService.class);
        }
        return this.networkAddressCacheService;
    }

    private IApplicationIDService getApplicationIDService() {
        if (ObjectUtils.isEmpty(applicationIDService)) {
            this.applicationIDService = moduleManager.find(AnalysisRegisterModule.NAME).getService(IApplicationIDService.class);
        }
        return this.applicationIDService;
    }

    private IInstanceIDService getInstanceIDService() {
        if (ObjectUtils.isEmpty(instanceIDService)) {
            this.instanceIDService = moduleManager.find(AnalysisRegisterModule.NAME).getService(IInstanceIDService.class);
        }
        return this.instanceIDService;
    }

    private Graph<NetworkAddress> getNetworkAddressGraph() {
        if (ObjectUtils.isEmpty(networkAddressGraph)) {
            this.networkAddressGraph = GraphManager.INSTANCE.findGraph(GraphIdDefine.NETWORK_ADDRESS_NAME_REGISTER_GRAPH_ID, NetworkAddress.class);
        }
        return this.networkAddressGraph;
    }

    @Override public int getOrCreate(String networkAddress) {
        int addressId = getNetworkAddressCacheService().getAddressId(networkAddress);

        if (addressId != 0) {
            int applicationId = getApplicationIDService().getOrCreateForAddressId(addressId, networkAddress);

            if (applicationId != 0) {
                int instanceId = getInstanceIDService().getOrCreateByAddressId(applicationId, addressId, System.currentTimeMillis());

                if (instanceId != 0) {
                    return addressId;
                }
            }
        } else {
            NetworkAddress newNetworkAddress = new NetworkAddress();
            newNetworkAddress.setId(String.valueOf(Const.NONE));
            newNetworkAddress.setNetworkAddress(networkAddress);
            newNetworkAddress.setSpanLayer(Const.NONE);
            newNetworkAddress.setServerType(Const.NONE);
            newNetworkAddress.setAddressId(Const.NONE);

            getNetworkAddressGraph().start(newNetworkAddress);
        }

        return 0;
    }

    @Override public int get(String networkAddress) {
        return getNetworkAddressCacheService().getAddressId(networkAddress);
    }

    @Override public void update(int addressId, int spanLayer, int serverType) {
        if (!networkAddressCacheService.compare(addressId, spanLayer, serverType)) {
            NetworkAddress newNetworkAddress = new NetworkAddress();
            newNetworkAddress.setId(String.valueOf(addressId));
            newNetworkAddress.setSpanLayer(spanLayer);
            newNetworkAddress.setServerType(serverType);
            newNetworkAddress.setAddressId(addressId);

            getNetworkAddressGraph().start(newNetworkAddress);
        }
    }
}
