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
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsSessionCache;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueManager;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCStorageConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Writes through OAP's batch DAO and the write DAOs the storage DAO hands out, built as the storage provider builds
 * them, the way OAP's workers use them, and reads each write back through its query DAO: records one by one through
 * {@code insert}, as a record worker hands them over, and a persistence round's rows through {@code flush}; a task row
 * at once through the none-stream DAO, as a task worker writes it; and a management row through the management DAO,
 * which inserts a row only when its id is new. Metrics are persisted the way the metrics workers persist them: a
 * persistence round's metrics are inserted and cached once the database has answered, the next round reads the stored
 * metrics back by id ({@code multiGet}) and writes the combined ones as updates. The records and the metrics lie on
 * both sides of midnight, so in two day tables, and the records are more than a SQL batch. Beside the metrics, a
 * metric of another name of the same function, so in the same table, has the same entities and minutes, and a
 * metadata metric, whose id has no time bucket, is read with a later minute than it is stored with. A subclass names
 * the server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCBatchDAOITBase {
    /** The name {@link JDBCBatchDAO} registers its queue under, one per JVM. */
    private static final String BATCH_QUEUE = "JDBC_ASYNC_BATCH_PERSISTENT";
    /** Fewer statements per SQL batch than the rows written to one day table. */
    private static final int MAX_SIZE_OF_BATCH_SQL = 3;
    private static final int ROWS = 10;
    private static final String RESP_TIME = "service_resp_time";
    private static final String OTHER_RESP_TIME = "service_relation_server_resp_time";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("payment", true);
    private static final int[] MINUTES = {-1, 0, 1};

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCBatchDAO batchDAO;
    private StorageDAO storageDAO;
    private Model dataModel;
    private Model taskModel;
    private Model templateModel;
    private JDBCEBPFProfilingDataDAO dataDAO;
    private Model respTime;
    private Model traffic;
    private StorageBuilder<Metrics> respTimeBuilder;

    JDBCBatchDAOITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void install() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        dataModel = support.install(
            EBPFProfilingDataRecord.class, DefaultScopeDefine.EBPF_PROFILING_DATA, EBPFProfilingDataRecord.INDEX_NAME,
            DownSampling.Second);
        taskModel = support.install(
            EBPFProfilingTaskRecord.class, DefaultScopeDefine.EBPF_PROFILING_TASK, EBPFProfilingTaskRecord.INDEX_NAME,
            DownSampling.Minute);
        templateModel = support.install(
            UITemplate.class, DefaultScopeDefine.UI_TEMPLATE, UITemplate.INDEX_NAME, DownSampling.None);
        batchDAO = new JDBCBatchDAO(
            support.getClient(), MAX_SIZE_OF_BATCH_SQL, new JDBCStorageConfig().getAsyncBatchPersistentPoolSize());
        storageDAO = new JDBCStorageDAO(support.getClient());
        dataDAO = new JDBCEBPFProfilingDataDAO(support.getClient(), support.getTableHelper());

        final Class<? extends Metrics> respTimeClass = OALTestMetrics.metricsClass(RESP_TIME);
        final Class<? extends Metrics> otherRespTimeClass = OALTestMetrics.metricsClass(OTHER_RESP_TIME);
        respTime = support.install(
            respTimeClass, OALTestMetrics.scopeId(respTimeClass), RESP_TIME, DownSampling.Minute);
        final Model otherRespTime = support.install(
            otherRespTimeClass, OALTestMetrics.scopeId(otherRespTimeClass), OTHER_RESP_TIME, DownSampling.Minute);
        traffic = support.install(
            ServiceTraffic.class, DefaultScopeDefine.SERVICE, ServiceTraffic.INDEX_NAME, DownSampling.Minute);
        respTimeBuilder = OALTestMetrics.builder(respTimeClass);
        final List<Metrics> sameEntities = new ArrayList<>();
        for (final int minute : MINUTES) {
            for (final String entityId : Arrays.asList(SERVICE_ID, OTHER_SERVICE_ID)) {
                sameEntities.add(respTime(OTHER_RESP_TIME, entityId, minute, 7L));
            }
        }
        support.writeMetrics(otherRespTime, OALTestMetrics.builder(otherRespTimeClass), sameEntities);
    }

    @AfterAll
    public void close() {
        BatchQueueManager.shutdown(BATCH_QUEUE);
        if (support != null) {
            support.close();
        }
    }

    /**
     * {@code insert} only queues a row; the queue's consumers write it shortly after.
     */
    @Test
    public void insertsRecordsThroughItsQueue() throws Exception {
        final List<EBPFProfilingDataRecord> rows = rows("insert-schedule");
        final IRecordDAO recordDAO = storageDAO.newRecordDao(new EBPFProfilingDataRecord.Builder());
        for (final EBPFProfilingDataRecord row : rows) {
            batchDAO.insert(recordDAO.prepareBatchInsert(dataModel, row));
        }

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(describe(rows), read("insert-schedule")));
    }

    /**
     * The persistence timer waits for a round's flush before the next round, so the rows are written when it
     * answers.
     */
    @Test
    public void flushesTheRowsOfARoundBeforeItAnswers() throws Exception {
        final List<EBPFProfilingDataRecord> rows = rows("flush-schedule");
        final IRecordDAO recordDAO = storageDAO.newRecordDao(new EBPFProfilingDataRecord.Builder());
        final List<PrepareRequest> round = new ArrayList<>();
        for (final EBPFProfilingDataRecord row : rows) {
            round.add(recordDAO.prepareBatchInsert(dataModel, row));
        }

        batchDAO.flush(round).get(30, TimeUnit.SECONDS);

        assertEquals(describe(rows), read("flush-schedule"));
    }

    /**
     * A task is created at once, so the profiling page lists it right after; the none-stream DAO writes it into the
     * table of the day it is written on.
     */
    @Test
    public void writesATaskRowAtOnceThroughTheNoneStreamDAO() throws Exception {
        final long now = System.currentTimeMillis();
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(IDManager.ServiceID.buildId("batch-it", true));
        task.setProcessLabelsJson(Const.EMPTY_STRING);
        task.setStartTime(now);
        task.setTriggerType(EBPFProfilingTriggerType.FIXED_TIME.value());
        task.setFixedTriggerDuration(60);
        task.setTargetType(EBPFProfilingTargetType.OFF_CPU.value());
        task.setCreateTime(now);
        task.setLastUpdateTime(now);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(now));
        task.generateLogicalId();
        task.setExtensionConfigJson(Const.EMPTY_STRING);

        storageDAO.newNoneStreamDao(new EBPFProfilingTaskRecord.Builder()).insert(taskModel, task);

        assertEquals(
            Collections.singletonList(describe(task)),
            new JDBCEBPFProfilingTaskDAO(support.getClient(), support.getTableHelper())
                .getTaskRecord(task.getLogicalId()).stream()
                .map(JDBCBatchDAOITBase::describe)
                .collect(Collectors.toList()));
    }

    @Test
    public void insertsAManagementRowOnlyWhenItsIdIsNew() throws Exception {
        final UITemplate first = template("batch-it-template", "{\"version\":1}");
        final UITemplate second = template("batch-it-template", "{\"version\":2}");
        final UITemplate other = template("batch-it-other-template", "{\"version\":1}");
        for (final UITemplate template : new UITemplate[] {first, second, other}) {
            storageDAO.newManagementDao(new UITemplate.Builder()).insert(templateModel, template);
        }

        final JDBCUITemplateManagementDAO dao =
            new JDBCUITemplateManagementDAO(support.getClient(), support.getTableHelper());
        assertEquals("batch-it-template|{\"version\":1}|false", describe(dao.getTemplate("batch-it-template")));
        assertEquals("batch-it-other-template|{\"version\":1}|false",
                     describe(dao.getTemplate("batch-it-other-template")));
    }

    /**
     * The metrics worker caches each metric of a round once its insert is answered, reads back by id the metrics it
     * does not hold, and writes a metric combined with what came in since as an update of the stored one.
     */
    @Test
    public void writesTheMetricsOfAPersistenceRoundAndReadsThemBackById() throws Exception {
        final IMetricsDAO dao = storageDAO.newMetricsDao(respTimeBuilder);
        final MetricsSessionCache sessionCache = new MetricsSessionCache(TimeUnit.MINUTES.toMillis(1), true);
        final List<Metrics> inserted = new ArrayList<>();
        final List<PrepareRequest> round = new ArrayList<>();
        for (final int minute : MINUTES) {
            for (final String entityId : Arrays.asList(SERVICE_ID, OTHER_SERVICE_ID)) {
                final Metrics metrics = respTime(RESP_TIME, entityId, minute, 100L + minute);
                inserted.add(metrics);
                round.add(dao.prepareBatchInsert(respTime, metrics, new SessionCacheCallback(sessionCache, metrics)));
            }
        }
        batchDAO.flush(round).get(30, TimeUnit.SECONDS);

        assertEquals(columns(inserted), columns(inserted.stream().map(sessionCache::remove)
                                                        .collect(Collectors.toList())),
                     "every metric of the round is cached once the database has answered");
        final List<Metrics> asked = new ArrayList<>(inserted);
        asked.add(respTime(RESP_TIME, IDManager.ServiceID.buildId("never-written", true), 0, 1L));
        assertEquals(columns(inserted), columns(dao.multiGet(respTime, asked)));

        final Metrics combined = respTime(RESP_TIME, SERVICE_ID, 0, 100L, 300L);
        batchDAO.flush(Collections.singletonList(dao.prepareBatchUpdate(respTime, combined, null)))
                .get(30, TimeUnit.SECONDS);
        final List<Metrics> afterUpdate = inserted.stream()
            .map(metrics -> metrics.id().build().equals(combined.id().build()) ? combined : metrics)
            .collect(Collectors.toList());
        assertEquals(columns(afterUpdate), columns(dao.multiGet(respTime, inserted)));
    }

    /**
     * A metadata metric has one row for all time. The metrics worker asks for it with the minute it sees it in again,
     * not the minute it was stored with.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void readsAMetadataMetricBackWhicheverDayItIsStoredIn() throws Exception {
        final StorageBuilder<Metrics> builder = (StorageBuilder) new ServiceTraffic.Builder();
        final ServiceTraffic stored = service("checkout", Layer.GENERAL, -1);
        support.writeMetrics(traffic, builder, Arrays.asList(stored, service("checkout", Layer.MESH, -1)));

        final List<Metrics> read = storageDAO.newMetricsDao(builder).multiGet(traffic, Arrays.asList(
            service("checkout", Layer.GENERAL, 1), service("never-seen", Layer.GENERAL, 1)));

        assertEquals(columns(builder, Collections.<Metrics>singletonList(stored)), columns(builder, read));
    }

    private List<String> columns(final List<Metrics> metrics) {
        return columns(respTimeBuilder, metrics);
    }

    /**
     * Every column of each metric, as the metrics workers store and read them, in no order.
     */
    private static List<String> columns(final StorageBuilder<Metrics> builder, final List<Metrics> metrics) {
        return metrics.stream().map(metric -> {
            final HashMapConverter.ToStorage columns = new HashMapConverter.ToStorage();
            builder.entity2Storage(metric, columns);
            return new TreeMap<>(columns.obtain()).toString();
        }).sorted().collect(Collectors.toList());
    }

    /**
     * The response time of a minute of an entity, one call of each latency.
     */
    private static Metrics respTime(final String metric, final String entityId, final int minute,
                                    final long... latencies) throws Exception {
        final LongAvgMetrics metrics = (LongAvgMetrics) OALTestMetrics.newMetrics(
            OALTestMetrics.metricsClass(metric), "entityId", entityId,
            "timeBucket", TimeBucket.getMinuteTimeBucket(MIDNIGHT + TimeUnit.MINUTES.toMillis(minute)));
        for (final long latency : latencies) {
            metrics.combine(latency, 1);
        }
        metrics.calculate();
        return metrics;
    }

    private static ServiceTraffic service(final String name, final Layer layer, final int minute) {
        final ServiceTraffic service = new ServiceTraffic();
        service.setName(name);
        service.setLayer(layer);
        service.setTimeBucket(TimeBucket.getMinuteTimeBucket(MIDNIGHT + TimeUnit.MINUTES.toMillis(minute)));
        return service;
    }

    private List<String> read(final String scheduleId) throws Exception {
        return describe(dataDAO.queryData(
            Collections.singletonList(scheduleId), MIDNIGHT - 10_000L, MIDNIGHT + 10_000L));
    }

    /**
     * Uploads of a schedule a second apart, half of them before midnight.
     */
    private static List<EBPFProfilingDataRecord> rows(final String scheduleId) {
        final List<EBPFProfilingDataRecord> rows = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            final long uploadTime = MIDNIGHT + (i - ROWS / 2) * 1_000L;
            final EBPFProfilingDataRecord row = new EBPFProfilingDataRecord();
            row.setScheduleId(scheduleId);
            row.setTaskId("batch-it-task");
            row.setStackIdList("stack-" + i);
            row.setTargetType(EBPFProfilingTargetType.ON_CPU.value());
            row.setDataBinary(("stack " + i).getBytes());
            row.setUploadTime(uploadTime);
            row.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime));
            rows.add(row);
        }
        return rows;
    }

    private static List<String> describe(final List<EBPFProfilingDataRecord> rows) {
        return rows.stream()
                   .map(row -> String.join("|", row.getScheduleId(), row.getTaskId(), row.getStackIdList(),
                                           String.valueOf(row.getTargetType()),
                                           Base64.getEncoder().encodeToString(row.getDataBinary()),
                                           String.valueOf(row.getUploadTime())))
                   .sorted()
                   .collect(Collectors.toList());
    }

    private static String describe(final EBPFProfilingTaskRecord task) {
        final HashMapConverter.ToStorage columns = new HashMapConverter.ToStorage();
        new EBPFProfilingTaskRecord.Builder().entity2Storage(task, columns);
        return new TreeMap<>(columns.obtain()).toString();
    }

    private static String describe(final DashboardConfiguration template) {
        return template.getId() + "|" + template.getConfiguration() + "|" + template.isDisabled();
    }

    private static UITemplate template(final String id, final String configuration) {
        final DashboardSetting setting = new DashboardSetting();
        setting.setId(id);
        setting.setConfiguration(configuration);
        return setting.toEntity();
    }
}
