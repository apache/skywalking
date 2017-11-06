/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.remote.grpc.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.skywalking.apm.collector.cluster.ClusterModuleListener;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.remote.RemoteModule;
import org.skywalking.apm.collector.remote.grpc.RemoteModuleGRPCProvider;
import org.skywalking.apm.collector.remote.service.RemoteClient;
import org.skywalking.apm.collector.remote.service.RemoteSenderService;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteSenderService extends ClusterModuleListener implements RemoteSenderService {

    public static final String PATH = "/" + RemoteModule.NAME + "/" + RemoteModuleGRPCProvider.NAME;
    private final GRPCRemoteClientService service;
    private final Map<String, RemoteClient> clientMap;

    @Override public void send(int graph, int nodeId, Data data) {

    }

    public GRPCRemoteSenderService() {
        this.service = new GRPCRemoteClientService();
        this.clientMap = new ConcurrentHashMap<>();
    }

    @Override public String path() {
        return PATH;
    }

    @Override public void serverJoinNotify(String serverAddress) {
        if (!clientMap.containsKey(serverAddress)) {
            String host = serverAddress.split(":")[0];
            int port = Integer.parseInt(serverAddress.split(":")[1]);
            RemoteClient remoteClient = service.create(host, port);
            clientMap.put(serverAddress, remoteClient);
        }
    }

    @Override public void serverQuitNotify(String serverAddress) {
        if (clientMap.containsKey(serverAddress)) {
            clientMap.remove(serverAddress);
        }
    }
}
