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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Deletes the history of a model the way the TTL timer does, through {@link HistoryDeleteEsDAO}: the daily indices of
 * the model's physical table whose day is past the TTL go, the others stay. A nested class of a server's IT names the
 * server and the storage mode.
 *
 * <p>Two records, eBPF profiling tasks and two metrics are written on the last day an index is deleted on, the
 * first day one is kept on, a day well past the TTL, and today, before each test, so every test starts from every
 * index. The tasks are written the way the task workers write them, with the minute they are created in. In OAP's
 * default mode the records and the tasks share one physical table and the two metrics another, so deleting the
 * history of one model deletes that of the others; with logic sharding each model has a table of its own, which the
 * history of another model must not touch. Metrics of every downsampling share their indices, and only the minute
 * model deletes them. An index that earlier versions named by an hour goes with the indices of its day.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class HistoryDeleteEsDAOITBase {
    private static final int RECORD_TTL_DAYS = 3;
    private static final int METRICS_TTL_DAYS = 7;
    private static final List<Integer> RECORD_DAYS_AGO = Arrays.asList(5, RECORD_TTL_DAYS, RECORD_TTL_DAYS - 1, 0);
    private static final List<Integer> METRICS_DAYS_AGO = Arrays.asList(9, METRICS_TTL_DAYS, METRICS_TTL_DAYS - 1, 0);
    private static final String SOURCE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String DEST_ID = IDManager.ServiceID.buildId("payment", true);

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private Model ebpfData;
    private Model pprofData;
    private Model ebpfTasks;
    private Model serverRelation;
    private Model clientRelation;
    private Model serverRelationHour;
    private Model asyncProfilerTasks;
    private LocalDate today;

    protected HistoryDeleteEsDAOITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void install() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        ebpfData = support.installRecord(EBPFProfilingDataRecord.class);
        pprofData = support.installRecord(PprofProfilingDataRecord.class);
        ebpfTasks = support.installNoneStream(EBPFProfilingTaskRecord.class);
        serverRelation = support.installMetrics(ServiceRelationServerSideMetrics.class);
        serverRelationHour = support.installMetrics(ServiceRelationServerSideMetrics.class, DownSampling.Hour);
        clientRelation = support.installMetrics(ServiceRelationClientSideMetrics.class);
        asyncProfilerTasks = support.installNoneStream(AsyncProfilerTaskRecord.class);
    }

    @BeforeEach
    public void writeEveryDay() throws Exception {
        today = LocalDate.now();
        final List<EBPFProfilingDataRecord> ebpfRows = new ArrayList<>();
        final List<PprofProfilingDataRecord> pprofRows = new ArrayList<>();
        for (final int daysAgo : RECORD_DAYS_AGO) {
            final long time = daysAgo(daysAgo);
            final EBPFProfilingDataRecord ebpfRow = new EBPFProfilingDataRecord();
            ebpfRow.setScheduleId("schedule");
            ebpfRow.setTaskId("task");
            ebpfRow.setStackIdList("stack-" + daysAgo);
            ebpfRow.setDataBinary(new byte[] {1});
            ebpfRow.setUploadTime(time);
            ebpfRow.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
            ebpfRows.add(ebpfRow);
            final PprofProfilingDataRecord pprofRow = new PprofProfilingDataRecord();
            pprofRow.setTaskId("task-" + daysAgo);
            pprofRow.setInstanceId("instance");
            pprofRow.setDataBinary(new byte[] {1});
            pprofRow.setUploadTime(time);
            pprofRow.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
            pprofRows.add(pprofRow);
        }
        support.writeRecords(ebpfData, new EBPFProfilingDataRecord.Builder(), ebpfRows);
        support.writeRecords(pprofData, new PprofProfilingDataRecord.Builder(), pprofRows);
        for (final int daysAgo : RECORD_DAYS_AGO) {
            final long time = daysAgo(daysAgo);
            final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
            task.setServiceId(SOURCE_ID);
            task.setProcessLabelsJson(Const.EMPTY_STRING);
            task.setStartTime(time);
            task.setTriggerType(EBPFProfilingTriggerType.FIXED_TIME.value());
            task.setFixedTriggerDuration(60);
            task.setTargetType(EBPFProfilingTargetType.ON_CPU.value());
            task.setCreateTime(time);
            task.setLastUpdateTime(time);
            task.setTimeBucket(TimeBucket.getMinuteTimeBucket(time));
            task.generateLogicalId();
            task.setExtensionConfigJson(Const.EMPTY_STRING);
            support.writeNoneStream(ebpfTasks, new EBPFProfilingTaskRecord.Builder(), task);
        }

        final List<ServiceRelationServerSideMetrics> serverRows = new ArrayList<>();
        final List<ServiceRelationClientSideMetrics> clientRows = new ArrayList<>();
        for (final int daysAgo : METRICS_DAYS_AGO) {
            final long minute = TimeBucket.getMinuteTimeBucket(daysAgo(daysAgo));
            final ServiceRelationServerSideMetrics serverRow = new ServiceRelationServerSideMetrics();
            serverRow.setSourceServiceId(SOURCE_ID);
            serverRow.setDestServiceId(DEST_ID);
            serverRow.setEntityId(relationId());
            serverRow.setComponentIds(new IntList(1));
            serverRow.setTimeBucket(minute);
            serverRows.add(serverRow);
            final ServiceRelationClientSideMetrics clientRow = new ServiceRelationClientSideMetrics();
            clientRow.setSourceServiceId(SOURCE_ID);
            clientRow.setDestServiceId(DEST_ID);
            clientRow.setEntityId(relationId());
            clientRow.setComponentIds(new IntList(1));
            clientRow.setTimeBucket(minute);
            clientRows.add(clientRow);
        }
        support.writeMetrics(serverRelation, new ServiceRelationServerSideMetrics.Builder(), serverRows);
        support.writeMetrics(clientRelation, new ServiceRelationClientSideMetrics.Builder(), clientRows);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void deletesTheRecordIndicesPastTheTTL() throws Exception {
        new HistoryDeleteEsDAO(support.getClient()).deleteHistory(ebpfData, Metrics.TIME_BUCKET, RECORD_TTL_DAYS);

        assertEquals(expected(ebpfData, RECORD_TTL_DAYS), indices());
    }

    @Test
    public void deletesTheTaskIndicesPastTheTTL() throws Exception {
        new HistoryDeleteEsDAO(support.getClient()).deleteHistory(ebpfTasks, Metrics.TIME_BUCKET, RECORD_TTL_DAYS);

        assertEquals(expected(ebpfTasks, RECORD_TTL_DAYS), indices());
    }

    @Test
    public void deletesTheMetricsIndicesPastTheTTL() throws Exception {
        new HistoryDeleteEsDAO(support.getClient()).deleteHistory(serverRelation, Metrics.TIME_BUCKET, METRICS_TTL_DAYS);

        assertEquals(expected(serverRelation, METRICS_TTL_DAYS), indices());
    }

    @Test
    public void leavesTheMetricsIndicesToTheMinuteModel() throws Exception {
        new HistoryDeleteEsDAO(support.getClient())
            .deleteHistory(serverRelationHour, Metrics.TIME_BUCKET, METRICS_TTL_DAYS);

        assertEquals(expected(null, 0), indices());
    }

    /**
     * Earlier versions wrote an async-profiler or pprof task, a second bucket on a minute-downsampled model, into an
     * index named by its hour, {@code <table>-<yyyyMMddHH>}. Such an index goes with the daily index of its day once
     * that day is past the TTL, and stays with it while the day is not. The tasks of today's version, written on the
     * same days, lie in the daily indices.
     */
    @Test
    public void deletesTheHourIndicesOfEarlierVersionsWithTheirDay() throws Exception {
        final String table = IndexController.INSTANCE.getTableName(asyncProfilerTasks);
        final ElasticSearchClient client = support.getClient();
        try {
            for (final int daysAgo : Arrays.asList(RECORD_TTL_DAYS, RECORD_TTL_DAYS - 1)) {
                final long time = daysAgo(daysAgo);
                final AsyncProfilerTaskRecord task = new AsyncProfilerTaskRecord();
                task.setTaskId(time + Const.ID_CONNECTOR + SOURCE_ID);
                task.setServiceId(SOURCE_ID);
                task.setServiceInstanceIds("[]");
                task.setEvents("[]");
                task.setExecArgs(Const.EMPTY_STRING);
                task.setCreateTime(time);
                task.setDuration(60);
                task.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
                support.writeNoneStream(asyncProfilerTasks, new AsyncProfilerTaskRecord.Builder(), task);
                client.createIndex(table + Const.LINE + day(daysAgo) + "09");
            }

            new HistoryDeleteEsDAO(client).deleteHistory(asyncProfilerTasks, Metrics.TIME_BUCKET, RECORD_TTL_DAYS);

            final TreeSet<String> indices = new TreeSet<>(client.retrievalIndexByAliases(table));
            assertEquals(Arrays.asList(false, false, true, true), Arrays.asList(
                indices.contains(client.formatIndexName(table + Const.LINE + day(RECORD_TTL_DAYS) + "09")),
                indices.contains(client.formatIndexName(table + Const.LINE + day(RECORD_TTL_DAYS))),
                indices.contains(client.formatIndexName(table + Const.LINE + day(RECORD_TTL_DAYS - 1) + "09")),
                indices.contains(client.formatIndexName(table + Const.LINE + day(RECORD_TTL_DAYS - 1)))),
                         indices.toString());
        } finally {
            // The other tests share the table in the default mode and expect daily indices only.
            for (final String index : client.retrievalIndexByAliases(table)) {
                if (index.substring(index.lastIndexOf(Const.LINE) + 1).length() == 10) {
                    client.deleteByIndexName(index);
                }
            }
        }
    }

    private String day(final int daysAgo) {
        return String.valueOf(TimeBucket.getTimeBucket(daysAgo(daysAgo), DownSampling.Day));
    }

    /**
     * Noon of the local calendar day this many days before today. The DAO counts the TTL in calendar days, which are
     * not all 24 hours long, and noon is clear of any daylight-saving change.
     */
    private long daysAgo(final int days) {
        return today.minusDays(days).toDateTime(new LocalTime(12, 0)).getMillis();
    }

    private static String relationId() {
        return IDManager.ServiceID.buildRelationId(new IDManager.ServiceID.ServiceRelationDefine(SOURCE_ID, DEST_ID));
    }

    /**
     * The indices of each physical table, by its alias.
     */
    private Map<String, TreeSet<String>> indices() {
        final Map<String, TreeSet<String>> indices = new TreeMap<>();
        for (final Model model : Arrays.asList(ebpfData, pprofData, ebpfTasks, serverRelation, clientRelation)) {
            final String table = IndexController.INSTANCE.getTableName(model);
            indices.put(table, new TreeSet<>(support.getClient().retrievalIndexByAliases(table)));
        }
        return indices;
    }

    /**
     * The indices of each physical table: a daily index of every day written, today's included, less the indices
     * past the TTL of the cleaned model's table.
     */
    private Map<String, TreeSet<String>> expected(final Model cleaned, final int ttl) {
        final Map<String, TreeSet<String>> indices = new TreeMap<>();
        for (final Model model : Arrays.asList(ebpfData, pprofData, ebpfTasks, serverRelation, clientRelation)) {
            final String table = IndexController.INSTANCE.getTableName(model);
            final TreeSet<String> tableIndices = indices.computeIfAbsent(table, t -> new TreeSet<>());
            final boolean isCleaned = cleaned != null && table.equals(IndexController.INSTANCE.getTableName(cleaned));
            for (final int daysAgo : model.isRecord() ? RECORD_DAYS_AGO : METRICS_DAYS_AGO) {
                if (isCleaned && daysAgo >= ttl) {
                    continue;
                }
                tableIndices.add(support.getClient().formatIndexName(
                    IndexNames.latestIndex(IndexFamily.of(model), daysAgo(daysAgo))));
            }
        }
        return indices;
    }
}
