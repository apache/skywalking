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
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
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
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.verification.AtLeast;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
public class RemoteClientManagerTestCase {

    private RemoteClientManager clientManager;
    private ClusterNodesQuery clusterNodesQuery;

    @Before
    public void setup() {
        ModuleManagerTesting moduleManager = new ModuleManagerTesting();
        ModuleDefineTesting clusterModuleDefine = new ModuleDefineTesting();
        moduleManager.put(ClusterModule.NAME, clusterModuleDefine);

        ModuleDefineTesting coreModuleDefine = new ModuleDefineTesting();
        moduleManager.put(CoreModule.NAME, coreModuleDefine);

        this.clusterNodesQuery = mock(ClusterNodesQuery.class);
        clusterModuleDefine.provider().registerServiceImplementation(ClusterNodesQuery.class, clusterNodesQuery);

        MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(metricsCreator.createGauge(any(), any(), any(), any())).thenReturn(new GaugeMetrics() {
            @Override
            public void inc() {

            }

            @Override
            public void inc(double value) {

            }

            @Override
            public void dec() {

            }

            @Override
            public void dec(double value) {

            }

            @Override
            public void setValue(double value) {

            }

            @Override
            public double getValue() {
                return 0;
            }
        });
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        this.clientManager = spy(new RemoteClientManager(moduleManager, 10));
    }

    @Test
    public void refresh() {
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

    @Test
    public void testConcurrenceGetRemoteClientAndRefresh() throws Exception {
        this.refresh(); //guarantee has any client in clientManager

        CyclicBarrier cyclicBarrier = new CyclicBarrier(3, () -> {
            log.debug("begin concurrency test");
        });

        final ExecutorService executorService = Executors.newFixedThreadPool(3);
        final Future<?> refreshFuture = executorService.submit(() -> {
            try {
                cyclicBarrier.await();
                this.refresh();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        });

        executorService.submit(() -> {
            try {
                int i = 0;
                cyclicBarrier.await();
                while (!refreshFuture.isDone()) {
                    Assert.assertFalse(this.clientManager.getRemoteClient().isEmpty());
                    log.debug("thread {} invoke {} times", Thread.currentThread().getName(), i++);
                }
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        });

        try {
            int i = 0;
            cyclicBarrier.await();
            while (!refreshFuture.isDone()) {
                Assert.assertFalse(this.clientManager.getRemoteClient().isEmpty());
                log.debug("thread {} invoke {} times", Thread.currentThread().getName(), i++);
            }
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }

        verify(this.clientManager, new AtLeast(2)).getRemoteClient();
    }

    @Test
    public void testGetRemoteClientAndNeverChange() {
        when(clusterNodesQuery.queryRemoteNodes()).thenReturn(groupOneInstances());
        this.clientManager.refresh();
        final List<RemoteClient> gotGroupOneInstances = this.clientManager.getRemoteClient();

        when(clusterNodesQuery.queryRemoteNodes()).thenReturn(groupTwoInstances());
        this.clientManager.refresh();
        final List<RemoteClient> gotGroupTwoInstances = this.clientManager.getRemoteClient();

        Assert.assertEquals(gotGroupOneInstances.size(), groupOneInstances().size());
        Assert.assertEquals(gotGroupTwoInstances.size(), groupTwoInstances().size());
        Assert.assertNotEquals(gotGroupOneInstances.size(), gotGroupTwoInstances.size());
    }

    @Test
    public void testCompare() {
        when(clusterNodesQuery.queryRemoteNodes()).thenReturn(groupOneInstances());
        clientManager.refresh();

        List<RemoteClient> groupOneRemoteClients = clientManager.getRemoteClient();

        when(clusterNodesQuery.queryRemoteNodes()).thenReturn(groupOneInstances());
        clientManager.refresh();

        List<RemoteClient> newGroupOneRemoteClients = clientManager.getRemoteClient();

        Assert.assertArrayEquals(groupOneRemoteClients.toArray(), newGroupOneRemoteClients.toArray());
    }

    @Test
    public void testUnChangeRefresh() {
        final List<RemoteInstance> groupOneInstances = groupOneInstances();
        when(clusterNodesQuery.queryRemoteNodes()).thenReturn(groupOneInstances);
        clientManager.refresh();

        List<RemoteClient> groupOneRemoteClients = clientManager.getRemoteClient();

        groupOneInstances.add(new RemoteInstance(new Address("host4", 100, false)));
        when(clusterNodesQuery.queryRemoteNodes()).thenReturn(groupOneInstances);
        clientManager.refresh();

        List<RemoteClient> newGroupOneRemoteClients = clientManager.getRemoteClient();

        Assert.assertEquals(groupOneRemoteClients.get(0).getAddress(), newGroupOneRemoteClients.get(0).getAddress());
        Assert.assertEquals(newGroupOneRemoteClients.get(3).getAddress().getHost(), "host4");
    }
}
