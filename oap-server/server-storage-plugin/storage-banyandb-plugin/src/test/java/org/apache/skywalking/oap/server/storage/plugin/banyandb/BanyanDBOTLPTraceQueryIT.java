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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBOTLPTraceQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Searches natively stored OTLP spans the way the TraceQL API does, on the real {@code otlp_span} trace, and reads
 * one trace by id. Every condition the search takes matches only the first and the last span of 30, so a matching
 * trace must be found behind every newer or longer one, and must come back whole. The trace's index answers the
 * service, the kind and the status; the instance, the scope, the span name, the peer service and the attributes
 * are residual filters on the spans BanyanDB reads.
 *
 * <p>The DAO serves {@code data_binary} untouched, so each span stores its label there instead of a
 * {@code ResourceSpans}.
 */
@Testcontainers
public class BanyanDBOTLPTraceQueryIT {
    /** Spans of one service, one second apart, each its own trace; the first and the last are the ones read. */
    private static final int SPANS = 30;
    private static final String SERVICE = "otlp-query-it";
    private static final String READ_INSTANCE = "instance-read";
    private static final String READ_SCOPE = "io.opentelemetry.read";
    private static final String READ_NAME = "POST /read";
    private static final String READ_PEER = "peer-read";
    private static final int SERVER = 2;
    private static final int CLIENT = 3;
    private static final int INTERNAL = 1;
    private static final int STATUS_ERROR = 2;
    private static final int STATUS_OK = 1;
    private static final int STATUS_UNSET = 0;
    /** The first trace's child span, on another instance; it matches no condition the tests search by. */
    private static final String CHILD = label(0) + " child";

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBOTLPTraceQueryDAO QUERY_DAO;
    private static Duration DURATION;

    @BeforeAll
    public static void writeSpans() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(OTLPSpanRecord.class, DefaultScopeDefine.OTLP_SPAN, OTLPSpanRecord.INDEX_NAME);
        final long first = System.currentTimeMillis() - SPANS * 1000L;
        final List<OTLPSpanRecord> spans = new ArrayList<>();
        for (int i = 0; i < SPANS; i++) {
            final OTLPSpanRecord span = i == 0 || i == SPANS - 1
                // The read spans are the shortest, so a search by duration finds them behind every other trace.
                ? span(label(i), traceId(i), spanId(i), "", READ_INSTANCE, READ_SCOPE, READ_NAME, SERVER, STATUS_ERROR,
                       READ_PEER, first + i * 1000L, i == 0 ? 2_000L : 1_000L, "POST")
                : span(label(i), traceId(i), spanId(i), "", "instance-other", "io.opentelemetry.other", "GET /other",
                       INTERNAL, STATUS_OK, "peer-other", first + i * 1000L, 100_000L + i, "GET");
            spans.add(span);
        }
        spans.add(span(CHILD, traceId(0), "00000000000000ff", spanId(0), "instance-other", "io.opentelemetry.other",
                       "GET /other", CLIENT, STATUS_UNSET, "peer-other", first + 500L, 50_000L, "GET"));
        SUPPORT.write(model, new OTLPSpanRecord.Builder(), spans);

        QUERY_DAO = new BanyanDBOTLPTraceQueryDAO(SUPPORT.getClient());
        DURATION = BanyanDBQueryITSupport.around(first, first + (SPANS - 1) * 1000L);
        await().atMost(30, TimeUnit.SECONDS)
               .untilAsserted(() -> assertEquals(SPANS, search(condition -> { }, QueryOrder.BY_START_TIME,
                                                               SPANS * 2).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsTheWholeTraceById() throws IOException {
        assertEquals(Arrays.asList(label(0), CHILD), labels(QUERY_DAO.queryTraceById(traceId(0), DURATION)));
    }

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("searches")
    public void findsEveryMatchingTrace(Condition condition, QueryOrder order) throws IOException {
        assertEquals(expected(order), search(condition.criterion, order, 2));
    }

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("searches")
    public void findsTheFirstMatchingTraceAlone(Condition condition, QueryOrder order) throws IOException {
        assertEquals(expected(order).subList(0, 1), search(condition.criterion, order, 1));
    }

    /** What the TraceQL search can filter by, each matching only the first and the last span. */
    enum Condition {
        INSTANCE(condition -> condition.setServiceInstance(READ_INSTANCE)),
        SCOPE(condition -> condition.setScopeName(READ_SCOPE)),
        SPAN_NAME(condition -> condition.setSpanName(READ_NAME)),
        PEER_SERVICE(condition -> condition.setPeerService(READ_PEER)),
        KIND(condition -> condition.setKind(SERVER)),
        STATUS(condition -> condition.setStatusCode(STATUS_ERROR)),
        MAX_DURATION(condition -> condition.setMaxDurationNanos(2_000L)),
        ATTRIBUTE(condition -> condition.setTags(Collections.singletonList(new Tag("http.method", "POST")))),
        SPAN_ATTRIBUTE(condition -> condition.setTags(Collections.singletonList(new Tag("span.http.method", "POST"))));

        private final Consumer<OTLPTraceQueryCondition> criterion;

        Condition(Consumer<OTLPTraceQueryCondition> criterion) {
            this.criterion = criterion;
        }
    }

    static Stream<Arguments> searches() {
        return Arrays.stream(Condition.values()).flatMap(condition -> Stream.of(
            Arguments.of(condition, QueryOrder.BY_START_TIME),
            Arguments.of(condition, QueryOrder.BY_DURATION)
        ));
    }

    /** The two matching traces in the order asked for, each whole: the first trace also holds its child span. */
    private static List<List<String>> expected(QueryOrder order) {
        final List<String> firstTrace = Arrays.asList(label(0), CHILD);
        final List<String> lastTrace = Collections.singletonList(label(SPANS - 1));
        return order == QueryOrder.BY_START_TIME
            ? Arrays.asList(lastTrace, firstTrace)
            : Arrays.asList(firstTrace, lastTrace);
    }

    /** The sorted span labels of each trace found. */
    private static List<List<String>> search(Consumer<OTLPTraceQueryCondition> criterion, QueryOrder order,
                                             int limit) throws IOException {
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        criterion.accept(condition);
        condition.setQueryDuration(DURATION);
        condition.setQueryOrder(order);
        condition.setLimit(limit);
        final List<List<String>> traces = new ArrayList<>();
        for (final List<SpanWrapper> trace : QUERY_DAO.queryTraces(condition)) {
            traces.add(labels(trace));
        }
        return traces;
    }

    private static List<String> labels(List<SpanWrapper> trace) {
        final List<String> labels = new ArrayList<>();
        for (final SpanWrapper span : trace) {
            labels.add(span.getSpan().toStringUtf8());
        }
        Collections.sort(labels);
        return labels;
    }

    private static OTLPSpanRecord span(String label, String traceId, String spanId, String parentSpanId,
                                       String instance, String scope, String name, int kind, int statusCode,
                                       String peer, long startTime, long durationNanos, String httpMethod) {
        final OTLPSpanRecord span = new OTLPSpanRecord();
        span.setTraceId(traceId);
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setServiceName(SERVICE);
        span.setServiceInstance(instance);
        span.setScopeName(scope);
        span.setName(name);
        span.setKind(kind);
        span.setStatusCode(statusCode);
        span.setPeerService(peer);
        span.setStartTime(startTime);
        span.setDuration(durationNanos);
        span.setTags(Arrays.asList(OTLPSpanRecord.RESOURCE_TAG_PREFIX + "service.name=" + SERVICE,
                                   OTLPSpanRecord.SPAN_TAG_PREFIX + "http.method=" + httpMethod));
        span.setDataBinary(label.getBytes(StandardCharsets.UTF_8));
        span.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
        return span;
    }

    private static String label(int index) {
        return "span " + index;
    }

    private static String traceId(int index) {
        return String.format("%032x", index + 1);
    }

    private static String spanId(int index) {
        return String.format("%016x", index + 1);
    }
}
