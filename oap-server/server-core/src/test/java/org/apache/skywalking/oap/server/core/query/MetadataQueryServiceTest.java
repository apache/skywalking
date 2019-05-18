package org.apache.skywalking.oap.server.core.query;/*
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

import com.google.common.collect.Lists;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.NodeType;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 * Created by dengming in 2019-05-18
 */
public class MetadataQueryServiceTest extends AbstractTest {


    private MetadataQueryService queryService = new MetadataQueryService(moduleManager);

    private IMetadataQueryDAO queryDAO = mock(IMetadataQueryDAO.class);

    private static final int SERVICE_NUM = 100;
    private static final int ENDPOINT_NUM = 1300;
    private static final int CONJECTURAL_NUM_DATABASE = 1200;
    private static final int CONJECTURAL_NUM_CACHE = 1300;
    private static final int CONJECTURAL_NUM_MQ = 1400;

    private static final long START_TIMESTAMP = 100L;
    private static final long END_TIMESTAMP = 200L;

    private static final String KEYWORD= "keyword";

    private static final String SERVICE_ID = "my-service-id";

    @Before
    public void setUp() throws Exception {

        when(queryDAO.numOfService(START_TIMESTAMP, END_TIMESTAMP)).thenReturn(SERVICE_NUM);
        when(queryDAO.numOfEndpoint(START_TIMESTAMP, END_TIMESTAMP)).thenReturn(ENDPOINT_NUM);


        when(queryDAO.numOfConjectural(START_TIMESTAMP, END_TIMESTAMP, NodeType.Database.value())).thenReturn(CONJECTURAL_NUM_DATABASE);
        when(queryDAO.numOfConjectural(START_TIMESTAMP, END_TIMESTAMP, NodeType.Cache.value())).thenReturn(CONJECTURAL_NUM_CACHE);
        when(queryDAO.numOfConjectural(START_TIMESTAMP, END_TIMESTAMP, NodeType.MQ.value())).thenReturn(CONJECTURAL_NUM_MQ);


        when(moduleServiceHolder.getService(IMetadataQueryDAO.class)).thenReturn(queryDAO);

        Service service = mock(Service.class);
        when(queryDAO.getAllServices(START_TIMESTAMP, END_TIMESTAMP)).thenReturn(Lists.newArrayList(service));

        Database database = mock(Database.class);
        when(queryDAO.getAllDatabases()).thenReturn(Lists.newArrayList(database));
    }

    @Test
    public void getGlobalBrief() throws Exception {
        ClusterBrief brief = queryService.getGlobalBrief(START_TIMESTAMP, END_TIMESTAMP);
        assertNotNull(brief);
        assertEquals(SERVICE_NUM, brief.getNumOfService());
        assertEquals(ENDPOINT_NUM, brief.getNumOfEndpoint());
        assertEquals(CONJECTURAL_NUM_CACHE, brief.getNumOfCache());
        assertEquals(CONJECTURAL_NUM_DATABASE, brief.getNumOfDatabase());
        assertEquals(CONJECTURAL_NUM_MQ, brief.getNumOfMQ());
    }

    @Test
    public void getAllServices() throws Exception {
        List<Service> serviceList = queryService.getAllServices(START_TIMESTAMP, END_TIMESTAMP);
        assertEquals(1, serviceList.size());
    }

    @Test
    public void getAllDatabases() throws Exception {
        List<Database> databases = queryService.getAllDatabases();
        assertEquals(1, databases.size());
    }

    @Test
    public void searchServices() throws Exception {
        List<Service> services = new LinkedList<>();
        for (int i = 0; i < 11; i++) {
            services.add(mock(Service.class));
        }
        when(queryDAO.searchServices(START_TIMESTAMP, END_TIMESTAMP, KEYWORD)).thenReturn(services);
        assertEquals(11, queryService.searchServices(START_TIMESTAMP, END_TIMESTAMP, KEYWORD).size());
    }

    @Test
    public void getServiceInstances() throws Exception {
        List<ServiceInstance> serviceInstances = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            serviceInstances.add(mock(ServiceInstance.class));
        }

        when(queryDAO.getServiceInstances(START_TIMESTAMP, END_TIMESTAMP, SERVICE_ID)).thenReturn(serviceInstances);
        assertEquals(10, queryService.getServiceInstances(START_TIMESTAMP, END_TIMESTAMP, SERVICE_ID).size());
    }

    @Test
    public void searchEndpoint() throws Exception {
        List<Endpoint> endpoints = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            endpoints.add(mock(Endpoint.class));
        }

        when(queryDAO.searchEndpoint(KEYWORD, SERVICE_ID, 100)).thenReturn(endpoints);

        assertEquals(10, queryService.searchEndpoint(KEYWORD, SERVICE_ID, 100).size());
    }

    @Test
    public void searchService() throws Exception {
        Service services = mock(Service.class);
        when(queryDAO.searchService(anyString())).thenReturn(services);

        assertEquals(services, queryService.searchService("123"));
    }

    @Test
    public void getEndpointInfo() throws Exception {
        when(endpointInventory.getSequence()).thenReturn(100);
        when(endpointInventory.getName()).thenReturn("endpoint");
        when(endpointInventory.getServiceId()).thenReturn(12);

        EndpointInfo info = queryService.getEndpointInfo(123);

        assertEquals(100, info.getId());
        assertEquals("endpoint", info.getName());
        assertEquals(12, info.getServiceId());
        assertEquals(SERVICE_INVENTORY_NAME, info.getServiceName());
    }

}