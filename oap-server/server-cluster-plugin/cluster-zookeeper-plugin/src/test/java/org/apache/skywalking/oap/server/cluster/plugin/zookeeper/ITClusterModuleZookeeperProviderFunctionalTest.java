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

import java.util.Collections;
import java.util.List;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ITClusterModuleZookeeperProviderFunctionalTest {

    private String zkAddress;

    @Before
    public void before() {
        zkAddress = System.getProperty("zk.address");
        assertFalse(StringUtil.isEmpty(zkAddress));
    }

    @Test
    public void registerRemote() throws Exception {
        final String namespace = "register_remote";
        ModuleProvider provider = createProvider(namespace);

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
        final String namespace = "register_remote_internal";
        ModuleProvider provider = createProvider(namespace, "127.0.1.2", 1000);

        Address selfAddress = new Address("127.0.0.2", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        getClusterRegister(provider).registerRemote(instance);

        List<RemoteInstance> remoteInstances = queryRemoteNodes(provider, 1);

        ClusterModuleZookeeperConfig config = (ClusterModuleZookeeperConfig) provider.createConfigBeanIfAbsent();
        assertEquals(1, remoteInstances.size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(config.getInternalComHost(), queryAddress.getHost());
        assertEquals(config.getInternalComPort(), queryAddress.getPort());
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfReceiver() throws Exception {
        final String namespace = "register_remote_receiver";
        ModuleProvider providerA = createProvider(namespace);
        ModuleProvider providerB = createProvider(namespace);

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
        final String namespace = "register_remote_cluster";
        ModuleProvider providerA = createProvider(namespace);
        ModuleProvider providerB = createProvider(namespace);

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
        final String namespace = "unregister_remote_cluster";
        ModuleProvider providerA = createProvider(namespace);
        ModuleProvider providerB = createProvider(namespace);

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
        ClusterRegister register = getClusterRegister(providerA);
        ServiceDiscovery<RemoteInstance> discoveryA = Whitebox.getInternalState(register, "serviceDiscovery");
        discoveryA.close();

        // only B
        remoteInstancesOfB = queryRemoteNodes(providerB, 1, 120);
        assertEquals(1, remoteInstancesOfB.size());
        Address address = remoteInstancesOfB.get(0).getAddress();
        assertEquals(address, addressB);
        assertTrue(addressB.isSelf());
    }

    private ClusterModuleZookeeperProvider createProvider(String namespace) throws Exception {
        return createProvider(namespace, null, 0);
    }

    private ClusterModuleZookeeperProvider createProvider(String namespace, String internalComHost,
        int internalComPort) throws Exception {
        ClusterModuleZookeeperProvider provider = new ClusterModuleZookeeperProvider();

        ClusterModuleZookeeperConfig moduleConfig = (ClusterModuleZookeeperConfig) provider.createConfigBeanIfAbsent();
        moduleConfig.setHostPort(zkAddress);
        moduleConfig.setBaseSleepTimeMs(3000);
        moduleConfig.setMaxRetries(3);

        if (!StringUtil.isEmpty(namespace)) {
            moduleConfig.setNameSpace(namespace);
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
