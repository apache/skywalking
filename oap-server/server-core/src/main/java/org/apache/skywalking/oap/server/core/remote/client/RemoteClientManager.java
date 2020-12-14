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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.server.grpc.ssl.DynamicSslContext;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages the connections between OAP servers. There is a task schedule that will automatically query a
 * server list from the cluster module. Such as Zookeeper cluster module or Kubernetes cluster module.
 */
public class RemoteClientManager implements Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteClientManager.class);

    private final ModuleDefineHolder moduleDefineHolder;
    private DynamicSslContext sslContext;
    private ClusterNodesQuery clusterNodesQuery;
    private volatile List<RemoteClient> usingClients;
    private GaugeMetrics gauge;
    private int remoteTimeout;

    /**
     * Initial the manager for all remote communication clients.
     *
     * @param moduleDefineHolder for looking up other modules
     * @param remoteTimeout      for cluster internal communication, in second unit.
     * @param trustedCAFile      SslContext to verify server certificates.
     */
    public RemoteClientManager(ModuleDefineHolder moduleDefineHolder,
                               int remoteTimeout,
                               String trustedCAFile) {
        this(moduleDefineHolder, remoteTimeout);
        sslContext = DynamicSslContext.forClient(trustedCAFile);
    }

    /**
     * Initial the manager for all remote communication clients.
     *
     * Initial the manager for all remote communication clients.
     *
     * @param moduleDefineHolder for looking up other modules
     * @param remoteTimeout      for cluster internal communication, in second unit.
     */
    public RemoteClientManager(final ModuleDefineHolder moduleDefineHolder, final int remoteTimeout) {
        this.moduleDefineHolder = moduleDefineHolder;
        this.usingClients = ImmutableList.of();
        this.remoteTimeout = remoteTimeout;
    }

    public void start() {
        Optional.ofNullable(sslContext).ifPresent(DynamicSslContext::start);
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::refresh, 1, 5, TimeUnit.SECONDS);
    }

    /**
     * Query OAP server list from the cluster module and create a new connection for the new node. Make the OAP server
     * orderly because of each of the server will send stream data to each other by hash code.
     */
    void refresh() {
        if (gauge == null) {
            gauge = moduleDefineHolder.find(TelemetryModule.NAME)
                                      .provider()
                                      .getService(MetricsCreator.class)
                                      .createGauge(
                                          "cluster_size", "Cluster size of current oap node", MetricsTag.EMPTY_KEY,
                                          MetricsTag.EMPTY_VALUE
                                      );
        }
        try {
            if (Objects.isNull(clusterNodesQuery)) {
                synchronized (RemoteClientManager.class) {
                    if (Objects.isNull(clusterNodesQuery)) {
                        this.clusterNodesQuery = moduleDefineHolder.find(ClusterModule.NAME)
                                                                   .provider()
                                                                   .getService(ClusterNodesQuery.class);
                    }
                }
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Refresh remote nodes collection.");
            }

            List<RemoteInstance> instanceList = clusterNodesQuery.queryRemoteNodes();
            instanceList = distinct(instanceList);
            Collections.sort(instanceList);

            gauge.setValue(instanceList.size());

            if (LOGGER.isDebugEnabled()) {
                instanceList.forEach(instance -> LOGGER.debug("Cluster instance: {}", instance.toString()));
            }

            if (!compare(instanceList)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("ReBuilding remote clients.");
                }
                reBuildRemoteClients(instanceList);
            }

            printRemoteClientList();
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    /**
     * Print the client list into log for confirm how many clients built.
     */
    private void printRemoteClientList() {
        if (LOGGER.isDebugEnabled()) {
            StringBuilder addresses = new StringBuilder();
            this.usingClients.forEach(client -> addresses.append(client.getAddress().toString()).append(","));
            LOGGER.debug("Remote client list: {}", addresses);
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

    /**
     * Compare clients between exist clients and remote instance collection. Move the clients into new client collection
     * which are alive to avoid create a new channel. Shutdown the clients which could not find in cluster config.
     * <p>
     * Create a gRPC client for remote instance except for self-instance.
     *
     * @param remoteInstances Remote instance collection by query cluster config.
     */
    private void reBuildRemoteClients(List<RemoteInstance> remoteInstances) {
        final Map<Address, RemoteClientAction> remoteClientCollection =
            this.usingClients.stream()
                             .collect(Collectors.toMap(
                                 RemoteClient::getAddress,
                                 client -> new RemoteClientAction(
                                     client, Action.Close)
                             ));

        final Map<Address, RemoteClientAction> latestRemoteClients =
            remoteInstances.stream()
                           .collect(Collectors.toMap(
                               RemoteInstance::getAddress,
                               remote -> new RemoteClientAction(
                                   null, Action.Create)
                           ));

        final Set<Address> unChangeAddresses = Sets.intersection(
            remoteClientCollection.keySet(), latestRemoteClients.keySet());

        unChangeAddresses.stream()
                         .filter(remoteClientCollection::containsKey)
                         .forEach(unChangeAddress -> remoteClientCollection.get(unChangeAddress)
                                                                           .setAction(Action.Unchanged));

        // make the latestRemoteClients including the new clients only
        unChangeAddresses.forEach(latestRemoteClients::remove);
        remoteClientCollection.putAll(latestRemoteClients);

        final List<RemoteClient> newRemoteClients = new LinkedList<>();
        remoteClientCollection.forEach((address, clientAction) -> {
            switch (clientAction.getAction()) {
                case Unchanged:
                    newRemoteClients.add(clientAction.getRemoteClient());
                    break;
                case Create:
                    if (address.isSelf()) {
                        RemoteClient client = new SelfRemoteClient(moduleDefineHolder, address);
                        newRemoteClients.add(client);
                    } else {
                        RemoteClient client;
                        client = new GRPCRemoteClient(moduleDefineHolder, address, 1, 3000, remoteTimeout, sslContext);
                        client.connect();
                        newRemoteClients.add(client);
                    }
                    break;
            }
        });

        //for stable ordering for rolling selector
        Collections.sort(newRemoteClients);
        this.usingClients = ImmutableList.copyOf(newRemoteClients);

        remoteClientCollection.values()
                              .stream()
                              .filter(remoteClientAction ->
                                          remoteClientAction.getAction().equals(Action.Close)
                                              && !remoteClientAction.getRemoteClient().getAddress().isSelf()
                              )
                              .forEach(remoteClientAction -> remoteClientAction.getRemoteClient().close());
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
        Close, Unchanged, Create
    }

    @Getter
    @AllArgsConstructor
    static private class RemoteClientAction {
        private RemoteClient remoteClient;

        @Setter
        private Action action;
    }
}
