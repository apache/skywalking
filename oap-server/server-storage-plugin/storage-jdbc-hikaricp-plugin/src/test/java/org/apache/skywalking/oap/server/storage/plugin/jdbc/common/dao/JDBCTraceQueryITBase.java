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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads segments the way the trace list and trace profiling do, on segments written on both sides of midnight, so
 * in two day tables. A subclass names the server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCTraceQueryITBase {
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-1");
    private static final String ENDPOINT_ID = IDManager.EndpointID.buildId(SERVICE_ID, "POST /orders");
    /** Segment id, offset from midnight in seconds, latency in ms. Three before midnight, two after. */
    private static final Object[][] SEGMENTS = {
        {"s1", -30, 300},
        {"s2", -20, 100},
        {"s3", -10, 500},
        {"s4", 10, 200},
        {"s5", 20, 400},
    };

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCTraceQueryDAO dao;
    private Duration duration;

    JDBCTraceQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeSegments() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        final var model = support.install(
            SegmentRecord.class, DefaultScopeDefine.SEGMENT, SegmentRecord.INDEX_NAME, DownSampling.Second);
        final List<SegmentRecord> segments = new ArrayList<>();
        for (final Object[] segment : SEGMENTS) {
            segments.add(segment((String) segment[0], MIDNIGHT + (int) segment[1] * 1000L, (int) segment[2]));
        }
        support.writeRecords(model, new SegmentRecord.Builder(), segments);
        dao = new JDBCTraceQueryDAO(support.getModuleManager(), support.getClient(), support.getTableHelper());
        duration = JDBCQueryITSupport.duration(MIDNIGHT - 60_000L, MIDNIGHT + 60_000L, Step.SECOND);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * apache/skywalking#14239: a trace profiling task without a sampled segment has no segment id to read.
     */
    @Test
    public void readsNoSegmentForNoSegmentId() throws Exception {
        assertEquals(Collections.emptyList(), dao.queryBySegmentIdList(Collections.emptyList(), duration));
    }

    @Test
    public void readsTheSegmentsOfTheIdsOfBothDays() throws Exception {
        assertEquals(new TreeSet<>(Arrays.asList("s1", "s4")),
                     segmentIds(dao.queryBySegmentIdList(Arrays.asList("s1", "s4"), duration)));
    }

    @Test
    public void readsNoSegmentForNoTraceOrNoInstance() throws Exception {
        assertEquals(Collections.emptyList(), dao.queryByTraceIdWithInstanceId(
            Collections.emptyList(), Collections.singletonList(INSTANCE_ID), null));
        assertEquals(Collections.emptyList(), dao.queryByTraceIdWithInstanceId(
            Collections.singletonList("trace-s1"), Collections.emptyList(), null));
    }

    /**
     * Each page is cut from the segments of both days ranked together, latest start first.
     */
    @Test
    public void pagesTheLatestTracesAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("s5", "s4"), page(0, 2, QueryOrder.BY_START_TIME));
        assertEquals(Arrays.asList("s3", "s2"), page(2, 2, QueryOrder.BY_START_TIME));
        assertEquals(Collections.singletonList("s1"), page(4, 2, QueryOrder.BY_START_TIME));
    }

    @Test
    public void pagesTheSlowestTracesAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("s3", "s5", "s1"), page(0, 3, QueryOrder.BY_DURATION));
        assertEquals(Arrays.asList("s4", "s2"), page(3, 3, QueryOrder.BY_DURATION));
    }

    private List<String> page(final int from, final int limit, final QueryOrder order) throws Exception {
        return dao.queryBasicTraces(duration, 0, 0, SERVICE_ID, null, null, null, limit, from, TraceState.ALL, order, null)
                  .getTraces()
                  .stream()
                  .map(BasicTrace::getSegmentId)
                  .collect(Collectors.toList());
    }

    private static Set<String> segmentIds(final List<SegmentRecord> segments) {
        return segments.stream().map(SegmentRecord::getSegmentId).collect(Collectors.toCollection(TreeSet::new));
    }

    private static SegmentRecord segment(final String segmentId, final long startTime, final int latency) {
        final SegmentRecord segment = new SegmentRecord();
        segment.setSegmentId(segmentId);
        segment.setTraceId("trace-" + segmentId);
        segment.setServiceId(SERVICE_ID);
        segment.setServiceInstanceId(INSTANCE_ID);
        segment.setEndpointId(ENDPOINT_ID);
        segment.setStartTime(startTime);
        segment.setLatency(latency);
        segment.setIsError(0);
        segment.setDataBinary(new byte[] {1});
        segment.setTags(new ArrayList<>());
        segment.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
        return segment;
    }
}
