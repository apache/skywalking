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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.record.Event;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads events the way the event list and the alarm list do, and browser error logs the way the browser log query
 * does, on rows written on both sides of midnight, so in two day tables. A subclass names the server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCLogQueryITBase {
    private static final String SERVICE = "checkout";
    /**
     * Event uuid, offset from midnight in seconds, layer. Kubernetes reports events to the second, so an event often
     * starts on a minute's first millisecond, as {@code e3} at midnight and {@code e5} a minute later do.
     */
    private static final Object[][] EVENTS = {
        {"e1", -90, Layer.MESH},
        {"e2", -30, Layer.GENERAL},
        {"e3", 0, Layer.MESH},
        {"e4", 30, Layer.GENERAL},
        {"e5", 60, Layer.MESH},
    };
    private static final String BROWSER_SERVICE = "shop-web";
    private static final String BROWSER_SERVICE_ID = IDManager.ServiceID.buildId(BROWSER_SERVICE, true);
    /** Browser error unique id, offset from midnight in seconds. Two before midnight, two after. */
    private static final Object[][] BROWSER_ERRORS = {
        {"b1", -20},
        {"b2", -10},
        {"b3", 10},
        {"b4", 20},
    };

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCEventQueryDAO eventDAO;
    private JDBCBrowserLogQueryDAO browserLogDAO;

    JDBCLogQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeEventsAndBrowserErrors() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        final var eventModel = support.install(
            Event.class, DefaultScopeDefine.EVENT, Event.INDEX_NAME, DownSampling.Second);
        final List<Event> events = new ArrayList<>();
        for (final Object[] event : EVENTS) {
            events.add(event((String) event[0], MIDNIGHT + (int) event[1] * 1000L, (Layer) event[2]));
        }
        support.writeRecords(eventModel, new Event.Builder(), events);

        final var browserErrorModel = support.install(
            BrowserErrorLogRecord.class, DefaultScopeDefine.BROWSER_ERROR_LOG, BrowserErrorLogRecord.INDEX_NAME,
            DownSampling.Second);
        final List<BrowserErrorLogRecord> errors = new ArrayList<>();
        for (final Object[] error : BROWSER_ERRORS) {
            errors.add(browserError((String) error[0], MIDNIGHT + (int) error[1] * 1000L));
        }
        support.writeRecords(browserErrorModel, new BrowserErrorLogRecord.Builder(), errors);

        eventDAO = new JDBCEventQueryDAO(support.getClient(), support.getTableHelper());
        browserLogDAO = new JDBCBrowserLogQueryDAO(support.getClient(), support.getTableHelper());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * The layer column is an INT; PostgreSQL compares it with an INT parameter only.
     */
    @Test
    public void readsTheEventsOfALayerOnBothDays() throws Exception {
        final EventQueryCondition mesh = condition(
            JDBCQueryITSupport.duration(MIDNIGHT - 120_000L, MIDNIGHT + 60_000L, Step.MINUTE));
        mesh.setLayer(Layer.MESH.name());
        assertEquals(Arrays.asList("e5", "e3", "e1"), uuids(eventDAO.queryEvents(mesh)));
        assertEquals(Arrays.asList("e5", "e3", "e1"), uuids(eventDAO.queryEvents(Collections.singletonList(mesh))));
    }

    /**
     * A minute window holds the events from its first millisecond up to, not including, the next minute's first, so
     * an event on a minute's first millisecond belongs to that minute alone.
     */
    @Test
    public void readsAnEventOnItsMinutesFirstMillisecond() throws Exception {
        assertEquals(Collections.singletonList("e2"), uuids(eventDAO.queryEvents(minute(MIDNIGHT - 60_000L))));
        assertEquals(Arrays.asList("e4", "e3"), uuids(eventDAO.queryEvents(minute(MIDNIGHT))));
        assertEquals(Collections.singletonList("e5"), uuids(eventDAO.queryEvents(minute(MIDNIGHT + 60_000L))));
    }

    /**
     * The query protocol's duration of a browser error log query is optional; without one the read covers every
     * day table within the record TTL.
     */
    @Test
    public void readsTheBrowserErrorsOfBothDaysWithoutADuration() {
        assertEquals(Arrays.asList("b4", "b3", "b2", "b1"), browserErrors(10, 0));
        assertEquals(Arrays.asList("b3", "b2"), browserErrors(2, 1));
    }

    private List<String> browserErrors(final int limit, final int from) {
        return browserLogDAO.queryBrowserErrorLogs(BROWSER_SERVICE_ID, null, null, null, null, limit, from)
                            .getLogs()
                            .stream()
                            .map(log -> log.getMessage())
                            .collect(Collectors.toList());
    }

    private static EventQueryCondition minute(final long start) {
        return condition(JDBCQueryITSupport.duration(start, start, Step.MINUTE));
    }

    private static EventQueryCondition condition(final Duration time) {
        final EventQueryCondition condition = new EventQueryCondition();
        condition.setTime(time);
        condition.setOrder(Order.DES);
        condition.setPaging(new Pagination(1, 10));
        return condition;
    }

    private static List<String> uuids(final Events events) {
        return events.getEvents()
                     .stream()
                     .map(event -> event.getUuid())
                     .collect(Collectors.toList());
    }

    private static Event event(final String uuid, final long startTime, final Layer layer) {
        final Event event = new Event();
        event.setUuid(uuid);
        event.setService(SERVICE);
        event.setServiceInstance(SERVICE + "-1");
        event.setEndpoint("POST /orders");
        event.setName(layer == Layer.MESH ? "Reboot" : "Upgrade");
        event.setType(EventType.Normal.name());
        event.setMessage("event " + uuid);
        event.setParameters("{}");
        event.setStartTime(startTime);
        event.setEndTime(startTime + 5_000L);
        event.setLayer(layer);
        event.setTimestamp(startTime);
        event.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
        return event;
    }

    private static BrowserErrorLogRecord browserError(final String uniqueId, final long time) {
        final String version = IDManager.ServiceInstanceID.buildId(BROWSER_SERVICE_ID, "v1.0.0");
        final BrowserErrorLogRecord record = new BrowserErrorLogRecord();
        record.setUniqueId(uniqueId);
        record.setServiceId(BROWSER_SERVICE_ID);
        record.setServiceVersionId(version);
        record.setPagePathId(IDManager.EndpointID.buildId(BROWSER_SERVICE_ID, "/cart"));
        record.setErrorCategory(BrowserErrorCategory.JS.getValue());
        record.setTimestamp(time);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
        record.setDataBinary(BrowserErrorLog.newBuilder()
                                            .setUniqueId(uniqueId)
                                            .setService(BROWSER_SERVICE)
                                            .setServiceVersion("v1.0.0")
                                            .setPagePath("/cart")
                                            .setCategory(ErrorCategory.js)
                                            .setMessage(uniqueId)
                                            .setTime(time)
                                            .build()
                                            .toByteArray());
        return record;
    }
}
