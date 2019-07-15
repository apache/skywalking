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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.curator.test.TestingServer;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.cluster.*;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author peng-yongsheng zhang-wei
 */
public class ClusterModuleZookeeperProviderTestCase {

    private TestingServer server;

    @Before
    public void before() throws Exception {
        server = new TestingServer(12181, true);
        server.start();
    }


    @After
    public void after() throws IOException {
        server.stop();
    }

    @Test
    public void registerRemote() throws Exception {
        ClusterModuleZookeeperProvider provider = createProvider("register_remote");

        Address selfAddress = new Address("127.0.0.1", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        getClusterRegister(provider).registerRemote(instance);

        List<RemoteInstance> remoteInstances = queryRemoteNodes(getClusterNodesQuery(provider), 1);
        assertEquals(1, remoteInstances.size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(selfAddress, queryAddress);
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfInternal() throws Exception {
        ClusterModuleZookeeperProvider provider =
            createProvider("register_remote_internal", "127.0.1.2", 1000);

        Address selfAddress = new Address("127.0.0.2", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        getClusterRegister(provider).registerRemote(instance);

        List<RemoteInstance> remoteInstances = queryRemoteNodes(getClusterNodesQuery(provider), 1);

        ClusterModuleZookeeperConfig config = (ClusterModuleZookeeperConfig) provider.createConfigBeanIfAbsent();
        assertEquals(1, remoteInstances.size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(config.getInternalComHost(), queryAddress.getHost());
        assertEquals(config.getInternalComPort(), queryAddress.getPort());
        assertTrue(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfReceiver() throws Exception {
        ClusterModuleZookeeperProvider providerA = createProvider("register_remote_receiver");
        ClusterModuleZookeeperProvider providerB = createProvider("register_remote_receiver");

        // Mixed or Aggregator
        Address selfAddress = new Address("127.0.0.3", 1000, true);
        RemoteInstance instance = new RemoteInstance(selfAddress);
        getClusterRegister(providerA).registerRemote(instance);

        // Receiver
        List<RemoteInstance> remoteInstances = queryRemoteNodes(getClusterNodesQuery(providerB), 1);
        assertEquals(1, remoteInstances.size());
        Address queryAddress = remoteInstances.get(0).getAddress();
        assertEquals(selfAddress, queryAddress);
        assertFalse(queryAddress.isSelf());
    }

    @Test
    public void registerRemoteOfCluster() throws Exception {
        ClusterModuleZookeeperProvider providerA = createProvider("register_remote_cluster");
        ClusterModuleZookeeperProvider providerB = createProvider("register_remote_cluster");

        Address addressA = new Address("127.0.0.4", 1000, true);
        Address addressB = new Address("127.0.0.5", 1000, true);

        RemoteInstance instanceA = new RemoteInstance(addressA);
        RemoteInstance instanceB = new RemoteInstance(addressB);

        getClusterRegister(providerA).registerRemote(instanceA);
        getClusterRegister(providerB).registerRemote(instanceB);

        List<RemoteInstance> remoteInstancesOfA = queryRemoteNodes(getClusterNodesQuery(providerA), 2);
        validateServiceInstance(addressA, addressB, remoteInstancesOfA);

        List<RemoteInstance> remoteInstancesOfB = queryRemoteNodes(getClusterNodesQuery(providerB), 2);
        validateServiceInstance(addressB, addressA, remoteInstancesOfB);
    }

    private ClusterModuleZookeeperProvider createProvider(String namespace) throws Exception {
        return createProvider(namespace, null, 0);
    }

    private ClusterModuleZookeeperProvider createProvider(String namespace, String internalComHost, int internalComPort) throws Exception {
        ClusterModuleZookeeperProvider provider = new ClusterModuleZookeeperProvider();

        ClusterModuleZookeeperConfig moduleConfig = (ClusterModuleZookeeperConfig) provider.createConfigBeanIfAbsent();
        moduleConfig.setHostPort(server.getConnectString());
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

        return provider;
    }

    private ClusterRegister getClusterRegister(ClusterModuleZookeeperProvider provider) {
        return provider.getService(ClusterRegister.class);
    }

    private ClusterNodesQuery getClusterNodesQuery(ClusterModuleZookeeperProvider provider) {
        return provider.getService(ClusterNodesQuery.class);
    }

    private List<RemoteInstance> queryRemoteNodes(ClusterNodesQuery clusterNodesQuery, int goals) throws InterruptedException {
        int i = 20;
        do {
            List<RemoteInstance> instances = clusterNodesQuery.queryRemoteNodes();
            if (instances.size() == goals) {
                return instances;
            } else {
                Thread.sleep(1000);
            }
        } while (--i > 0);
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
