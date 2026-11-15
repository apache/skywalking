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
import java.util.stream.Stream;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.record.LongText;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBLogQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads logs by trace the way the query service does, in each form it sends, on the real {@code log} stream. A
 * trace's lines must come back wherever they sit in their series, not only within the first {@code limit + offset}
 * lines of it (apache/skywalking#14215, fixed in BanyanDB by apache/skywalking-banyandb#1476), and a span read must
 * return only that span's lines, not the rest of its trace. {@code trace_id} has a skipping index and the span ids
 * none; only the native span form also names the inverted-indexed {@code trace_segment_id}.
 */
@Testcontainers
public class BanyanDBLogQueryIT {
    /** Lines in the one series, one second apart, each its own trace except the two pairs at the ends. */
    private static final int LINES = 30;
    /**
     * The split trace, one segment: the first and the last line were written by the read span, the second and the
     * second-to-last by another span of it, and every other line of the series sits between them.
     */
    private static final String SPLIT_TRACE = "trace-split";
    private static final String SPLIT_SEGMENT = "segment-split";
    private static final int READ_SPAN = 3;
    private static final int OTHER_SPAN = 4;
    private static final String READ_OTLP_SPAN = "00f067aa0ba902b7";
    private static final String OTHER_OTLP_SPAN = "00f067aa0ba902b8";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("log-query-it", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "instance");

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBLogQueryDAO QUERY_DAO;
    private static Duration DURATION;

    @BeforeAll
    public static void writeLines() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(LogRecord.class, DefaultScopeDefine.LOG, LogRecord.INDEX_NAME);
        final long first = System.currentTimeMillis() - LINES * 1000L;
        final List<LogRecord> lines = new ArrayList<>();
        for (int i = 0; i < LINES; i++) {
            lines.add(line(i, first + i * 1000L));
        }
        SUPPORT.write(model, new LogRecord.Builder(), lines);

        QUERY_DAO = new BanyanDBLogQueryDAO(SUPPORT.getClient());
        DURATION = BanyanDBQueryITSupport.around(first, first + (LINES - 1) * 1000L);
        await().atMost(30, TimeUnit.SECONDS)
               .untilAsserted(() -> assertEquals(LINES, read(null, Order.ASC, 0, LINES * 2).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("splitTraceReads")
    public void findsEveryLineOfTheReadInOnePage(String form, TraceScopeCondition read, List<String> lines)
        throws IOException {
        final List<String> newestFirst = new ArrayList<>(lines);
        Collections.reverse(newestFirst);
        assertEquals(lines, read(read, Order.ASC, 0, lines.size()));
        assertEquals(newestFirst, read(read, Order.DES, 0, lines.size()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("splitTraceReads")
    public void pagesThroughTheRead(String form, TraceScopeCondition read, List<String> lines) throws IOException {
        for (int i = 0; i < lines.size(); i++) {
            assertEquals(Collections.singletonList(lines.get(i)), read(read, Order.ASC, i, 1), "offset " + i);
        }
        assertEquals(Collections.emptyList(), read(read, Order.ASC, lines.size(), 1));
    }

    /**
     * The three reads of the split trace the query service sends, each with the lines it must return: the trace,
     * its native span, its OTLP span.
     */
    static Stream<Arguments> splitTraceReads() {
        final TraceScopeCondition trace = new TraceScopeCondition();
        trace.setTraceId(SPLIT_TRACE);
        final TraceScopeCondition nativeSpan = new TraceScopeCondition();
        nativeSpan.setTraceId(SPLIT_TRACE);
        nativeSpan.setSegmentId(SPLIT_SEGMENT);
        nativeSpan.setSpanId(READ_SPAN);
        final TraceScopeCondition otlpSpan = new TraceScopeCondition();
        otlpSpan.setTraceId(SPLIT_TRACE);
        otlpSpan.setOtlpSpanId(READ_OTLP_SPAN);
        final List<String> traceLines = Arrays.asList(content(0), content(1), content(LINES - 2), content(LINES - 1));
        final List<String> spanLines = Arrays.asList(content(0), content(LINES - 1));
        return Stream.of(
            Arguments.of("trace", trace, traceLines),
            Arguments.of("native span", nativeSpan, spanLines),
            Arguments.of("OTLP span", otlpSpan, spanLines)
        );
    }

    private static List<String> read(TraceScopeCondition relatedTrace, Order order, int from, int limit) throws IOException {
        return QUERY_DAO.queryLogs(null, null, null, relatedTrace, order, from, limit, DURATION, null, null, null)
                        .getLogs().stream().map(Log::getContent).collect(Collectors.toList());
    }

    private static LogRecord line(int index, long timestamp) {
        final boolean readSpan = index == 0 || index == LINES - 1;
        final boolean otherSpan = index == 1 || index == LINES - 2;
        final LogRecord line = new LogRecord();
        line.setUniqueId("line-" + index);
        line.setServiceId(SERVICE_ID);
        line.setServiceInstanceId(INSTANCE_ID);
        if (readSpan || otherSpan) {
            line.setTraceId(SPLIT_TRACE);
            line.setTraceSegmentId(SPLIT_SEGMENT);
            line.setSpanId(readSpan ? READ_SPAN : OTHER_SPAN);
            line.setOtlpSpanId(readSpan ? READ_OTLP_SPAN : OTHER_OTLP_SPAN);
        } else {
            line.setTraceId("trace-" + index);
            line.setTraceSegmentId("segment-" + index);
            line.setOtlpSpanId(String.format("%016x", index));
        }
        line.setContentType(ContentType.TEXT.value());
        line.setContent(new LongText(content(index)));
        line.setTimestamp(timestamp);
        line.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        line.setTagsRawData(new byte[0]);
        line.setTagsInString(Collections.emptyList());
        return line;
    }

    private static String content(int index) {
        return "line " + index;
    }
}
