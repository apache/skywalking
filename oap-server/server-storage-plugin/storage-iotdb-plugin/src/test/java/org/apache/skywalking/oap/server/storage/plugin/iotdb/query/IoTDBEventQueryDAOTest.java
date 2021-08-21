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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.Event;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;
import org.junit.Before;
import org.junit.Test;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;

public class IoTDBEventQueryDAOTest {
    private IoTDBEventQueryDAO eventQueryDAO;

    @Before
    public void setUp() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost("127.0.0.1");
        config.setRpcPort(6667);
        config.setUsername("root");
        config.setPassword("root");
        config.setStorageGroup("root.skywalking");
        config.setSessionPoolSize(3);
        config.setFetchTaskLogMaxSize(1000);

        IoTDBClient client = new IoTDBClient(config);
        client.connect();

        eventQueryDAO = new IoTDBEventQueryDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(Event.class, Event.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model eventModel = new Model(
                Event.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(Event.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(eventModel);

        StorageHashMapBuilder<Event> eventBuilder = new Event.Builder();
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put(Event.UUID, "uuid_1");
        eventMap.put(Event.SERVICE, "service_1");
        eventMap.put(Event.SERVICE_INSTANCE, "instance_1");
        eventMap.put(Event.ENDPOINT, "endpoint_1");
        eventMap.put(Event.NAME, "name_1");
        eventMap.put(Event.TYPE, EventType.Normal.name());
        eventMap.put(Event.MESSAGE, "message_1");
        eventMap.put(Event.PARAMETERS, "{parameter1: 1}");
        eventMap.put(Event.START_TIME, 1L);
        eventMap.put(Event.END_TIME, 2L);
        eventMap.put(Event.TIME_BUCKET, 3L);
        Event event1 = eventBuilder.storage2Entity(eventMap);

        eventMap.put(Event.START_TIME, 4L);
        eventMap.put(Event.END_TIME, 5L);
        Event event2 = eventBuilder.storage2Entity(eventMap);

        eventMap.put(Event.UUID, "uuid_2");
        eventMap.put(Event.START_TIME, 6L);
        eventMap.put(Event.END_TIME, 7L);
        Event event3 = eventBuilder.storage2Entity(eventMap);

        IoTDBInsertRequest request = new IoTDBInsertRequest(Event.INDEX_NAME, 1L, event1, eventBuilder);
        client.write(request);
        request = new IoTDBInsertRequest(Event.INDEX_NAME, 2L, event2, eventBuilder);
        client.write(request);
        request = new IoTDBInsertRequest(Event.INDEX_NAME, 3L, event3, eventBuilder);
        client.write(request);
    }

    @Test
    public void queryEvents() throws Exception {
        Source source = new Source("service_1", "instance_1", "endpoint_1");
        Duration duration = null;
        Pagination pagination = new Pagination(0, 10, true);
        EventQueryCondition condition = new EventQueryCondition("uuid_1", source, "name_1",
                EventType.Normal, duration, Order.DES, pagination);
        Events events = eventQueryDAO.queryEvents(condition);
        long startTime = Long.MAX_VALUE;
        for (org.apache.skywalking.oap.server.core.query.type.event.Event event : events.getEvents()) {
            assert event.getUuid().equals("uuid_1");
            assert event.getSource().getService().equals("service_1");
            assert event.getSource().getServiceInstance().equals("instance_1");
            assert event.getSource().getEndpoint().equals("endpoint_1");
            assert event.getName().equals("name_1");
            assert event.getType().name().equals(EventType.Normal.name());
            assert event.getStartTime() > 0;
            assert event.getEndTime() < 10;
            assert event.getStartTime() < startTime;
            startTime = event.getStartTime();
        }
    }

    @Test
    public void queryEvents2() throws Exception {
        Source source1 = new Source("service_1", "instance_1", "endpoint_1");
        Duration duration = null;
        Pagination pagination = new Pagination(0, 10, true);
        EventQueryCondition condition1 = new EventQueryCondition("uuid_1", source1, "name_1",
                EventType.Normal, duration, Order.DES, pagination);
        EventQueryCondition condition2 = new EventQueryCondition("uuid_2", source1, "name_1",
                EventType.Normal, duration, Order.DES, pagination);
        List<EventQueryCondition> conditionList = new ArrayList<>();
        conditionList.add(condition1);
        conditionList.add(condition2);
        Events events = eventQueryDAO.queryEvents(conditionList);
        long startTime = Long.MAX_VALUE;
        for (org.apache.skywalking.oap.server.core.query.type.event.Event event : events.getEvents()) {
            assert event.getUuid().equals("uuid_1") || event.getUuid().equals("uuid_2");
            assert event.getSource().getService().equals("service_1");
            assert event.getSource().getServiceInstance().equals("instance_1");
            assert event.getSource().getEndpoint().equals("endpoint_1");
            assert event.getName().equals("name_1");
            assert event.getType().name().equals(EventType.Normal.name());
            assert event.getStartTime() > 0;
            assert event.getEndTime() < 10;
            assert event.getStartTime() < startTime;
            startTime = event.getStartTime();
        }
    }
}