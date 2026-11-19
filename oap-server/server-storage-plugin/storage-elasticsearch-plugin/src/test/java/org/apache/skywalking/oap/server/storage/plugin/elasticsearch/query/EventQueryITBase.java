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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.record.Event;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads events the way the event query service does on Elasticsearch and OpenSearch, through
 * {@link ESEventQueryDAO}: by uuid, service, instance, endpoint, name, type and layer, newest or oldest first, page by
 * page, and by several conditions, an OR of groups that each AND their fields, where the first condition's order,
 * paging and duration apply to the whole read. A nested class of a server's IT names the server and the storage mode.
 *
 * <p>The events are written on both sides of midnight, so a read spans two daily indices. For each filter an event
 * matches every other condition but that one, and two events of other groups match one field of each group. Events
 * of their own lie on and just outside the bounds of a duration: on its first millisecond and the one before, on its
 * last millisecond, on the first millisecond after it ({@link Duration#getEndTimestamp()}) and the millisecond after
 * that.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class EventQueryITBase {
    /** A fixed local midnight, so the daily indices the events are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    private static final long MINUTE = 60_000L;
    private static final Duration AROUND_MIDNIGHT = minutes(MIDNIGHT - 5 * MINUTE, MIDNIGHT + 4 * MINUTE);
    private static final Duration BOUNDED = minutes(MIDNIGHT + 10 * MINUTE, MIDNIGHT + 11 * MINUTE);

    private static final String CHECKOUT = "checkout";
    private static final String PAYMENT = "payment";
    private static final String BOUNDS = "bounds";
    private static final String POST_ORDERS = "POST /orders";

    /**
     * An event that has ended is stored at its end time, one that has only started at its start time, as the event
     * analyzer stores them.
     */
    private static final List<EventRow> EVENTS = Arrays.asList(
        new EventRow("ev1", MIDNIGHT - 150_000L, CHECKOUT, "checkout-1", POST_ORDERS, "Upgrade", EventType.Normal,
                     Layer.GENERAL, true),
        new EventRow("ev2", MIDNIGHT - 90_000L, CHECKOUT, "checkout-2", POST_ORDERS, "Reboot", EventType.Error,
                     Layer.MESH, false),
        new EventRow("ev3", MIDNIGHT - 30_000L, CHECKOUT, "checkout-1", "GET /orders", "Upgrade", EventType.Error,
                     Layer.GENERAL, true),
        new EventRow("ev4", MIDNIGHT + 30_000L, PAYMENT, "payment-1", "POST /pay", "Upgrade", EventType.Normal,
                     Layer.MESH, true),
        new EventRow("ev5", MIDNIGHT + 90_000L, CHECKOUT, "checkout-1", POST_ORDERS, "Reboot", EventType.Normal,
                     Layer.GENERAL, true),
        new EventRow("ev6", MIDNIGHT + 150_000L, PAYMENT, "payment-1", "POST /pay", "Reboot", EventType.Error,
                     Layer.GENERAL, false),
        new EventRow("before-start", BOUNDED.getStartTimestamp() - 1, BOUNDS, "bounds-1", "/", "Scale",
                     EventType.Normal, Layer.GENERAL, false),
        new EventRow("on-start", BOUNDED.getStartTimestamp(), BOUNDS, "bounds-1", "/", "Scale",
                     EventType.Normal, Layer.GENERAL, false),
        new EventRow("last-millisecond", BOUNDED.getEndTimestamp() - 1, BOUNDS, "bounds-1", "/", "Scale",
                     EventType.Normal, Layer.GENERAL, false),
        new EventRow("on-end", BOUNDED.getEndTimestamp(), BOUNDS, "bounds-1", "/", "Scale",
                     EventType.Normal, Layer.GENERAL, false),
        new EventRow("after-end", BOUNDED.getEndTimestamp() + 1, BOUNDS, "bounds-1", "/", "Scale",
                     EventType.Normal, Layer.GENERAL, false)
    );

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private ESEventQueryDAO dao;

    EventQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeEvents() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        final Model model = support.installRecord(Event.class);
        support.writeRecords(model, new Event.Builder(), EVENTS.stream()
            .map(EventRow::record).collect(Collectors.toList()));
        dao = new ESEventQueryDAO(support.getClient());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsTheEventsOfAServiceNewestOrOldestFirst() throws Exception {
        assertEquals(Arrays.asList("ev5", "ev3", "ev2", "ev1"), uuids(dao.queryEvents(
            condition().source(CHECKOUT, null, null).build())));
        assertEquals(Arrays.asList("ev1", "ev2", "ev3", "ev5"), uuids(dao.queryEvents(
            condition().source(CHECKOUT, null, null).order(Order.ASC).build())));
    }

    @Test
    public void readsTheEventsOfAnInstance() throws Exception {
        assertEquals(Arrays.asList("ev5", "ev3", "ev1"), uuids(dao.queryEvents(
            condition().source(null, "checkout-1", null).build())));
    }

    @Test
    public void readsTheEventsOfAnEndpoint() throws Exception {
        assertEquals(Arrays.asList("ev5", "ev2", "ev1"), uuids(dao.queryEvents(
            condition().source(null, null, POST_ORDERS).build())));
    }

    @Test
    public void readsAnEventByUuidWithOrWithoutADuration() throws Exception {
        assertEquals(Collections.singletonList("ev3"), uuids(dao.queryEvents(condition().uuid("ev3").build())));
        assertEquals(Collections.singletonList("ev3"), uuids(dao.queryEvents(
            condition().uuid("ev3").during(null).build())));
    }

    @Test
    public void readsTheEventsOfANameATypeAndALayer() throws Exception {
        assertEquals(Arrays.asList("ev4", "ev3", "ev1"), uuids(dao.queryEvents(condition().name("Upgrade").build())));
        assertEquals(Arrays.asList("ev6", "ev3", "ev2"), uuids(dao.queryEvents(
            condition().type(EventType.Error).build())));
        assertEquals(Arrays.asList("ev4", "ev2"), uuids(dao.queryEvents(condition().layer(Layer.MESH).build())));
    }

    @Test
    public void readsTheEventsOfEitherGroupOnly() throws Exception {
        assertEquals(Arrays.asList("ev5", "ev4", "ev2"), uuids(dao.queryEvents(Arrays.asList(
            condition().source(CHECKOUT, null, null).name("Reboot").build(),
            condition().source(PAYMENT, null, null).name("Upgrade").build()))));
        assertEquals(Arrays.asList("ev2", "ev4", "ev5"), uuids(dao.queryEvents(Arrays.asList(
            condition().source(CHECKOUT, null, null).name("Reboot").order(Order.ASC).build(),
            condition().source(PAYMENT, null, null).name("Upgrade").build()))));
    }

    @Test
    public void pagesThroughTheEventsOfAService() throws Exception {
        assertEquals(Arrays.asList("ev5", "ev3"), uuids(dao.queryEvents(
            condition().source(CHECKOUT, null, null).page(1, 2).build())));
        assertEquals(Arrays.asList("ev2", "ev1"), uuids(dao.queryEvents(
            condition().source(CHECKOUT, null, null).page(2, 2).build())));
        assertEquals(Collections.emptyList(), uuids(dao.queryEvents(
            condition().source(CHECKOUT, null, null).page(3, 2).build())));
    }

    @Test
    public void pagesThroughTheEventsOfTheGroups() throws Exception {
        final List<List<String>> pages = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            pages.add(uuids(dao.queryEvents(Arrays.asList(
                condition().source(CHECKOUT, null, null).name("Reboot").page(page, 2).build(),
                condition().source(PAYMENT, null, null).name("Upgrade").page(page, 2).build()))));
        }
        assertEquals(Arrays.asList(Arrays.asList("ev5", "ev4"), Collections.singletonList("ev2"),
                                   Collections.emptyList()), pages);
    }

    @Test
    public void readsEachEventWhole() throws Exception {
        final List<String> expected = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            expected.add(EVENTS.get(i).render());
        }
        assertEquals(expected, dao.queryEvents(condition().build()).getEvents().stream()
                                  .map(EventQueryITBase::render).collect(Collectors.toList()));
    }

    @Test
    public void keepsTheEventsWithinTheDuration() throws Exception {
        assertEquals(Arrays.asList("last-millisecond", "on-start"), uuids(dao.queryEvents(
            condition().source(BOUNDS, null, null).during(BOUNDED).build())));
    }

    private static Condition condition() {
        return new Condition();
    }

    private static List<String> uuids(final Events events) {
        return events.getEvents().stream().map(event -> event.getUuid()).collect(Collectors.toList());
    }

    private static String render(final org.apache.skywalking.oap.server.core.query.type.event.Event event) {
        return String.join("|", event.getUuid(), event.getSource().getService(),
                           event.getSource().getServiceInstance(), event.getSource().getEndpoint(), event.getName(),
                           event.getType().name(), event.getMessage(), event.getParameters().stream()
                               .map(kv -> kv.getKey() + "=" + kv.getValue()).collect(Collectors.joining(",")),
                           String.valueOf(event.getStartTime()), String.valueOf(event.getEndTime()),
                           String.valueOf(event.getTimestamp()), event.getLayer());
    }

    private static Duration minutes(final long start, final long end) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(new DateTime(start).toString("yyyy-MM-dd HHmm"));
        duration.setEnd(new DateTime(end).toString("yyyy-MM-dd HHmm"));
        return duration;
    }

    /**
     * An event query condition as the event query service passes it, newest first over the events around midnight
     * unless told otherwise.
     */
    private static final class Condition {
        private final EventQueryCondition condition = new EventQueryCondition();

        Condition() {
            condition.setTime(AROUND_MIDNIGHT);
            condition.setOrder(Order.DES);
            condition.setPaging(new Pagination(1, 20));
        }

        Condition uuid(final String uuid) {
            condition.setUuid(uuid);
            return this;
        }

        Condition source(final String service, final String instance, final String endpoint) {
            condition.setSource(new Source(service, instance, endpoint));
            return this;
        }

        Condition name(final String name) {
            condition.setName(name);
            return this;
        }

        Condition type(final EventType type) {
            condition.setType(type);
            return this;
        }

        Condition layer(final Layer layer) {
            condition.setLayer(layer.name());
            return this;
        }

        Condition order(final Order order) {
            condition.setOrder(order);
            return this;
        }

        Condition page(final int pageNum, final int pageSize) {
            condition.setPaging(new Pagination(pageNum, pageSize));
            return this;
        }

        Condition during(final Duration duration) {
            condition.setTime(duration);
            return this;
        }

        EventQueryCondition build() {
            return condition;
        }
    }

    /**
     * An event as the event analyzer hands it to the record stream.
     */
    private static final class EventRow {
        private final String uuid;
        private final long time;
        private final String service;
        private final String instance;
        private final String endpoint;
        private final String name;
        private final EventType type;
        private final Layer layer;
        private final long startTime;
        private final long endTime;

        EventRow(final String uuid, final long time, final String service, final String instance,
                 final String endpoint, final String name, final EventType type, final Layer layer,
                 final boolean ended) {
            this.uuid = uuid;
            this.time = time;
            this.service = service;
            this.instance = instance;
            this.endpoint = endpoint;
            this.name = name;
            this.type = type;
            this.layer = layer;
            this.startTime = ended ? time - 5_000L : time;
            this.endTime = ended ? time : 0;
        }

        Event record() {
            final Event event = new Event();
            event.setUuid(uuid);
            event.setService(service);
            event.setServiceInstance(instance);
            event.setEndpoint(endpoint);
            event.setName(name);
            event.setType(type.name());
            event.setMessage(name + " of " + uuid);
            event.setParameters("{\"replicas\":\"3\"}");
            event.setStartTime(startTime);
            event.setEndTime(endTime);
            event.setLayer(layer);
            event.setTimestamp(time);
            event.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
            return event;
        }

        String render() {
            return String.join("|", uuid, service, instance, endpoint, name, type.name(), name + " of " + uuid,
                               "replicas=3", String.valueOf(startTime), String.valueOf(endTime),
                               String.valueOf(time), layer.name());
        }
    }
}
