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

package org.apache.skywalking.oap.server.cluster.plugin.zookeeper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.apache.curator.x.discovery.ServiceDiscovery;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.net.ssl.*", "javax.security.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
public class ITClusterModuleZookeeperProviderFunctionalTest {

    private String zkAddress;

    @Rule
    public final GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse("zookeeper:3.5"))
            .waitingFor(Wait.forLogMessage(".*binding to port.*", 1));

    @Mock
    private ModuleManager moduleManager;
    @Mock
    private NoneTelemetryProvider telemetryProvider;

    @Before
    public void init() {
        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
                .thenReturn(new MetricsCreatorNoop());
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);
        zkAddress = container.getHost() + ":" + container.getMappedPort(2181);
    }

    @Test
    public void registerRemote() throws Exception {
        final String namespace = "register_remote";
        ModuleProvider provider = createProvider(namespace);

        Address selfAddress = new Address("127.0.0.1", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        ClusterCoordinator coordinator = getClusterCoordinator(provider);
        ClusterMockWatcher watcher = new ClusterMockWatcher();
        coordinator.registerWatcher(watcher);
        coordinator.startCoordinator();
        coordinator.registerRemote(instance);

        List<RemoteInstance> remoteInstances = notifiedRemoteNodes(watcher, 1);
        assertEquals(1, remoteInstances.size());
        assertEquals(1, queryRemoteNodes(provider, 1).size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(selfAddress, queryAddress);
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfInternal() throws Exception {
        final String namespace = "register_remote_internal";
        ModuleProvider provider = createProvider(namespace, "127.0.1.2", 1000);

        Address selfAddress = new Address("127.0.0.2", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        ClusterCoordinator coordinator = getClusterCoordinator(provider);
        ClusterMockWatcher watcher = new ClusterMockWatcher();
        coordinator.registerWatcher(watcher);
        coordinator.startCoordinator();
        coordinator.registerRemote(instance);

        List<RemoteInstance> remoteInstances = notifiedRemoteNodes(watcher, 1);

        assertEquals(1, remoteInstances.size());
        assertEquals(1, queryRemoteNodes(provider, 1).size());

        assertEquals(1, remoteInstances.size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals("127.0.1.2", queryAddress.getHost());
        assertEquals(1000, queryAddress.getPort());
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfReceiver() throws Exception {
        final String namespace = "register_remote_receiver";
        ModuleProvider providerA = createProvider(namespace);
        ModuleProvider providerB = createProvider(namespace);
        ClusterCoordinator coordinatorA = getClusterCoordinator(providerA);
        ClusterCoordinator coordinatorB = getClusterCoordinator(providerB);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);
        coordinatorB.startCoordinator();
        // Mixed or Aggregator
        Address selfAddress = new Address("127.0.0.3", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        coordinatorA.registerRemote(instance);

        // Receiver
        List<RemoteInstance> remoteInstances = notifiedRemoteNodes(watcherB, 1);
        assertEquals(1, remoteInstances.size());
        assertEquals(1, queryRemoteNodes(providerB, 1).size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(selfAddress, queryAddress);
        assertFalse(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfCluster() throws Exception {
        final String namespace = "register_remote_cluster";
        ModuleProvider providerA = createProvider(namespace);
        ModuleProvider providerB = createProvider(namespace);
        ClusterCoordinator coordinatorA = getClusterCoordinator(providerA);
        ClusterMockWatcher watcherA = new ClusterMockWatcher();
        coordinatorA.registerWatcher(watcherA);
        coordinatorA.startCoordinator();
        ClusterCoordinator coordinatorB = getClusterCoordinator(providerB);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);
        coordinatorB.startCoordinator();

        Address addressA = new Address("127.0.0.4", 1000, true);
        Address addressB = new Address("127.0.0.5", 1000, true);
        RemoteInstance instanceA = new RemoteInstance(addressA);
        RemoteInstance instanceB = new RemoteInstance(addressB);
        coordinatorA.registerRemote(instanceA);
        coordinatorB.registerRemote(instanceB);

        List<RemoteInstance> remoteInstancesOfA = notifiedRemoteNodes(watcherA, 2);
        validateServiceInstance(addressA, addressB, remoteInstancesOfA);
        assertEquals(2, queryRemoteNodes(providerA, 2).size());

        List<RemoteInstance> remoteInstancesOfB = notifiedRemoteNodes(watcherB, 2);
        validateServiceInstance(addressB, addressA, remoteInstancesOfB);
        assertEquals(2, queryRemoteNodes(providerB, 2).size());
    }

    @Test
    public void unregisterRemoteOfCluster() throws Exception {
        final String namespace = "unregister_remote_cluster";
        ModuleProvider providerA = createProvider(namespace);
        ModuleProvider providerB = createProvider(namespace);
        ClusterCoordinator coordinatorA = getClusterCoordinator(providerA);
        ClusterMockWatcher watcherA = new ClusterMockWatcher();
        coordinatorA.registerWatcher(watcherA);
        coordinatorA.startCoordinator();
        ClusterCoordinator coordinatorB = getClusterCoordinator(providerB);
        ClusterMockWatcher watcherB = new ClusterMockWatcher();
        coordinatorB.registerWatcher(watcherB);
        coordinatorB.startCoordinator();

        Address addressA = new Address("127.0.0.4", 1000, true);
        Address addressB = new Address("127.0.0.5", 1000, true);
        RemoteInstance instanceA = new RemoteInstance(addressA);
        RemoteInstance instanceB = new RemoteInstance(addressB);
        coordinatorA.registerRemote(instanceA);
        coordinatorB.registerRemote(instanceB);

        List<RemoteInstance> remoteInstancesOfA = notifiedRemoteNodes(watcherA, 2);
        validateServiceInstance(addressA, addressB, remoteInstancesOfA);
        assertEquals(2, queryRemoteNodes(providerA, 2).size());

        List<RemoteInstance> remoteInstancesOfB = notifiedRemoteNodes(watcherB, 2);
        validateServiceInstance(addressB, addressA, remoteInstancesOfB);
        assertEquals(2, queryRemoteNodes(providerB, 2).size());

        // unregister A
        ServiceDiscovery<RemoteInstance> discoveryA = Whitebox.getInternalState(providerA, "serviceDiscovery");
        discoveryA.close();

        // only B
        remoteInstancesOfB = notifiedRemoteNodes(watcherB, 1);
        assertEquals(1, remoteInstancesOfB.size());
        assertEquals(1, queryRemoteNodes(providerB, 1).size());

        Address address = remoteInstancesOfB.get(0).getAddress();
        assertEquals(addressB, address);
        assertTrue(address.isSelf());
    }

    private ClusterModuleZookeeperProvider createProvider(String namespace) throws Exception {
        return createProvider(namespace, null, 0);
    }

    private ClusterModuleZookeeperProvider createProvider(String namespace, String internalComHost,
        int internalComPort) throws Exception {
        ClusterModuleZookeeperProvider provider = new ClusterModuleZookeeperProvider();
        provider.setManager(moduleManager);
        ClusterModuleZookeeperConfig moduleConfig = new ClusterModuleZookeeperConfig();
        provider.newConfigCreator().onInitialized(moduleConfig);
        moduleConfig.setHostPort(zkAddress);
        moduleConfig.setBaseSleepTimeMs(3000);
        moduleConfig.setMaxRetries(3);

        if (!StringUtil.isEmpty(namespace)) {
            moduleConfig.setNamespace(namespace);
        }

        if (!StringUtil.isEmpty(internalComHost)) {
            moduleConfig.setInternalComHost(internalComHost);
        }

        if (internalComPort > 0) {
            moduleConfig.setInternalComPort(internalComPort);
        }

        provider.prepare();
        provider.start();
        provider.notifyAfterCompleted();

        return provider;
    }

    private ClusterRegister getClusterRegister(ModuleProvider provider) {
        return provider.getService(ClusterRegister.class);
    }

    private ClusterCoordinator getClusterCoordinator(ModuleProvider provider) {
        return provider.getService(ClusterCoordinator.class);
    }

    private ClusterNodesQuery getClusterNodesQuery(ModuleProvider provider) {
        return provider.getService(ClusterNodesQuery.class);
    }

    private List<RemoteInstance> queryRemoteNodes(ModuleProvider provider, int goals) {
        return getClusterNodesQuery(provider).queryRemoteNodes();
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
