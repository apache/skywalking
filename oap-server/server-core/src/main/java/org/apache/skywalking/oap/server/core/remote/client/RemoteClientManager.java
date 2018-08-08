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

package org.apache.skywalking.oap.server.core.remote.client;

import java.util.*;
import java.util.concurrent.*;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamDataAnnotationContainer;
import org.apache.skywalking.oap.server.core.cluster.*;
import org.apache.skywalking.oap.server.library.module.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class RemoteClientManager implements Service {

    private static final Logger logger = LoggerFactory.getLogger(RemoteClientManager.class);

    private final ModuleManager moduleManager;
    private StreamDataAnnotationContainer indicatorMapper;
    private ClusterNodesQuery clusterNodesQuery;
    private final List<RemoteClient> clientsA;
    private final List<RemoteClient> clientsB;
    private List<RemoteClient> usingClients;

    public RemoteClientManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.clientsA = new LinkedList<>();
        this.clientsB = new LinkedList<>();
        this.usingClients = clientsA;
    }

    public void start() {
        this.clusterNodesQuery = moduleManager.find(ClusterModule.NAME).getService(ClusterNodesQuery.class);
        this.indicatorMapper = moduleManager.find(ClusterModule.NAME).getService(StreamDataAnnotationContainer.class);
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::refresh, 1, 2, TimeUnit.SECONDS);
    }

    private void refresh() {
        List<RemoteInstance> instanceList = clusterNodesQuery.queryRemoteNodes();
        Collections.sort(instanceList);

        if (!compare(instanceList)) {
            buildNewClients(instanceList);
        }
    }

    public List<RemoteClient> getRemoteClient() {
        return usingClients;
    }

    private List<RemoteClient> getFreeClients() {
        if (usingClients.equals(clientsA)) {
            return clientsB;
        } else {
            return clientsA;
        }
    }

    private void switchCurrentClients() {
        if (usingClients.equals(clientsA)) {
            usingClients = clientsB;
        } else {
            usingClients = clientsA;
        }
    }

    private void buildNewClients(List<RemoteInstance> remoteInstances) {
        getFreeClients().clear();

        Map<String, RemoteClient> currentClientsMap = new HashMap<>();
        this.usingClients.forEach(remoteClient -> {
            currentClientsMap.put(address(remoteClient.getHost(), remoteClient.getPort()), remoteClient);
        });

        remoteInstances.forEach(remoteInstance -> {
            String address = address(remoteInstance.getHost(), remoteInstance.getPort());
            RemoteClient client;
            if (currentClientsMap.containsKey(address)) {
                client = currentClientsMap.get(address);
            } else {
                if (remoteInstance.isSelf()) {
                    client = new SelfRemoteClient(moduleManager, remoteInstance.getHost(), remoteInstance.getPort());
                } else {
                    client = new GRPCRemoteClient(indicatorMapper, remoteInstance, 1, 3000);
                }
            }
            getFreeClients().add(client);
        });

        switchCurrentClients();
    }

    private boolean compare(List<RemoteInstance> remoteInstances) {
        if (usingClients.size() == remoteInstances.size()) {
            for (int i = 0; i < usingClients.size(); i++) {
                if (!address(usingClients.get(i).getHost(), usingClients.get(i).getPort()).equals(address(remoteInstances.get(i).getHost(), remoteInstances.get(i).getPort()))) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private String address(String host, int port) {
        return host + String.valueOf(port);
    }
}
