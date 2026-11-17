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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Searches Zipkin traces the way the Zipkin query API does, on spans written on both sides of midnight, so in two
 * day tables. A search ranks the traces by their first span that matches, latest first, and keeps {@code limit} of
 * them; a trace comes back whole, every span of it, whichever day table holds it. A subclass names the server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCZipkinQueryITBase {
    private static final String BACKEND = "backend";
    private static final String FRONTEND = "frontend";
    /** The backend's traces, latest first by their first backend span. */
    private static final List<String> LATEST_FIRST = Arrays.asList(
        "a02", "b07", "b06", "b05", "b04", "b03", "b02", "a01");

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCZipkinQueryDAO dao;
    private Duration duration;

    JDBCZipkinQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeSpans() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        final var model = support.install(
            ZipkinSpanRecord.class, DefaultScopeDefine.ZIPKIN_SPAN, ZipkinSpanRecord.INDEX_NAME, DownSampling.Second);
        support.writeRecords(model, new ZipkinSpanRecord.Builder(), Arrays.asList(
            // a01 starts in the backend before midnight; its child, after midnight, is the backend too.
            span("a01", 1, 0, BACKEND, -60),
            span("a01", 2, 1, BACKEND, 1),
            // a02 starts in the frontend before midnight; only its backend child, after midnight, matches.
            span("a02", 1, 0, FRONTEND, -1),
            span("a02", 2, 1, BACKEND, 60),
            span("b02", 1, 0, BACKEND, -40),
            span("b03", 1, 0, BACKEND, -30),
            span("b04", 1, 0, BACKEND, -20),
            span("b05", 1, 0, BACKEND, 10),
            span("b06", 1, 0, BACKEND, 20),
            span("b07", 1, 0, BACKEND, 30)
        ));
        dao = new JDBCZipkinQueryDAO(support.getClient(), support.getTableHelper());
        duration = JDBCQueryITSupport.duration(MIDNIGHT - 120_000L, MIDNIGHT + 120_000L, Step.SECOND);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @ParameterizedTest(name = "limit {0}")
    @ValueSource(ints = {4, 5, 8})
    public void keepsTheLatestTracesAcrossMidnight(final int limit) throws Exception {
        final Set<List<String>> expected = new HashSet<>();
        for (final String trace : LATEST_FIRST.subList(0, limit)) {
            expected.add(spanIds(trace));
        }
        final QueryRequest request = QueryRequest.newBuilder()
                                                 .serviceName(BACKEND)
                                                 .endTs(MIDNIGHT + 120_000L)
                                                 .lookback(240_000L)
                                                 .limit(limit)
                                                 .build();
        assertEquals(expected, traces(dao.getTraces(request, duration)));
    }

    @Test
    public void readsWholeTracesByIdAcrossMidnight() throws Exception {
        final Set<String> traceIds = new HashSet<>(Arrays.asList(traceId("a01"), traceId("a02"), traceId("b05")));
        assertEquals(new HashSet<>(Arrays.asList(spanIds("a01"), spanIds("a02"), spanIds("b05"))),
                     traces(dao.getTraces(traceIds, duration)));
    }

    /**
     * Each trace as its sorted span ids; a trace split into two lists shows up as two entries.
     */
    private static Set<List<String>> traces(final List<List<Span>> traces) {
        final Set<List<String>> result = new HashSet<>();
        for (final List<Span> trace : traces) {
            final Set<String> ids = new TreeSet<>();
            for (final Span span : trace) {
                ids.add(span.id());
            }
            assertTrue(result.add(new ArrayList<>(ids)), "trace returned twice: " + ids);
        }
        return result;
    }

    private static List<String> spanIds(final String trace) {
        return trace.startsWith("a")
            ? Arrays.asList(spanId(trace, 1), spanId(trace, 2))
            : Collections.singletonList(spanId(trace, 1));
    }

    private static String traceId(final String trace) {
        return String.format("%032x", Integer.parseInt(trace.substring(1), 16) + (trace.charAt(0) == 'a' ? 0xa00 : 0xb00));
    }

    private static String spanId(final String trace, final int span) {
        return traceId(trace).substring(20) + String.format("%04x", span);
    }

    private static ZipkinSpanRecord span(final String trace, final int span, final int parent,
                                         final String service, final int secondsFromMidnight) {
        final long timestampMillis = MIDNIGHT + secondsFromMidnight * 1000L;
        final ZipkinSpanRecord record = new ZipkinSpanRecord();
        record.setTraceId(traceId(trace));
        record.setSpanId(spanId(trace, span));
        record.setParentId(parent == 0 ? null : spanId(trace, parent));
        record.setKind(parent == 0 ? "SERVER" : "CLIENT");
        record.setName("get /orders");
        record.setLocalEndpointServiceName(service);
        record.setTimestamp(timestampMillis * 1000L);
        record.setTimestampMillis(timestampMillis);
        record.setDuration(1000L);
        record.setQuery(new ArrayList<>());
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestampMillis));
        return record;
    }
}
