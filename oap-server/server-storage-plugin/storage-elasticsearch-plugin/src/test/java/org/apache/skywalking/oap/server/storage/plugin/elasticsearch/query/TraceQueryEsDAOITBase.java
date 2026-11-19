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
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads native segments the way the trace list and trace profiling do on Elasticsearch and OpenSearch, through
 * {@link TraceQueryEsDAO}: the trace search, the segments of one trace by its id, and the two reads of a trace
 * profiling task, its profiled segments by id and then the segments of their traces on the profiled instances. A
 * nested class of a server's IT names the server and the storage mode.
 *
 * <p>The search range is a minute either side of midnight, so it reads two daily indices. Each search condition is
 * tried alone, in both orders, and with all the others. For every condition a near-miss segment matches all the
 * others but that one, and the two matching segments lie on the bounds of the range and of the latency. A segment is
 * kept by the second it starts in, so the segments just outside the range start a millisecond before its first second
 * and on the first millisecond after its last. The search lists segments, not traces: the trace that matches every
 * condition has a matching segment either side of midnight, and a third segment, of another service, after the range.
 * The reads by trace id and by segment id are not bounded by the range on this storage.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TraceQueryEsDAOITBase {
    /** A fixed local midnight, so the daily indices the segments are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    private static final long START = MIDNIGHT - 60_000L;
    /** The first millisecond after the range's last second. */
    private static final long END = MIDNIGHT + 60_000L;
    private static final Duration AROUND_MIDNIGHT = duration(START, END - 1_000L);

    private static final String CHECKOUT = "checkout";
    private static final String CANARY = "checkout-canary";
    private static final String INVENTORY = "inventory";
    private static final String CHECKOUT_1 = "checkout-1";
    private static final String OTHER_INSTANCE = "checkout-2";
    private static final String POST_ORDERS = "POST:/orders";
    private static final String OTHER_ENDPOINT = "GET:/orders";
    private static final int MIN_LATENCY = 100;
    private static final int MAX_LATENCY = 900;
    private static final Tag METHOD = new Tag("http.method", "POST");
    private static final Tag STATUS = new Tag("http.status_code", "500");

    /** The trace matching every condition. */
    private static final String TRACE = "4a2b6c8d0e1f.37.17684352000000001";
    /** One digit off the matching trace's id. */
    private static final String OTHER_TRACE = "4a2b6c8d0e1f.37.17684352000000002";
    private static final String UNKNOWN_TRACE = "4a2b6c8d0e1f.37.17684352000000009";

    /** On the range's start, before midnight, at the least latency. */
    private static final String FIRST_MATCH = "segment-first-match";
    /** On the range's last millisecond, after midnight, at the most latency. */
    private static final String LAST_MATCH = "segment-last-match";
    private static final String OTHER_SERVICE = "segment-other-service";
    private static final String OTHER_INSTANCE_SEGMENT = "segment-other-instance";
    private static final String OTHER_ENDPOINT_SEGMENT = "segment-other-endpoint";
    private static final String OTHER_TRACE_SEGMENT = "segment-other-trace";
    private static final String BELOW_MIN_LATENCY = "segment-below-min-latency";
    private static final String ABOVE_MAX_LATENCY = "segment-above-max-latency";
    private static final String SUCCEEDED = "segment-succeeded";
    /** Has one of the two tags searched for, and the other with another value. */
    private static final String OTHER_TAG_VALUE = "segment-other-tag-value";
    private static final String BEFORE_START = "segment-before-start";
    private static final String AFTER_END = "segment-after-end";
    /** The matching trace's call to the inventory, after the range. */
    private static final String DOWNSTREAM = "segment-downstream";

    private static final Map<String, StoredSegment> SEGMENTS = fixture();
    /** The segments in the range, latest start first. */
    private static final List<String> IN_RANGE = Arrays.asList(
        LAST_MATCH, OTHER_TRACE_SEGMENT, OTHER_TAG_VALUE, SUCCEEDED, ABOVE_MAX_LATENCY, BELOW_MIN_LATENCY,
        OTHER_ENDPOINT_SEGMENT, OTHER_INSTANCE_SEGMENT, OTHER_SERVICE, FIRST_MATCH);

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private TraceQueryEsDAO dao;

    TraceQueryEsDAOITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeSegments() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        final Model model = support.installRecord(SegmentRecord.class);
        // Neither in start order nor against it.
        final List<SegmentRecord> records = new ArrayList<>();
        for (final String id : Arrays.asList(
            OTHER_TAG_VALUE, FIRST_MATCH, AFTER_END, OTHER_INSTANCE_SEGMENT, DOWNSTREAM, SUCCEEDED, LAST_MATCH,
            BELOW_MIN_LATENCY, OTHER_SERVICE, BEFORE_START, OTHER_TRACE_SEGMENT, ABOVE_MAX_LATENCY,
            OTHER_ENDPOINT_SEGMENT)) {
            records.add(SEGMENTS.get(id).record());
        }
        support.writeRecords(model, new SegmentRecord.Builder(), records);
        dao = new TraceQueryEsDAO(support.getClient(), support.getConfig().getSegmentQueryMaxSize());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("conditionsInBothOrders")
    public void findsTheSegmentsMatchingOneCondition(final Condition condition, final QueryOrder order)
        throws IOException {
        final List<String> expected = expected(condition.matching(), order);
        assertEquals(expected, search(condition.criterion, order, 0, expected.size()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(QueryOrder.class)
    public void findsOnlyTheSegmentsMatchingEveryCondition(final QueryOrder order) throws IOException {
        final Consumer<Search> everyCondition = search -> {
            for (final Condition condition : Condition.values()) {
                if (condition != Condition.SUCCESS) {
                    condition.criterion.accept(search);
                }
            }
        };
        assertEquals(expected(Arrays.asList(FIRST_MATCH, LAST_MATCH), order), search(everyCondition, order, 0, 10));
    }

    @Test
    public void findsNoSegmentWhenNoneMatches() throws IOException {
        assertEquals(Collections.emptyList(),
                     search(search -> search.serviceId = serviceId("unknown"), QueryOrder.BY_START_TIME, 0, 10));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(QueryOrder.class)
    public void pagesThroughTheSegmentsOfTheRange(final QueryOrder order) throws IOException {
        final List<String> all = expected(IN_RANGE, order);
        assertEquals(all.subList(0, 4), search(search -> { }, order, 0, 4));
        assertEquals(all.subList(4, 8), search(search -> { }, order, 4, 4));
        assertEquals(all.subList(8, 10), search(search -> { }, order, 8, 4));
        assertEquals(Collections.emptyList(), search(search -> { }, order, 12, 4));
    }

    @Test
    public void readsAWholeTraceByIdAcrossMidnight() throws IOException {
        final List<String> trace = records(FIRST_MATCH, LAST_MATCH, DOWNSTREAM);
        assertEquals(trace, sorted(dao.queryByTraceId(TRACE, AROUND_MIDNIGHT)));
        assertEquals(trace, sorted(dao.queryByTraceId(TRACE, null)));
        assertEquals(Collections.emptyList(), dao.queryByTraceId(UNKNOWN_TRACE, null));
    }

    /**
     * The flexible trace query reads the traces of a third-party format that has no segments, which this storage
     * does not keep.
     */
    @Test
    public void readsNoTraceWithoutSegments() throws IOException {
        assertEquals(Collections.emptyList(), dao.doFlexibleTraceQuery(TRACE));
    }

    @Test
    public void readsTheProfiledSegmentsById() throws IOException {
        assertEquals(records(FIRST_MATCH, LAST_MATCH, BEFORE_START),
                     sorted(dao.queryBySegmentIdList(
                         Arrays.asList(LAST_MATCH, "segment-unknown", BEFORE_START, FIRST_MATCH), AROUND_MIDNIGHT)));
    }

    /**
     * apache/skywalking#14239: a trace profiling task without a sampled segment has no segment id to read.
     */
    @Test
    public void readsNoSegmentForNoSegmentId() throws IOException {
        assertEquals(Collections.emptyList(), dao.queryBySegmentIdList(Collections.emptyList(), AROUND_MIDNIGHT));
    }

    /**
     * The segments of other traces on the profiled instance stay out, and so do the listed traces' segments on the
     * other instances until those are asked for: the matching trace's call to the inventory, and the other listed
     * trace's segment on the second checkout instance.
     */
    @Test
    public void readsTheSegmentsOfTheProfiledTracesOnTheProfiledInstances() throws IOException {
        final List<String> traces = Arrays.asList(TRACE, traceOf(OTHER_INSTANCE_SEGMENT));
        assertEquals(records(FIRST_MATCH, LAST_MATCH), sorted(dao.queryByTraceIdWithInstanceId(
            traces, Collections.singletonList(instanceId(CHECKOUT, CHECKOUT_1)), null)));
        assertEquals(records(FIRST_MATCH, LAST_MATCH, DOWNSTREAM, OTHER_INSTANCE_SEGMENT),
                     sorted(dao.queryByTraceIdWithInstanceId(traces, Arrays.asList(
                         instanceId(CHECKOUT, CHECKOUT_1), instanceId(CHECKOUT, OTHER_INSTANCE),
                         instanceId(INVENTORY, "inventory-1")), null)));
    }

    @Test
    public void readsNoSegmentForNoTraceOrNoInstance() throws IOException {
        assertEquals(Collections.emptyList(), dao.queryByTraceIdWithInstanceId(
            Collections.emptyList(), Collections.singletonList(instanceId(CHECKOUT, CHECKOUT_1)), null));
        assertEquals(Collections.emptyList(), dao.queryByTraceIdWithInstanceId(
            Collections.singletonList(TRACE), Collections.emptyList(), null));
    }

    /** What the trace search filters by, and the segments in the range each keeps. */
    enum Condition {
        TIME_RANGE_ONLY(search -> { }),
        SERVICE(search -> search.serviceId = serviceId(CHECKOUT), OTHER_SERVICE),
        // The other service's segment is on an instance and an endpoint of that service.
        INSTANCE(search -> search.instanceId = instanceId(CHECKOUT, CHECKOUT_1),
                 OTHER_INSTANCE_SEGMENT, OTHER_SERVICE),
        ENDPOINT(search -> search.endpointId = endpointId(CHECKOUT, POST_ORDERS),
                 OTHER_ENDPOINT_SEGMENT, OTHER_SERVICE),
        TRACE_ID(search -> search.traceId = TRACE, only(FIRST_MATCH, LAST_MATCH)),
        MIN_DURATION(search -> search.minLatency = MIN_LATENCY, BELOW_MIN_LATENCY),
        MAX_DURATION(search -> search.maxLatency = MAX_LATENCY, ABOVE_MAX_LATENCY),
        ERROR(search -> search.state = TraceState.ERROR, SUCCEEDED),
        SUCCESS(search -> search.state = TraceState.SUCCESS, only(SUCCEEDED)),
        TAGS(search -> search.tags = Arrays.asList(METHOD, STATUS), OTHER_TAG_VALUE);

        private final Consumer<Search> criterion;
        private final List<String> excluded;
        private final List<String> matching;

        Condition(final Consumer<Search> criterion, final String... excluded) {
            this.criterion = criterion;
            this.excluded = Arrays.asList(excluded);
            this.matching = null;
        }

        Condition(final Consumer<Search> criterion, final List<String> matching) {
            this.criterion = criterion;
            this.excluded = Collections.emptyList();
            this.matching = matching;
        }

        private List<String> matching() {
            if (matching != null) {
                return matching;
            }
            final List<String> kept = new ArrayList<>(IN_RANGE);
            kept.removeAll(excluded);
            return kept;
        }

        private static List<String> only(final String... segments) {
            return Arrays.asList(segments);
        }
    }

    static Stream<Arguments> conditionsInBothOrders() {
        return Arrays.stream(Condition.values()).flatMap(condition -> Stream.of(
            Arguments.of(condition, QueryOrder.BY_START_TIME),
            Arguments.of(condition, QueryOrder.BY_DURATION)));
    }

    /** The arguments of a trace search besides the range, the order and the page. */
    private static final class Search {
        private long minLatency;
        private long maxLatency;
        private String serviceId;
        private String instanceId;
        private String endpointId;
        private String traceId;
        private TraceState state = TraceState.ALL;
        private List<Tag> tags;
    }

    private List<String> search(final Consumer<Search> criteria, final QueryOrder order, final int from,
                                final int limit) throws IOException {
        final Search search = new Search();
        criteria.accept(search);
        final List<String> found = new ArrayList<>();
        for (final BasicTrace trace : dao.queryBasicTraces(
            AROUND_MIDNIGHT, search.minLatency, search.maxLatency, search.serviceId, search.instanceId,
            search.endpointId, search.traceId, limit, from, search.state, order, search.tags).getTraces()) {
            found.add(render(trace));
        }
        return found;
    }

    /** The segments as the search lists them, in the order asked for. */
    private static List<String> expected(final List<String> segments, final QueryOrder order) {
        final Comparator<StoredSegment> latestFirst = Comparator.comparingLong(segment -> -segment.start);
        final Comparator<StoredSegment> slowestFirst = Comparator.comparingInt(segment -> -segment.latency);
        return segments.stream()
                       .map(SEGMENTS::get)
                       .sorted(order == QueryOrder.BY_DURATION ? slowestFirst : latestFirst)
                       .map(StoredSegment::listed)
                       .collect(Collectors.toList());
    }

    private static String render(final BasicTrace trace) {
        return trace.getSegmentId() + " of " + trace.getTraceIds() + " at " + trace.getEndpointNames()
            + ", start " + trace.getStart() + ", " + trace.getDuration() + " ms, error " + trace.isError();
    }

    /** Every column of the segment the trace and profiling queries use. */
    private static String render(final SegmentRecord segment) {
        return segment.getSegmentId() + " of " + segment.getTraceId() + " on " + segment.getServiceId() + " / "
            + segment.getServiceInstanceId() + " / " + segment.getEndpointId() + ", start " + segment.getStartTime()
            + ", " + segment.getLatency() + " ms, error " + segment.getIsError() + ", "
            + Base64.getEncoder().encodeToString(segment.getDataBinary());
    }

    private static List<String> records(final String... segments) {
        return Arrays.stream(segments).sorted().map(id -> render(SEGMENTS.get(id).record()))
                     .collect(Collectors.toList());
    }

    private static List<String> sorted(final List<SegmentRecord> segments) {
        return segments.stream()
                       .sorted(Comparator.comparing(SegmentRecord::getSegmentId))
                       .map(TraceQueryEsDAOITBase::render)
                       .collect(Collectors.toList());
    }

    private static Map<String, StoredSegment> fixture() {
        final Map<String, StoredSegment> segments = new LinkedHashMap<>();
        add(segments, new StoredSegment(FIRST_MATCH, START, MIN_LATENCY).trace(TRACE));
        add(segments, new StoredSegment(OTHER_SERVICE, MIDNIGHT - 50_000L, 150).service(CANARY));
        add(segments, new StoredSegment(OTHER_INSTANCE_SEGMENT, MIDNIGHT - 45_000L, 200).instance(OTHER_INSTANCE));
        add(segments, new StoredSegment(OTHER_ENDPOINT_SEGMENT, MIDNIGHT - 40_000L, 250).endpoint(OTHER_ENDPOINT));
        add(segments, new StoredSegment(BELOW_MIN_LATENCY, MIDNIGHT - 30_000L, MIN_LATENCY - 1));
        add(segments, new StoredSegment(ABOVE_MAX_LATENCY, MIDNIGHT - 20_000L, MAX_LATENCY + 1));
        add(segments, new StoredSegment(SUCCEEDED, MIDNIGHT - 10_000L, 300).succeeded());
        add(segments, new StoredSegment(OTHER_TAG_VALUE, MIDNIGHT + 10_000L, 350)
            .tags(METHOD, new Tag(STATUS.getKey(), "200")));
        add(segments, new StoredSegment(OTHER_TRACE_SEGMENT, MIDNIGHT + 20_000L, 400).trace(OTHER_TRACE));
        add(segments, new StoredSegment(LAST_MATCH, END - 1, MAX_LATENCY).trace(TRACE));
        add(segments, new StoredSegment(BEFORE_START, START - 1, 500));
        add(segments, new StoredSegment(AFTER_END, END, 550));
        add(segments, new StoredSegment(DOWNSTREAM, END + 1_000L, 50)
            .trace(TRACE).service(INVENTORY).instance("inventory-1").endpoint("GET:/stock").tags());
        return segments;
    }

    private static void add(final Map<String, StoredSegment> segments, final StoredSegment segment) {
        segments.put(segment.id, segment);
    }

    /** A segment of its own trace matching every condition unless told otherwise. */
    private static final class StoredSegment {
        private final String id;
        private final long start;
        private final int latency;
        private String traceId;
        private String service = CHECKOUT;
        private String instance = CHECKOUT_1;
        private String endpoint = POST_ORDERS;
        private boolean error = true;
        private List<Tag> tags = Arrays.asList(METHOD, STATUS);

        private StoredSegment(final String id, final long start, final int latency) {
            this.id = id;
            this.start = start;
            this.latency = latency;
            this.traceId = traceOf(id);
        }

        private StoredSegment trace(final String traceId) {
            this.traceId = traceId;
            return this;
        }

        private StoredSegment service(final String service) {
            this.service = service;
            return this;
        }

        private StoredSegment instance(final String instance) {
            this.instance = instance;
            return this;
        }

        private StoredSegment endpoint(final String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        private StoredSegment succeeded() {
            this.error = false;
            return this;
        }

        private StoredSegment tags(final Tag... tags) {
            this.tags = Arrays.asList(tags);
            return this;
        }

        /** The record {@code SegmentAnalysisListener} stores for the segment, with its searchable tags. */
        private SegmentRecord record() {
            final SegmentObject segment = SegmentObject.newBuilder()
                .setTraceId(traceId)
                .setTraceSegmentId(id)
                .setService(service)
                .setServiceInstance(instance)
                .addSpans(SpanObject.newBuilder()
                                    .setSpanId(0)
                                    .setParentSpanId(-1)
                                    .setStartTime(start)
                                    .setEndTime(start + latency)
                                    .setOperationName(endpoint)
                                    .setSpanType(SpanType.Entry)
                                    .setIsError(error))
                .build();
            final SegmentRecord record = new SegmentRecord();
            record.setSegmentId(id);
            record.setTraceId(traceId);
            record.setServiceId(serviceId(service));
            record.setServiceInstanceId(instanceId(service, instance));
            record.setEndpointId(endpointId(service, endpoint));
            record.setStartTime(start);
            record.setLatency(latency);
            record.setIsError(error ? 1 : 0);
            record.setDataBinary(segment.toByteArray());
            record.setTags(tags.stream().map(Tag::toString).collect(Collectors.toList()));
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(start));
            return record;
        }

        /** The segment as the trace search lists it. */
        private String listed() {
            final BasicTrace trace = new BasicTrace();
            trace.setSegmentId(id);
            trace.getTraceIds().add(traceId);
            trace.getEndpointNames().add(endpoint);
            trace.setStart(String.valueOf(start));
            trace.setDuration(latency);
            trace.setError(error);
            return render(trace);
        }
    }

    private static String traceOf(final String segment) {
        return "trace-of-" + segment;
    }

    private static String serviceId(final String service) {
        return IDManager.ServiceID.buildId(service, true);
    }

    private static String instanceId(final String service, final String instance) {
        return IDManager.ServiceInstanceID.buildId(serviceId(service), instance);
    }

    private static String endpointId(final String service, final String endpoint) {
        return IDManager.EndpointID.buildId(serviceId(service), endpoint);
    }

    /** The duration the query protocol passes for a range read by the second. */
    private static Duration duration(final long start, final long end) {
        final Duration duration = new Duration();
        duration.setStep(Step.SECOND);
        duration.setStart(new DateTime(start).toString("yyyy-MM-dd HHmmss"));
        duration.setEnd(new DateTime(end).toString("yyyy-MM-dd HHmmss"));
        return duration;
    }
}
