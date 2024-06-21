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

package org.apache.skywalking.oap.server.cluster.plugin.nacos;

import com.alibaba.nacos.api.naming.NamingService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterWatcher;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ExtendWith(MockitoExtension.class)
public class ClusterModuleNacosProviderFunctionalIT {

    @Container
    public final GenericContainer<?> container =
        new GenericContainer<>(DockerImageName.parse("nacos/nacos-server:v2.3.2-slim"))
            .waitingFor(Wait.forLogMessage(".*Nacos started successfully.*", 1))
            .withEnv(Collections.singletonMap("MODE", "standalone"))
            .withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()))
            .withExposedPorts(8848, 9848);
    private final String username = "nacos";
    private final String password = "nacos";
    private String nacosAddress;
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
        nacosAddress = container.getHost() + ":" + container.getMappedPort(8848);
        Integer nacosPortOffset = container.getMappedPort(9848) - container.getMappedPort(8848);
        System.setProperty("nacos.server.grpc.port.offset", nacosPortOffset.toString());
    }

    @AfterEach
    public void after() {
        System.clearProperty("nacos.server.grpc.port.offset");
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
        assertEquals(1, queryRemoteNodes(provider, 1).size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(selfAddress, queryAddress);
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfInternal() throws Exception {
        final String serviceName = "register_remote_internal";
        ModuleProvider provider = createProvider(serviceName, "127.0.1.2", 1000);

        Address selfAddress = new Address("127.0.0.2", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        ClusterCoordinator coordinator = getClusterCoordinator(provider);
        ClusterMockWatcher watcher = new ClusterMockWatcher();
        coordinator.registerWatcher(watcher);
        coordinator.start();
        coordinator.registerRemote(instance);

        List<RemoteInstance> remoteInstances = notifiedRemoteNodes(watcher, 1);

        assertEquals(1, remoteInstances.size());
        assertEquals(1, queryRemoteNodes(provider, 1).size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals("127.0.1.2", queryAddress.getHost());
        assertEquals(1000, queryAddress.getPort());
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
        Address selfAddress = new Address("127.0.0.3", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        coordinatorA.start();
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
    public void deregisterRemoteOfCluster() throws Exception {
        final String serviceName = "deregister_remote_cluster";
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

        Address addressA = new Address("127.0.0.6", 1000, true);
        Address addressB = new Address("127.0.0.7", 1000, true);
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

        // deregister A
        NamingService namingServiceA = Whitebox.getInternalState(coordinatorA, "namingService");
        namingServiceA.deregisterInstance(serviceName, addressA.getHost(), addressA.getPort());

        // only B
        remoteInstancesOfB = notifiedRemoteNodes(watcherB, 1);
        assertEquals(1, remoteInstancesOfB.size());
        assertEquals(1, queryRemoteNodes(providerB, 1).size());

        Address address = remoteInstancesOfB.get(0).getAddress();
        assertEquals(addressB, address);
        assertTrue(address.isSelf());
    }

    private ClusterModuleNacosProvider createProvider(String servicName)
        throws ModuleStartException {
        ClusterModuleNacosProvider provider = new ClusterModuleNacosProvider();

        ClusterModuleNacosConfig config = new ClusterModuleNacosConfig();
        provider.newConfigCreator().onInitialized(config);

        config.setHostPort(nacosAddress);
        config.setServiceName(servicName);
        provider.setManager(moduleManager);
        config.setUsername(username);
        config.setPassword(password);

        provider.prepare();
        provider.start();
        provider.notifyAfterCompleted();
        return provider;
    }

    private ClusterModuleNacosProvider createProvider(String serviceName, String internalComHost,
                                                      int internalComPort) throws Exception {
        ClusterModuleNacosProvider provider = new ClusterModuleNacosProvider();

        ClusterModuleNacosConfig config = new ClusterModuleNacosConfig();
        provider.newConfigCreator().onInitialized(config);

        config.setHostPort(nacosAddress);
        config.setServiceName(serviceName);
        config.setUsername(username);
        config.setPassword(password);

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

    private ClusterCoordinator getClusterCoordinator(ModuleProvider provider) {
        return provider.getService(ClusterCoordinator.class);
    }

    private ClusterNodesQuery getClusterNodesQuery(ModuleProvider provider) {
        return provider.getService(ClusterNodesQuery.class);
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

    private List<RemoteInstance> notifiedRemoteNodes(ClusterMockWatcher watcher, int goals)
        throws InterruptedException {
        return notifiedRemoteNodes(watcher, goals, 30);
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

    private void validateServiceInstance(Address selfAddress, Address otherAddress,
                                         List<RemoteInstance> queryResult) {
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

    static class ClusterMockWatcher implements ClusterWatcher {
        @Getter
        private List<RemoteInstance> remoteInstances = new ArrayList<>();

        @Override
        public void onClusterNodesChanged(final List<RemoteInstance> remoteInstances) {
            this.remoteInstances = remoteInstances;
        }
    }
}
