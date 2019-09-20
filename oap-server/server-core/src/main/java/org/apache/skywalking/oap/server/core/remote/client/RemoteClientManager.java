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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages the connections between OAP servers. There is a task schedule that will automatically query a
 * server list from the cluster module. Such as Zookeeper cluster module or Kubernetes cluster module.
 *
 * @author peng-yongsheng
 */
public class RemoteClientManager implements Service {

    private static final Logger logger = LoggerFactory.getLogger(RemoteClientManager.class);

    private final ModuleDefineHolder moduleDefineHolder;
    private ClusterNodesQuery clusterNodesQuery;
    private final List<RemoteClient> clientsA;
    private final List<RemoteClient> clientsB;
    private volatile List<RemoteClient> usingClients;
    private GaugeMetrics gauge;
    private int remoteTimeout;

    /**
     * Initial the manager for all remote communication clients.
     * @param moduleDefineHolder for looking up other modules
     * @param remoteTimeout for cluster internal communication, in second unit.
     */
    public RemoteClientManager(ModuleDefineHolder moduleDefineHolder, int remoteTimeout) {
        this.moduleDefineHolder = moduleDefineHolder;
        this.clientsA = new LinkedList<>();
        this.clientsB = new LinkedList<>();
        this.usingClients = clientsA;
        this.remoteTimeout = remoteTimeout;
    }

    public void start() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::refresh, 1, 5, TimeUnit.SECONDS);
    }

    /**
     * Query OAP server list from the cluster module and create a new connection for the new node. Make the OAP server
     * orderly because of each of the server will send stream data to each other by hash code.
     */
    void refresh() {
        if (gauge == null) {
            gauge = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class)
                .createGauge("cluster_size", "Cluster size of current oap node",
                    MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        }
        try {
            if (Objects.isNull(clusterNodesQuery)) {
                synchronized (RemoteClientManager.class) {
                    if (Objects.isNull(clusterNodesQuery)) {
                        this.clusterNodesQuery = moduleDefineHolder.find(ClusterModule.NAME).provider().getService(ClusterNodesQuery.class);
                    }
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Refresh remote nodes collection.");
            }

            List<RemoteInstance> instanceList = clusterNodesQuery.queryRemoteNodes();
            instanceList = distinct(instanceList);
            Collections.sort(instanceList);

            gauge.setValue(instanceList.size());

            if (logger.isDebugEnabled()) {
                instanceList.forEach(instance -> logger.debug("Cluster instance: {}", instance.toString()));
            }

            if (!compare(instanceList)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("ReBuilding remote clients.");
                }
                reBuildRemoteClients(instanceList);
            }

            printRemoteClientList();
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }

    /**
     * Print the client list into log for confirm how many clients built.
     */
    private void printRemoteClientList() {
        if (logger.isDebugEnabled()) {
            StringBuilder addresses = new StringBuilder();
            getRemoteClient().forEach(client -> addresses.append(client.getAddress().toString()).append(","));
            logger.debug("Remote client list: {}", addresses);
        }
    }

    /**
     * Because of OAP server register by the UUID which one-to-one mapping with process number. The register information
     * not delete immediately after process shutdown because of there is always happened network fault, not really
     * process shutdown. So, cluster module must wait a few seconds to confirm it. Then there are more than one register
     * information in the cluster.
     *
     * @param instanceList the instances query from cluster module.
     * @return distinct remote instances
     */
    private List<RemoteInstance> distinct(List<RemoteInstance> instanceList) {
        Set<Address> addresses = new HashSet<>();
        List<RemoteInstance> newInstanceList = new ArrayList<>();
        instanceList.forEach(instance -> {
            if (addresses.add(instance.getAddress())) {
                newInstanceList.add(instance);
            }
        });
        return newInstanceList;
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

    /**
     * Compare clients between exist clients and remote instance collection. Move the clients into new client collection
     * which are alive to avoid create a new channel. Shutdown the clients which could not find in cluster config.
     *
     * Create a gRPC client for remote instance except for self-instance.
     *
     * @param remoteInstances Remote instance collection by query cluster config.
     */
    private synchronized void reBuildRemoteClients(List<RemoteInstance> remoteInstances) {
        getFreeClients().clear();

        Map<Address, RemoteClient> remoteClients = new HashMap<>();
        getRemoteClient().forEach(client -> remoteClients.put(client.getAddress(), client));

        Map<Address, Action> tempRemoteClients = new HashMap<>();
        getRemoteClient().forEach(client -> tempRemoteClients.put(client.getAddress(), Action.Close));

        remoteInstances.forEach(remoteInstance -> {
            if (tempRemoteClients.containsKey(remoteInstance.getAddress())) {
                tempRemoteClients.put(remoteInstance.getAddress(), Action.Leave);
            } else {
                tempRemoteClients.put(remoteInstance.getAddress(), Action.Create);
            }
        });

        tempRemoteClients.forEach((address, action) -> {
            switch (action) {
                case Leave:
                    if (remoteClients.containsKey(address)) {
                        getFreeClients().add(remoteClients.get(address));
                    }
                    break;
                case Create:
                    if (address.isSelf()) {
                        RemoteClient client = new SelfRemoteClient(moduleDefineHolder, address);
                        getFreeClients().add(client);
                    } else {
                        RemoteClient client = new GRPCRemoteClient(moduleDefineHolder, address, 1, 3000, remoteTimeout);
                        client.connect();
                        getFreeClients().add(client);
                    }
                    break;
            }
        });

        Collections.sort(getFreeClients());
        switchCurrentClients();

        tempRemoteClients.forEach((address, action) -> {
            if (Action.Close.equals(action) && remoteClients.containsKey(address)) {
                remoteClients.get(address).close();
            }
        });

        getFreeClients().clear();
    }

    private boolean compare(List<RemoteInstance> remoteInstances) {
        if (usingClients.size() == remoteInstances.size()) {
            for (int i = 0; i < usingClients.size(); i++) {
                if (!usingClients.get(i).getAddress().equals(remoteInstances.get(i).getAddress())) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    enum Action {
        Close, Leave, Create
    }
}
