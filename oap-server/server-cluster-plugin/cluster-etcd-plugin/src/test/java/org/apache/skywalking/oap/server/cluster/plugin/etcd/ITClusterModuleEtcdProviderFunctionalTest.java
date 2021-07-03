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

package org.apache.skywalking.oap.server.cluster.plugin.etcd;

import io.etcd.jetcd.Client;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ITClusterModuleEtcdProviderFunctionalTest {

    private static String ENDPOINTS;
    private static NoneTelemetryProvider TELEMETRY_PROVIDER = mock(NoneTelemetryProvider.class);

    @ClassRule
    public static final GenericContainer CONTAINER =
        new GenericContainer(DockerImageName.parse("bitnami/etcd:3.5.0"))
            .waitingFor(Wait.forLogMessage(".*etcd setup finished!.*", 1))
            .withEnv(Collections.singletonMap("ALLOW_NONE_AUTHENTICATION", "yes"));

    @Before
    public void setup() {
        Mockito.when(TELEMETRY_PROVIDER.getService(MetricsCreator.class))
               .thenReturn(new MetricsCreatorNoop());
        ENDPOINTS = "http://127.0.0.1:" + CONTAINER.getMappedPort(2379);
    }

    @Test
    public void registerRemote() throws Exception {
        final String serviceName = "register_remote";
        ModuleProvider provider = createProvider(serviceName);

        Address selfAddress = new Address("127.0.0.1", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        getClusterRegister(provider).registerRemote(instance);

        List<RemoteInstance> remoteInstances = queryRemoteNodes(provider, 1);
        assertEquals(1, remoteInstances.size());
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
        getClusterRegister(provider).registerRemote(instance);

        List<RemoteInstance> remoteInstances = queryRemoteNodes(provider, 1);

        ClusterModuleEtcdConfig config = (ClusterModuleEtcdConfig) provider.createConfigBeanIfAbsent();
        assertEquals(1, remoteInstances.size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(config.getInternalComHost(), queryAddress.getHost());
        assertEquals(config.getInternalComPort(), queryAddress.getPort());
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfReceiver() throws Exception {
        final String serviceName = "register_remote_receiver";
        ModuleProvider providerA = createProvider(serviceName);
        ModuleProvider providerB = createProvider(serviceName);

        // Mixed or Aggregator
        Address selfAddress = new Address("127.0.0.3", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        getClusterRegister(providerA).registerRemote(instance);

        // Receiver
        List<RemoteInstance> remoteInstances = queryRemoteNodes(providerB, 1);
        assertEquals(1, remoteInstances.size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(selfAddress, queryAddress);
        assertFalse(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfCluster() throws Exception {
        final String serviceName = "register_remote_cluster";
        ModuleProvider providerA = createProvider(serviceName);
        ModuleProvider providerB = createProvider(serviceName);

        Address addressA = new Address("127.0.0.4", 1000, true);
        Address addressB = new Address("127.0.0.5", 1000, true);

        RemoteInstance instanceA = new RemoteInstance(addressA);
        RemoteInstance instanceB = new RemoteInstance(addressB);

        getClusterRegister(providerA).registerRemote(instanceA);
        getClusterRegister(providerB).registerRemote(instanceB);

        List<RemoteInstance> remoteInstancesOfA = queryRemoteNodes(providerA, 2);
        validateServiceInstance(addressA, addressB, remoteInstancesOfA);

        List<RemoteInstance> remoteInstancesOfB = queryRemoteNodes(providerB, 2);
        validateServiceInstance(addressB, addressA, remoteInstancesOfB);
    }

    @Test
    public void unregisterRemoteOfCluster() throws Exception {
        final String serviceName = "unregister_remote_cluster";
        ModuleProvider providerA = createProvider(serviceName);
        ModuleProvider providerB = createProvider(serviceName);

        Address addressA = new Address("127.0.0.4", 1000, true);
        Address addressB = new Address("127.0.0.5", 1000, true);

        RemoteInstance instanceA = new RemoteInstance(addressA);
        RemoteInstance instanceB = new RemoteInstance(addressB);

        getClusterRegister(providerA).registerRemote(instanceA);
        getClusterRegister(providerB).registerRemote(instanceB);

        List<RemoteInstance> remoteInstancesOfA = queryRemoteNodes(providerA, 2);
        validateServiceInstance(addressA, addressB, remoteInstancesOfA);

        List<RemoteInstance> remoteInstancesOfB = queryRemoteNodes(providerB, 2);
        validateServiceInstance(addressB, addressA, remoteInstancesOfB);

        // unregister A
        Client client = Whitebox.getInternalState(getClusterRegister(providerA), "client");
        client.close();

        // only B
        remoteInstancesOfB = queryRemoteNodes(providerB, 1, 120);
        assertEquals(1, remoteInstancesOfB.size());
        Address address = remoteInstancesOfB.get(0).getAddress();
        assertEquals(address, addressB);
        assertTrue(addressB.isSelf());
    }

    private ClusterModuleEtcdProvider createProvider(String serviceName) throws ModuleStartException {
        return createProvider(serviceName, null, 0);
    }

    private ClusterModuleEtcdProvider createProvider(String serviceName, String internalComHost,
                                                     int internalComPort) throws ModuleStartException {
        ClusterModuleEtcdProvider provider = new ClusterModuleEtcdProvider();

        ClusterModuleEtcdConfig config = (ClusterModuleEtcdConfig) provider.createConfigBeanIfAbsent();

        config.setEndpoints(ENDPOINTS);
        config.setServiceName(serviceName);

        if (!StringUtil.isEmpty(internalComHost)) {
            config.setInternalComHost(internalComHost);
        }

        if (internalComPort > 0) {
            config.setInternalComPort(internalComPort);
        }
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", TELEMETRY_PROVIDER);
        ModuleManager manager = mock(ModuleManager.class);
        Mockito.when(manager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);

        provider.setManager(manager);
        provider.prepare();
        provider.start();
        provider.notifyAfterCompleted();
        return provider;
    }

    private ClusterRegister getClusterRegister(ModuleProvider provider) {
        return provider.getService(ClusterRegister.class);
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
        return Collections.EMPTY_LIST;
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

}
