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
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.testing.module.ModuleDefineTesting;
import org.apache.skywalking.oap.server.testing.module.ModuleManagerTesting;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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


        MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(metricsCreator.createGauge(any(), any(), any(), any())).thenReturn(new GaugeMetrics() {
            @Override public void inc() {

            }

            @Override public void inc(double value) {

            }

            @Override public void dec() {

            }

            @Override public void dec(double value) {

            }

            @Override public void setValue(double value) {

            }
        });
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        RemoteClientManager clientManager = new RemoteClientManager(moduleManager, 10);

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
