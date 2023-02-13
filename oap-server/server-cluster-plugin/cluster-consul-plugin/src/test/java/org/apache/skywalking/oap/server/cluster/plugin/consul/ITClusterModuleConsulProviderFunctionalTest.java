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

package org.apache.skywalking.oap.server.cluster.plugin.consul;

import com.google.common.base.Strings;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.ClusterWatcher;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ExtendWith(MockitoExtension.class)
public class ITClusterModuleConsulProviderFunctionalTest {

    private String consulAddress;

    @Container
    public final GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse("consul:0.9"))
            .waitingFor(Wait.forLogMessage(".*Synced node info.*", 1))
            .withCommand("agent", "-server", "-bootstrap-expect=1", "-client=0.0.0.0")
            .withExposedPorts(8500);

    @Mock
    private ModuleManager moduleManager;
    @Mock
    private NoneTelemetryProvider telemetryProvider;

    @BeforeEach
    public void before() {
        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
                .thenReturn(new MetricsCreatorNoop());
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);
        consulAddress = container.getHost() + ":" + container.getMappedPort(8500);
    }

    @Test
    public void registerRemote() throws Exception {
        final String serviceName = "register_remote";
        ModuleProvider provider = createProvider(serviceName);

        Address selfAddress = new Address("127.0.0.1", 1000, true);

        RemoteInstance instance = new RemoteInstance(selfAddress);
        ClusterCoordinator coordinator = getClusterCoordinator(provider);
        ClusterMockWatcher watcher = new ClusterMockWatcher();
        coordinator.registerWatcher(watcher);
        coordinator.start();
        coordinator.registerRemote(instance);

        List<RemoteInstance> remoteInstances = notifiedRemoteNodes(watcher, 1);
        assertEquals(1, remoteInstances.size());
        assertEquals(1,  queryRemoteNodes(provider, 1).size());

        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(selfAddress, queryAddress);
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfInternal() throws Exception {
        final String serviceName = "register_remote_internal";
        ModuleProvider provider = createProvider(serviceName, "127.0.1.2", 1001);

        Address selfAddress = new Address("127.0.0.2", 1002, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        ClusterCoordinator coordinator = getClusterCoordinator(provider);
        ClusterMockWatcher watcher = new ClusterMockWatcher();
        coordinator.registerWatcher(watcher);
        coordinator.start();
        coordinator.registerRemote(instance);

        List<RemoteInstance> remoteInstances = notifiedRemoteNodes(watcher, 1);
        assertEquals(1, remoteInstances.size());
        assertEquals(1,  queryRemoteNodes(provider, 1).size());

        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals("127.0.1.2", queryAddress.getHost());
        assertEquals(1001, queryAddress.getPort());
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfReceiver() throws Exception {
        final String serviceName = "register_remote_receiver";
        ModuleProvider providerA = createProvider(serviceName);
        ModuleProvider providerB = createProvider(serviceName);
        ClusterCoordinator coordinatorA = getClusterCoordinator(providerA);

        ClusterCoordinator coordinatorB = getClusterCoordinator(providerB);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);
        coordinatorB.start();
        // Mixed or Aggregator
        Address selfAddress = new Address("127.0.0.3", 1003, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        coordinatorA.start();
        coordinatorA.registerRemote(instance);

        // Receiver
        List<RemoteInstance> remoteInstances = notifiedRemoteNodes(watcherB, 1);
        assertEquals(1, remoteInstances.size());
        assertEquals(1,  queryRemoteNodes(providerB, 1).size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(selfAddress, queryAddress);
        assertFalse(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfCluster() throws Exception {
        final String serviceName = "register_remote_cluster";
        ModuleProvider providerA = createProvider(serviceName);
        ModuleProvider providerB = createProvider(serviceName);
        ClusterCoordinator coordinatorA = getClusterCoordinator(providerA);
        ClusterMockWatcher watcherA = new ClusterMockWatcher();
        coordinatorA.registerWatcher(watcherA);
        coordinatorA.start();
        ClusterCoordinator coordinatorB = getClusterCoordinator(providerB);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);
        coordinatorB.start();

        Address addressA = new Address("127.0.0.4", 1004, true);
        Address addressB = new Address("127.0.0.5", 1005, true);

        RemoteInstance instanceA = new RemoteInstance(addressA);
        RemoteInstance instanceB = new RemoteInstance(addressB);

        coordinatorA.registerRemote(instanceA);
        coordinatorB.registerRemote(instanceB);

        List<RemoteInstance> remoteInstancesOfA = notifiedRemoteNodes(watcherA, 2);
        validateServiceInstance(addressA, addressB, remoteInstancesOfA);
        assertEquals(2,  queryRemoteNodes(providerA, 2).size());

        List<RemoteInstance> remoteInstancesOfB = notifiedRemoteNodes(watcherB, 2);
        validateServiceInstance(addressB, addressA, remoteInstancesOfB);
        assertEquals(2,  queryRemoteNodes(providerB, 2).size());
    }

    @Test
    public void unregisterRemoteOfCluster() throws Exception {
        final String serviceName = "unregister_remote_cluster";
        ModuleProvider providerA = createProvider(serviceName);
        ModuleProvider providerB = createProvider(serviceName);
        ClusterCoordinator coordinatorA = getClusterCoordinator(providerA);
        ClusterMockWatcher watcherA = new ClusterMockWatcher();
        coordinatorA.registerWatcher(watcherA);
        coordinatorA.start();
        ClusterCoordinator coordinatorB = getClusterCoordinator(providerB);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);
        coordinatorB.start();

        Address addressA = new Address("127.0.0.6", 1006, true);
        Address addressB = new Address("127.0.0.7", 1007, true);

        RemoteInstance instanceA = new RemoteInstance(addressA);
        RemoteInstance instanceB = new RemoteInstance(addressB);

        coordinatorA.registerRemote(instanceA);
        coordinatorB.registerRemote(instanceB);

        List<RemoteInstance> remoteInstancesOfA = notifiedRemoteNodes(watcherA, 2);
        validateServiceInstance(addressA, addressB, remoteInstancesOfA);
        assertEquals(2,  queryRemoteNodes(providerA, 2).size());

        List<RemoteInstance> remoteInstancesOfB = notifiedRemoteNodes(watcherB, 2);
        validateServiceInstance(addressB, addressA, remoteInstancesOfB);
        assertEquals(2,  queryRemoteNodes(providerB, 2).size());

        // unregister A
        Consul client = Whitebox.getInternalState(providerA, "client");
        AgentClient agentClient = client.agentClient();
        agentClient.deregister(instanceA.getAddress().toString());

        // only B
        remoteInstancesOfB = notifiedRemoteNodes(watcherB, 1, 120);
        assertEquals(1, remoteInstancesOfB.size());
        assertEquals(1,  queryRemoteNodes(providerB, 1).size());
        Address address = remoteInstancesOfB.get(0).getAddress();
        assertEquals(address, addressB);
        assertTrue(addressB.isSelf());
    }

    private ClusterModuleConsulProvider createProvider(String serviceName) throws Exception {
        return createProvider(serviceName, null, 0);
    }

    private ClusterModuleConsulProvider createProvider(String serviceName, String internalComHost,
        int internalComPort) throws Exception {
        ClusterModuleConsulProvider provider = new ClusterModuleConsulProvider();

        ClusterModuleConsulConfig config = new ClusterModuleConsulConfig();
        provider.newConfigCreator().onInitialized(config);

        config.setHostPort(consulAddress);
        config.setServiceName(serviceName);

        if (!StringUtil.isEmpty(internalComHost)) {
            config.setInternalComHost(internalComHost);
        }

        if (internalComPort > 0) {
            config.setInternalComPort(internalComPort);
        }
        provider.setManager(moduleManager);
        provider.prepare();
        provider.start();
        provider.notifyAfterCompleted();

        return provider;
    }

    private boolean needUsingInternalAddr(ClusterModuleConsulConfig config) {
        return !Strings.isNullOrEmpty(config.getInternalComHost()) && config.getInternalComPort() > 0;
    }

    private ClusterCoordinator getClusterCoordinator(ModuleProvider provider) {
        return provider.getService(ClusterCoordinator.class);
    }

    private ClusterRegister getClusterRegister(ModuleProvider provider) {
        return provider.getService(ClusterRegister.class);
    }

    private ClusterNodesQuery getClusterNodesQuery(ModuleProvider provider) {
        return provider.getService(ClusterNodesQuery.class);
    }

    private List<RemoteInstance> notifiedRemoteNodes(ClusterMockWatcher watcher, int goals)
        throws InterruptedException {
        return notifiedRemoteNodes(watcher, goals, 20);
    }

    private List<RemoteInstance> notifiedRemoteNodes(ClusterMockWatcher watcher, int goals,
                                                     int cyclic) throws InterruptedException {
        do {
            List<RemoteInstance> instances = watcher.getRemoteInstances();
            if (instances.size() == goals) {
                return instances;
            } else {
                Thread.sleep(1000);
            }
        }
        while (--cyclic > 0);
        return Collections.emptyList();
    }

    private List<RemoteInstance> queryRemoteNodes(ModuleProvider provider, int goals) throws InterruptedException {
        return queryRemoteNodes(provider, goals, 20);
    }

    private List<RemoteInstance> queryRemoteNodes(ModuleProvider provider, int goals,
        int cyclic) throws InterruptedException {
        do {
            List<RemoteInstance> instances = getClusterNodesQuery(provider).queryRemoteNodes();
            if (instances.size() == goals) {
                return instances;
            } else {
                Thread.sleep(1000);
            }
        }
        while (--cyclic > 0);
        return Collections.emptyList();
    }

    private void validateServiceInstance(Address selfAddress, Address otherAddress, List<RemoteInstance> queryResult) {
        assertEquals(2, queryResult.size());

        boolean selfExist = false, otherExist = false;

        for (RemoteInstance instance : queryResult) {
            Address queryAddress = instance.getAddress();
            if (queryAddress.equals(selfAddress) && queryAddress.isSelf()) {
                selfExist = true;
            } else if (queryAddress.equals(otherAddress) && !queryAddress.isSelf()) {
                otherExist = true;
            }
        }

        assertTrue(selfExist);
        assertTrue(otherExist);
    }

    class ClusterMockWatcher implements ClusterWatcher {
        @Getter
        private List<RemoteInstance> remoteInstances = new ArrayList<>();

        @Override
        public void onClusterNodesChanged(final List<RemoteInstance> remoteInstances) {
            this.remoteInstances = remoteInstances;
        }
    }
}
