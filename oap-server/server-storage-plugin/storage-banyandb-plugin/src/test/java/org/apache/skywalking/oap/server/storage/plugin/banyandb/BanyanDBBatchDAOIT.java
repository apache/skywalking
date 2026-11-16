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

import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.database.TopNDatabaseStatement;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.LongText;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsSessionCache;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetricsDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBTraceQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Writes through OAP's batch DAO, built as the provider builds it, the way OAP's write paths use it, and reads each
 * write back through its query DAO: records one by one through {@code insert}, as a record worker hands them over,
 * and the metrics and TopN records of a persistence round through {@code flush} and then {@code endOfFlush}, as the
 * persistence timer does. Each kind of request must reach the bulk of its kind — log lines a stream, segments a
 * trace, metrics a measure — and a persistence round must be answered within the round, not after a bulk's own
 * {@code flushInterval} (15 seconds): the timer schedules the next round from the answer.
 */
@Testcontainers
public class BanyanDBBatchDAOIT {
    private static final String RESP_TIME = "service_resp_time";
    private static final String SERVICE = "batch-it";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId(SERVICE, true);
    private static final String OTHER_SERVICE = "batch-it-other";
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId(OTHER_SERVICE, true);
    private static final String DATABASE = "batch-it-db";
    private static final String OTHER_DATABASE = "batch-it-db-other";
    private static final int MINUTES = 3;
    /** Less than the bulk's flushInterval: the most a round may wait for its answer. */
    private static final long ROUND_ANSWER_SECONDS = 10;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBBatchDAO BATCH_DAO;
    private static Model LOG;
    private static Model SEGMENT;
    private static Model RESP_TIME_MINUTE;
    private static Model STATEMENT;
    private static StorageBuilder<Metrics> RESP_TIME_BUILDER;
    /** The first minute written, aligned. */
    private static long FIRST_MINUTE;

    @BeforeAll
    public static void install() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        FIRST_MINUTE = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)) / 60_000L * 60_000L;
        LOG = SUPPORT.install(LogRecord.class, DefaultScopeDefine.LOG, LogRecord.INDEX_NAME);
        SEGMENT = SUPPORT.install(SegmentRecord.class, DefaultScopeDefine.SEGMENT, SegmentRecord.INDEX_NAME);
        RESP_TIME_MINUTE = SUPPORT.installOAL(RESP_TIME, DownSampling.Minute);
        RESP_TIME_BUILDER = OALTestMetrics.builder(OALTestMetrics.metricsClass(RESP_TIME));
        STATEMENT = SUPPORT.install(TopNDatabaseStatement.class, DefaultScopeDefine.DATABASE_SLOW_STATEMENT,
                                    TopNDatabaseStatement.INDEX_NAME);
        BATCH_DAO = SUPPORT.batchDAO();
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void insertsLogLinesIntoTheStream() throws Exception {
        final List<LogRecord> lines = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            lines.add(line("line " + i, SERVICE_ID, FIRST_MINUTE + i * 1000L));
            lines.add(line("other line " + i, OTHER_SERVICE_ID, FIRST_MINUTE + i * 1000L + 500L));
        }
        for (final LogRecord line : lines) {
            BATCH_DAO.insert(SUPPORT.prepareInsert(LOG, new LogRecord.Builder(), line));
        }

        final BanyanDBLogQueryDAO dao = new BanyanDBLogQueryDAO(SUPPORT.getClient());
        final List<String> newestFirst = new ArrayList<>();
        newestFirst.add(BanyanDBQueryITSupport.json(null, null));
        lines.stream().filter(line -> SERVICE_ID.equals(line.getServiceId()))
             .sorted(Comparator.comparingLong(LogRecord::getTimestamp).reversed())
             .map(BanyanDBBatchDAOIT::describe).forEach(newestFirst::add);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
            newestFirst,
            describe(dao.queryLogs(SERVICE_ID, null, null, null, Order.DES, 0, newestFirst.size() - 1, minutes(0, 0),
                                   null, null, null))));
    }

    @Test
    public void writesATaskRowThroughTheDAOTheStorageDAOGives() throws Exception {
        final Model tasks = SUPPORT.installNoneStream(
            ProfileTaskRecord.class, DefaultScopeDefine.PROFILE_TASK, ProfileTaskRecord.INDEX_NAME);
        final ProfileTaskRecord task = new ProfileTaskRecord();
        task.setTaskId("batch-it-task");
        task.setServiceId(SERVICE_ID);
        task.setEndpointName("GET:/batch");
        task.setStartTime(FIRST_MINUTE + 1_000L);
        task.setCreateTime(FIRST_MINUTE);
        task.setDuration(5);
        task.setMinDurationThreshold(10);
        task.setDumpPeriod(20);
        task.setMaxSamplingCount(3);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(FIRST_MINUTE));

        // The DAO a task worker writes its rows with: one by one, not through the batch DAO.
        new BanyanDBStorageDAO(SUPPORT.getClient()).newNoneStreamDao(new ProfileTaskRecord.Builder()).insert(tasks, task);

        final BanyanDBProfileTaskQueryDAO dao = new BanyanDBProfileTaskQueryDAO(SUPPORT.getClient(), 200);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
            BanyanDBQueryITSupport.json("batch-it-task", SERVICE_ID, null, "GET:/batch", FIRST_MINUTE + 1_000L, FIRST_MINUTE,
                                        5, 10, 20, 3, null),
            describe(dao.getById("batch-it-task"))));
    }

    @Test
    public void insertsSegmentsIntoTheTrace() throws Exception {
        final List<SegmentObject> trace = Arrays.asList(
            segmentObject("insert-trace", "insert-segment-1", FIRST_MINUTE + 2_000L),
            segmentObject("insert-trace", "insert-segment-0", FIRST_MINUTE + 1_000L));
        for (final SegmentObject segment : trace) {
            BATCH_DAO.insert(SUPPORT.prepareInsert(SEGMENT, new SegmentRecord.Builder(), segment(segment)));
        }
        BATCH_DAO.insert(SUPPORT.prepareInsert(
            SEGMENT, new SegmentRecord.Builder(),
            segment(segmentObject("insert-trace-other", "insert-segment-other", FIRST_MINUTE + 1_500L))));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(written(trace), readTrace("insert-trace")));
    }

    @Test
    public void insertsAMetricThatTheEndOfARoundSends() throws Exception {
        final String service = "batch-it-insert";
        BATCH_DAO.insert(SUPPORT.prepareMetricsInsert(
            RESP_TIME_MINUTE, RESP_TIME_BUILDER, respTime(IDManager.ServiceID.buildId(service, true), 0, 42L),
            SessionCacheCallback.NOOP));
        BATCH_DAO.endOfFlush();

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
            series(point(0, IDManager.ServiceID.buildId(service, true), "42")),
            values(service, minutes(0, 0))));
    }

    @Test
    public void writesTheMetricsOfAPersistenceRoundAndCachesThemOnTheAnswer() throws Exception {
        final MetricsSessionCache sessionCache = new MetricsSessionCache(TimeUnit.MINUTES.toMillis(1), true);
        final List<Metrics> inserted = new ArrayList<>();
        final List<PrepareRequest> round = new ArrayList<>();
        for (int i = MINUTES - 1; i >= 0; i--) {
            for (final Metrics metrics : Arrays.asList(respTime(SERVICE_ID, i, (i + 1) * 10L),
                                                       respTime(OTHER_SERVICE_ID, i, (i + 1) * 1000L))) {
                inserted.add(metrics);
                round.add(SUPPORT.prepareMetricsInsert(
                    RESP_TIME_MINUTE, RESP_TIME_BUILDER, metrics, new SessionCacheCallback(sessionCache, metrics)));
            }
        }
        persistenceRound(round);

        assertEquals(inserted.stream().map(BanyanDBBatchDAOIT::cached).collect(Collectors.toList()),
                     inserted.stream().map(sessionCache::remove).map(BanyanDBBatchDAOIT::cached)
                             .collect(Collectors.toList()),
                     "every metric of the round is in the session cache once BanyanDB has answered");
        final List<String> service = series(
            point(0, SERVICE_ID, "10"), point(1, SERVICE_ID, "20"), point(2, SERVICE_ID, "30"));
        final List<String> otherService = series(
            point(0, OTHER_SERVICE_ID, "1000"), point(1, OTHER_SERVICE_ID, "2000"), point(2, OTHER_SERVICE_ID, "3000"));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(service, values(SERVICE, minutes(0, MINUTES - 1)));
            assertEquals(otherService, values(OTHER_SERVICE, minutes(0, MINUTES - 1)));
        });
        assertEquals(storedAsRead(inserted), readBack(inserted),
                     "every column of every metric, as a metrics worker reads them back");

        // The next round writes the second minute again, combined with what came in since: two more calls.
        final Metrics combined = respTime(SERVICE_ID, 1, 20L, 25L, 30L);
        persistenceRound(Collections.singletonList(
            SUPPORT.prepareMetricsUpdate(RESP_TIME_MINUTE, RESP_TIME_BUILDER, combined)));

        final List<String> combinedService = series(
            point(0, SERVICE_ID, "10"), point(1, SERVICE_ID, "25"), point(2, SERVICE_ID, "30"));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(combinedService, values(SERVICE, minutes(0, MINUTES - 1))));
        assertEquals(otherService, values(OTHER_SERVICE, minutes(0, MINUTES - 1)));
        final List<Metrics> afterUpdate = inserted.stream()
            .map(metrics -> metrics.id().build().equals(combined.id().build()) ? combined : metrics)
            .collect(Collectors.toList());
        assertEquals(storedAsRead(afterUpdate), readBack(inserted),
                     "the combined minute replaced, summation and count included, the rest as they were");
    }

    @Test
    public void writesTheTopNRecordsOfEachPersistenceRoundWithinTheRound() throws Exception {
        final List<TopNDatabaseStatement> statements = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            final List<PrepareRequest> requests = new ArrayList<>();
            for (int i = 2; i >= 0; i--) {
                final int index = round * 3 + i;
                // Latency order is not time order.
                final TopNDatabaseStatement statement = statement(DATABASE, index, ((index * 5) % 6 + 1) * 10L);
                statements.add(statement);
                requests.add(SUPPORT.prepareInsert(STATEMENT, new TopNDatabaseStatement.Builder(), statement));
                requests.add(SUPPORT.prepareInsert(STATEMENT, new TopNDatabaseStatement.Builder(),
                                                   statement(OTHER_DATABASE, index, 10_000L + index)));
            }
            persistenceRound(requests);

            // Every statement written so far, and nothing else, asked for more than were ever written.
            final List<String> slowestFirst = statements.stream()
                .sorted(Comparator.comparingLong(TopNDatabaseStatement::getLatency).reversed())
                .map(statement -> BanyanDBQueryITSupport.json(statement.getStatement(), statement.getTraceId(),
                                                              statement.getTraceId(), String.valueOf(statement.getLatency())))
                .collect(Collectors.toList());
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(slowestFirst, readSlowestStatements(20)));
        }
    }

    @Test
    public void flushesASegmentIntoTheTrace() throws Exception {
        final SegmentObject segment = segmentObject("flush-trace", "flush-segment", FIRST_MINUTE + 3_000L);
        final CompletableFuture<Void> flushed = BATCH_DAO.flush(Collections.singletonList(
            SUPPORT.prepareInsert(SEGMENT, new SegmentRecord.Builder(), segment(segment))));
        BATCH_DAO.endOfFlush();
        // Segments are written by record workers, never by the persistence timer, so they keep the bulk's interval.
        flushed.get(30, TimeUnit.SECONDS);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(written(Collections.singletonList(segment)), readTrace("flush-trace")));
    }

    /**
     * One worker's share of a persistence round, as the persistence timer writes it, answered within the round: from
     * the flush to the answer, the end of the round included, as the timer waits for it.
     */
    private static void persistenceRound(List<PrepareRequest> requests) throws Exception {
        final long start = System.nanoTime();
        final CompletableFuture<Void> answered = BATCH_DAO.flush(requests);
        BATCH_DAO.endOfFlush();
        answered.get(30, TimeUnit.SECONDS);
        final long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(tookMillis < TimeUnit.SECONDS.toMillis(ROUND_ANSWER_SECONDS),
                   "the round was answered after " + tookMillis + " ms");
    }

    /** The answer of the log query whole: its error reason and debugging trace, then every line. */
    private static List<String> describe(Logs logs) {
        final List<String> described = new ArrayList<>();
        described.add(BanyanDBQueryITSupport.json(logs.getErrorReason(), Objects.toString(logs.getDebuggingTrace(), null)));
        logs.getLogs().stream().map(BanyanDBBatchDAOIT::describe).forEach(described::add);
        return described;
    }

    /** Every field of a log line as the log query returns it. */
    private static String describe(Log log) {
        return BanyanDBQueryITSupport.json(
            log.getServiceName(), log.getServiceId(), log.getServiceInstanceName(), log.getServiceInstanceId(),
            log.getEndpointId(), log.getEndpointName(), log.getTraceId(), log.getTimestamp(), log.getContentType(),
            log.getContent(),
            log.getTags().stream().map(tag -> Arrays.asList(tag.getKey(), tag.getValue())).collect(Collectors.toList()));
    }

    /**
     * The same fields of a written line: names are filled in above the DAO, and the endpoint name is read off the
     * endpoint id.
     */
    private static String describe(LogRecord line) {
        final List<List<String>> tags;
        try {
            tags = LogTags.parseFrom(line.getTagsRawData()).getDataList().stream()
                          .map(tag -> Arrays.asList(tag.getKey(), tag.getValue())).collect(Collectors.toList());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
        return BanyanDBQueryITSupport.json(
            null, line.getServiceId(), null, line.getServiceInstanceId(), line.getEndpointId(),
            IDManager.EndpointID.analysisId(line.getEndpointId()).getEndpointName(), line.getTraceId(), line.getTimestamp(),
            ContentType.instanceOf(line.getContentType()), line.getContent().getText(), tags);
    }

    /** Every field of a trace-profiling task as the task query returns it. */
    private static String describe(ProfileTask task) {
        return BanyanDBQueryITSupport.json(
            task.getId(), task.getServiceId(), task.getServiceName(), task.getEndpointName(), task.getStartTime(),
            task.getCreateTime(), task.getDuration(), task.getMinDurationThreshold(), task.getDumpPeriod(),
            task.getMaxSamplingCount(), Objects.toString(task.getLogs(), null));
    }

    private static List<String> values(String service, Duration duration) throws IOException {
        final Entity entity = new Entity();
        entity.setScope(Scope.Service);
        entity.setServiceName(service);
        entity.setNormal(true);
        final MetricsCondition condition = new MetricsCondition();
        condition.setName(RESP_TIME);
        condition.setEntity(entity);
        final MetricsValues values = new BanyanDBMetricsQueryDAO(SUPPORT.getClient())
            .readMetricsValues(condition, ValueColumnMetadata.INSTANCE.getValueCName(RESP_TIME), duration);
        final List<String> read = new ArrayList<>();
        read.add(BanyanDBQueryITSupport.json(values.getLabel()));
        values.getValues().getValues().stream()
              .map(kv -> BanyanDBQueryITSupport.json(kv.getId(), kv.getValue(), kv.isEmptyValue()))
              .forEach(read::add);
        return read;
    }

    /** What the metrics query must return: no label, then these points. */
    private static List<String> series(String... points) {
        final List<String> series = new ArrayList<>();
        series.add(BanyanDBQueryITSupport.json((Object) null));
        series.addAll(Arrays.asList(points));
        return series;
    }

    /** A point the metrics query must return: the id it gives a minute of an entity, its value, and not empty. */
    private static String point(int minute, String entityId, String value) {
        return BanyanDBQueryITSupport.json(
            new PointOfTime(TimeBucket.getMinuteTimeBucket(FIRST_MINUTE + minute * 60_000L)).id(entityId),
            Long.parseLong(value), false);
    }

    /**
     * Every column of these metrics as a measure reads them back: a string column written as null reads back empty,
     * as {@code BanyanDBConverter.StorageToMeasure} gives a missing tag its type's default. Columns of other types
     * stay as written.
     */
    private static List<String> storedAsRead(List<Metrics> metrics) {
        final List<String> stringColumns = RESP_TIME_MINUTE.getColumns().stream()
            .filter(column -> column.getType() == String.class).map(column -> column.getColumnName().getName())
            .collect(Collectors.toList());
        return metrics.stream().map(metric -> {
            final Map<String, Object> columns = BanyanDBQueryITSupport.columnMap(RESP_TIME_BUILDER, metric);
            columns.replaceAll((name, value) -> value == null && stringColumns.contains(name) ? "" : value);
            return BanyanDBQueryITSupport.columns(columns);
        }).sorted().collect(Collectors.toList());
    }

    /** These metrics as a metrics worker reads them back by id, every column of each. */
    private static List<String> readBack(List<Metrics> asked) throws IOException {
        return BanyanDBQueryITSupport.stored(
            RESP_TIME_BUILDER, new BanyanDBMetricsDAO(SUPPORT.getClient(), RESP_TIME_BUILDER).multiGet(RESP_TIME_MINUTE, asked));
    }

    /** Every statement of the database, slowest first, each record whole: name, id, refId, value. */
    private static List<String> readSlowestStatements(int topN) throws IOException {
        final Entity database = new Entity();
        database.setScope(Scope.Service);
        database.setServiceName(DATABASE);
        database.setNormal(false);
        final RecordCondition condition = new RecordCondition();
        condition.setName(TopNDatabaseStatement.INDEX_NAME);
        condition.setParentEntity(database);
        condition.setTopN(topN);
        condition.setOrder(Order.DES);
        final List<Record> records = new BanyanDBRecordsQueryDAO(SUPPORT.getClient())
            .readRecords(condition, TopN.LATENCY, minutes(0, 0));
        return records.stream()
                      .map(record -> BanyanDBQueryITSupport.json(record.getName(), record.getId(), record.getRefId(),
                                                                 record.getValue()))
                      .collect(Collectors.toList());
    }

    /** Every span of a trace, read by its id, each wrapper whole. */
    private static List<String> readTrace(String traceId) throws IOException {
        return BanyanDBQueryITSupport.describe(new BanyanDBTraceQueryDAO(SUPPORT.getClient(), 200, namingControl())
                                                   .queryByTraceIdV2(traceId, minutes(0, 0)));
    }

    /** The wrappers OAP writes for these segments. */
    private static List<String> written(List<SegmentObject> segments) {
        return BanyanDBQueryITSupport.describe(
            segments.stream().map(s -> segment(s).getSpanWrapper()).collect(Collectors.toList()));
    }

    /** Every column of a metric the session cache held, or that it held none. */
    private static String cached(Metrics metrics) {
        return metrics == null ? "absent" : BanyanDBQueryITSupport.columns(RESP_TIME_BUILDER, metrics);
    }

    /** The response time of a minute of an entity, one call of each latency. */
    private static Metrics respTime(String entityId, int minute, long... latencies) throws Exception {
        final Metrics metrics = OALTestMetrics.newMetrics(
            OALTestMetrics.metricsClass(RESP_TIME), "entityId", entityId, "attr0", "GENERAL",
            "timeBucket", TimeBucket.getMinuteTimeBucket(FIRST_MINUTE + minute * 60_000L));
        for (final long latency : latencies) {
            ((LongAvgMetrics) metrics).combine(latency, 1);
        }
        metrics.calculate();
        return metrics;
    }

    private static TopNDatabaseStatement statement(String database, int index, long latency) {
        final long timestamp = FIRST_MINUTE + index * 1000L;
        final TopNDatabaseStatement statement = new TopNDatabaseStatement();
        statement.setId(database + "-" + index);
        statement.setStatement("select " + index);
        statement.setLatency(latency);
        statement.setTraceId("trace-" + index);
        statement.setEntityId(IDManager.ServiceID.buildId(database, false));
        statement.setTimestamp(timestamp);
        statement.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        return statement;
    }

    private static LogRecord line(String content, String serviceId, long timestamp) {
        final LogRecord line = new LogRecord();
        line.setUniqueId(content);
        line.setServiceId(serviceId);
        line.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(serviceId, "instance"));
        line.setTraceId("trace-" + content);
        line.setTraceSegmentId("segment-" + content);
        line.setContentType(ContentType.TEXT.value());
        line.setContent(new LongText(content));
        line.setTimestamp(timestamp);
        line.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        line.setEndpointId(IDManager.EndpointID.buildId(serviceId, "GET:/batch"));
        line.setTagsRawData(LogTags.newBuilder()
                                   .addData(KeyStringValuePair.newBuilder().setKey("level").setValue("INFO"))
                                   .addData(KeyStringValuePair.newBuilder().setKey("line").setValue(content))
                                   .build().toByteArray());
        line.setTagsInString(Arrays.asList("level=INFO", "line=" + content));
        return line;
    }

    private static SegmentObject segmentObject(String traceId, String segmentId, long startTime) {
        return SegmentObject.newBuilder()
            .setTraceId(traceId)
            .setTraceSegmentId(segmentId)
            .setService(SERVICE)
            .setServiceInstance("instance")
            .addSpans(SpanObject.newBuilder()
                                .setSpanId(0)
                                .setParentSpanId(-1)
                                .setStartTime(startTime)
                                .setEndTime(startTime + 10)
                                .setOperationName("GET:/batch")
                                .setSpanType(SpanType.Entry))
            .build();
    }

    private static SegmentRecord segment(SegmentObject segmentObject) {
        final long startTime = segmentObject.getSpans(0).getStartTime();
        final SegmentRecord segment = new SegmentRecord();
        segment.setSegmentId(segmentObject.getTraceSegmentId());
        segment.setTraceId(segmentObject.getTraceId());
        segment.setServiceId(SERVICE_ID);
        segment.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(SERVICE_ID, "instance"));
        segment.setEndpointId(IDManager.EndpointID.buildId(SERVICE_ID, "GET:/batch"));
        segment.setStartTime(startTime);
        segment.setLatency(10);
        segment.setIsError(0);
        segment.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
        segment.setDataBinary(segmentObject.toByteArray());
        segment.setTags(Collections.emptyList());
        return segment;
    }

    private static Duration minutes(int first, int last) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE + first * 60_000L));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE + last * 60_000L));
        return duration;
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
