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
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBTraceQueryDAO;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads native segments the way the query services do, on the real {@code segment} trace: trace profiling's two
 * segment reads, the trace search, and the read of one trace by id.
 *
 * <p>Trace profiling reads its profiled segments by id under the DAO's cap, and then the segments of those traces
 * on the profiled instances. The segment id is the trace's span id, so BanyanDB filters it after reading, and the
 * cap must count the segments that match, not the newest traces it read (fixed in BanyanDB by
 * apache/skywalking-banyandb#1444). The search filters on tags the trace's index does not answer, so a matching
 * trace must be found behind any number of newer or longer ones.
 */
@Testcontainers
public class BanyanDBTraceQueryIT {
    /** Segments of one service, one second apart, each its own trace; the first and the last are the ones read. */
    private static final int SEGMENTS = 30;
    /** The DAO's cap in this test: as many as the profiled segments, so each read asks for exactly what matches. */
    private static final int PROFILED_SEGMENTS = 2;
    private static final String SERVICE = "trace-query-it";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId(SERVICE, true);
    private static final String READ_INSTANCE = "instance-read";
    private static final String OTHER_INSTANCE = "instance-other";
    private static final String READ_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, READ_INSTANCE);
    private static final String READ_ENDPOINT = "POST:/read";
    private static final String OTHER_ENDPOINT = "GET:/other";
    private static final Tag READ_TAG = new Tag("http.method", "POST");
    /** A segment the first trace's call made on another instance; it matches no condition the tests read by. */
    private static final String DOWNSTREAM_SEGMENT = segmentId(0) + "-downstream";

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBTraceQueryDAO QUERY_DAO;
    private static Duration DURATION;

    @BeforeAll
    public static void writeSegments() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(SegmentRecord.class, DefaultScopeDefine.SEGMENT, SegmentRecord.INDEX_NAME);
        final long first = System.currentTimeMillis() - SEGMENTS * 1000L;
        final List<SegmentRecord> segments = new ArrayList<>();
        for (int i = 0; i < SEGMENTS; i++) {
            final boolean read = i == 0 || i == SEGMENTS - 1;
            // The read segments are the shortest, so a search by duration finds them behind every other trace.
            final int latency = i == 0 ? 2 : i == SEGMENTS - 1 ? 1 : 100 + i;
            segments.add(read
                ? segment(segmentId(i), traceId(i), READ_INSTANCE, READ_ENDPOINT, first + i * 1000L, latency, true,
                          Collections.singletonList(READ_TAG.toString()))
                : segment(segmentId(i), traceId(i), OTHER_INSTANCE, OTHER_ENDPOINT, first + i * 1000L, latency, false,
                          Collections.singletonList("http.method=GET")));
        }
        segments.add(segment(DOWNSTREAM_SEGMENT, traceId(0), OTHER_INSTANCE, OTHER_ENDPOINT, first + 500L, 50, false,
                             Collections.emptyList()));
        SUPPORT.write(model, new SegmentRecord.Builder(), segments);

        QUERY_DAO = new BanyanDBTraceQueryDAO(SUPPORT.getClient(), PROFILED_SEGMENTS, namingControl());
        DURATION = BanyanDBQueryITSupport.around(first, first + (SEGMENTS - 1) * 1000L);
        await().atMost(30, TimeUnit.SECONDS)
               .untilAsserted(() -> assertEquals(SEGMENTS, search(new TraceQueryCondition(), QueryOrder.BY_START_TIME,
                                                                  1, SEGMENTS * 2).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsTheProfiledSegmentsById() throws IOException {
        final List<SegmentRecord> segments = QUERY_DAO.queryBySegmentIdList(
            Arrays.asList(segmentId(0), segmentId(SEGMENTS - 1)), DURATION);
        assertEquals(Arrays.asList(segmentId(0), segmentId(SEGMENTS - 1)), sortedIds(segments));
    }

    @Test
    public void readsTheSegmentsOfTheProfiledTracesOnTheProfiledInstances() throws IOException {
        final List<SegmentRecord> segments = QUERY_DAO.queryByTraceIdWithInstanceId(
            Arrays.asList(traceId(0), traceId(SEGMENTS - 1)), Collections.singletonList(READ_INSTANCE_ID), null);
        assertEquals(Arrays.asList(segmentId(0), segmentId(SEGMENTS - 1)), sortedIds(segments));
    }

    @Test
    public void readsTheWholeTraceById() throws IOException {
        final List<String> segmentIds = new ArrayList<>();
        for (final SpanWrapper span : QUERY_DAO.queryByTraceIdV2(traceId(0), DURATION)) {
            segmentIds.add(SegmentObject.parseFrom(span.getSpan()).getTraceSegmentId());
        }
        Collections.sort(segmentIds);
        assertEquals(Arrays.asList(segmentId(0), DOWNSTREAM_SEGMENT), segmentIds);
    }

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("searches")
    public void findsEveryMatchingTraceInOnePage(Condition condition, QueryOrder order) throws IOException {
        assertEquals(expected(order), search(condition.build(), order, 1, 2));
    }

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("searches")
    public void pagesThroughTheMatchingTraces(Condition condition, QueryOrder order) throws IOException {
        final List<List<String>> expected = expected(order);
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(Collections.singletonList(expected.get(i)), search(condition.build(), order, i + 1, 1),
                         "page " + (i + 1));
        }
        assertEquals(Collections.emptyList(), search(condition.build(), order, expected.size() + 1, 1));
    }

    /** What the trace search can filter by, each matching only the first and the last segment. */
    enum Condition {
        INSTANCE {
            @Override
            TraceQueryCondition build() {
                final TraceQueryCondition condition = new TraceQueryCondition();
                condition.setServiceInstanceId(READ_INSTANCE_ID);
                return condition;
            }
        },
        ENDPOINT {
            @Override
            TraceQueryCondition build() {
                final TraceQueryCondition condition = new TraceQueryCondition();
                condition.setEndpointId(IDManager.EndpointID.buildId(SERVICE_ID, READ_ENDPOINT));
                return condition;
            }
        },
        TAG {
            @Override
            TraceQueryCondition build() {
                final TraceQueryCondition condition = new TraceQueryCondition();
                condition.setTags(Collections.singletonList(READ_TAG));
                return condition;
            }
        },
        ERROR {
            @Override
            TraceQueryCondition build() {
                final TraceQueryCondition condition = new TraceQueryCondition();
                condition.setTraceState(TraceState.ERROR);
                return condition;
            }
        },
        MAX_DURATION {
            @Override
            TraceQueryCondition build() {
                final TraceQueryCondition condition = new TraceQueryCondition();
                condition.setMaxTraceDuration(2);
                return condition;
            }
        };

        abstract TraceQueryCondition build();
    }

    static Stream<Arguments> searches() {
        return Arrays.stream(Condition.values()).flatMap(condition -> Stream.of(
            Arguments.of(condition, QueryOrder.BY_START_TIME),
            Arguments.of(condition, QueryOrder.BY_DURATION)
        ));
    }

    /**
     * The two matching traces in the order asked for, each whole: the first trace also holds its downstream segment.
     */
    private static List<List<String>> expected(QueryOrder order) {
        final List<String> firstTrace = Arrays.asList(segmentId(0), DOWNSTREAM_SEGMENT);
        final List<String> lastTrace = Collections.singletonList(segmentId(SEGMENTS - 1));
        return order == QueryOrder.BY_START_TIME
            ? Arrays.asList(lastTrace, firstTrace)
            : Arrays.asList(firstTrace, lastTrace);
    }

    /** The sorted segment ids of each trace found. */
    private static List<List<String>> search(TraceQueryCondition condition, QueryOrder order, int pageNum,
                                             int pageSize) throws IOException {
        condition.setQueryDuration(DURATION);
        condition.setQueryOrder(order);
        if (condition.getTraceState() == null) {
            condition.setTraceState(TraceState.ALL);
        }
        condition.setPaging(new Pagination(pageNum, pageSize));
        final List<List<String>> traces = new ArrayList<>();
        for (final List<SpanWrapper> trace : QUERY_DAO.queryTraces(condition).getTraces()) {
            final List<String> segmentIds = new ArrayList<>();
            for (final SpanWrapper span : trace) {
                segmentIds.add(SegmentObject.parseFrom(span.getSpan()).getTraceSegmentId());
            }
            Collections.sort(segmentIds);
            traces.add(segmentIds);
        }
        return traces;
    }

    private static List<String> sortedIds(List<SegmentRecord> segments) {
        return segments.stream().map(SegmentRecord::getSegmentId).sorted().collect(Collectors.toList());
    }

    private static SegmentRecord segment(String segmentId, String traceId, String instance, String endpoint,
                                         long startTime, int latency, boolean error, List<String> tags) {
        final String instanceId = IDManager.ServiceInstanceID.buildId(SERVICE_ID, instance);
        final SegmentObject segmentObject = SegmentObject.newBuilder()
            .setTraceId(traceId)
            .setTraceSegmentId(segmentId)
            .setService(SERVICE)
            .setServiceInstance(instance)
            .addSpans(SpanObject.newBuilder()
                                .setSpanId(0)
                                .setParentSpanId(-1)
                                .setStartTime(startTime)
                                .setEndTime(startTime + latency)
                                .setOperationName(endpoint)
                                .setSpanType(SpanType.Entry)
                                .setIsError(error))
            .build();
        final SegmentRecord segment = new SegmentRecord();
        segment.setSegmentId(segmentId);
        segment.setTraceId(traceId);
        segment.setServiceId(SERVICE_ID);
        segment.setServiceInstanceId(instanceId);
        segment.setEndpointId(IDManager.EndpointID.buildId(SERVICE_ID, endpoint));
        segment.setStartTime(startTime);
        segment.setLatency(latency);
        segment.setIsError(error ? 1 : 0);
        segment.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
        segment.setDataBinary(segmentObject.toByteArray());
        segment.setTags(tags);
        return segment;
    }

    private static String segmentId(int index) {
        return "segment-" + index;
    }

    private static String traceId(int index) {
        return "trace-" + index;
    }

    /** The trace DAO names each segment's service, instance and endpoint the way the core module does. */
    private static ModuleManager namingControl() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder coreProvider = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreProvider);
        when(coreProvider.provider()).thenReturn(coreServices);
        when(coreServices.getService(NamingControl.class))
            .thenReturn(new NamingControl(70, 100, 150, new EndpointNameGrouping()));
        return moduleManager;
    }
}
