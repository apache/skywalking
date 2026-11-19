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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.zipkin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads Zipkin data the way the Zipkin query API does, through {@link ZipkinQueryEsDAO}, from what OAP's own write
 * path stored in Elasticsearch or OpenSearch. A nested class of a server's IT names the server and the storage mode.
 *
 * <p>The search range spans a midnight, so it reads two daily indices. Each search criterion is tried alone and
 * with all the others. For every criterion a near-miss trace matches all the others but that one, the matching traces
 * lie on the bounds of the time range and of the duration, and a search asks for exactly as many traces as should
 * match, so a near-miss that leaks in pushes a match out. A trace found by one span must come back with all of them.
 *
 * <p>The Zipkin API promises no order of the traces it returns (zipkin2 {@code SpanStore#getTraces},
 * {@code Traces#getTraces}) and the query handler passes the DAO's order through, so traces are compared regardless
 * of order. Which traces a limit keeps is promised: the ones nearest the end of the range (zipkin2
 * {@code QueryRequest}), by their first span.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ZipkinQueryEsDAOITBase {
    /** A fixed local midnight, so the daily indices the spans are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    /** The main search range: a minute either side of midnight. */
    private static final Range AROUND_MIDNIGHT = Range.ending(MIDNIGHT + 59_000L, 119_000L);
    private static final long START = AROUND_MIDNIGHT.duration.getStartTimestamp();
    private static final long END = AROUND_MIDNIGHT.duration.getEndTimestamp();
    /** Ten to five minutes before midnight, apart from the main range. */
    private static final Range BEFORE_MIDNIGHT = Range.ending(MIDNIGHT - 301_000L, 299_000L);

    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;
    private static final ZipkinSpanRecord.Builder BUILDER = new ZipkinSpanRecord.Builder();
    /** The service of the ranking cases, whose spans are days after the other fixtures' and in their own indices. */
    private static final String RANKED = "ranked";
    private static final int RANKED_LIMIT = 2;
    /** More than the traces a shard answers a terms aggregation of {@link #RANKED_LIMIT} with by default. */
    private static final int RANKED_OTHERS = RANKED_LIMIT * 3 / 2 + 10 + 3;

    private static final String BACKEND = "backend";
    private static final String CANARY = "backend-canary";
    private static final String FRONTEND = "frontend";
    private static final String SCHEDULER = "scheduler";
    private static final String MYSQL = "mysql";
    private static final String REDIS = "redis";
    private static final String KAFKA = "kafka";
    private static final String POSTGRES = "postgres";
    private static final String SELECT_ORDERS = "select orders";
    private static final String SELECT_USERS = "select users";
    private static final String PUBLISH_ORDERS = "publish orders";
    private static final String GET_ORDERS = "get /orders";
    private static final String RUN_JOB = "run job";
    private static final String LOAD_BATCH = "load batch";
    private static final long MIN_DURATION_US = 1_000L;
    private static final long MAX_DURATION_US = 2_000L;

    /** Found by a span on the range's start, whose child is written after midnight. */
    private static final String FIRST_MATCH = "08603e4bc22d2e110000000000000a01";
    /** Found by a span on the range's end, whose parent is written before midnight. */
    private static final String LAST_MATCH = "08603e4bc22d2e110000000000000a02";
    private static final String OTHER_SERVICE = "08603e4bc22d2e110000000000000b01";
    private static final String OTHER_REMOTE_SERVICE = "08603e4bc22d2e110000000000000b02";
    private static final String OTHER_SPAN_NAME = "08603e4bc22d2e110000000000000b03";
    private static final String OTHER_TAG_VALUE = "08603e4bc22d2e110000000000000b04";
    /** Has "error" as a tag value, not as a tag key or an annotation. */
    private static final String NO_ERROR_KEY = "08603e4bc22d2e110000000000000b05";
    private static final String BELOW_MIN_DURATION = "08603e4bc22d2e110000000000000b06";
    private static final String ABOVE_MAX_DURATION = "08603e4bc22d2e110000000000000b07";
    private static final String BEFORE_START = "08603e4bc22d2e110000000000000b08";
    private static final String AFTER_END = "08603e4bc22d2e110000000000000b09";
    /** The scheduler's traces, each of two spans: the later a trace starts, the earlier it ends. */
    private static final String STARTED_FIRST = "08603e4bc22d2e110000000000000c01";
    private static final String STARTED_SECOND = "08603e4bc22d2e110000000000000c02";
    private static final String STARTED_LAST = "08603e4bc22d2e110000000000000c03";
    private static final String UNKNOWN = "08603e4bc22d2e110000000000000fff";

    private static final List<String> IN_MAIN_RANGE = Arrays.asList(
        FIRST_MATCH, LAST_MATCH, OTHER_SERVICE, OTHER_REMOTE_SERVICE, OTHER_SPAN_NAME, OTHER_TAG_VALUE, NO_ERROR_KEY,
        BELOW_MIN_DURATION, ABOVE_MAX_DURATION);
    private static final Map<String, List<Span>> TRACES = fixture();
    /** Neither in time order nor against it, and each trace's spans child first. */
    private static final List<String> WRITE_ORDER = Arrays.asList(
        STARTED_FIRST, AFTER_END, LAST_MATCH, OTHER_SPAN_NAME, STARTED_LAST, BELOW_MIN_DURATION, FIRST_MATCH,
        BEFORE_START, OTHER_TAG_VALUE, OTHER_REMOTE_SERVICE, STARTED_SECOND, ABOVE_MAX_DURATION, OTHER_SERVICE,
        NO_ERROR_KEY);

    private static final List<String> SERVICES = Arrays.asList(BACKEND, CANARY, FRONTEND, SCHEDULER);
    private static final Map<String, List<String>> REMOTE_SERVICES = Map.of(
        BACKEND, Arrays.asList(KAFKA, MYSQL, REDIS),
        CANARY, Collections.singletonList(MYSQL),
        FRONTEND, Collections.emptyList(),
        SCHEDULER, Collections.singletonList(POSTGRES));
    private static final Map<String, List<String>> SPAN_NAMES = Map.of(
        BACKEND, Arrays.asList(PUBLISH_ORDERS, SELECT_ORDERS, SELECT_USERS),
        CANARY, Collections.singletonList(SELECT_ORDERS),
        FRONTEND, Collections.singletonList(GET_ORDERS),
        SCHEDULER, Arrays.asList(LOAD_BATCH, RUN_JOB));

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private ZipkinQueryEsDAO dao;
    private Model spans;

    protected ZipkinQueryEsDAOITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeSpans() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        spans = support.installRecord(ZipkinSpanRecord.class);
        final Model services = support.installMetrics(ZipkinServiceTraffic.class);
        final Model remoteServices = support.installMetrics(ZipkinServiceRelationTraffic.class);
        final Model spanNames = support.installMetrics(ZipkinServiceSpanTraffic.class);

        final List<ZipkinSpanRecord> records = new ArrayList<>();
        for (final String traceId : WRITE_ORDER) {
            final List<Span> trace = new ArrayList<>(TRACES.get(traceId));
            Collections.reverse(trace);
            for (final Span span : trace) {
                records.add(ZipkinSpanRecords.of(span));
            }
        }
        support.writeRecords(spans, new ZipkinSpanRecord.Builder(), records);
        support.writeMetrics(services, new ZipkinServiceTraffic.Builder(), serviceTraffic());
        support.writeMetrics(remoteServices, new ZipkinServiceRelationTraffic.Builder(), relationTraffic());
        support.writeMetrics(spanNames, new ZipkinServiceSpanTraffic.Builder(), spanTraffic());

        dao = new ZipkinQueryEsDAO(support.getClient());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void listsEveryServiceThatReportedSpans() {
        assertEquals(SERVICES, sorted(dao.getServiceNames()));
    }

    @Test
    public void listsTheRemoteServicesEachServiceCalled() {
        for (final String service : SERVICES) {
            assertEquals(REMOTE_SERVICES.get(service), sorted(dao.getRemoteServiceNames(service)), service);
        }
        assertEquals(Collections.emptyList(), dao.getRemoteServiceNames("unknown"));
    }

    @Test
    public void listsTheSpanNamesOfEachService() {
        for (final String service : SERVICES) {
            assertEquals(SPAN_NAMES.get(service), sorted(dao.getSpanNames(service)), service);
        }
        assertEquals(Collections.emptyList(), dao.getSpanNames("unknown"));
    }

    @Test
    public void readsAWholeTraceById() {
        assertEquals(sortedById(TRACES.get(FIRST_MATCH)), sortedById(dao.getTrace(FIRST_MATCH, null)));
        assertEquals(sortedById(TRACES.get(LAST_MATCH)),
                     sortedById(dao.getTrace(LAST_MATCH, AROUND_MIDNIGHT.duration)));
        assertEquals(Collections.emptyList(), dao.getTrace(UNKNOWN, null));
    }

    @Test
    public void readsWholeTracesByIds() {
        final Set<String> ids = new HashSet<>(Arrays.asList(FIRST_MATCH, LAST_MATCH, STARTED_LAST, UNKNOWN));
        assertEquals(traces(FIRST_MATCH, LAST_MATCH, STARTED_LAST), canonical(dao.getTraces(ids, null)));
    }

    /**
     * A scroll context left open is held for the scroll's keep-alive, and a node refuses every new scroll, of any
     * query, past {@code search.max_open_scroll_context}.
     */
    @Test
    public void leavesNoScrollContextOpenWhenNothingIsFound() throws Exception {
        final long openBefore = support.openScrollContexts();
        assertEquals(Collections.emptyList(), dao.getTraces(Collections.singleton(UNKNOWN), null));
        final long openAfter = support.openScrollContexts();
        assertTrue(openAfter <= openBefore,
                   "scroll contexts open before the read: " + openBefore + ", after it: " + openAfter);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Criterion.class)
    public void findsEveryTraceMatchingOneCriterion(final Criterion criterion) {
        final List<String> matching = new ArrayList<>(IN_MAIN_RANGE);
        matching.remove(criterion.nearMiss);
        assertEquals(traces(matching.toArray(new String[0])),
                     canonical(search(AROUND_MIDNIGHT, criterion.criterion, matching.size())));
    }

    /**
     * The two traces matching every criterion lie on the bounds: one has a span on the range's start lasting the
     * minimum duration, the other a span on the range's end lasting the maximum. Neither has another span that
     * matches.
     */
    @Test
    public void findsOnlyTheTracesMatchingEveryCriterion() {
        assertEquals(traces(FIRST_MATCH, LAST_MATCH), canonical(search(AROUND_MIDNIGHT, request -> request
            .serviceName(BACKEND)
            .remoteServiceName(MYSQL)
            .spanName(SELECT_ORDERS)
            .parseAnnotationQuery("error and db.instance=orders")
            .minDuration(MIN_DURATION_US)
            .maxDuration(MAX_DURATION_US), 2)));
    }

    @Test
    public void findsNoTraceWhenNoSpanMatches() {
        assertEquals(Collections.emptyList(), search(AROUND_MIDNIGHT, request -> request.serviceName("unknown"), 10));
    }

    @ParameterizedTest(name = "limit {0}")
    @ValueSource(ints = {1, 2, 3})
    public void keepsTheTracesThatStartedLast(final int limit) {
        final List<String> latestStartFirst = Arrays.asList(STARTED_LAST, STARTED_SECOND, STARTED_FIRST);
        assertEquals(traces(latestStartFirst.subList(0, limit).toArray(new String[0])),
                     canonical(search(BEFORE_MIDNIGHT, request -> request.serviceName(SCHEDULER), limit)));
    }

    /**
     * A trace ranks by its first matching span, but the search's terms aggregation lets each shard rank a trace by the
     * spans it holds and answer with its first {@code shard_size} traces, an approximation accepted for its cost (see
     * {@link ZipkinQueryEsDAO#getTraces(QueryRequest, Duration)}). The older trace's first span shares its shard with
     * more than {@code shard_size} later traces, and its later span, newer than all of them, is routed by its document
     * id to another shard, which ranks the trace first: the search keeps the older trace and the newest of the others.
     * Custom routing keeps a trace's spans on one shard of a daily index, and there the search is exact. The JDBC
     * storages, which rank over every table, keep the exact traces in both cases.
     */
    @Test
    public void keepsAnOlderTraceWithALaterSpanOnAnotherShardOnlyWithDefaultRouting() throws Exception {
        final long first = MIDNIGHT + 2 * DAY + 12 * HOUR;
        final RankedCase ranked = writeRanked(first, first + 30_000L, false);
        assertEquals(customRouting() ? ranked.exact() : ranked.approximate(),
                     searchRanked(first, first + 30_000L));
    }

    /**
     * The older trace's first span is in the earlier daily index, on a shard with more than {@code shard_size} later
     * traces, and its later span is in the next daily index, which ranks the trace first; routing keeps a trace on
     * one shard of a daily index only. So with either routing the search keeps the older trace and the newest of the
     * others, where the JDBC storages keep the two newest others.
     */
    @Test
    public void keepsAnOlderTraceThatCrossesMidnightByItsLaterSpan() throws Exception {
        final long midnight = MIDNIGHT + 4 * DAY;
        final RankedCase ranked = writeRanked(midnight - 50_000L, midnight + 10_000L, true);
        assertEquals(ranked.approximate(), searchRanked(midnight - 50_000L, midnight + 10_000L));
    }

    /** What a search can filter by, each with the trace in the main range that matches every other criterion. */
    enum Criterion {
        TIME_RANGE_ONLY(request -> { }, null),
        SERVICE_NAME(request -> request.serviceName(BACKEND), OTHER_SERVICE),
        REMOTE_SERVICE_NAME(request -> request.remoteServiceName(MYSQL), OTHER_REMOTE_SERVICE),
        SPAN_NAME(request -> request.spanName(SELECT_ORDERS), OTHER_SPAN_NAME),
        ANNOTATION_KEY(request -> request.parseAnnotationQuery("error"), NO_ERROR_KEY),
        ANNOTATION_KEY_AND_VALUE(request -> request.parseAnnotationQuery("db.instance=orders"), OTHER_TAG_VALUE),
        MIN_DURATION(request -> request.minDuration(MIN_DURATION_US), BELOW_MIN_DURATION),
        MAX_DURATION(request -> request.minDuration(1L).maxDuration(MAX_DURATION_US), ABOVE_MAX_DURATION);

        private final Consumer<QueryRequest.Builder> criterion;
        private final String nearMiss;

        Criterion(final Consumer<QueryRequest.Builder> criterion, final String nearMiss) {
            this.criterion = criterion;
            this.nearMiss = nearMiss;
        }
    }

    private List<List<Span>> search(final Range range, final Consumer<QueryRequest.Builder> criteria,
                                    final int limit) {
        final QueryRequest.Builder request = QueryRequest.newBuilder()
                                                         .endTs(range.endTs)
                                                         .lookback(range.lookback)
                                                         .limit(limit);
        criteria.accept(request);
        return dao.getTraces(request.build(), range.duration);
    }

    private static Map<String, List<Span>> fixture() {
        final Map<String, List<Span>> traces = new LinkedHashMap<>();
        traces.put(FIRST_MATCH, Arrays.asList(
            matching(FIRST_MATCH, "000000000000a011", START).duration(MIN_DURATION_US).build(),
            span(FIRST_MATCH, "000000000000a012", "000000000000a011", Span.Kind.PRODUCER, PUBLISH_ORDERS, BACKEND,
                 MIDNIGHT + 1_000L).remoteEndpoint(endpoint(KAFKA)).duration(5_000L).debug(true).build()
        ));
        traces.put(LAST_MATCH, Arrays.asList(
            span(LAST_MATCH, "000000000000a021", null, Span.Kind.SERVER, GET_ORDERS, FRONTEND, MIDNIGHT - 1_000L)
                .duration(62_000_000L).shared(true).build(),
            matching(LAST_MATCH, "000000000000a022", END)
                .parentId("000000000000a021")
                .duration(MAX_DURATION_US)
                .clearTags()
                .putTag("db.instance", "orders")
                .addAnnotation(END * 1000L, "error")
                .build()
        ));
        traces.put(OTHER_SERVICE, Collections.singletonList(
            matching(OTHER_SERVICE, "000000000000b011", MIDNIGHT - 50_000L).localEndpoint(endpoint(CANARY)).build()));
        traces.put(OTHER_REMOTE_SERVICE, Collections.singletonList(
            matching(OTHER_REMOTE_SERVICE, "000000000000b021", MIDNIGHT - 40_000L)
                .remoteEndpoint(endpoint(REDIS)).build()));
        traces.put(OTHER_SPAN_NAME, Collections.singletonList(
            matching(OTHER_SPAN_NAME, "000000000000b031", MIDNIGHT - 30_000L).name(SELECT_USERS).build()));
        traces.put(OTHER_TAG_VALUE, Collections.singletonList(
            matching(OTHER_TAG_VALUE, "000000000000b041", MIDNIGHT - 20_000L).putTag("db.instance", "users").build()));
        traces.put(NO_ERROR_KEY, Collections.singletonList(
            matching(NO_ERROR_KEY, "000000000000b051", MIDNIGHT + 10_000L)
                .clearTags()
                .putTag("db.instance", "orders")
                .putTag("otel.status_code", "error")
                .build()));
        traces.put(BELOW_MIN_DURATION, Collections.singletonList(
            matching(BELOW_MIN_DURATION, "000000000000b061", MIDNIGHT + 20_000L)
                .duration(MIN_DURATION_US - 1).build()));
        traces.put(ABOVE_MAX_DURATION, Collections.singletonList(
            matching(ABOVE_MAX_DURATION, "000000000000b071", MIDNIGHT + 30_000L)
                .duration(MAX_DURATION_US + 1).build()));
        traces.put(BEFORE_START, Collections.singletonList(
            matching(BEFORE_START, "000000000000b081", START - 1).build()));
        traces.put(AFTER_END, Collections.singletonList(
            matching(AFTER_END, "000000000000b091", END + 1).build()));
        traces.put(STARTED_FIRST, scheduled(STARTED_FIRST, "000000000000c011", MIDNIGHT - 540_000L,
                                            MIDNIGHT - 310_000L));
        traces.put(STARTED_SECOND, scheduled(STARTED_SECOND, "000000000000c021", MIDNIGHT - 500_000L,
                                             MIDNIGHT - 350_000L));
        traces.put(STARTED_LAST, scheduled(STARTED_LAST, "000000000000c031", MIDNIGHT - 460_000L,
                                           MIDNIGHT - 390_000L));
        return traces;
    }

    /** A backend span matching every criterion of the search in the main range. */
    private static Span.Builder matching(final String traceId, final String spanId, final long timestampMillis) {
        return span(traceId, spanId, null, Span.Kind.CLIENT, SELECT_ORDERS, BACKEND, timestampMillis)
            .localEndpoint(Endpoint.newBuilder().serviceName(BACKEND).ip("10.0.0.1").port(8080).build())
            .remoteEndpoint(Endpoint.newBuilder().serviceName(MYSQL).ip("2001:db8::1").port(3306).build())
            .duration(1_500L)
            .putTag("db.instance", "orders")
            .putTag("error", "deadlock");
    }

    /** A scheduler job and the batch it loads, both matching a search by the scheduler's name. */
    private static List<Span> scheduled(final String traceId, final String rootSpanId, final long startMillis,
                                        final long loadMillis) {
        final String loadSpanId = rootSpanId.substring(0, 15) + "2";
        return Arrays.asList(
            span(traceId, rootSpanId, null, Span.Kind.SERVER, RUN_JOB, SCHEDULER, startMillis)
                .duration((loadMillis - startMillis + 1_000L) * 1000L).build(),
            span(traceId, loadSpanId, rootSpanId, Span.Kind.CLIENT, LOAD_BATCH, SCHEDULER, loadMillis)
                .remoteEndpoint(endpoint(POSTGRES)).duration(1_000_000L).build()
        );
    }

    private static Span.Builder span(final String traceId, final String spanId, final String parentId,
                                     final Span.Kind kind, final String name, final String service,
                                     final long timestampMillis) {
        return Span.newBuilder()
                   .traceId(traceId)
                   .id(spanId)
                   .parentId(parentId)
                   .kind(kind)
                   .name(name)
                   .localEndpoint(endpoint(service))
                   .timestamp(timestampMillis * 1000L)
                   .debug(false)
                   .shared(false);
    }

    private static Endpoint endpoint(final String service) {
        return Endpoint.newBuilder().serviceName(service).build();
    }

    /**
     * What the Zipkin receiver reports for every span — its service, its span name and the remote service it
     * called — each kept once, as the metrics workers merge them, in the minute it was first seen.
     */
    private static List<ZipkinServiceTraffic> serviceTraffic() {
        final Map<String, ZipkinServiceTraffic> traffic = new LinkedHashMap<>();
        for (final Span span : spansByTime()) {
            final ZipkinServiceTraffic service = new ZipkinServiceTraffic();
            service.setServiceName(span.localServiceName());
            service.setTimeBucket(minuteOf(span));
            traffic.putIfAbsent(span.localServiceName(), service);
        }
        return new ArrayList<>(traffic.values());
    }

    private static List<ZipkinServiceRelationTraffic> relationTraffic() {
        final Map<String, ZipkinServiceRelationTraffic> traffic = new LinkedHashMap<>();
        for (final Span span : spansByTime()) {
            if (span.remoteServiceName() == null) {
                continue;
            }
            final ZipkinServiceRelationTraffic relation = new ZipkinServiceRelationTraffic();
            relation.setServiceName(span.localServiceName());
            relation.setRemoteServiceName(span.remoteServiceName());
            relation.setTimeBucket(minuteOf(span));
            traffic.putIfAbsent(span.localServiceName() + "/" + span.remoteServiceName(), relation);
        }
        return new ArrayList<>(traffic.values());
    }

    private static List<ZipkinServiceSpanTraffic> spanTraffic() {
        final Map<String, ZipkinServiceSpanTraffic> traffic = new LinkedHashMap<>();
        for (final Span span : spansByTime()) {
            final ZipkinServiceSpanTraffic spanName = new ZipkinServiceSpanTraffic();
            spanName.setServiceName(span.localServiceName());
            spanName.setSpanName(span.name());
            spanName.setTimeBucket(minuteOf(span));
            traffic.putIfAbsent(span.localServiceName() + "/" + span.name(), spanName);
        }
        return new ArrayList<>(traffic.values());
    }

    private static List<Span> spansByTime() {
        final List<Span> spans = new ArrayList<>();
        TRACES.values().forEach(spans::addAll);
        spans.sort(Comparator.comparingLong(Span::timestampAsLong));
        return spans;
    }

    private static long minuteOf(final Span span) {
        return TimeBucket.getMinuteTimeBucket(span.timestampAsLong() / 1000);
    }

    private static List<List<Span>> traces(final String... traceIds) {
        final List<List<Span>> traces = new ArrayList<>();
        for (final String traceId : traceIds) {
            traces.add(TRACES.get(traceId));
        }
        return canonical(traces);
    }

    /** The traces sorted by id, each trace's spans sorted by id: the Zipkin API promises neither order. */
    private static List<List<Span>> canonical(final List<List<Span>> traces) {
        final List<List<Span>> canonical = new ArrayList<>();
        for (final List<Span> trace : traces) {
            canonical.add(sortedById(trace));
        }
        canonical.sort(Comparator.comparing(trace -> trace.isEmpty() ? "" : trace.get(0).traceId()));
        return canonical;
    }

    private static List<Span> sortedById(final List<Span> trace) {
        final List<Span> sorted = new ArrayList<>(trace);
        sorted.sort(Comparator.comparing(Span::id));
        return sorted;
    }

    private static List<String> sorted(final List<String> names) {
        final List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Writes the older trace of the {@link #RANKED} service and {@link #RANKED_OTHERS} later single-span traces
     * between its first span and its later one, placed on shards as the server routes them, and checks the placement.
     *
     * @param crossesMidnight whether the later span is in the next daily index, where its shard does not matter
     */
    private RankedCase writeRanked(final long first, final long later,
                                   final boolean crossesMidnight) throws Exception {
        final String olderTrace = String.format("%032x", (first / 1000) << 16);
        final Span firstSpan = rankedSpan(olderTrace, "000000000000d001", first);
        support.writeRecords(spans, BUILDER, Collections.singletonList(ZipkinSpanRecords.of(firstSpan)));
        final int shard = support.shardOf(spans, BUILDER, ZipkinSpanRecords.of(firstSpan));
        final boolean customRouting = customRouting();

        // The next daily index does not exist yet, and custom routing places the span with the first one.
        final Span laterSpan = crossesMidnight || customRouting
            ? rankedSpan(olderTrace, "000000000000d100", later)
            : placed(i -> rankedSpan(olderTrace, String.format("%016x", 0xd100L + i), later), 1,
                     placedOn -> placedOn != shard).get(0);
        final List<Span> others = new ArrayList<>();
        final List<Span> onTheShard = placed(
            i -> rankedSpan(String.format("%032x", ((first / 1000) << 16) + 0x100 + i), "000000000000e001", first),
            RANKED_OTHERS, placedOn -> placedOn == shard);
        for (int i = 0; i < onTheShard.size(); i++) {
            others.add(onTheShard.get(i).toBuilder().timestamp((first + (i + 1) * 1000L) * 1000L).build());
        }
        final List<ZipkinSpanRecord> records = new ArrayList<>();
        records.add(ZipkinSpanRecords.of(laterSpan));
        others.forEach(span -> records.add(ZipkinSpanRecords.of(span)));
        support.writeRecords(spans, BUILDER, records);

        assertEquals(shard, support.shardHolding(spans, ZipkinSpanRecords.of(firstSpan)));
        if (!crossesMidnight) {
            assertEquals(customRouting, support.shardHolding(spans, ZipkinSpanRecords.of(laterSpan)) == shard,
                         "the later span shares the first span's shard");
        }
        for (final Span other : others) {
            assertEquals(shard, support.shardHolding(spans, ZipkinSpanRecords.of(other)), other.traceId());
        }
        return new RankedCase(Arrays.asList(firstSpan, laterSpan), others);
    }

    private boolean customRouting() {
        return mode == ElasticSearchQueryITSupport.Mode.LOGIC_SHARDING_AND_CUSTOM_ROUTING;
    }

    /** The older trace and the later traces of a ranking case, the later ones in start order. */
    private static final class RankedCase {
        private final List<Span> older;
        private final List<Span> others;

        private RankedCase(final List<Span> older, final List<Span> others) {
            this.older = older;
            this.others = others;
        }

        /** The {@link #RANKED_LIMIT} newest of the later traces. */
        private List<List<Span>> exact() {
            final List<List<Span>> latest = new ArrayList<>();
            for (int i = others.size() - RANKED_LIMIT; i < others.size(); i++) {
                latest.add(Collections.singletonList(others.get(i)));
            }
            return canonical(latest);
        }

        /** The older trace, ranked by its later span, and the newest of the later traces after it. */
        private List<List<Span>> approximate() {
            final List<List<Span>> kept = new ArrayList<>();
            kept.add(older);
            for (int i = others.size() - RANKED_LIMIT + 1; i < others.size(); i++) {
                kept.add(Collections.singletonList(others.get(i)));
            }
            return canonical(kept);
        }
    }

    /** The latest {@link #RANKED_LIMIT} traces of the {@link #RANKED} service from a second before the first span. */
    private List<List<Span>> searchRanked(final long first, final long end) {
        return canonical(search(Range.ending(end, end - first + 1_000L),
                                request -> request.serviceName(RANKED), RANKED_LIMIT));
    }

    /**
     * The first {@code count} candidates the server routes to a shard the predicate takes, asked of the server
     * before they are written.
     */
    private List<Span> placed(final IntFunction<Span> candidates, final int count,
                              final IntPredicate shard) throws Exception {
        final List<Span> placed = new ArrayList<>();
        for (int i = 0; placed.size() < count; i++) {
            assertTrue(i < 10_000, "no candidate placed as asked after " + i);
            final Span candidate = candidates.apply(i);
            if (shard.test(support.shardOf(spans, BUILDER, ZipkinSpanRecords.of(candidate)))) {
                placed.add(candidate);
            }
        }
        return placed;
    }

    private static Span rankedSpan(final String traceId, final String spanId, final long timestampMillis) {
        return span(traceId, spanId, null, Span.Kind.SERVER, "get /rank", RANKED, timestampMillis)
            .duration(1_000L).build();
    }

    /** A search's time range, from the Zipkin API's {@code endTs} and {@code lookback}. */
    private static final class Range {
        private final long endTs;
        private final long lookback;
        private final Duration duration;

        private Range(final long endTs, final long lookback, final Duration duration) {
            this.endTs = endTs;
            this.lookback = lookback;
            this.duration = duration;
        }

        /** The duration {@code ZipkinQueryHandler#buildDuration} passes to the storage for this range. */
        private static Range ending(final long endTs, final long lookback) {
            final Duration duration = new Duration();
            duration.setStep(Step.SECOND);
            duration.setStart(new DateTime(endTs - lookback).toString("yyyy-MM-dd HHmmss"));
            duration.setEnd(new DateTime(endTs).toString("yyyy-MM-dd HHmmss"));
            return new Range(endTs, lookback, duration);
        }
    }
}
