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

import org.apache.skywalking.oap.server.core.cache.EndpointInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.query.entity.Order;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.query.entity.TopNEntity;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnIds;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Created by dengming in 2019-05-06
 */
public class AggregationQueryServiceTest extends AbstractTest {

    private AggregationQueryService aggregationQueryService = new AggregationQueryService(moduleManager);

    private IAggregationQueryDAO iAggregationQueryDAO = mock(IAggregationQueryDAO.class);

    private static final String INS_NAME = "my-ins";
    private static final int SERVICE_ID = 12;
    private static final String C_NAME = "my-c-name";

    private static final int N = 10;

    private static final Step STEP = Step.MINUTE;

    private static final long START_TB = 1000L;

    private static final long END_TB = 2000L;

    private static final Order ORDER = Order.ASC;

    @Before
    public void setUp() throws Exception {

        // mock ValueColumnIds
        ValueColumnIds.INSTANCE.putIfAbsent(INS_NAME, C_NAME, Function.None);

        // mock moduleServiceHolder
        when(moduleServiceHolder.getService(IAggregationQueryDAO.class)).thenReturn(iAggregationQueryDAO);


    }

    @Test
    public void getServiceTopN() throws Exception {

        List<TopNEntity> mockEntities = mockTopNForDAO();
        List<TopNEntity> topNEntities = aggregationQueryService.getServiceTopN(INS_NAME, N, STEP, START_TB, END_TB, ORDER);

        assertTopNEntities(mockEntities, topNEntities, SERVICE_INVENTORY_NAME);
    }

    @Test
    public void getServiceTopNWithEmpty() throws Exception {
        mockTopNForDAO(Collections.emptyList());
        List<TopNEntity> topNEntities = aggregationQueryService.getServiceTopN(INS_NAME, N, STEP, START_TB, END_TB, ORDER);
        assertEquals(0, topNEntities.size());
    }

    @Test
    public void getAllServiceInstanceTopN() throws Exception {

        List<TopNEntity> mockEntities = mockTopNForDAO();
        List<TopNEntity> topNEntities = aggregationQueryService.getAllServiceInstanceTopN(INS_NAME, N, STEP, START_TB, END_TB, ORDER);
        assertTopNEntities(mockEntities, topNEntities, SERVICE_INSTANCE_INVENTORY_NAME);
    }

    @Test
    public void getServiceInstanceTopN() throws Exception {
        List<TopNEntity> mockEntities = mockTopNForDAO();
        List<TopNEntity> topNEntities = aggregationQueryService.getServiceInstanceTopN(SERVICE_ID, INS_NAME, N, STEP, START_TB, END_TB, ORDER);
        assertTopNEntities(mockEntities, topNEntities, SERVICE_INSTANCE_INVENTORY_NAME);
    }

    @Test
    public void getAllEndpointTopN() throws Exception {
        List<TopNEntity> mockEntities = mockTopNForDAO();
        List<TopNEntity> topNEntities = aggregationQueryService.getAllEndpointTopN(INS_NAME, N, STEP, START_TB, END_TB, ORDER);
        assertTopNEntities(mockEntities, topNEntities, ENDPOINT_INVENTORY_NAME);
    }

    @Test
    public void getEndpointTopN() throws Exception {
        List<TopNEntity> mockEntities = mockTopNForDAO();
        List<TopNEntity> topNEntities = aggregationQueryService.getEndpointTopN(SERVICE_ID, INS_NAME, N, STEP, START_TB, END_TB, ORDER);
        assertTopNEntities(mockEntities, topNEntities, ENDPOINT_INVENTORY_NAME);
    }


    private List<TopNEntity> mockEntities() {
        List<TopNEntity> entities = new ArrayList<>(N);

        for (int i = 0; i < N; i++) {
            TopNEntity en = new TopNEntity();
            en.setId(String.valueOf(i));
            en.setName("name-" + i);
            en.setValue(i * 10);
            entities.add(en);
        }

        return entities;
    }

    private List<TopNEntity> mockTopNForDAO() throws Exception {
        List<TopNEntity> mockEntities = mockEntities();
        mockTopNForDAO(mockEntities);
        return mockEntities;
    }

    private void mockTopNForDAO(List<TopNEntity> topNEntities) throws Exception {

        //mock query dao
        when(iAggregationQueryDAO.getServiceTopN(
                anyString(),
                anyString(),
                anyInt(),
                any(Step.class),
                anyLong(),
                anyLong(),
                any(Order.class))).thenReturn(topNEntities);

        when(iAggregationQueryDAO.getAllServiceInstanceTopN(
                anyString(),
                anyString(),
                anyInt(),
                any(Step.class),
                anyLong(),
                anyLong(),
                any(Order.class))).thenReturn(topNEntities);

        when(iAggregationQueryDAO.getAllEndpointTopN(
                anyString(),
                anyString(),
                anyInt(),
                any(Step.class),
                anyLong(),
                anyLong(),
                any(Order.class)
        )).thenReturn(topNEntities);

        when(iAggregationQueryDAO.getEndpointTopN(
                anyInt(),
                anyString(),
                anyString(),
                anyInt(),
                any(Step.class),
                anyLong(),
                anyLong(),
                any(Order.class)
        )).thenReturn(topNEntities);

        when(iAggregationQueryDAO.getServiceInstanceTopN(
                anyInt(),
                anyString(),
                anyString(),
                anyInt(),
                any(Step.class),
                anyLong(),
                anyLong(),
                any(Order.class)
        )).thenReturn(topNEntities);
    }

    private void assertTopNEntities(List<TopNEntity> expected, List<TopNEntity> actual, String expectedName) {

        assertEquals(expected.size(), actual.size());
        assertEquals(N, expected.size());
        for (int i = 0; i < N; i++) {
            assertTopNEntity(expected.get(i), actual.get(i), expectedName);
        }
    }

    private void assertTopNEntity(TopNEntity expected, TopNEntity actual, String expecteName) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expecteName, actual.getName());
        assertEquals(expected.getValue(), actual.getValue());
    }
}