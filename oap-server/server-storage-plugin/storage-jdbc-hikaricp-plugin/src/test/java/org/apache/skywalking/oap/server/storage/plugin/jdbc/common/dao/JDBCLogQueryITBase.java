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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.Event;
import org.apache.skywalking.oap.server.core.analysis.record.LongText;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
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
import static org.mockito.Mockito.when;

/**
 * Reads events the way the event list and the alarm list do, browser error logs the way the browser log query does,
 * and logs the way the log query service does, on rows written on both sides of midnight, so in two day tables. A
 * subclass names the server.
 *
 * <p>Logs are read by service, instance, endpoint, trace, native span, OTLP span and searchable tag, newest or oldest
 * first, page by page. For each filter a line matches every other condition but that one; a tag kept in the raw tags
 * only is not searchable. Log lines and browser errors of their own lie on and just outside the bounds of a duration:
 * on its first millisecond and the one before, on its last millisecond, and just after its end.
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
    private static final long MINUTE = 60_000L;
    /** Every log line of the filter reads, which lie within three minutes of midnight. */
    private static final Duration AROUND_MIDNIGHT = JDBCQueryITSupport.duration(
        MIDNIGHT - 5 * MINUTE, MIDNIGHT + 4 * MINUTE, Step.MINUTE);
    private static final Duration BOUNDED = JDBCQueryITSupport.duration(
        MIDNIGHT + 10 * MINUTE, MIDNIGHT + 11 * MINUTE, Step.MINUTE);
    /** OAP's default {@code searchableLogsTags}, the tag keys a log line is searchable by. */
    private static final String SEARCHABLE_LOG_TAGS = "level,http.status_code";
    private static final String PAYMENT = "payment";
    private static final String BOUNDS = "bounds";
    private static final String POST_ORDERS = "POST /orders";
    private static final String TRACE_A = "trace-a";
    private static final String TRACE_B = "trace-b";
    private static final String OTLP_SPAN = "00f067aa0ba902b7";
    private static final List<Line> LINES = Arrays.asList(
        new Line("l1", MIDNIGHT - 150_000L, SERVICE, "checkout-1", POST_ORDERS)
            .nativeSpan(TRACE_A, "segment-a1", 1).text("order created").tags("level=INFO", "thread=main"),
        new Line("l2", MIDNIGHT - 90_000L, SERVICE, "checkout-1", POST_ORDERS)
            .nativeSpan(TRACE_A, "segment-a1", 2).text("payment timeout for order")
            .tags("level=ERROR", "http.status_code=500"),
        new Line("l3", MIDNIGHT - 30_000L, SERVICE, "checkout-2", POST_ORDERS)
            .nativeSpan(TRACE_A, "segment-a2", 1).text("order was created").tags("level=ERROR"),
        new Line("l4", MIDNIGHT + 30_000L, SERVICE, "checkout-1", "GET /orders")
            .otlpSpan(TRACE_B, OTLP_SPAN).text("order created again").tags("level=ERROR", "http.status_code=500"),
        new Line("l5", MIDNIGHT + 90_000L, SERVICE, "checkout-1", null)
            .otlpSpan(TRACE_B, "00f067aa0ba902b8").json("{\"event\":\"order created\"}"),
        new Line("l6", MIDNIGHT + 150_000L, PAYMENT, "payment-1", "POST /pay")
            .nativeSpan("trace-c", "segment-c1", 1).text("order created").tags("level=ERROR", "http.status_code=500"),
        new Line("before-start", BOUNDED.getStartTimestamp() - 1, BOUNDS, "bounds-1", null).text("health check"),
        new Line("on-start", BOUNDED.getStartTimestamp(), BOUNDS, "bounds-1", null).text("health check"),
        new Line("last-millisecond", BOUNDED.getEndTimestamp() - 1, BOUNDS, "bounds-1", null).text("health check"),
        new Line("after-end", BOUNDED.getEndTimestamp() + 1, BOUNDS, "bounds-1", null).text("health check")
    );
    private static final Map<Long, String> LINE_BY_TIME = new HashMap<>();
    private static final String BOUNDS_BROWSER_SERVICE = "bounds-web";
    /** Browser errors on and just outside the bounds, by their unique id. */
    private static final Object[][] BOUND_BROWSER_ERRORS = {
        {"before-start", BOUNDED.getStartTimestamp() - 1},
        {"on-start", BOUNDED.getStartTimestamp()},
        {"last-millisecond", BOUNDED.getEndTimestamp() - 1},
        {"after-end", BOUNDED.getEndTimestamp() + 1},
    };

    static {
        LINES.forEach(line -> LINE_BY_TIME.put(line.time, line.id));
    }

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCEventQueryDAO eventDAO;
    private JDBCBrowserLogQueryDAO browserLogDAO;
    private JDBCLogQueryDAO logDAO;

    JDBCLogQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeEventsBrowserErrorsAndLogs() throws Exception {
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
        for (final Object[] error : BOUND_BROWSER_ERRORS) {
            errors.add(browserError(BOUNDS_BROWSER_SERVICE, (String) error[0], (long) error[1]));
        }
        support.writeRecords(browserErrorModel, new BrowserErrorLogRecord.Builder(), errors);

        eventDAO = new JDBCEventQueryDAO(support.getClient(), support.getTableHelper());
        browserLogDAO = new JDBCBrowserLogQueryDAO(support.getClient(), support.getTableHelper());

        final var logModel = support.install(
            LogRecord.class, DefaultScopeDefine.LOG, LogRecord.INDEX_NAME, DownSampling.Second);
        support.writeRecords(logModel, new LogRecord.Builder(), LINES.stream()
            .map(Line::record).collect(Collectors.toList()));
        final ConfigService config = support.getModuleManager().find(CoreModule.NAME).provider()
                                            .getService(ConfigService.class);
        when(config.getSearchableLogsTags()).thenReturn(SEARCHABLE_LOG_TAGS);
        logDAO = new JDBCLogQueryDAO(support.getClient(), support.getModuleManager(), support.getTableHelper());
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
        return browserError(BROWSER_SERVICE, uniqueId, time);
    }

    private static BrowserErrorLogRecord browserError(final String service, final String uniqueId, final long time) {
        final String serviceId = IDManager.ServiceID.buildId(service, true);
        final String version = IDManager.ServiceInstanceID.buildId(serviceId, "v1.0.0");
        final BrowserErrorLogRecord record = new BrowserErrorLogRecord();
        record.setUniqueId(uniqueId);
        record.setServiceId(serviceId);
        record.setServiceVersionId(version);
        record.setPagePathId(IDManager.EndpointID.buildId(serviceId, "/cart"));
        record.setErrorCategory(BrowserErrorCategory.JS.getValue());
        record.setTimestamp(time);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
        record.setDataBinary(BrowserErrorLog.newBuilder()
                                            .setUniqueId(uniqueId)
                                            .setService(service)
                                            .setServiceVersion("v1.0.0")
                                            .setPagePath("/cart")
                                            .setCategory(ErrorCategory.js)
                                            .setMessage(uniqueId)
                                            .setTime(time)
                                            .build()
                                            .toByteArray());
        return record;
    }

    @Test
    public void readsTheLogsOfAServiceNewestOrOldestFirst() throws Exception {
        assertEquals(Arrays.asList("l5", "l4", "l3", "l2", "l1"), lines(read().service(SERVICE)));
        assertEquals(Arrays.asList("l1", "l2", "l3", "l4", "l5"), lines(read().service(SERVICE).order(Order.ASC)));
        assertEquals(Collections.singletonList("l6"), lines(read().service(PAYMENT)));
    }

    @Test
    public void readsTheLogsOfAnInstance() throws Exception {
        assertEquals(Arrays.asList("l5", "l4", "l2", "l1"), lines(read().instance(SERVICE, "checkout-1")));
    }

    @Test
    public void readsTheLogsOfAnEndpoint() throws Exception {
        assertEquals(Arrays.asList("l3", "l2", "l1"), lines(read().endpoint(SERVICE, POST_ORDERS)));
    }

    @Test
    public void readsTheLogsOfATrace() throws Exception {
        assertEquals(Arrays.asList("l3", "l2", "l1"), lines(read().trace(TRACE_A, null, null, null)));
        assertEquals(Arrays.asList("l5", "l4"), lines(read().trace(TRACE_B, null, null, null)));
    }

    /**
     * A native span is its segment and its number within the segment: the trace's other span of the segment, and
     * the span of the same number in its other segment, stay out.
     */
    @Test
    public void readsTheLogsOfANativeSpan() throws Exception {
        assertEquals(Collections.singletonList("l1"), lines(read().trace(TRACE_A, "segment-a1", 1, null)));
        assertEquals(Collections.singletonList("l2"), lines(read().trace(TRACE_A, "segment-a1", 2, null)));
    }

    @Test
    public void readsTheLogsOfAnOtlpSpan() throws Exception {
        assertEquals(Collections.singletonList("l4"), lines(read().trace(TRACE_B, null, null, OTLP_SPAN)));
    }

    @Test
    public void readsTheLogsHavingEverySearchableTag() throws Exception {
        assertEquals(Arrays.asList("l6", "l4", "l3", "l2"), lines(read().tags("level=ERROR")));
        assertEquals(Arrays.asList("l6", "l4", "l2"), lines(read().tags("level=ERROR", "http.status_code=500")));
        assertEquals(Collections.emptyList(), lines(read().tags("thread=main")));
    }

    @Test
    public void pagesThroughTheLogsOfAService() throws Exception {
        assertEquals(Arrays.asList("l5", "l4"), lines(read().service(SERVICE).page(0, 2)));
        assertEquals(Arrays.asList("l3", "l2"), lines(read().service(SERVICE).page(2, 2)));
        assertEquals(Collections.singletonList("l1"), lines(read().service(SERVICE).page(4, 2)));
        assertEquals(Collections.emptyList(), lines(read().service(SERVICE).page(6, 2)));
    }

    @Test
    public void readsEachLogWhole() throws Exception {
        final List<String> expected = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            expected.add(LINES.get(i).render());
        }
        assertEquals(expected, read().run(logDAO).stream().map(JDBCLogQueryITBase::render)
                                     .collect(Collectors.toList()));
    }

    @Test
    public void readsTheLogsOfEveryDayWithoutADuration() throws Exception {
        assertEquals(Arrays.asList("l3", "l2", "l1"), lines(read().trace(TRACE_A, null, null, null).during(null)));
    }

    @Test
    public void keepsTheLogsWithinTheDuration() throws Exception {
        assertEquals(Arrays.asList("last-millisecond", "on-start"), lines(read().service(BOUNDS).during(BOUNDED)));
    }

    @Test
    public void keepsTheBrowserErrorsWithinTheDuration() {
        assertEquals(Arrays.asList("last-millisecond", "on-start"), boundBrowserErrors(BOUNDED));
    }

    private List<String> boundBrowserErrors(final Duration duration) {
        return browserLogDAO.queryBrowserErrorLogs(IDManager.ServiceID.buildId(BOUNDS_BROWSER_SERVICE, true), null,
                                                   null, null, duration, 10, 0)
                            .getLogs()
                            .stream()
                            .map(log -> log.getMessage())
                            .collect(Collectors.toList());
    }

    private List<String> lines(final LogRead read) {
        return read.run(logDAO).stream().map(log -> LINE_BY_TIME.get(log.getTimestamp())).collect(Collectors.toList());
    }

    private static LogRead read() {
        return new LogRead();
    }

    private static String render(final Log log) {
        return String.join("|", log.getServiceId(), log.getServiceInstanceId(), log.getEndpointId(),
                           log.getEndpointName(), log.getTraceId(), String.valueOf(log.getTimestamp()),
                           log.getContentType().name(), log.getContent(), log.getTags().stream()
                               .map(tag -> tag.getKey() + "=" + tag.getValue()).collect(Collectors.joining(",")));
    }

    private static String serviceId(final String service) {
        return IDManager.ServiceID.buildId(service, true);
    }

    /**
     * The arguments of one log read, as the log query service passes them.
     */
    private static final class LogRead {
        private String serviceId;
        private String instanceId;
        private String endpointId;
        private TraceScopeCondition trace;
        private Order order = Order.DES;
        private int from = 0;
        private int limit = 100;
        private Duration duration = AROUND_MIDNIGHT;
        private List<Tag> tags;

        LogRead service(final String service) {
            serviceId = serviceId(service);
            return this;
        }

        LogRead instance(final String service, final String instance) {
            instanceId = IDManager.ServiceInstanceID.buildId(serviceId(service), instance);
            return this;
        }

        LogRead endpoint(final String service, final String endpoint) {
            endpointId = IDManager.EndpointID.buildId(serviceId(service), endpoint);
            return this;
        }

        LogRead trace(final String traceId, final String segmentId, final Integer spanId, final String otlpSpanId) {
            trace = new TraceScopeCondition();
            trace.setTraceId(traceId);
            trace.setSegmentId(segmentId);
            trace.setSpanId(spanId);
            trace.setOtlpSpanId(otlpSpanId);
            return this;
        }

        LogRead order(final Order order) {
            this.order = order;
            return this;
        }

        LogRead page(final int from, final int limit) {
            this.from = from;
            this.limit = limit;
            return this;
        }

        LogRead during(final Duration duration) {
            this.duration = duration;
            return this;
        }

        LogRead tags(final String... tags) {
            this.tags = Arrays.stream(tags).map(tag -> tag.split("=")).map(kv -> new Tag(kv[0], kv[1]))
                              .collect(Collectors.toList());
            return this;
        }

        List<Log> run(final JDBCLogQueryDAO dao) {
            return dao.queryLogs(serviceId, instanceId, endpointId, trace, order, from, limit, duration, tags,
                                 null, null).getLogs();
        }
    }

    /**
     * A log line as the log analyzer hands it to the record stream: every tag in the raw tags, the searchable ones
     * indexed.
     */
    private static final class Line {
        private final String id;
        private final long time;
        private final String serviceId;
        private final String instanceId;
        private final String endpointId;
        private final String endpoint;
        private String traceId;
        private String segmentId;
        private int spanId;
        private String otlpSpanId;
        private ContentType contentType;
        private String content;
        private List<String> tags = Collections.emptyList();

        Line(final String id, final long time, final String service, final String instance, final String endpoint) {
            this.id = id;
            this.time = time;
            this.serviceId = serviceId(service);
            this.instanceId = IDManager.ServiceInstanceID.buildId(serviceId, instance);
            this.endpointId = endpoint == null ? null : IDManager.EndpointID.buildId(serviceId, endpoint);
            this.endpoint = endpoint;
        }

        Line nativeSpan(final String traceId, final String segmentId, final int spanId) {
            this.traceId = traceId;
            this.segmentId = segmentId;
            this.spanId = spanId;
            return this;
        }

        Line otlpSpan(final String traceId, final String otlpSpanId) {
            this.traceId = traceId;
            this.otlpSpanId = otlpSpanId;
            return this;
        }

        Line text(final String content) {
            this.contentType = ContentType.TEXT;
            this.content = content;
            return this;
        }

        Line json(final String content) {
            this.contentType = ContentType.JSON;
            this.content = content;
            return this;
        }

        Line tags(final String... tags) {
            this.tags = Arrays.asList(tags);
            return this;
        }

        LogRecord record() {
            final LogRecord record = new LogRecord();
            record.setUniqueId(id);
            record.setServiceId(serviceId);
            record.setServiceInstanceId(instanceId);
            record.setEndpointId(endpointId);
            record.setTraceId(traceId);
            record.setTraceSegmentId(segmentId);
            record.setSpanId(spanId);
            record.setOtlpSpanId(otlpSpanId);
            record.setContentType(contentType.value());
            record.setContent(new LongText(content));
            record.setTimestamp(time);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
            if (!tags.isEmpty()) {
                final LogTags.Builder raw = LogTags.newBuilder();
                tags.forEach(tag -> raw.addData(KeyStringValuePair.newBuilder()
                                                                  .setKey(tag.split("=")[0])
                                                                  .setValue(tag.split("=")[1])));
                record.setTagsRawData(raw.build().toByteArray());
            }
            final List<String> searchable = Arrays.asList(SEARCHABLE_LOG_TAGS.split(","));
            record.setTagsInString(tags.stream().filter(tag -> searchable.contains(tag.split("=")[0]))
                                       .collect(Collectors.toList()));
            return record;
        }

        String render() {
            return String.join("|", serviceId, instanceId, endpointId, endpoint, traceId, String.valueOf(time),
                               contentType.name(), content, String.join(",", tags));
        }
    }
}
