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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBZipkinQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.storage.QueryRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads Zipkin traces by service and span name the way the Zipkin query API does ({@code getTracesV2}), on the real
 * {@code zipkin_span} trace. A trace found by one of its spans must come back whole, every span of every service in
 * it. The service name is answered by the trace's index; the span name is a residual filter on the spans BanyanDB
 * reads.
 */
@Testcontainers
public class BanyanDBZipkinTraceQueryIT {
    private static final String FRONTEND = "frontend";
    private static final String CATALOG = "productcatalogservice";
    private static final String LIST_PRODUCTS = "oteldemo.productcatalogservice/listproducts";
    /** The trace of one page load: its HTTP server span and gRPC client span in the frontend, the server span in the catalog. */
    private static final String TRACE_ID = "08603e4bc22d2e115aae7721d7e8269e";
    private static final Set<String> TRACE_SPANS = new TreeSet<>(Arrays.asList(
        "http get", "grpc." + LIST_PRODUCTS, LIST_PRODUCTS));
    /** Newer catalog traces with another span name, so the service alone does not find the trace. */
    private static final int OTHER_TRACES = 10;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBZipkinQueryDAO QUERY_DAO;
    private static Duration DURATION;
    private static long END_MILLIS;

    @BeforeAll
    public static void writeSpans() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(
            ZipkinSpanRecord.class, DefaultScopeDefine.ZIPKIN_SPAN, ZipkinSpanRecord.INDEX_NAME);
        final long first = System.currentTimeMillis() - (OTHER_TRACES + 2) * 1000L;
        // Each service reports its own spans, so one trace reaches storage in more than one write. A pause longer
        // than the trace flush timeout puts the frontend's and the catalog's spans in different parts.
        SUPPORT.write(model, new ZipkinSpanRecord.Builder(), Arrays.asList(
            span(TRACE_ID, "8653a8161edcc29e", null, "SERVER", "http get", FRONTEND, null, first),
            span(TRACE_ID, "4eb3de4313260320", "8653a8161edcc29e", "CLIENT", "grpc." + LIST_PRODUCTS, FRONTEND, CATALOG, first)
        ));
        TimeUnit.SECONDS.sleep(1);
        final List<ZipkinSpanRecord> catalogSpans = new ArrayList<>();
        catalogSpans.add(span(TRACE_ID, "4eb3de4313260327", "8653a8161edcc29e", "SERVER", LIST_PRODUCTS, CATALOG, null, first));
        for (int i = 1; i <= OTHER_TRACES; i++) {
            catalogSpans.add(span(String.format("%032x", i), String.format("%016x", i), null, "SERVER",
                                  "oteldemo.productcatalogservice/getproduct", CATALOG, null, first + i * 1000L));
        }
        SUPPORT.write(model, new ZipkinSpanRecord.Builder(), catalogSpans);

        QUERY_DAO = new BanyanDBZipkinQueryDAO(SUPPORT.getClient());
        END_MILLIS = first + (OTHER_TRACES + 1) * 1000L;
        DURATION = BanyanDBQueryITSupport.around(first, END_MILLIS);
        await().atMost(30, TimeUnit.SECONDS)
               .untilAsserted(() -> assertEquals(OTHER_TRACES + 1, read(CATALOG, null, OTHER_TRACES * 2).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void returnsTheWholeTraceFoundByOneOfItsSpans() throws IOException {
        assertEquals(Collections.singletonList(TRACE_SPANS), read(CATALOG, LIST_PRODUCTS, 1),
                     "found by the catalog's server span");
        assertEquals(Collections.singletonList(TRACE_SPANS), read(FRONTEND, "http get", 1),
                     "found by the frontend's root span");
    }

    @Test
    public void readsAWholeTraceById() throws IOException {
        assertEquals(TRACE_SPANS, names(QUERY_DAO.getTraceV2(TRACE_ID, DURATION)));
    }

    @Test
    public void readsTracesByIdNewestFirst() throws IOException {
        final List<Set<String>> traces = new ArrayList<>();
        for (final List<SpanWrapper> trace : QUERY_DAO.getTracesV2(
            new HashSet<>(Arrays.asList(TRACE_ID, String.format("%032x", OTHER_TRACES), "f".repeat(32))), DURATION)) {
            traces.add(names(trace));
        }
        assertEquals(Arrays.asList(Collections.singleton("oteldemo.productcatalogservice/getproduct"), TRACE_SPANS), traces);
    }

    private static Set<String> names(List<SpanWrapper> trace) {
        final Set<String> names = new TreeSet<>();
        for (final SpanWrapper span : trace) {
            names.add(SpanBytesDecoder.PROTO3.decodeOne(span.getSpan().toByteArray()).name());
        }
        return names;
    }

    /** The span names of each trace returned. */
    private static List<Set<String>> read(String serviceName, String spanName, int limit) throws IOException {
        final QueryRequest request = QueryRequest.newBuilder()
                                                 .serviceName(serviceName)
                                                 .spanName(spanName)
                                                 .endTs(END_MILLIS + 120_000L)
                                                 .lookback(600_000L)
                                                 .limit(limit)
                                                 .build();
        final List<Set<String>> traces = new ArrayList<>();
        for (final List<SpanWrapper> trace : QUERY_DAO.getTracesV2(request, DURATION)) {
            final Set<String> names = new TreeSet<>();
            for (final SpanWrapper span : trace) {
                names.add(SpanBytesDecoder.PROTO3.decodeOne(span.getSpan().toByteArray()).name());
            }
            traces.add(names);
        }
        return traces;
    }

    private static ZipkinSpanRecord span(String traceId, String spanId, String parentId, String kind, String name,
                                         String localService, String remoteService, long timestampMillis) {
        final ZipkinSpanRecord span = new ZipkinSpanRecord();
        span.setTraceId(traceId);
        span.setSpanId(spanId);
        span.setParentId(parentId);
        span.setKind(kind);
        span.setName(name);
        span.setLocalEndpointServiceName(localService);
        span.setRemoteEndpointServiceName(remoteService);
        span.setTimestamp(timestampMillis * 1000L);
        span.setTimestampMillis(timestampMillis);
        span.setDuration(18L);
        span.setQuery(Collections.emptyList());
        span.setTimeBucket(TimeBucket.getRecordTimeBucket(timestampMillis));
        return span;
    }
}
