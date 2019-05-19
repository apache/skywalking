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

package org.apache.skywalking.oap.server.core.query;

import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.entity.Call;
import org.apache.skywalking.oap.server.core.query.entity.Node;
import org.apache.skywalking.oap.server.core.query.entity.Topology;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by dengming in 2019-05-19
 */
public class AbstractTopologyTest extends AbstractTest {

    public AbstractTopologyTest() {
        super();
        IComponentLibraryCatalogService componentLibraryCatalogService = mock(IComponentLibraryCatalogService.class);
        when(moduleServiceHolder.getService(IComponentLibraryCatalogService.class)).thenReturn(componentLibraryCatalogService);

        when(componentLibraryCatalogService.getServerNameBasedOnComponent(anyInt())).then(invocation -> {
            int componentId = invocation.getArgumentAt(0, int.class);
            return "service-name-component-id-" + componentId;
        });

        when(componentLibraryCatalogService.getComponentName(anyInt())).thenAnswer(invocation -> {
            int componentId = invocation.getArgumentAt(0, int.class);
            return "component-name-" + componentId;
        });
    }

    protected void assertTopology(Topology topology) {
        assertEquals(7, topology.getNodes().size());

        Node node0 = topology.getNodes().get(0);
        assertEquals("component-name-3", node0.getType());

        Node node1 = topology.getNodes().get(1);
        assertEquals(33, node1.getId());

        Node node2 = topology.getNodes().get(2);
        assertEquals("User", node2.getName());
        assertEquals("USER", node2.getType());

        Node node3 = topology.getNodes().get(3);
        assertEquals(2, node3.getId());
        assertTrue(node3.isReal());

        Node node4 = topology.getNodes().get(4);
        assertEquals("service-name-component-id-3", node4.getType());
        assertTrue(node4.isReal());

        Node node5 = topology.getNodes().get(5);
        assertEquals(22, node5.getId());
        assertTrue(node5.isReal());

        Node node6 = topology.getNodes().get(6);
        assertEquals(11, node6.getId());

        assertEquals(6, topology.getCalls().size());
        Call call0 = topology.getCalls().get(0);
        assertEquals(1, call0.getSource());
        assertEquals(11, call0.getTarget());
        Call call1 = topology.getCalls().get(1);
        assertEquals(2, call1.getSource());
        assertEquals(22, call1.getTarget());
        Call call2 = topology.getCalls().get(2);
        assertEquals(3, call2.getSource());
        assertEquals(33, call2.getTarget());

        Call call3 = topology.getCalls().get(3);
        assertEquals(101, call3.getSource());
        assertEquals(111, call3.getTarget());
        Call call4 = topology.getCalls().get(4);
        assertEquals(102, call4.getSource());
        assertEquals(122, call4.getTarget());
        Call call5 = topology.getCalls().get(5);
        assertEquals(103, call5.getSource());
        assertEquals(133, call5.getTarget());
    }

    protected List<Call> mockClientCall() {

        List<Call> result = new ArrayList<>(10);

        Call call0 = new Call();
        call0.setSource(0);
        result.add(call0);

        Call call1 = new Call();
        call1.setSource(1);
        call1.setTarget(11);
        ServiceInventory inventorySource1 = mock(ServiceInventory.class);
        when(inventorySource1.getSequence()).thenReturn(11);
        when(inventorySource1.getIsAddress()).thenReturn(1);

        ServiceInventory inventoryTarget1 = mock(ServiceInventory.class);
        when(inventoryTarget1.getIsAddress()).thenReturn(1);
        when(serviceInventoryCache.get(1)).thenReturn(inventorySource1);
        when(serviceInventoryCache.get(11)).thenReturn(inventoryTarget1);

        Call call2 = new Call();
        call2.setSource(2);
        call2.setTarget(22);

        ServiceInventory inventorySource2 = mock(ServiceInventory.class);
        when(inventorySource2.getSequence()).thenReturn(22);
        when(inventorySource2.getIsAddress()).thenReturn(0);

        ServiceInventory inventoryTarget2 = mock(ServiceInventory.class);
        when(serviceInventoryCache.get(2)).thenReturn(inventorySource2);
        when(serviceInventoryCache.get(22)).thenReturn(inventoryTarget2);

        Call call3 = new Call();
        call3.setSource(3);
        call3.setTarget(33);

        ServiceInventory inventorySource3 = mock(ServiceInventory.class);
        when(inventorySource3.getSequence()).thenReturn(33);
        when(inventorySource3.getIsAddress()).thenReturn(1);

        ServiceInventory inventoryTarget3 = mock(ServiceInventory.class);
        when(serviceInventoryCache.get(3)).thenReturn(inventorySource3);
        when(serviceInventoryCache.get(33)).thenReturn(inventoryTarget3);

        Call call4 = new Call();
        call4.setSource(4);
        call4.setTarget(44);

        ServiceInventory inventorySource4 = mock(ServiceInventory.class);
        ServiceInventory inventoryTarget4 = mock(ServiceInventory.class);
        when(inventoryTarget4.getMappingServiceId()).thenReturn(1);
        when(serviceInventoryCache.get(4)).thenReturn(inventorySource4);
        when(serviceInventoryCache.get(44)).thenReturn(inventoryTarget4);


        result.add(call1);
        result.add(call2);
        result.add(call3);
        result.add(call4);

        return result;
    }

    protected List<Call> mockServerCall() {
        List<Call> result = new ArrayList<>(10);

        Call call0 = new Call();
        call0.setSource(0);
        result.add(call0);

        Call call1 = new Call();
        call1.setSource(101);
        call1.setTarget(111);
        call1.setId("1");
        call1.setComponentId(1);
        ServiceInventory inventorySource1 = mock(ServiceInventory.class);
        when(inventorySource1.getSequence()).thenReturn(1);
        when(inventorySource1.getIsAddress()).thenReturn(1);

        ServiceInventory inventoryTarget1 = mock(ServiceInventory.class);
        when(serviceInventoryCache.get(101)).thenReturn(inventorySource1);
        when(serviceInventoryCache.get(111)).thenReturn(inventoryTarget1);


        Call call2 = new Call();
        call2.setSource(102);
        call2.setTarget(122);
        call2.setId("2");
        call2.setComponentId(2);

        ServiceInventory inventorySource2 = mock(ServiceInventory.class);
        when(inventorySource2.getSequence()).thenReturn(2);
        when(inventorySource2.getIsAddress()).thenReturn(0);

        ServiceInventory inventoryTarget2 = mock(ServiceInventory.class);
        when(serviceInventoryCache.get(102)).thenReturn(inventorySource2);
        when(serviceInventoryCache.get(122)).thenReturn(inventoryTarget2);

        Call call3 = new Call();
        call3.setSource(103);
        call3.setTarget(133);
        call3.setId("3");
        call3.setComponentId(3);

        ServiceInventory inventorySource3 = mock(ServiceInventory.class);
        when(inventorySource3.getSequence()).thenReturn(3);
        when(inventorySource3.getIsAddress()).thenReturn(1);

        ServiceInventory inventoryTarget3 = mock(ServiceInventory.class);
        when(serviceInventoryCache.get(103)).thenReturn(inventorySource3);
        when(serviceInventoryCache.get(133)).thenReturn(inventoryTarget3);


        result.add(call1);
        result.add(call2);
        result.add(call3);
        return result;
    }
}
