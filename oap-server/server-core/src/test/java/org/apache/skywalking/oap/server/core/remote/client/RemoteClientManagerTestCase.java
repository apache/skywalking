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
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.*;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamDataClassGetter;
import org.apache.skywalking.oap.server.testing.module.*;
import org.junit.*;

import static org.mockito.Mockito.*;

/**
 * @author peng-yongsheng
 */
public class RemoteClientManagerTestCase {

    @Test
    public void refresh() {
        ModuleManagerTesting moduleManager = new ModuleManagerTesting();
        ModuleDefineTesting clusterModuleDefine = new ModuleDefineTesting();
        moduleManager.put(ClusterModule.NAME, clusterModuleDefine);

        ModuleDefineTesting coreModuleDefine = new ModuleDefineTesting();
        moduleManager.put(CoreModule.NAME, coreModuleDefine);

        ClusterNodesQuery clusterNodesQuery = mock(ClusterNodesQuery.class);
        clusterModuleDefine.provider().registerServiceImplementation(ClusterNodesQuery.class, clusterNodesQuery);

        StreamDataClassGetter streamDataClassGetter = mock(StreamDataClassGetter.class);
        coreModuleDefine.provider().registerServiceImplementation(StreamDataClassGetter.class, streamDataClassGetter);

        RemoteClientManager clientManager = new RemoteClientManager(moduleManager);

        when(clusterNodesQuery.queryRemoteNodes()).thenReturn(groupOneInstances());
        clientManager.refresh();

        List<RemoteClient> remoteClients = clientManager.getRemoteClient();
        Assert.assertEquals("host1", remoteClients.get(0).getAddress().getHost());
        Assert.assertEquals("host2", remoteClients.get(1).getAddress().getHost());
        Assert.assertEquals("host3", remoteClients.get(2).getAddress().getHost());

        Assert.assertTrue(remoteClients.get(0) instanceof GRPCRemoteClient);
        Assert.assertTrue(remoteClients.get(1) instanceof SelfRemoteClient);
        Assert.assertTrue(remoteClients.get(2) instanceof GRPCRemoteClient);

        when(clusterNodesQuery.queryRemoteNodes()).thenReturn(groupTwoInstances());
        clientManager.refresh();

        remoteClients = clientManager.getRemoteClient();
        Assert.assertEquals("host1", remoteClients.get(0).getAddress().getHost());
        Assert.assertEquals("host2", remoteClients.get(1).getAddress().getHost());
        Assert.assertEquals("host4", remoteClients.get(2).getAddress().getHost());
        Assert.assertEquals("host5", remoteClients.get(3).getAddress().getHost());

        Assert.assertTrue(remoteClients.get(0) instanceof GRPCRemoteClient);
        Assert.assertTrue(remoteClients.get(1) instanceof SelfRemoteClient);
        Assert.assertTrue(remoteClients.get(2) instanceof GRPCRemoteClient);
        Assert.assertTrue(remoteClients.get(3) instanceof GRPCRemoteClient);
    }

    private List<RemoteInstance> groupOneInstances() {
        List<RemoteInstance> instances = new ArrayList<>();
        instances.add(new RemoteInstance(new Address("host3", 100, false)));
        instances.add(new RemoteInstance(new Address("host1", 100, false)));
        instances.add(new RemoteInstance(new Address("host2", 100, true)));
        return instances;
    }

    private List<RemoteInstance> groupTwoInstances() {
        List<RemoteInstance> instances = new ArrayList<>();
        instances.add(new RemoteInstance(new Address("host5", 100, false)));
        instances.add(new RemoteInstance(new Address("host1", 100, false)));
        instances.add(new RemoteInstance(new Address("host2", 100, true)));
        instances.add(new RemoteInstance(new Address("host4", 100, false)));
        return instances;
    }
}
