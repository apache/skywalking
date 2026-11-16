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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.common.v3.Instant;
import org.apache.skywalking.apm.network.common.v3.KeyIntValuePair;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.Trace;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.RetrievedTimeRange;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceList;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TracesQueryResult;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBSpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBTraceQueryDAO;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads native and Zipkin traces whose spans carry the events eBPF profiling attaches to them, the way BanyanDB
 * serves the trace and Zipkin queries: the V2 reads return a trace's spans and its events together, told apart by
 * their source, and the query services hang each event on its span, a server-side event on the server's span.
 *
 * <p>An event is stored as a span of its trace with only the trace id, the event's start time and its own id set, so
 * a trace found by its spans must come back with all its events, and its events must not find it: they have no
 * duration and no error flag. The traps are in the fixture: one trace's newest record is an event, another's oldest
 * is, and a third starts before the window with an event inside it.
 */
@Testcontainers
public class BanyanDBTraceWithEventsQueryIT {
    private static final String FRONT = "trace-events-front";
    private static final String BACK = "trace-events-back";
    private static final String FRONT_ID = IDManager.ServiceID.buildId(FRONT, true);
    private static final String BACK_ID = IDManager.ServiceID.buildId(BACK, true);
    /** A trace across two services, with an event on each of three spans and its newest record an event. */
    private static final String PROFILED = "trace-events-profiled";
    private static final String PROFILED_FRONT = PROFILED + ".front";
    private static final String PROFILED_BACK = PROFILED + ".back";
    /** The newest by its span, its oldest record an event. */
    private static final String OTHER = "trace-events-other";
    /** The longest and the only erroneous one, with an event. */
    private static final String LONG = "trace-events-long";
    /** Its span starts before the window, its event inside it. */
    private static final String LATE = "trace-events-late";
    private static final String ZK_PROFILED = "5af7183fb1d4cf5f0000000000000b01";
    private static final String ZK_OTHER = "5af7183fb1d4cf5f0000000000000b02";
    private static final String ZK_LONG = "5af7183fb1d4cf5f0000000000000b03";
    private static final String ZK_LATE = "5af7183fb1d4cf5f0000000000000b04";
    private static final String ZK_FRONT = "zk-events-front";
    private static final String ZK_BACK = "zk-events-back";

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBTraceQueryDAO TRACE_DAO;
    private static BanyanDBZipkinQueryDAO ZIPKIN_DAO;
    /** Two whole minutes from W: W + 2 minutes is its end, which it counts. */
    private static Duration WINDOW;
    private static long W;
    private static final Map<String, List<String>> NATIVE_TRACES = new LinkedHashMap<>();
    private static final Map<String, List<String>> ZIPKIN_TRACES = new LinkedHashMap<>();
    private static final List<SegmentObject> SEGMENTS = new ArrayList<>();
    private static final List<SpanAttachedEvent> NATIVE_EVENTS = new ArrayList<>();
    private static final List<ZipkinSpanRecord> ZIPKIN_SPANS = new ArrayList<>();
    private static final List<SpanAttachedEvent> ZIPKIN_EVENTS = new ArrayList<>();

    @BeforeAll
    public static void writeTraces() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        W = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)) / 60_000L * 60_000L;
        WINDOW = new Duration();
        WINDOW.setStep(Step.MINUTE);
        WINDOW.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(W));
        WINDOW.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(W + 60_000L));

        final Model segment = SUPPORT.install(SegmentRecord.class, DefaultScopeDefine.SEGMENT, SegmentRecord.INDEX_NAME);
        final Model zipkinSpan = SUPPORT.install(
            ZipkinSpanRecord.class, DefaultScopeDefine.ZIPKIN_SPAN, ZipkinSpanRecord.INDEX_NAME);
        final Model swEvent = SUPPORT.install(
            SWSpanAttachedEventRecord.class, DefaultScopeDefine.SW_SPAN_ATTACHED_EVENT, SWSpanAttachedEventRecord.INDEX_NAME);
        final Model zkEvent = SUPPORT.install(
            SpanAttachedEventRecord.class, DefaultScopeDefine.SPAN_ATTACHED_EVENT, SpanAttachedEventRecord.INDEX_NAME);

        final SegmentObject profiledFront = SegmentObject.newBuilder()
            .setTraceId(PROFILED).setTraceSegmentId(PROFILED_FRONT).setService(FRONT).setServiceInstance("instance")
            .addSpans(span(0, -1, SpanType.Entry, "GET:/api", W + 10_000L, 100))
            .addSpans(span(1, 0, SpanType.Exit, "GET:/backend", W + 10_010L, 80))
            .build();
        final SegmentObject profiledBack = SegmentObject.newBuilder()
            .setTraceId(PROFILED).setTraceSegmentId(PROFILED_BACK).setService(BACK).setServiceInstance("instance")
            .addSpans(span(0, -1, SpanType.Entry, "GET:/backend", W + 10_020L, 50).toBuilder()
                          .addRefs(SegmentReference.newBuilder()
                                                   .setRefType(RefType.CrossProcess)
                                                   .setTraceId(PROFILED)
                                                   .setParentTraceSegmentId(PROFILED_FRONT)
                                                   .setParentSpanId(1)))
            .build();
        final SegmentObject other = segmentObject(OTHER, "GET:/other", W + 50_000L, 300);
        final SegmentObject longest = segmentObject(LONG, "GET:/long", W + 30_000L, 900);
        final SegmentObject late = segmentObject(LATE, "GET:/late", W - 5_000L, 10);
        SEGMENTS.addAll(Arrays.asList(profiledFront, profiledBack, other, longest, late));
        final List<SegmentRecord> segments = new ArrayList<>();
        for (final SegmentObject segmentObject : SEGMENTS) {
            segments.add(segment(segmentObject, segmentObject == longest));
        }
        SUPPORT.write(segment, new SegmentRecord.Builder(), segments);

        NATIVE_EVENTS.addAll(Arrays.asList(
            // The newest record of all.
            nativeEvent(PROFILED_FRONT, 0, "close", W + 100_000L, Collections.emptyMap()),
            nativeEvent(PROFILED_FRONT, 1, "connect", W + 10_030L, direction("request", "outbound")),
            // A response read on the client's exit span belongs to the server's entry span.
            nativeEvent(PROFILED_FRONT, 1, "read", W + 10_060L, direction("response", "outbound")),
            nativeEvent(PROFILED_BACK, 0, "syscall", W + 10_040L, Collections.emptyMap()),
            // The oldest record of the window.
            nativeEvent(OTHER + ".segment", 0, "other", W + 1_000L, Collections.emptyMap()),
            nativeEvent(LONG + ".segment", 0, "long", W + 31_000L, Collections.emptyMap()),
            nativeEvent(LATE + ".segment", 0, "late", W + 5_000L, Collections.emptyMap())));
        SUPPORT.write(swEvent, new SWSpanAttachedEventRecord.Builder(), NATIVE_EVENTS.stream()
            .map(BanyanDBQueryITSupport::swEventRecord).collect(Collectors.toList()));

        ZIPKIN_SPANS.addAll(Arrays.asList(
            zipkinSpan(ZK_PROFILED, "0000000000000001", null, "SERVER", "get /api", ZK_FRONT, W + 10_000L, 100),
            zipkinSpan(ZK_PROFILED, "0000000000000002", "0000000000000001", "CLIENT", "get /backend", ZK_FRONT,
                       W + 10_010L, 80),
            zipkinSpan(ZK_PROFILED, "0000000000000003", "0000000000000002", "SERVER", "get /backend", ZK_BACK,
                       W + 10_020L, 50),
            zipkinSpan(ZK_OTHER, "0000000000000004", null, "SERVER", "get /other", ZK_FRONT, W + 50_000L, 300),
            zipkinSpan(ZK_LONG, "0000000000000005", null, "SERVER", "get /long", ZK_FRONT, W + 30_000L, 900),
            zipkinSpan(ZK_LATE, "0000000000000006", null, "SERVER", "get /late", ZK_FRONT, W - 5_000L, 10)));
        SUPPORT.write(zipkinSpan, new ZipkinSpanRecord.Builder(), ZIPKIN_SPANS);

        ZIPKIN_EVENTS.addAll(Arrays.asList(
            zipkinEvent(ZK_PROFILED, "0000000000000001", "close", W + 100_000L, Collections.emptyMap(), false),
            zipkinEvent(ZK_PROFILED, "0000000000000002", "connect", W + 10_030L, direction("request", "outbound"), false),
            zipkinEvent(ZK_PROFILED, "0000000000000002", "read", W + 10_060L, direction("response", "outbound"), false),
            // Two events of one name on one span, numbered by their start.
            zipkinEvent(ZK_PROFILED, "0000000000000003", "syscall", W + 10_045L, Collections.emptyMap(), true),
            zipkinEvent(ZK_PROFILED, "0000000000000003", "syscall", W + 10_040L, Collections.emptyMap(), false),
            zipkinEvent(ZK_OTHER, "0000000000000004", "other", W + 1_000L, Collections.emptyMap(), false),
            zipkinEvent(ZK_LONG, "0000000000000005", "long", W + 31_000L, Collections.emptyMap(), false),
            zipkinEvent(ZK_LATE, "0000000000000006", "late", W + 5_000L, Collections.emptyMap(), false)));
        SUPPORT.write(zkEvent, new SpanAttachedEventRecord.Builder(), ZIPKIN_EVENTS.stream()
            .map(BanyanDBQueryITSupport::zkEventRecord).collect(Collectors.toList()));

        // Each trace as OAP writes it: the wrappers of its segments or Zipkin spans and of its events.
        for (final String traceId : Arrays.asList(PROFILED, OTHER, LONG, LATE)) {
            final List<SpanWrapper> wrappers = new ArrayList<>();
            segments.stream().filter(s -> s.getTraceId().equals(traceId)).forEach(s -> wrappers.add(s.getSpanWrapper()));
            NATIVE_EVENTS.stream().filter(e -> e.getTraceContext().getTraceId().equals(traceId))
                         .forEach(e -> wrappers.add(BanyanDBQueryITSupport.swEventRecord(e).getSpanWrapper()));
            NATIVE_TRACES.put(traceId, BanyanDBQueryITSupport.describe(wrappers));
        }
        for (final String traceId : Arrays.asList(ZK_PROFILED, ZK_OTHER, ZK_LONG, ZK_LATE)) {
            final List<SpanWrapper> wrappers = new ArrayList<>();
            ZIPKIN_SPANS.stream().filter(s -> s.getTraceId().equals(traceId)).forEach(s -> wrappers.add(s.getSpanWrapper()));
            ZIPKIN_EVENTS.stream().filter(e -> e.getTraceContext().getTraceId().equals(traceId))
                         .forEach(e -> wrappers.add(BanyanDBQueryITSupport.zkEventRecord(e).getSpanWrapper()));
            ZIPKIN_TRACES.put(traceId, BanyanDBQueryITSupport.describe(wrappers));
        }

        TRACE_DAO = new BanyanDBTraceQueryDAO(SUPPORT.getClient(), 200, coreServices());
        ZIPKIN_DAO = new BanyanDBZipkinQueryDAO(SUPPORT.getClient());
        // Ready once every trace written reads back whole, events included.
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            for (final Map.Entry<String, List<String>> trace : NATIVE_TRACES.entrySet()) {
                assertEquals(trace.getValue(), describeNative(TRACE_DAO.queryByTraceIdV2(trace.getKey(), null)), trace.getKey());
            }
            for (final Map.Entry<String, List<String>> trace : ZIPKIN_TRACES.entrySet()) {
                assertEquals(trace.getValue(), describeZipkin(ZIPKIN_DAO.getTraceV2(trace.getKey(), null)), trace.getKey());
            }
        });
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsANativeTraceByIdWithItsEvents() throws IOException {
        assertEquals(NATIVE_TRACES.get(PROFILED), describeNative(TRACE_DAO.queryByTraceIdV2(PROFILED, WINDOW)));
    }

    @Test
    public void findsANativeTraceBySpanWithItsEvents() throws IOException {
        final TraceQueryCondition byBackendEndpoint = condition(QueryOrder.BY_START_TIME, 1, 10);
        byBackendEndpoint.setEndpointId(IDManager.EndpointID.buildId(BACK_ID, "GET:/backend"));
        assertEquals(Collections.singletonList(NATIVE_TRACES.get(PROFILED)), search(byBackendEndpoint));
    }

    @Test
    public void ordersAndPagesNativeTracesFoundByTheirSpans() throws IOException {
        final List<List<String>> newestFirst = nativeNewestFirst();
        assertEquals(newestFirst, search(ofFront(condition(QueryOrder.BY_START_TIME, 1, 10))));
        for (int page = 1; page <= newestFirst.size(); page++) {
            assertEquals(Collections.singletonList(newestFirst.get(page - 1)),
                         search(ofFront(condition(QueryOrder.BY_START_TIME, page, 1))), "page " + page);
        }
        assertEquals(Arrays.asList(NATIVE_TRACES.get(LONG), NATIVE_TRACES.get(OTHER), NATIVE_TRACES.get(PROFILED)),
                     search(ofFront(condition(QueryOrder.BY_DURATION, 1, 10))));
    }

    @Test
    public void doesNotFindANativeTraceByItsEvents() throws IOException {
        final TraceQueryCondition shortOnes = condition(QueryOrder.BY_START_TIME, 1, 10);
        shortOnes.setMaxTraceDuration(200);
        assertEquals(Collections.singletonList(NATIVE_TRACES.get(PROFILED)), search(shortOnes));
        final TraceQueryCondition successful = condition(QueryOrder.BY_START_TIME, 1, 10);
        successful.setTraceState(TraceState.SUCCESS);
        assertEquals(Arrays.asList(NATIVE_TRACES.get(OTHER), NATIVE_TRACES.get(PROFILED)), search(successful));
    }

    @Test
    public void readsAZipkinTraceByIdWithItsEvents() throws IOException {
        assertEquals(ZIPKIN_TRACES.get(ZK_PROFILED), describeZipkin(ZIPKIN_DAO.getTraceV2(ZK_PROFILED, WINDOW)));
    }

    @Test
    public void findsAZipkinTraceBySpanWithItsEvents() throws IOException {
        assertEquals(Collections.singletonList(ZIPKIN_TRACES.get(ZK_PROFILED)),
                     zipkinSearch(zipkinRequest(ZK_BACK, "get /backend", 10)));
    }

    @Test
    public void ordersAndLimitsZipkinTracesFoundByTheirSpans() throws IOException {
        assertEquals(zipkinNewestFirst(), zipkinSearch(zipkinRequest(ZK_FRONT, null, 10)));
        assertEquals(zipkinNewestFirst().subList(0, 1), zipkinSearch(zipkinRequest(ZK_FRONT, null, 1)));
    }

    /**
     * The traces of the window, newest first by the start of their first span; the trace whose span starts before the
     * window is not among them.
     */
    private static List<List<String>> nativeNewestFirst() {
        return Arrays.asList(NATIVE_TRACES.get(OTHER), NATIVE_TRACES.get(LONG), NATIVE_TRACES.get(PROFILED));
    }

    private static List<List<String>> zipkinNewestFirst() {
        return Arrays.asList(ZIPKIN_TRACES.get(ZK_OTHER), ZIPKIN_TRACES.get(ZK_LONG), ZIPKIN_TRACES.get(ZK_PROFILED));
    }

    @Test
    public void hangsEachEventOnItsNativeSpan() throws IOException {
        final SegmentObject front = SEGMENTS.get(0);
        final SegmentObject back = SEGMENTS.get(1);
        // In the order the trace query sorts spans: the root, its child, and the server span it called.
        final List<String> expected = Arrays.asList(
            expectedSpan(front, 0, true, nativeEvents("close")),
            expectedSpan(front, 1, false, nativeEvents("connect")),
            expectedSpan(back, 0, false, nativeEvents("syscall", "read")));
        final TraceQueryService service = new TraceQueryService(traceModules());

        final Trace trace = service.queryTrace(PROFILED, WINDOW);
        assertEquals(expected, describeSpans(trace.getSpans()), "queryTrace");
        assertNull(trace.getDebuggingTrace(), "queryTrace debugging trace");
        final TraceQueryCondition byBackendEndpoint = condition(QueryOrder.BY_START_TIME, 1, 10);
        byBackendEndpoint.setEndpointId(IDManager.EndpointID.buildId(BACK_ID, "GET:/backend"));
        final TraceList found = service.queryTraces(byBackendEndpoint);
        assertEquals(Collections.singletonList(expected),
                     found.getTraces().stream().map(each -> describeSpans(each.getSpans())).collect(Collectors.toList()),
                     "queryTraces");
        assertEquals(window(), range(found.getRetrievedTimeRange()), "queryTraces retrieved time range");
        assertNull(found.getDebuggingTrace(), "queryTraces debugging trace");
    }

    @Test
    public void hangsEachEventOnItsZipkinSpan() throws IOException {
        // The events in the order they were written: close, connect, read, syscall (the later one), syscall.
        final List<String> expected = sorted(Arrays.asList(
            expectedZipkinSpan(ZIPKIN_SPANS.get(0), Collections.singletonList(ZIPKIN_EVENTS.get(0)),
                               Collections.singletonList("close"), Collections.emptyMap()),
            expectedZipkinSpan(ZIPKIN_SPANS.get(1), Collections.singletonList(ZIPKIN_EVENTS.get(1)),
                               Collections.singletonList("connect"),
                               Collections.singletonMap("connect.tags", "{data_direction: outbound, data_type: request}\n")),
            expectedZipkinSpan(ZIPKIN_SPANS.get(2), Arrays.asList(ZIPKIN_EVENTS.get(4), ZIPKIN_EVENTS.get(3),
                                                                  ZIPKIN_EVENTS.get(2)),
                               Arrays.asList("syscall-1", "syscall-2", "read"),
                               tags("syscall-2.summary", "{read_bytes: 4096}\n",
                                    "read.tags", "{data_direction: outbound, data_type: response}\n"))));
        final ZipkinQueryService service = new ZipkinQueryService(zipkinModules());

        assertEquals(expected, describeZipkinSpans(service.getTraceById(ZK_PROFILED, WINDOW)), "getTraceById");
        assertEquals(Collections.singletonList(expected),
                     service.getTraces(zipkinRequest(ZK_BACK, "get /backend", 10), WINDOW).stream()
                            .map(BanyanDBTraceWithEventsQueryIT::describeZipkinSpans).collect(Collectors.toList()),
                     "getTraces");
        assertEquals(Collections.singletonList(expected),
                     service.getTracesByIds(Collections.singleton(ZK_PROFILED), WINDOW).stream()
                            .map(BanyanDBTraceWithEventsQueryIT::describeZipkinSpans).collect(Collectors.toList()),
                     "getTracesByIds");
    }

    /** Every span of a trace whole, in the order the trace query returns them. */
    private static List<String> describeSpans(List<Span> spans) {
        return spans.stream().map(BanyanDBTraceWithEventsQueryIT::describe).collect(Collectors.toList());
    }

    /** Every field of a span as the trace query returns it, with its attached events whole. */
    private static String describe(Span span) {
        return BanyanDBQueryITSupport.json(
            span.getTraceId(), span.getSegmentId(), span.getSpanId(), span.getParentSpanId(), span.getSegmentSpanId(),
            span.getSegmentParentSpanId(), span.isRoot(), span.getServiceCode(), span.getServiceInstanceName(),
            span.getStartTime(), span.getEndTime(), span.getEndpointName(), span.getType(), span.getPeer(),
            span.getComponent(), span.isError(), span.getLayer(),
            span.getRefs().stream()
                .map(ref -> Arrays.asList(ref.getTraceId(), ref.getParentSegmentId(), ref.getParentSpanId(), ref.getType().name()))
                .collect(Collectors.toList()),
            span.getTags().stream().map(tag -> Arrays.asList(tag.getKey(), tag.getValue())).collect(Collectors.toList()),
            span.getLogs().stream()
                .map(log -> Arrays.asList(log.getTime(), log.getData().stream()
                    .map(data -> Arrays.asList(data.getKey(), data.getValue())).collect(Collectors.toList())))
                .collect(Collectors.toList()),
            span.getAttachedEvents().stream().map(event -> Arrays.asList(
                event.getEvent(), event.getStartTime().getSeconds(), event.getStartTime().getNanos(),
                event.getEndTime().getSeconds(), event.getEndTime().getNanos(),
                event.getTags().stream().map(tag -> Arrays.asList(tag.getKey(), tag.getValue())).collect(Collectors.toList()),
                event.getSummary().stream().map(pair -> Arrays.asList(pair.getKey(), pair.getValue()))
                     .collect(Collectors.toList())))
                .collect(Collectors.toList()));
    }

    /**
     * The same fields the trace query must give a written span, as it builds them from the span: the parent of a span
     * with a reference is the referenced span, the root is the one whose parent is not in the trace, and the
     * component name comes from the catalog (Unknown here). The events are the ones it must hang on the span.
     */
    private static String expectedSpan(SegmentObject segment, int index, boolean root, List<SpanAttachedEvent> events) {
        final SpanObject span = segment.getSpans(index);
        final String segmentParentSpanId = span.getRefsCount() > 0
            ? span.getRefs(0).getParentTraceSegmentId() + "S" + span.getRefs(0).getParentSpanId()
            : segment.getTraceSegmentId() + "S" + span.getParentSpanId();
        return BanyanDBQueryITSupport.json(
            segment.getTraceId(), segment.getTraceSegmentId(), span.getSpanId(), span.getParentSpanId(),
            segment.getTraceSegmentId() + "S" + span.getSpanId(), segmentParentSpanId, root, segment.getService(),
            segment.getServiceInstance(), span.getStartTime(), span.getEndTime(), span.getOperationName(),
            span.getSpanType().name(), span.getPeer(), "Unknown", span.getIsError(), span.getSpanLayer().name(),
            span.getRefsList().stream()
                .map(ref -> Arrays.asList(ref.getTraceId(), ref.getParentTraceSegmentId(), ref.getParentSpanId(),
                                          ref.getRefType() == RefType.CrossProcess ? "CROSS_PROCESS" : "CROSS_THREAD"))
                .collect(Collectors.toList()),
            span.getTagsList().stream().map(tag -> Arrays.asList(tag.getKey(), tag.getValue())).collect(Collectors.toList()),
            span.getLogsList().stream()
                .map(log -> Arrays.asList(log.getTime(), log.getDataList().stream()
                    .map(data -> Arrays.asList(data.getKey(), data.getValue())).collect(Collectors.toList())))
                .collect(Collectors.toList()),
            events.stream().map(event -> Arrays.asList(
                event.getEvent(), event.getStartTime().getSeconds(), event.getStartTime().getNanos(),
                event.getEndTime().getSeconds(), event.getEndTime().getNanos(),
                event.getTagsList().stream().map(tag -> Arrays.asList(tag.getKey(), tag.getValue())).collect(Collectors.toList()),
                event.getSummaryList().stream().map(pair -> Arrays.asList(pair.getKey(), pair.getValue()))
                     .collect(Collectors.toList())))
                .collect(Collectors.toList()));
    }

    /** The written native events of these names, in this order. */
    private static List<SpanAttachedEvent> nativeEvents(String... names) {
        final List<SpanAttachedEvent> events = new ArrayList<>();
        for (final String name : names) {
            events.add(NATIVE_EVENTS.stream().filter(e -> e.getEvent().equals(name)).findFirst()
                                    .orElseThrow(IllegalStateException::new));
        }
        return events;
    }

    /** Every span of a Zipkin trace whole, sorted, as the Zipkin query defines no order for them. */
    private static List<String> describeZipkinSpans(List<zipkin2.Span> spans) {
        return sorted(spans.stream().map(BanyanDBQueryITSupport::zipkin).collect(Collectors.toList()));
    }

    /**
     * The Zipkin span the Zipkin query must return for a written span with these events on it, each under the name it
     * numbers it by: a start and a finish annotation each, and these tags, which carry the events' summaries and tags
     * as the query writes them.
     */
    private static String expectedZipkinSpan(ZipkinSpanRecord record, List<SpanAttachedEvent> events,
                                             List<String> names, Map<String, String> tags) {
        final zipkin2.Span.Builder span = SpanBytesDecoder.PROTO3.decodeOne(
            record.getSpanWrapper().getSpan().toByteArray()).toBuilder();
        for (int i = 0; i < events.size(); i++) {
            span.addAnnotation(micros(events.get(i).getStartTime()), "Start " + names.get(i));
            span.addAnnotation(micros(events.get(i).getEndTime()), "Finished " + names.get(i));
        }
        tags.forEach(span::putTag);
        return BanyanDBQueryITSupport.zipkin(span.build());
    }

    private static Map<String, String> tags(String... keysAndValues) {
        final Map<String, String> tags = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            tags.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return tags;
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().collect(Collectors.toList());
    }

    private static long micros(Instant instant) {
        return TimeUnit.SECONDS.toMicros(instant.getSeconds()) + TimeUnit.NANOSECONDS.toMicros(instant.getNanos());
    }

    /** The traces a search finds, whole, after checking it reports the window it read. */
    private static List<List<String>> search(TraceQueryCondition condition) throws IOException {
        final TracesQueryResult result = TRACE_DAO.queryTraces(condition);
        assertEquals(window(), range(result.getRetrievedTimeRange()), "the retrieved time range");
        final List<List<String>> traces = new ArrayList<>();
        for (final List<SpanWrapper> trace : result.getTraces()) {
            traces.add(describeNative(trace));
        }
        return traces;
    }

    private static String range(RetrievedTimeRange range) {
        return range.getStartTime() + "-" + range.getEndTime();
    }

    /** The time range a read of the window reports: the window's own start and end. */
    private static String window() {
        return WINDOW.getStartTimestamp() + "-" + WINDOW.getEndTimestamp();
    }

    private static List<List<String>> zipkinSearch(QueryRequest request) throws IOException {
        final List<List<String>> traces = new ArrayList<>();
        for (final List<SpanWrapper> trace : ZIPKIN_DAO.getTracesV2(request, WINDOW)) {
            traces.add(describeZipkin(trace));
        }
        return traces;
    }

    /** The condition narrowed to the front service, which only spans can match. */
    private static TraceQueryCondition ofFront(TraceQueryCondition condition) {
        condition.setServiceId(FRONT_ID);
        return condition;
    }

    private static TraceQueryCondition condition(QueryOrder order, int pageNum, int pageSize) {
        final TraceQueryCondition condition = new TraceQueryCondition();
        condition.setQueryDuration(WINDOW);
        condition.setQueryOrder(order);
        condition.setTraceState(TraceState.ALL);
        condition.setPaging(new Pagination(pageNum, pageSize));
        return condition;
    }

    private static QueryRequest zipkinRequest(String serviceName, String spanName, int limit) {
        return QueryRequest.newBuilder()
                           .serviceName(serviceName)
                           .spanName(spanName)
                           .endTs(W + 120_000L)
                           .lookback(120_000L)
                           .limit(limit)
                           .build();
    }

    /** Every span of a native trace whole: its segments and its events, each wrapper with its source and service. */
    private static List<String> describeNative(List<SpanWrapper> trace) {
        return BanyanDBQueryITSupport.describe(trace);
    }

    private static List<String> describeZipkin(List<SpanWrapper> trace) {
        return BanyanDBQueryITSupport.describe(trace);
    }

    private static Map<String, String> direction(String type, String direction) {
        final Map<String, String> tags = new LinkedHashMap<>();
        tags.put("data_type", type);
        tags.put("data_direction", direction);
        return tags;
    }

    private static SpanAttachedEvent nativeEvent(String segmentId, int spanId, String name, long startMillis,
                                                 Map<String, String> tags) {
        return event(SpanAttachedEvent.SpanReference.newBuilder()
                                                    .setType(SpanAttachedEvent.SpanReferenceType.SKYWALKING)
                                                    .setTraceId(segmentId.substring(0, segmentId.lastIndexOf('.')))
                                                    .setTraceSegmentId(segmentId)
                                                    .setSpanId(String.valueOf(spanId)),
                     name, startMillis, tags, name.equals("syscall"));
    }

    private static SpanAttachedEvent zipkinEvent(String traceId, String spanId, String name, long startMillis,
                                                 Map<String, String> tags, boolean withSummary) {
        return event(SpanAttachedEvent.SpanReference.newBuilder()
                                                    .setType(SpanAttachedEvent.SpanReferenceType.ZIPKIN)
                                                    .setTraceId(traceId)
                                                    .setSpanId(spanId),
                     name, startMillis, tags, withSummary);
    }

    private static SpanAttachedEvent event(SpanAttachedEvent.SpanReference.Builder reference, String name,
                                           long startMillis, Map<String, String> tags, boolean withSummary) {
        final SpanAttachedEvent.Builder event = SpanAttachedEvent.newBuilder()
            .setStartTime(instant(startMillis))
            .setEndTime(instant(startMillis + 2))
            .setEvent(name)
            .setTraceContext(reference);
        tags.forEach((key, value) -> event.addTags(KeyStringValuePair.newBuilder().setKey(key).setValue(value)));
        if (withSummary) {
            event.addSummary(KeyIntValuePair.newBuilder().setKey("read_bytes").setValue(4096));
        }
        return event.build();
    }

    private static Instant instant(long millis) {
        return Instant.newBuilder().setSeconds(millis / 1000).setNanos((int) (millis % 1000) * 1_000_000).build();
    }

    private static SpanObject span(int spanId, int parentSpanId, SpanType type, String operation, long start,
                                   int latency) {
        return SpanObject.newBuilder()
                         .setSpanId(spanId)
                         .setParentSpanId(parentSpanId)
                         .setSpanType(type)
                         .setOperationName(operation)
                         .setStartTime(start)
                         .setEndTime(start + latency)
                         .build();
    }

    private static SegmentObject segmentObject(String traceId, String endpoint, long start, int latency) {
        return SegmentObject.newBuilder()
                            .setTraceId(traceId)
                            .setTraceSegmentId(traceId + ".segment")
                            .setService(FRONT)
                            .setServiceInstance("instance")
                            .addSpans(span(0, -1, SpanType.Entry, endpoint, start, latency))
                            .build();
    }

    /** The segment record of a segment, its latency and endpoint those of its entry span. */
    private static SegmentRecord segment(SegmentObject segmentObject, boolean error) {
        final SpanObject entry = segmentObject.getSpans(0);
        final String serviceId = IDManager.ServiceID.buildId(segmentObject.getService(), true);
        final SegmentRecord segment = new SegmentRecord();
        segment.setSegmentId(segmentObject.getTraceSegmentId());
        segment.setTraceId(segmentObject.getTraceId());
        segment.setServiceId(serviceId);
        segment.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(serviceId, segmentObject.getServiceInstance()));
        segment.setEndpointId(IDManager.EndpointID.buildId(serviceId, entry.getOperationName()));
        segment.setStartTime(entry.getStartTime());
        segment.setLatency((int) (entry.getEndTime() - entry.getStartTime()));
        segment.setIsError(error ? 1 : 0);
        segment.setTimeBucket(TimeBucket.getRecordTimeBucket(entry.getStartTime()));
        segment.setDataBinary(segmentObject.toByteArray());
        segment.setTags(Collections.emptyList());
        return segment;
    }

    /** The record the Zipkin receiver stores for a span. */
    private static ZipkinSpanRecord zipkinSpan(String traceId, String spanId, String parentId, String kind, String name,
                                               String service, long timestampMillis, int durationMillis) {
        final ZipkinSpanRecord record = new ZipkinSpanRecord();
        record.setTraceId(traceId);
        record.setSpanId(spanId);
        record.setParentId(parentId);
        record.setKind(kind);
        record.setName(name);
        record.setLocalEndpointServiceName(service);
        record.setTimestamp(timestampMillis * 1000L);
        record.setTimestampMillis(timestampMillis);
        record.setDuration(durationMillis * 1000L);
        record.setQuery(Collections.emptyList());
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestampMillis));
        return record;
    }

    /** The core services the trace DAO and the trace query service look up. */
    private static ModuleManager coreServices() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder coreProvider = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreProvider);
        when(coreProvider.provider()).thenReturn(services);
        when(services.getService(NamingControl.class))
            .thenReturn(new NamingControl(70, 100, 150, new EndpointNameGrouping()));
        final IComponentLibraryCatalogService components = mock(IComponentLibraryCatalogService.class);
        when(components.getComponentName(anyInt())).thenReturn("Unknown");
        when(services.getService(IComponentLibraryCatalogService.class)).thenReturn(components);
        return moduleManager;
    }

    /** A module manager whose storage module is BanyanDB, with the DAOs its provider registers. */
    private static ModuleManager traceModules() {
        final ModuleManager moduleManager = coreServices();
        final ModuleServiceHolder storage = storage(moduleManager);
        when(storage.getService(ITraceQueryDAO.class)).thenReturn(TRACE_DAO);
        when(storage.getService(ISpanAttachedEventQueryDAO.class))
            .thenReturn(new BanyanDBSpanAttachedEventQueryDAO(SUPPORT.getClient()));
        return moduleManager;
    }

    private static ModuleManager zipkinModules() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleServiceHolder storage = storage(moduleManager);
        when(storage.getService(IZipkinQueryDAO.class)).thenReturn(ZIPKIN_DAO);
        when(storage.getService(ISpanAttachedEventQueryDAO.class))
            .thenReturn(new BanyanDBSpanAttachedEventQueryDAO(SUPPORT.getClient()));
        return moduleManager;
    }

    private static ModuleServiceHolder storage(ModuleManager moduleManager) {
        final ModuleProviderHolder storageProvider = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder storage = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(storageProvider);
        when(storageProvider.provider()).thenReturn(storage);
        return storage;
    }
}
