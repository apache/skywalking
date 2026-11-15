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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.common.v3.Instant;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBSpanAttachedEventQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads the events eBPF profiling attaches to a trace's spans the way the trace query services do, for native and
 * Zipkin traces, from where OAP writes them on BanyanDB: the {@code segment} and {@code zipkin_span} traces, as spans
 * of their trace beside its segments or Zipkin spans. Every event of every trace asked for must come back, however
 * many a trace has, in start-time order to the nanosecond, as the other storages return them; none of another trace
 * and no segment or Zipkin span.
 *
 * <p>A window only tells BanyanDB where to look: a lookup by trace id is not clipped to it (BanyanDB's trace design
 * keeps explicit trace-id lookups out of its time filter), as a trace read by id is not, and as the other storages,
 * which ignore it, are not. So a trace in the window comes back with its events on both sides of it, and so do traces
 * that sit just before or just after it.
 */
@Testcontainers
public class BanyanDBSpanAttachedEventQueryIT {
    /** The trace with more events than a read capped at 100 rows returns. Zipkin trace ids are lower hex. */
    private static final String READ_TRACE = "5af7183fb1d4cf5f00000000000000a1";
    private static final String OTHER_READ_TRACE = "5af7183fb1d4cf5f00000000000000a2";
    /** Not asked for: its id is one digit from a read one, its events sit among theirs. */
    private static final String UNREAD_TRACE = "5af7183fb1d4cf5f00000000000000a3";
    /** Wholly before the window: its last event starts a millisecond before it. */
    private static final String BEFORE_TRACE = "5af7183fb1d4cf5f00000000000000b1";
    /** Its first event starts on the window's end, which the window counts; the others come after it. */
    private static final String AFTER_TRACE = "5af7183fb1d4cf5f00000000000000b2";
    /**
     * Two traces with one event each, on the same span id and segment, with one name and one start: what the storage
     * id of an event held before it carried the trace id (rover sends a Zipkin event with an empty segment id).
     */
    private static final String COLLIDING_TRACE = "5af7183fb1d4cf5f00000000000000c1";
    private static final String OTHER_COLLIDING_TRACE = "5af7183fb1d4cf5f00000000000000c2";
    /** A trace with an event on each of two spans of one segment, with one name and one start. */
    private static final String TWO_SPAN_TRACE = "5af7183fb1d4cf5f00000000000000c3";
    private static final List<String> ASKED = Arrays.asList(AFTER_TRACE, OTHER_READ_TRACE, BEFORE_TRACE, READ_TRACE);
    private static final int READ_TRACE_EVENTS = 130;
    private static final int OTHER_TRACE_EVENTS = 3;
    private static final String SERVICE = "span-attached-event-it";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId(SERVICE, true);
    private static final String INSTANCE = "instance";
    private static final Comparator<SpanAttachedEvent> BY_START_TIME =
        Comparator.comparingLong((SpanAttachedEvent event) -> event.getStartTime().getSeconds())
                  .thenComparingInt(event -> event.getStartTime().getNanos());

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBSpanAttachedEventQueryDAO QUERY_DAO;
    /** Two whole minutes from WINDOW_START; WINDOW_END, its end, is counted. */
    private static Duration WINDOW;
    private static long WINDOW_START;
    private static long WINDOW_END;
    private static List<SpanAttachedEvent> SW_EVENTS;
    private static List<SpanAttachedEvent> ZK_EVENTS;

    @BeforeAll
    public static void writeEvents() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        WINDOW_START = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)) / 60_000L * 60_000L;
        WINDOW_END = WINDOW_START + 120_000L;
        WINDOW = new Duration();
        WINDOW.setStep(Step.MINUTE);
        WINDOW.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(WINDOW_START));
        WINDOW.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(WINDOW_START + 60_000L));

        // The events are written into the traces they attach to, so those traces are installed first.
        final Model segment = SUPPORT.install(SegmentRecord.class, DefaultScopeDefine.SEGMENT, SegmentRecord.INDEX_NAME);
        final Model zipkinSpan = SUPPORT.install(
            ZipkinSpanRecord.class, DefaultScopeDefine.ZIPKIN_SPAN, ZipkinSpanRecord.INDEX_NAME);
        final Model swEvent = SUPPORT.install(
            SWSpanAttachedEventRecord.class, DefaultScopeDefine.SW_SPAN_ATTACHED_EVENT, SWSpanAttachedEventRecord.INDEX_NAME);
        final Model zkEvent = SUPPORT.install(
            SpanAttachedEventRecord.class, DefaultScopeDefine.SPAN_ATTACHED_EVENT, SpanAttachedEventRecord.INDEX_NAME);

        final List<SegmentRecord> segments = new ArrayList<>();
        final List<ZipkinSpanRecord> zipkinSpans = new ArrayList<>();
        for (final String traceId : Arrays.asList(READ_TRACE, OTHER_READ_TRACE, UNREAD_TRACE)) {
            segments.add(segment(traceId, WINDOW_START + 30_000L));
            zipkinSpans.add(zipkinSpan(traceId, WINDOW_START + 30_000L));
        }
        segments.add(segment(BEFORE_TRACE, WINDOW_START - 10_000L));
        zipkinSpans.add(zipkinSpan(BEFORE_TRACE, WINDOW_START - 10_000L));
        segments.add(segment(AFTER_TRACE, WINDOW_END + 10_000L));
        zipkinSpans.add(zipkinSpan(AFTER_TRACE, WINDOW_END + 10_000L));
        SUPPORT.write(segment, new SegmentRecord.Builder(), segments);
        SUPPORT.write(zipkinSpan, new ZipkinSpanRecord.Builder(), zipkinSpans);

        SW_EVENTS = events(SpanAttachedEvent.SpanReferenceType.SKYWALKING);
        SUPPORT.write(swEvent, new SWSpanAttachedEventRecord.Builder(), swRecords(SW_EVENTS));
        ZK_EVENTS = events(SpanAttachedEvent.SpanReferenceType.ZIPKIN);
        SUPPORT.write(zkEvent, new SpanAttachedEventRecord.Builder(), zkRecords(ZK_EVENTS));

        QUERY_DAO = new BanyanDBSpanAttachedEventQueryDAO(SUPPORT.getClient());
        // Ready once every event written reads back whole.
        final List<String> swTraces = SW_EVENTS.stream().map(event -> event.getTraceContext().getTraceId()).distinct()
                                               .collect(Collectors.toList());
        final List<String> zkTraces = ZK_EVENTS.stream().map(event -> event.getTraceContext().getTraceId()).distinct()
                                               .collect(Collectors.toList());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(sorted(expectedSW(SW_EVENTS)), sorted(describeSW(QUERY_DAO.querySWSpanAttachedEvents(swTraces, null))));
            assertEquals(sorted(expectedZK(ZK_EVENTS)), sorted(describeZK(QUERY_DAO.queryZKSpanAttachedEvents(zkTraces, null))));
        });
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsEveryEventOfTheNativeTracesAskedFor() throws IOException {
        assertEquals(expectedSW(eventsOf(SW_EVENTS, ASKED)),
                     describeSW(QUERY_DAO.querySWSpanAttachedEvents(ASKED, null)));
    }

    @Test
    public void readsTheNativeTracesWholeThroughAWindow() throws IOException {
        assertEquals(expectedSW(eventsOf(SW_EVENTS, ASKED)),
                     describeSW(QUERY_DAO.querySWSpanAttachedEvents(ASKED, WINDOW)));
    }

    @Test
    public void readsEveryEventOfTheZipkinTracesAskedFor() throws IOException {
        assertEquals(expectedZK(eventsOf(ZK_EVENTS, ASKED)),
                     describeZK(QUERY_DAO.queryZKSpanAttachedEvents(ASKED, null)));
    }

    @Test
    public void readsTheZipkinTracesWholeThroughAWindow() throws IOException {
        assertEquals(expectedZK(eventsOf(ZK_EVENTS, ASKED)),
                     describeZK(QUERY_DAO.queryZKSpanAttachedEvents(ASKED, WINDOW)));
    }

    @Test
    public void keepsNativeEventsOfTwoTracesOnOneSpanWithOneNameAndStart() throws IOException {
        final List<String> traces = Arrays.asList(COLLIDING_TRACE, OTHER_COLLIDING_TRACE);
        assertEquals(sorted(expectedSW(eventsOf(SW_EVENTS, traces))),
                     sorted(describeSW(QUERY_DAO.querySWSpanAttachedEvents(traces, null))));
    }

    @Test
    public void keepsNativeEventsOfTwoSpansWithOneNameAndStart() throws IOException {
        final List<String> traces = Collections.singletonList(TWO_SPAN_TRACE);
        assertEquals(sorted(expectedSW(eventsOf(SW_EVENTS, traces))),
                     sorted(describeSW(QUERY_DAO.querySWSpanAttachedEvents(traces, null))));
    }

    @Test
    public void keepsZipkinEventsOfTwoTracesOnOneSpanWithOneNameAndStart() throws IOException {
        final List<String> traces = Arrays.asList(COLLIDING_TRACE, OTHER_COLLIDING_TRACE);
        assertEquals(sorted(expectedZK(eventsOf(ZK_EVENTS, traces))),
                     sorted(describeZK(QUERY_DAO.queryZKSpanAttachedEvents(traces, null))));
    }

    @Test
    public void keepsZipkinEventsOfTwoSpansWithOneNameAndStart() throws IOException {
        final List<String> traces = Collections.singletonList(TWO_SPAN_TRACE);
        assertEquals(sorted(expectedZK(eventsOf(ZK_EVENTS, traces))),
                     sorted(describeZK(QUERY_DAO.queryZKSpanAttachedEvents(traces, null))));
    }

    /**
     * The events of every trace, in an order unlike their start times. The read trace's events reach both ends of the
     * window; two of them start in the same millisecond, apart only in nanoseconds, and the later one is written
     * first.
     */
    private static List<SpanAttachedEvent> events(SpanAttachedEvent.SpanReferenceType type) {
        final List<SpanAttachedEvent> events = new ArrayList<>();
        events.add(event(type, AFTER_TRACE, "after-0", WINDOW_END, 0));
        final long step = (WINDOW_END - 1 - WINDOW_START) / (READ_TRACE_EVENTS - 1);
        for (int i = READ_TRACE_EVENTS - 1; i >= 0; i--) {
            final long start = i == READ_TRACE_EVENTS - 1 ? WINDOW_END - 1 : WINDOW_START + i * step;
            events.add(event(type, READ_TRACE, "event-" + i, start, i == 1 ? 900 : 0));
        }
        events.add(event(type, READ_TRACE, "same-millisecond", WINDOW_START + step, 100));
        for (int i = 0; i < OTHER_TRACE_EVENTS; i++) {
            final long start = WINDOW_START + 1_000L + i * 50_000L;
            events.add(event(type, UNREAD_TRACE, "unread-" + i, start, 0));
            events.add(event(type, OTHER_READ_TRACE, "other-" + i, start + 1, 0));
            events.add(event(type, BEFORE_TRACE, "before-" + i, WINDOW_START - 1 - i * 1_000L, 0));
            events.add(event(type, AFTER_TRACE, "after-" + (OTHER_TRACE_EVENTS - i), WINDOW_END + 1_000L + i, 0));
        }
        final boolean zipkin = type == SpanAttachedEvent.SpanReferenceType.ZIPKIN;
        final String segment = zipkin ? "" : "collision.segment";
        final long start = WINDOW_START + 20_000L;
        events.add(event(type, COLLIDING_TRACE, segment, zipkin ? "00000000000000c0" : "0", "collision", start, 0));
        events.add(event(type, OTHER_COLLIDING_TRACE, segment, zipkin ? "00000000000000c0" : "0", "collision", start, 0));
        events.add(event(type, TWO_SPAN_TRACE, segment, zipkin ? "00000000000000c0" : "0", "collision", start, 0));
        events.add(event(type, TWO_SPAN_TRACE, segment, zipkin ? "00000000000000c1" : "1", "collision", start, 0));
        return events;
    }

    /** The events of these traces in start-time order. */
    private static List<SpanAttachedEvent> eventsOf(List<SpanAttachedEvent> events, List<String> traceIds) {
        return events.stream().filter(event -> traceIds.contains(event.getTraceContext().getTraceId()))
                     .sorted(BY_START_TIME).collect(Collectors.toList());
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().collect(Collectors.toList());
    }

    /**
     * Each record whole: every column its storage builder stores, and its time bucket, which no event query fills in
     * (the event builders neither store nor read it, on any storage).
     */
    private static List<String> describeSW(List<SWSpanAttachedEventRecord> records) {
        return records.stream()
                      .map(record -> BanyanDBQueryITSupport.columns(new SWSpanAttachedEventRecord.Builder(), record)
                          + " time_bucket=" + record.getTimeBucket())
                      .collect(Collectors.toList());
    }

    /** The records the event query must return for these events, rendered as {@link #describeSW}. */
    private static List<String> expectedSW(List<SpanAttachedEvent> events) {
        return swRecords(events).stream()
                                .map(record -> BanyanDBQueryITSupport.columns(new SWSpanAttachedEventRecord.Builder(), record)
                                    + " time_bucket=0")
                                .collect(Collectors.toList());
    }

    private static List<String> describeZK(List<SpanAttachedEventRecord> records) {
        return records.stream()
                      .map(record -> BanyanDBQueryITSupport.columns(new SpanAttachedEventRecord.Builder(), record)
                          + " time_bucket=" + record.getTimeBucket())
                      .collect(Collectors.toList());
    }

    private static List<String> expectedZK(List<SpanAttachedEvent> events) {
        return zkRecords(events).stream()
                                .map(record -> BanyanDBQueryITSupport.columns(new SpanAttachedEventRecord.Builder(), record)
                                    + " time_bucket=0")
                                .collect(Collectors.toList());
    }

    private static SpanAttachedEvent event(SpanAttachedEvent.SpanReferenceType type, String traceId, String name,
                                           long startMillis, int extraNanos) {
        final boolean zipkin = type == SpanAttachedEvent.SpanReferenceType.ZIPKIN;
        return event(type, traceId, zipkin ? "" : segmentId(traceId), zipkin ? zipkinSpanId(traceId) : "0", name,
                     startMillis, extraNanos);
    }

    private static SpanAttachedEvent event(SpanAttachedEvent.SpanReferenceType type, String traceId, String segmentId,
                                           String spanId, String name, long startMillis, int extraNanos) {
        return SpanAttachedEvent.newBuilder()
            .setStartTime(instant(startMillis, extraNanos))
            .setEndTime(instant(startMillis + 1, 0))
            .setEvent(name)
            .addTags(KeyStringValuePair.newBuilder().setKey("data_direction").setValue("outbound"))
            .setTraceContext(SpanAttachedEvent.SpanReference.newBuilder()
                                                            .setType(type)
                                                            .setTraceId(traceId)
                                                            .setTraceSegmentId(segmentId)
                                                            .setSpanId(spanId))
            .build();
    }

    private static Instant instant(long millis, int extraNanos) {
        return Instant.newBuilder()
                      .setSeconds(millis / 1000)
                      .setNanos((int) (millis % 1000) * 1_000_000 + extraNanos)
                      .build();
    }

    /** The records the trace receiver builds from native traces' events. */
    private static List<SWSpanAttachedEventRecord> swRecords(List<SpanAttachedEvent> events) {
        final List<SWSpanAttachedEventRecord> records = new ArrayList<>();
        for (final SpanAttachedEvent event : events) {
            records.add(BanyanDBQueryITSupport.swEventRecord(event));
        }
        return records;
    }

    /** The records the trace receiver builds from Zipkin traces' events. */
    private static List<SpanAttachedEventRecord> zkRecords(List<SpanAttachedEvent> events) {
        final List<SpanAttachedEventRecord> records = new ArrayList<>();
        for (final SpanAttachedEvent event : events) {
            records.add(BanyanDBQueryITSupport.zkEventRecord(event));
        }
        return records;
    }

    private static SegmentRecord segment(String traceId, long startTime) {
        final String instanceId = IDManager.ServiceInstanceID.buildId(SERVICE_ID, INSTANCE);
        final SegmentObject segmentObject = SegmentObject.newBuilder()
            .setTraceId(traceId)
            .setTraceSegmentId(segmentId(traceId))
            .setService(SERVICE)
            .setServiceInstance(INSTANCE)
            .addSpans(SpanObject.newBuilder()
                                .setSpanId(0)
                                .setParentSpanId(-1)
                                .setStartTime(startTime)
                                .setEndTime(startTime + 10)
                                .setOperationName("GET:/read")
                                .setSpanType(SpanType.Exit))
            .build();
        final SegmentRecord segment = new SegmentRecord();
        segment.setSegmentId(segmentId(traceId));
        segment.setTraceId(traceId);
        segment.setServiceId(SERVICE_ID);
        segment.setServiceInstanceId(instanceId);
        segment.setEndpointId(IDManager.EndpointID.buildId(SERVICE_ID, "GET:/read"));
        segment.setStartTime(startTime);
        segment.setLatency(10);
        segment.setIsError(0);
        segment.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
        segment.setDataBinary(segmentObject.toByteArray());
        segment.setTags(Collections.emptyList());
        return segment;
    }

    private static ZipkinSpanRecord zipkinSpan(String traceId, long timestampMillis) {
        final ZipkinSpanRecord span = new ZipkinSpanRecord();
        span.setTraceId(traceId);
        span.setSpanId(zipkinSpanId(traceId));
        span.setKind("CLIENT");
        span.setName("get /read");
        span.setLocalEndpointServiceName(SERVICE);
        span.setTimestamp(timestampMillis * 1000L);
        span.setTimestampMillis(timestampMillis);
        span.setDuration(10_000L);
        span.setQuery(Collections.emptyList());
        span.setTimeBucket(TimeBucket.getRecordTimeBucket(timestampMillis));
        return span;
    }

    private static String segmentId(String traceId) {
        return traceId + ".segment";
    }

    private static String zipkinSpanId(String traceId) {
        return String.format("%016x", traceId.hashCode() & 0xffffffffL);
    }
}
