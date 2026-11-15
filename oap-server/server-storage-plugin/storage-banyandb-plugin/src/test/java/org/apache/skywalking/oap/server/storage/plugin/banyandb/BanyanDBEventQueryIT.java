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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.record.Event;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBEventQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads events the way the event query service does, on the real {@code events} stream: several conditions are an OR
 * of groups that each AND their fields, so an event matching one field of one group and another field of another
 * group must stay out. The first condition's order, paging and time range apply to the whole read.
 */
@Testcontainers
public class BanyanDBEventQueryIT {
    private static final int EVENTS = 20;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBEventQueryDAO QUERY_DAO;
    private static Duration DURATION;
    private static long FIRST;

    @BeforeAll
    public static void writeEvents() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(Event.class, DefaultScopeDefine.EVENT, Event.INDEX_NAME);
        FIRST = System.currentTimeMillis() - EVENTS * 1000L;
        final List<Event> events = new ArrayList<>();
        for (int i = 0; i < EVENTS; i++) {
            events.add(event(i));
        }
        SUPPORT.write(model, new Event.Builder(), events);
        QUERY_DAO = new BanyanDBEventQueryDAO(SUPPORT.getClient());
        DURATION = BanyanDBQueryITSupport.around(FIRST, FIRST + EVENTS * 1000L);
        await().atMost(30, TimeUnit.SECONDS)
               .untilAsserted(() -> assertEquals(EVENTS, uuids(QUERY_DAO.queryEvents(condition(null, null, Order.DES, 1, 100))).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsTheEventsOfEitherGroupOnly() throws Exception {
        assertEquals(Arrays.asList(uuid(19), uuid(18), uuid(1), uuid(0)), uuids(QUERY_DAO.queryEvents(Arrays.asList(
            condition("service-a", "Upgrade", Order.DES, 1, 10), condition("service-b", "Reboot", Order.DES, 1, 10)))));
        assertEquals(Arrays.asList(uuid(0), uuid(1), uuid(18), uuid(19)), uuids(QUERY_DAO.queryEvents(Arrays.asList(
            condition("service-a", "Upgrade", Order.ASC, 1, 10), condition("service-b", "Reboot", Order.DES, 1, 10)))));
    }

    @Test
    public void pagesThroughTheEventsOfTheGroups() throws Exception {
        final List<String> expected = Arrays.asList(uuid(19), uuid(18), uuid(1), uuid(0));
        for (int page = 1; page <= expected.size(); page++) {
            assertEquals(Collections.singletonList(expected.get(page - 1)), uuids(QUERY_DAO.queryEvents(Arrays.asList(
                condition("service-a", "Upgrade", Order.DES, page, 1), condition("service-b", "Reboot", Order.DES, page, 1)))),
                "page " + page);
        }
    }

    @Test
    public void filtersByUuidTypeAndLayer() throws Exception {
        final EventQueryCondition byUuid = condition(null, null, Order.DES, 1, 10);
        byUuid.setUuid(uuid(7));
        assertEquals(Collections.singletonList(uuid(7)), uuids(QUERY_DAO.queryEvents(byUuid)));
        final EventQueryCondition errors = condition(null, null, Order.DES, 1, 10);
        errors.setType(EventType.Error);
        assertEquals(Arrays.asList(uuid(15), uuid(10), uuid(5), uuid(0)), uuids(QUERY_DAO.queryEvents(errors)));
        final EventQueryCondition mesh = condition(null, null, Order.DES, 1, 10);
        mesh.setLayer(Layer.MESH.name());
        assertEquals(Arrays.asList(uuid(17), uuid(14), uuid(11), uuid(8), uuid(5), uuid(2)), uuids(QUERY_DAO.queryEvents(mesh)));
    }

    @Test
    public void readsEachEventWhole() throws Exception {
        final EventQueryCondition byUuid = condition(null, null, Order.DES, 1, 10);
        byUuid.setUuid(uuid(5));
        final org.apache.skywalking.oap.server.core.query.type.event.Event event =
            QUERY_DAO.queryEvents(byUuid).getEvents().get(0);
        assertEquals(String.join("|", uuid(5), "service-c", "instance-5", "/endpoint-5", "Scale", "Error", "message 5",
                                 String.valueOf(FIRST + 5000L), String.valueOf(FIRST + 5500L), "MESH"),
                     String.join("|", event.getUuid(), event.getSource().getService(), event.getSource().getServiceInstance(),
                                 event.getSource().getEndpoint(), event.getName(), event.getType().name(), event.getMessage(),
                                 String.valueOf(event.getStartTime()), String.valueOf(event.getEndTime()), event.getLayer()));
    }

    private static List<String> uuids(Events events) {
        return events.getEvents().stream().map(e -> e.getUuid()).collect(Collectors.toList());
    }

    private static EventQueryCondition condition(String service, String name, Order order, int pageNum, int pageSize) {
        final EventQueryCondition condition = new EventQueryCondition();
        if (service != null) {
            condition.setSource(new Source(service, null, null));
        }
        condition.setName(name);
        condition.setOrder(order);
        condition.setTime(DURATION);
        condition.setPaging(new Pagination(pageNum, pageSize));
        return condition;
    }

    /**
     * service-a/Upgrade at both ends and service-b/Reboot just inside them; service-a/Reboot and service-b/Upgrade
     * match one field of each group; the rest match nothing.
     */
    private static Event event(int i) {
        final String service;
        final String name;
        if (i == 0 || i == EVENTS - 1) {
            service = "service-a";
            name = "Upgrade";
        } else if (i == 1 || i == EVENTS - 2) {
            service = "service-b";
            name = "Reboot";
        } else if (i % 2 == 0) {
            service = i % 4 == 0 ? "service-a" : "service-b";
            name = i % 4 == 0 ? "Reboot" : "Upgrade";
        } else {
            service = "service-c";
            name = "Scale";
        }
        final long timestamp = FIRST + i * 1000L;
        final Event event = new Event();
        event.setUuid(uuid(i));
        event.setService(service);
        event.setServiceInstance("instance-" + i);
        event.setEndpoint("/endpoint-" + i);
        event.setName(name);
        event.setType(i % 5 == 0 ? EventType.Error.name() : EventType.Normal.name());
        event.setMessage("message " + i);
        event.setParameters("{}");
        event.setStartTime(timestamp);
        event.setEndTime(timestamp + 500L);
        event.setLayer(i % 3 == 2 ? Layer.MESH : Layer.GENERAL);
        event.setTimestamp(timestamp);
        event.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        return event;
    }

    private static String uuid(int i) {
        return String.format("event-%02d", i);
    }
}
