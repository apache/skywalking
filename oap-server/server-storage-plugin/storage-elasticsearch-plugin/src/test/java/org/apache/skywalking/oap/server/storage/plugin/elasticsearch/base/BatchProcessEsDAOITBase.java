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
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.EBPFProfilingDataEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.EBPFProfilingTaskEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.UITemplateManagementEsDAO;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Writes through OAP's batch DAO and the write DAOs the storage DAO hands out, built as the storage provider builds
 * them, the way OAP's workers use them, and reads each write back through its query DAO: records one by one through
 * {@code insert}, as a record worker hands them over, which the bulk sends on its own flush interval; a task row at
 * once through the none-stream DAO, as a task worker writes it, readable as soon as the write returns; and a
 * management row through the management DAO, which inserts a row only when its id is new. The records lie on both
 * sides of midnight, so in two daily indices. A nested class of a server's IT names the server and the storage mode.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BatchProcessEsDAOITBase {
    /** A fixed local midnight, so the daily indices the records are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    private static final int ROWS = 10;

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private BatchProcessEsDAO batchDAO;
    private StorageDAO storageDAO;
    private Model dataModel;
    private Model taskModel;
    private Model templateModel;

    protected BatchProcessEsDAOITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void install() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        dataModel = support.installRecord(EBPFProfilingDataRecord.class);
        taskModel = support.installNoneStream(EBPFProfilingTaskRecord.class);
        templateModel = support.installManagement(UITemplate.class);
        final StorageModuleElasticsearchConfig config = support.getConfig();
        batchDAO = new BatchProcessEsDAO(
            support.getClient(), config.getBulkActions(), config.getFlushInterval(), config.getConcurrentRequests(),
            config.getBatchOfBytes());
        storageDAO = new StorageEsDAO(support.getClient());
    }

    @AfterAll
    public void close() {
        if (batchDAO != null) {
            BatchProcessEsDAOITExt.close(batchDAO);
        }
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void insertsRecordsThatTheBulkSendsOnItsFlushInterval() throws Exception {
        final List<EBPFProfilingDataRecord> rows = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            final long uploadTime = MIDNIGHT + (i - ROWS / 2) * 1_000L;
            final EBPFProfilingDataRecord row = new EBPFProfilingDataRecord();
            row.setScheduleId("insert-schedule");
            row.setTaskId("batch-it-task");
            row.setStackIdList("stack-" + i);
            row.setTargetType(EBPFProfilingTargetType.ON_CPU.value());
            row.setDataBinary(("stack " + i).getBytes());
            row.setUploadTime(uploadTime);
            row.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime));
            rows.add(row);
        }
        final IRecordDAO recordDAO = storageDAO.newRecordDao(new EBPFProfilingDataRecord.Builder());
        for (final EBPFProfilingDataRecord row : rows) {
            batchDAO.insert(recordDAO.prepareBatchInsert(dataModel, row));
        }

        final EBPFProfilingDataEsDAO dao = new EBPFProfilingDataEsDAO(support.getClient(), support.getConfig());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            support.refresh();
            assertEquals(describe(rows), describe(dao.queryData(
                Collections.singletonList("insert-schedule"), MIDNIGHT - 10_000L, MIDNIGHT + 10_000L)));
        });
    }

    /**
     * A task is created at once, so the profiling page lists it right after.
     */
    @Test
    public void writesATaskRowAtOnceThroughTheNoneStreamDAO() throws Exception {
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(IDManager.ServiceID.buildId("batch-it", true));
        task.setProcessLabelsJson(Const.EMPTY_STRING);
        task.setStartTime(MIDNIGHT);
        task.setTriggerType(EBPFProfilingTriggerType.FIXED_TIME.value());
        task.setFixedTriggerDuration(60);
        task.setTargetType(EBPFProfilingTargetType.OFF_CPU.value());
        task.setCreateTime(MIDNIGHT);
        task.setLastUpdateTime(MIDNIGHT);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(MIDNIGHT));
        task.generateLogicalId();
        task.setExtensionConfigJson(Const.EMPTY_STRING);

        storageDAO.newNoneStreamDao(new EBPFProfilingTaskRecord.Builder()).insert(taskModel, task);

        assertEquals(
            Collections.singletonList(describe(task)),
            new EBPFProfilingTaskEsDAO(support.getClient(), support.getConfig())
                .getTaskRecord(task.getLogicalId()).stream()
                .map(BatchProcessEsDAOITBase::describe)
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

        final UITemplateManagementEsDAO dao = new UITemplateManagementEsDAO(support.getClient(), new UITemplate.Builder());
        assertEquals("batch-it-template|{\"version\":1}|false", describe(dao.getTemplate("batch-it-template")));
        assertEquals("batch-it-other-template|{\"version\":1}|false",
                     describe(dao.getTemplate("batch-it-other-template")));
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
