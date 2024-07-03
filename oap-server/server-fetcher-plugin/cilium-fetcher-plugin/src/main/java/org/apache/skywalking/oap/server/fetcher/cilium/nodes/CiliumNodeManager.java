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

package org.apache.skywalking.oap.server.fetcher.cilium.nodes;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import io.cilium.api.peer.NotifyRequest;
import io.cilium.api.peer.PeerGrpc;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.helpers.LogLog;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterWatcher;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.fetcher.cilium.CiliumFetcherConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.RunnableWithExceptionProtection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class CiliumNodeManager implements ClusterWatcher {
    private static final Executor EXECUTOR = Executors.newCachedThreadPool();

    private final PeerGrpc.PeerBlockingStub peerStub;
    private final ClientBuilder clientBuilder;
    private final ModuleManager moduleManager;
    private final int retrySecond;
    private volatile List<RemoteInstance> remoteInstances;
    private List<CiliumNodeUpdateListener> listeners;
    private ClusterNodesQuery clusterNodesQuery;

    // This is a list of all cilium nodes
    private volatile List<CiliumNode> allNodes;
    // This is a list of cilium nodes that are should be used in current OAP node
    private volatile List<CiliumNode> usingNodes;

    public CiliumNodeManager(ModuleManager moduleManager, ClientBuilder clientBuilder, CiliumFetcherConfig config) {
        this.moduleManager = moduleManager;
        this.clientBuilder = clientBuilder;
        this.peerStub = this.clientBuilder.buildClient(config.getPeerHost(), config.getPeerPort(), PeerGrpc.PeerBlockingStub.class);
        this.allNodes = new ArrayList<>();
        this.usingNodes = new ArrayList<>();
        this.listeners = new ArrayList<>();
        this.retrySecond = config.getFetchFailureRetrySecond();
    }

    public void start() {
        ClusterCoordinator coordinator = this.moduleManager
            .find(ClusterModule.NAME)
            .provider()
            .getService(ClusterCoordinator.class);
        coordinator.registerWatcher(this);
        // init the remote instances
        this.remoteInstances = ImmutableList.copyOf(coordinator.queryRemoteNodes());
        startWatchNodeUpdates();
        startRefreshRemoteNodes();
    }

    public void addListener(CiliumNodeUpdateListener listener) {
        listeners.add(listener);
    }

    private void listenNotified() {
        peerStub.notify(NotifyRequest.newBuilder().build())
            .forEachRemaining(changeNotification -> {
                log.debug("Receive cilium node change notification, name: {}, address: {}, type: {}", changeNotification.getName(),
                    changeNotification.getAddress(), changeNotification.getType());
                switch (changeNotification.getType()) {
                    case PEER_ADDED:
                    case PEER_UPDATED:
                        this.addOrUpdateNode(new CiliumNode(changeNotification.getAddress(), clientBuilder));
                        break;
                    case PEER_DELETED:
                        this.removeNode(new CiliumNode(changeNotification.getAddress(), clientBuilder));
                        break;
                    default:
                        log.error("Unknown cilium node change notification type: {}", changeNotification);
                        break;
                }
            });
    }

    private void startWatchNodeUpdates() {
        EXECUTOR.execute(new RunnableWithExceptionProtection(this::listenNotified, t -> {
            LogLog.error("Cilium node manager listen notified failure.", t);
            try {
                TimeUnit.SECONDS.sleep(this.retrySecond);
            } catch (InterruptedException e) {
                log.error("Failed to sleep for {} seconds.", this.retrySecond, e);
                return;
            }

            startWatchNodeUpdates();
        }));
    }

    private void startRefreshRemoteNodes() {
        Executors.newSingleThreadScheduledExecutor()
            .scheduleWithFixedDelay(new RunnableWithExceptionProtection(this::refreshRemoteNodes, t -> log.error(
                "Scheduled refresh Remote Clients failure.", t)), 1, 10, TimeUnit.SECONDS);
    }

    private void refreshRemoteNodes() {
        if (Objects.isNull(clusterNodesQuery)) {
            this.clusterNodesQuery = moduleManager.find(ClusterModule.NAME)
                .provider()
                .getService(ClusterNodesQuery.class);
        }

        this.onClusterNodesChanged(clusterNodesQuery.queryRemoteNodes());
    }

    private void addOrUpdateNode(CiliumNode node) {
        if (allNodes.contains(node)) {
            allNodes.remove(node);
        }
        allNodes.add(node);
        this.refreshUsingNodes();
    }

    private void removeNode(CiliumNode node) {
        allNodes.remove(node);
        this.refreshUsingNodes();
    }

    void refreshUsingNodes() {
        final List<CiliumNode> shouldUsingNodes = buildShouldUsingNodes();
        log.debug("Trying to rebuilding using cilium nodes, current using nodes count: {}, new using nodes count: {}",
            usingNodes.size(), shouldUsingNodes.size());

        if (log.isDebugEnabled()) {
            shouldUsingNodes.forEach(node -> log.debug("Ready to using cilium node, wait notify: {}", node.getAddress()));
        }

        if (!compare(shouldUsingNodes)) {
            log.info("Rebuilding using cilium nodes, old using nodes count: {}, new using nodes count: {}",
                usingNodes.size(), shouldUsingNodes.size());
            this.reBuildUsingNodes(shouldUsingNodes);
        } else {
            log.debug("No need to rebuild using cilium nodes, old using nodes count: {}, new using nodes count: {}",
                usingNodes.size(), shouldUsingNodes.size());
        }
    }

    private void reBuildUsingNodes(List<CiliumNode> shouldUsingNodes) {
        final Map<String, NodeWithAction> remoteClientCollection =
            this.usingNodes.stream()
                .collect(Collectors.toMap(
                    CiliumNode::getAddress,
                    node -> new NodeWithAction(
                        node, Action.Close)
                ));

        final Map<String, NodeWithAction> latestRemoteClients =
            shouldUsingNodes.stream()
                .collect(Collectors.toMap(
                    CiliumNode::getAddress,
                    remote -> new NodeWithAction(
                        remote, Action.Create)
                ));

        final Set<String> unChangeAddresses = Sets.intersection(
            remoteClientCollection.keySet(), latestRemoteClients.keySet());

        unChangeAddresses.stream()
            .filter(remoteClientCollection::containsKey)
            .forEach(unChangeAddress -> remoteClientCollection.get(unChangeAddress)
                .setAction(Action.Unchanged));

        // make the latestRemoteClients including the new clients only
        unChangeAddresses.forEach(latestRemoteClients::remove);
        remoteClientCollection.putAll(latestRemoteClients);

        final List<CiliumNode> newNodes = new LinkedList<>();
        remoteClientCollection.forEach((address, clientAction) -> {
            switch (clientAction.getAction()) {
                case Unchanged:
                    newNodes.add(clientAction.getNode());
                    break;
                case Create:
                    newNodes.add(clientAction.getNode());
                    notifyListeners(clientAction.getNode(), Action.Create);
                    break;
                case Close:
                    notifyListeners(clientAction.getNode(), Action.Close);
                    clientAction.getNode().close();
                    break;
            }
        });

        newNodes.sort(Comparator.comparing(CiliumNode::getAddress));
        this.usingNodes = ImmutableList.copyOf(newNodes);
    }

    private void notifyListeners(CiliumNode node, Action action) {
        listeners.forEach(listener -> {
            if (action == Action.Create) {
                listener.onNodeAdded(node);
            } else if (action == Action.Close) {
                listener.onNodeDelete(node);
            }
        });
    }

    private void printUsingNodesList() {
        if (!log.isDebugEnabled()) {
            return;
        }
        final String addresses = Joiner.on(", ").join(usingNodes.stream().map(CiliumNode::getAddress).collect(Collectors.toList()));
        log.debug("Current using cilium nodes: {}", addresses);
    }

    private List<CiliumNode> buildShouldUsingNodes() {
        if (CollectionUtils.isEmpty(allNodes) || CollectionUtils.isEmpty(remoteInstances)) {
            log.debug("Found no cilium or backend nodes, skip all nodes, cilium nodes: {}, backend clients: {}",
                allNodes, remoteInstances);
            return ImmutableList.of();
        }
        allNodes.sort(Comparator.comparing(CiliumNode::getAddress));
        final List<RemoteInstance> totalBackendClients = remoteInstances
            .stream().sorted(Comparator.comparing(RemoteInstance::getAddress)).collect(Collectors.toList());
        final int currentNodeIndex = totalBackendClients.indexOf(totalBackendClients.stream()
            .filter(t -> t.getAddress().isSelf()).findFirst().get());
        // if the backend count bigger than cilium node count, we need to
        if (totalBackendClients.size() > allNodes.size()) {
            if (currentNodeIndex >= allNodes.size()) {
                log.debug("Found no cilium nodes for current OAP node, skip all nodes, total cilium nodes: {}, total backend clients: {}, " +
                    "current node index: {}", allNodes.size(), totalBackendClients.size(), currentNodeIndex);
                return ImmutableList.of();
            }
            log.debug("Total cilium nodes: {}, total backend clients: {}, current node index: {}, using cilium node: {}",
                allNodes.size(), totalBackendClients.size(), currentNodeIndex, allNodes.get(currentNodeIndex));
            return ImmutableList.of(allNodes.get(currentNodeIndex));
        }

        final int partNodesCount = allNodes.size() / totalBackendClients.size();
        if (partNodesCount == 0 && currentNodeIndex >= allNodes.size()) {
            log.debug("Found no cilium nodes for current OAP node, skip all nodes, total cilium nodes: {}, total backend clients: {}, " +
                    "current node index: {}", allNodes.size(), totalBackendClients.size(), currentNodeIndex);
            return ImmutableList.of();
        }
        final int startIndex = currentNodeIndex * partNodesCount;
        final int endIndex = currentNodeIndex == totalBackendClients.size() - 1 ? allNodes.size() : (currentNodeIndex + 1) * partNodesCount;
        log.debug("Total cilium nodes: {}, part nodes count: {}, current node index: {}, using nodes part: {} - {}",
            allNodes.size(), partNodesCount, currentNodeIndex, startIndex, endIndex);
        return ImmutableList.copyOf(allNodes.subList(startIndex, endIndex));
    }

    private boolean compare(List<CiliumNode> remoteInstances) {
        if (usingNodes.size() == remoteInstances.size()) {
            for (int i = 0; i < usingNodes.size(); i++) {
                if (!usingNodes.get(i).getAddress().equals(remoteInstances.get(i).getAddress())) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onClusterNodesChanged(List<RemoteInstance> remoteInstances) {
        this.remoteInstances = ImmutableList.copyOf(remoteInstances);
        refreshUsingNodes();
    }

    enum Action {
        Close, Unchanged, Create
    }

    @AllArgsConstructor
    @Getter
    private static class NodeWithAction {
        private final CiliumNode node;
        @Setter
        private Action action;
    }
}
