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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.LongText;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads logs the way the log query service does on Elasticsearch and OpenSearch, through {@link LogQueryEsDAO}: by
 * service, instance, endpoint, trace, native span, OTLP span, searchable tag, and keywords the content has or must
 * not have, newest or oldest first, page by page. A nested class of a server's IT names the server and the storage
 * mode.
 *
 * <p>The lines are written as the log analyzer writes them, on both sides of midnight, so a read spans two daily
 * indices. For each filter a line matches every other condition but that one. Lines of their own lie on and just
 * outside the bounds of a duration: on its first millisecond and the one before, on its last millisecond, and just
 * after its end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class LogQueryITBase {
    /** A fixed local midnight, so the daily indices the lines are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    private static final long MINUTE = 60_000L;
    /** Every line of the filter reads, which lie within three minutes of midnight. */
    private static final Duration AROUND_MIDNIGHT = minutes(MIDNIGHT - 5 * MINUTE, MIDNIGHT + 4 * MINUTE);
    private static final Duration BOUNDED = minutes(MIDNIGHT + 10 * MINUTE, MIDNIGHT + 11 * MINUTE);
    /** The tag keys OAP's default {@code searchableLogsTags} indexes; the others are kept in the raw tags only. */
    private static final List<String> SEARCHABLE_TAG_KEYS = Arrays.asList("level", "http.status_code");

    private static final String CHECKOUT = "checkout";
    private static final String PAYMENT = "payment";
    private static final String BOUNDS = "bounds";
    private static final String POST_ORDERS = "POST /orders";
    private static final String TRACE_A = "trace-a";
    private static final String TRACE_B = "trace-b";
    private static final String OTLP_SPAN = "00f067aa0ba902b7";

    private static final List<Line> LINES = Arrays.asList(
        new Line("l1", MIDNIGHT - 150_000L, CHECKOUT, "checkout-1", POST_ORDERS)
            .nativeSpan(TRACE_A, "segment-a1", 1).text("order created").tags("level=INFO", "thread=main"),
        new Line("l2", MIDNIGHT - 90_000L, CHECKOUT, "checkout-1", POST_ORDERS)
            .nativeSpan(TRACE_A, "segment-a1", 2).text("payment timeout for order")
            .tags("level=ERROR", "http.status_code=500"),
        new Line("l3", MIDNIGHT - 30_000L, CHECKOUT, "checkout-2", POST_ORDERS)
            .nativeSpan(TRACE_A, "segment-a2", 1).text("order was created").tags("level=ERROR"),
        new Line("l4", MIDNIGHT + 30_000L, CHECKOUT, "checkout-1", "GET /orders")
            .otlpSpan(TRACE_B, OTLP_SPAN).text("order created again").tags("level=ERROR", "http.status_code=500"),
        new Line("l5", MIDNIGHT + 90_000L, CHECKOUT, "checkout-1", null)
            .otlpSpan(TRACE_B, "00f067aa0ba902b8").json("{\"event\":\"order created\"}"),
        new Line("l6", MIDNIGHT + 150_000L, PAYMENT, "payment-1", "POST /pay")
            .nativeSpan("trace-c", "segment-c1", 1).text("order created").tags("level=ERROR", "http.status_code=500"),
        new Line("before-start", BOUNDED.getStartTimestamp() - 1, BOUNDS, "bounds-1", null).text("health check"),
        new Line("on-start", BOUNDED.getStartTimestamp(), BOUNDS, "bounds-1", null).text("health check"),
        new Line("last-millisecond", BOUNDED.getEndTimestamp() - 1, BOUNDS, "bounds-1", null).text("health check"),
        new Line("after-end", BOUNDED.getEndTimestamp() + 1, BOUNDS, "bounds-1", null).text("health check")
    );
    private static final Map<Long, String> ID_BY_TIME = new HashMap<>();

    static {
        LINES.forEach(line -> ID_BY_TIME.put(line.time, line.id));
    }

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private LogQueryEsDAO dao;

    LogQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeLines() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        final Model model = support.installRecord(LogRecord.class);
        support.writeRecords(model, new LogRecord.Builder(), LINES.stream()
            .map(Line::record).collect(Collectors.toList()));
        dao = new LogQueryEsDAO(support.getClient());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsTheLogsOfAServiceNewestOrOldestFirst() throws IOException {
        assertEquals(Arrays.asList("l5", "l4", "l3", "l2", "l1"), ids(read().service(CHECKOUT)));
        assertEquals(Arrays.asList("l1", "l2", "l3", "l4", "l5"), ids(read().service(CHECKOUT).order(Order.ASC)));
        assertEquals(Collections.singletonList("l6"), ids(read().service(PAYMENT)));
    }

    @Test
    public void readsTheLogsOfAnInstance() throws IOException {
        assertEquals(Arrays.asList("l5", "l4", "l2", "l1"), ids(read().instance(CHECKOUT, "checkout-1")));
    }

    @Test
    public void readsTheLogsOfAnEndpoint() throws IOException {
        assertEquals(Arrays.asList("l3", "l2", "l1"), ids(read().endpoint(CHECKOUT, POST_ORDERS)));
    }

    @Test
    public void readsTheLogsOfATrace() throws IOException {
        assertEquals(Arrays.asList("l3", "l2", "l1"), ids(read().trace(TRACE_A, null, null, null)));
        assertEquals(Arrays.asList("l5", "l4"), ids(read().trace(TRACE_B, null, null, null)));
    }

    /**
     * A native span is its segment and its number within the segment: the trace's other span of the segment, and
     * the span of the same number in its other segment, stay out.
     */
    @Test
    public void readsTheLogsOfANativeSpan() throws IOException {
        assertEquals(Collections.singletonList("l1"), ids(read().trace(TRACE_A, "segment-a1", 1, null)));
        assertEquals(Collections.singletonList("l2"), ids(read().trace(TRACE_A, "segment-a1", 2, null)));
    }

    @Test
    public void readsTheLogsOfAnOtlpSpan() throws IOException {
        assertEquals(Collections.singletonList("l4"), ids(read().trace(TRACE_B, null, null, OTLP_SPAN)));
    }

    /**
     * Only the searchable tags are indexed, so a tag kept in the raw tags alone finds nothing.
     */
    @Test
    public void readsTheLogsHavingEverySearchableTag() throws IOException {
        assertEquals(Arrays.asList("l6", "l4", "l3", "l2"), ids(read().tags("level=ERROR")));
        assertEquals(Arrays.asList("l6", "l4", "l2"), ids(read().tags("level=ERROR", "http.status_code=500")));
        assertEquals(Collections.emptyList(), ids(read().tags("thread=main")));
    }

    /**
     * A keyword is a phrase of the content: {@code order was created} does not have {@code order created}.
     */
    @Test
    public void readsTheLogsHavingEveryKeywordAndNoExcludedOne() throws IOException {
        assertTrue(dao.supportQueryLogsByKeywords());
        assertEquals(Arrays.asList("l6", "l5", "l4", "l1"), ids(read().keywords("order created")));
        assertEquals(Collections.singletonList("l2"), ids(read().keywords("order", "timeout")));
        assertEquals(Arrays.asList("l6", "l5", "l1"), ids(read().keywords("order created").excluding("again")));
        assertEquals(Collections.singletonList("l2"), ids(read().service(CHECKOUT).excluding("created")));
    }

    @Test
    public void pagesThroughTheLogsOfAService() throws IOException {
        assertEquals(Arrays.asList("l5", "l4"), ids(read().service(CHECKOUT).page(0, 2)));
        assertEquals(Arrays.asList("l3", "l2"), ids(read().service(CHECKOUT).page(2, 2)));
        assertEquals(Collections.singletonList("l1"), ids(read().service(CHECKOUT).page(4, 2)));
        assertEquals(Collections.emptyList(), ids(read().service(CHECKOUT).page(6, 2)));
    }

    @Test
    public void readsEachLogWhole() throws IOException {
        final List<String> expected = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            expected.add(LINES.get(i).render());
        }
        assertEquals(expected, read().run(dao).stream().map(LogQueryITBase::render).collect(Collectors.toList()));
    }

    @Test
    public void readsTheLogsOfEveryDayWithoutADuration() throws IOException {
        assertEquals(Arrays.asList("l3", "l2", "l1"), ids(read().trace(TRACE_A, null, null, null).during(null)));
    }

    @Test
    public void keepsTheLogsWithinTheDuration() throws IOException {
        assertEquals(Arrays.asList("last-millisecond", "on-start"), ids(read().service(BOUNDS).during(BOUNDED)));
    }

    private List<String> ids(final LogRead read) throws IOException {
        return read.run(dao).stream().map(log -> ID_BY_TIME.get(log.getTimestamp())).collect(Collectors.toList());
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

    /**
     * A duration as the query protocol writes it for minute steps.
     */
    private static Duration minutes(final long start, final long end) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(new DateTime(start).toString("yyyy-MM-dd HHmm"));
        duration.setEnd(new DateTime(end).toString("yyyy-MM-dd HHmm"));
        return duration;
    }

    private static String serviceId(final String service) {
        return IDManager.ServiceID.buildId(service, true);
    }

    /**
     * The arguments of one read, as the log query service passes them.
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
        private List<String> keywords;
        private List<String> excluding;

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

        LogRead keywords(final String... keywords) {
            this.keywords = Arrays.asList(keywords);
            return this;
        }

        LogRead excluding(final String... keywords) {
            this.excluding = Arrays.asList(keywords);
            return this;
        }

        List<Log> run(final LogQueryEsDAO dao) throws IOException {
            return dao.queryLogs(serviceId, instanceId, endpointId, trace, order, from, limit, duration, tags,
                                 keywords, excluding).getLogs();
        }
    }

    /**
     * A log line as the log analyzer hands it to the record stream.
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
            record.setTagsInString(tags.stream()
                                       .filter(tag -> SEARCHABLE_TAG_KEYS.contains(tag.split("=")[0]))
                                       .collect(Collectors.toList()));
            return record;
        }

        String render() {
            return String.join("|", serviceId, instanceId, endpointId, endpoint, traceId, String.valueOf(time),
                               contentType.name(), content, String.join(",", tags));
        }
    }
}
