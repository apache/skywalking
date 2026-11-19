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

import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.AsyncProfilerMutationService;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.AsyncProfilerQueryService;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JFRProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.pprof.PprofMutationService;
import org.apache.skywalking.oap.server.core.profiling.pprof.PprofQueryService;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.AsyncProfilerTaskLog;
import org.apache.skywalking.oap.server.core.query.PprofTaskLog;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerEventType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskCreationType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskLogOperationType;
import org.apache.skywalking.oap.server.core.query.type.PprofEventType;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskCreationType;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskLogOperationType;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.jfr.type.JFREventType;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reads trace-profiling, async-profiler and pprof tasks, their logs and the async-profiler (JFR) and pprof data the
 * way the profiling query services, the mutation services and the agent task caches do on Elasticsearch and
 * OpenSearch. OAP writes a task at once through its none-stream DAO, into the index of the task's time bucket: a
 * trace-profiling task into the day it starts, an async-profiler or pprof task into the day it is created. Logs and
 * data are records, written into the index of the day they are reported. A nested class of a server's IT names the
 * server and the storage mode.
 *
 * <p>The fixture lies on both sides of a midnight, so every read spans two daily indices. Every filter has a near miss:
 * a task of another service, of another endpoint, created or starting a second or a minute outside a time range, a
 * log of another task, data of another task, instance or event. In the default mode the task, log and data models
 * share one physical index, so a read missing its model filter would also see the other models' documents. A task's
 * data is more than a search returns without a size, and some of its uploads are too large to share a response.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ProfilingTaskQueryITBase {
    /** A fixed local midnight, so the daily indices the fixture is written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 3, 10, 0, 0).getMillis();
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("payment", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-1");
    private static final String OTHER_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-2");
    /** A service with two async-profiler and two pprof tasks created in the same minute, half a minute apart. */
    private static final String SAME_MINUTE_SERVICE_ID = IDManager.ServiceID.buildId("inventory", true);
    private static final int SAME_MINUTE = 5;
    /** A service whose tasks the creation tests write as running now. */
    private static final String RUNNING_SERVICE_ID = IDManager.ServiceID.buildId("shipping", true);
    private static final int TRACE_PROFILING_MINUTES = 10;
    /** When each async-profiler and pprof task of the service was created, in minutes from midnight. */
    private static final int[] CREATED_MINUTES = {-30, -20, -10, 10, 20};
    /** When the other service's async-profiler and pprof task was created, in minutes from midnight. */
    private static final int OTHER_SERVICE_CREATED_MINUTE = -15;
    /** The task whose logs and data are read: created ten minutes before midnight, reported on both sides of it. */
    private static final int READ_TASK_MINUTE = -10;
    /** Uploads of the read task by its first instance, more than a search returns without a size. */
    private static final int UPLOADS = 12;
    /** As {@code application.yml} sets the async-profiler and pprof task query sizes. */
    private static final int TASK_QUERY_MAX_SIZE = 200;
    /** The task with large uploads: three of them, each of which fits a response alone but not with the others. */
    private static final int LARGE_UPLOAD_TASK_MINUTE = 10;
    private static final int LARGE_UPLOADS = 3;
    /** Four MiB of frame tree, over five MiB as Base64: two make a response larger than Armeria's 10 MiB default. */
    private static final int LARGE_UPLOAD_BYTES = 4 * 1024 * 1024;

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;

    private final ProfileTaskRecord startedYesterday =
        traceProfilingTask(SERVICE_ID, "/pay", MIDNIGHT - minutes(10), MIDNIGHT - minutes(10));
    private final ProfileTaskRecord startedToday =
        traceProfilingTask(SERVICE_ID, "/checkout", MIDNIGHT + minutes(1), MIDNIGHT + minutes(1));
    /** Created yesterday evening to start today at 10:00. */
    private final ProfileTaskRecord scheduledForToday =
        traceProfilingTask(SERVICE_ID, "/checkout", MIDNIGHT - minutes(120), MIDNIGHT + minutes(600));
    private final ProfileTaskRecord otherServiceTask =
        traceProfilingTask(OTHER_SERVICE_ID, "/checkout", MIDNIGHT + minutes(2), MIDNIGHT + minutes(2));
    private final List<ProfileTaskLogRecord> traceProfilingLogs = new ArrayList<>();
    private final List<AsyncProfilerTaskLogRecord> asyncProfilerLogs = new ArrayList<>();
    private final List<PprofTaskLogRecord> pprofLogs = new ArrayList<>();
    private final List<JFRProfilingDataRecord> jfrData = new ArrayList<>();
    private final List<PprofProfilingDataRecord> pprofData = new ArrayList<>();
    private final List<JFRProfilingDataRecord> largeJfrData = new ArrayList<>();
    private final List<PprofProfilingDataRecord> largePprofData = new ArrayList<>();
    private Model traceProfilingModel;
    private Model asyncProfilerModel;
    private Model pprofModel;
    private Model traceProfilingLogModel;
    private Model asyncProfilerLogModel;
    private Model pprofLogModel;
    private Model jfrDataModel;
    private Model pprofDataModel;

    private ProfileTaskQueryEsDAO traceProfilingDAO;
    private ProfileTaskLogEsDAO traceProfilingLogDAO;
    private AsyncProfilerTaskQueryEsDAO asyncProfilerDAO;
    private AsyncProfilerTaskLogQueryEsDAO asyncProfilerLogDAO;
    private JFRDataQueryEsDAO jfrDataDAO;
    private PprofTaskQueryEsDAO pprofDAO;
    private PprofTaskLogQueryEsDAO pprofLogDAO;
    private PprofDataQueryEsDAO pprofDataDAO;

    ProfilingTaskQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeProfiling() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass(), config -> {
            config.setAsyncProfilerTaskQueryMaxSize(TASK_QUERY_MAX_SIZE);
            config.setPprofTaskQueryMaxSize(TASK_QUERY_MAX_SIZE);
        });
        // As at boot, every model is installed before anything is written: in the default mode they share one
        // index template, and an index created before a model is installed lacks the model's columns.
        traceProfilingModel = support.installNoneStream(ProfileTaskRecord.class);
        asyncProfilerModel = support.installNoneStream(AsyncProfilerTaskRecord.class);
        pprofModel = support.installNoneStream(PprofTaskRecord.class);
        traceProfilingLogModel = support.installRecord(ProfileTaskLogRecord.class);
        asyncProfilerLogModel = support.installRecord(AsyncProfilerTaskLogRecord.class);
        pprofLogModel = support.installRecord(PprofTaskLogRecord.class);
        jfrDataModel = support.installRecord(JFRProfilingDataRecord.class);
        pprofDataModel = support.installRecord(PprofProfilingDataRecord.class);
        writeTasks();
        writeLogs();
        writeData();

        final ElasticSearchClient client = support.getClient();
        final StorageModuleElasticsearchConfig config = support.getConfig();
        traceProfilingDAO = new ProfileTaskQueryEsDAO(client, config.getProfileTaskQueryMaxSize());
        traceProfilingLogDAO = new ProfileTaskLogEsDAO(client, config.getProfileTaskQueryMaxSize());
        asyncProfilerDAO = new AsyncProfilerTaskQueryEsDAO(client, config.getAsyncProfilerTaskQueryMaxSize());
        asyncProfilerLogDAO = new AsyncProfilerTaskLogQueryEsDAO(client, config.getAsyncProfilerTaskQueryMaxSize());
        jfrDataDAO = new JFRDataQueryEsDAO(client);
        pprofDAO = new PprofTaskQueryEsDAO(client, config.getPprofTaskQueryMaxSize());
        pprofLogDAO = new PprofTaskLogQueryEsDAO(client, config.getPprofTaskQueryMaxSize());
        pprofDataDAO = new PprofDataQueryEsDAO(client);
    }

    private void writeTasks() throws Exception {
        for (final ProfileTaskRecord task : Arrays.asList(
            startedYesterday, startedToday, scheduledForToday, otherServiceTask)) {
            support.writeNoneStream(traceProfilingModel, new ProfileTaskRecord.Builder(), task);
        }
        for (final int minute : CREATED_MINUTES) {
            support.writeNoneStream(
                asyncProfilerModel, new AsyncProfilerTaskRecord.Builder(), asyncProfilerTask(SERVICE_ID, minute));
            support.writeNoneStream(pprofModel, new PprofTaskRecord.Builder(), pprofTask(SERVICE_ID, minute));
        }
        support.writeNoneStream(asyncProfilerModel, new AsyncProfilerTaskRecord.Builder(),
                                asyncProfilerTask(OTHER_SERVICE_ID, OTHER_SERVICE_CREATED_MINUTE));
        support.writeNoneStream(
            pprofModel, new PprofTaskRecord.Builder(), pprofTask(OTHER_SERVICE_ID, OTHER_SERVICE_CREATED_MINUTE));
        for (final long createTime : sameMinuteCreateTimes()) {
            support.writeNoneStream(asyncProfilerModel, new AsyncProfilerTaskRecord.Builder(),
                                    asyncProfilerTask(SAME_MINUTE_SERVICE_ID, createTime));
            support.writeNoneStream(
                pprofModel, new PprofTaskRecord.Builder(), pprofTask(SAME_MINUTE_SERVICE_ID, createTime));
        }
    }

    private static long[] sameMinuteCreateTimes() {
        return new long[] {createTime(SAME_MINUTE) + 10_000L, createTime(SAME_MINUTE) + 40_000L};
    }

    /**
     * The task started yesterday finishes after midnight; the task started today is notified to two instances.
     * The async-profiler and pprof task read has a log on each side of midnight, and the tasks before and after it
     * have logs of the same instance.
     */
    private void writeLogs() throws Exception {
        traceProfilingLogs.add(traceProfilingLog(startedYesterday, INSTANCE_ID, ProfileTaskLogOperationType.NOTIFIED,
                                                 MIDNIGHT - minutes(9)));
        traceProfilingLogs.add(traceProfilingLog(startedYesterday, INSTANCE_ID,
                                                 ProfileTaskLogOperationType.EXECUTION_FINISHED,
                                                 MIDNIGHT + minutes(1)));
        traceProfilingLogs.add(traceProfilingLog(startedToday, INSTANCE_ID, ProfileTaskLogOperationType.NOTIFIED,
                                                 MIDNIGHT + minutes(1) + 10_000L));
        traceProfilingLogs.add(traceProfilingLog(startedToday, OTHER_INSTANCE_ID, ProfileTaskLogOperationType.NOTIFIED,
                                                 MIDNIGHT + minutes(1) + 20_000L));
        support.writeRecords(traceProfilingLogModel, new ProfileTaskLogRecord.Builder(), traceProfilingLogs);

        // Each log: the minute its task was created, the minute it is reported, its operation type.
        for (final int[] log : new int[][] {
            {READ_TASK_MINUTE, -9, 1}, {READ_TASK_MINUTE, 1, 2}, {-20, -19, 1}, {-20, -14, 4}, {10, 11, 1}}) {
            asyncProfilerLogs.add(asyncProfilerLog(taskId(SERVICE_ID, log[0]), MIDNIGHT + minutes(log[1]), log[2]));
            pprofLogs.add(pprofLog(taskId(SERVICE_ID, log[0]), MIDNIGHT + minutes(log[1]), log[2]));
        }
        support.writeRecords(asyncProfilerLogModel, new AsyncProfilerTaskLogRecord.Builder(), asyncProfilerLogs);
        support.writeRecords(pprofLogModel, new PprofTaskLogRecord.Builder(), pprofLogs);
    }

    /**
     * Uploads every second from six seconds before midnight. The read task also has uploads by its other instance,
     * uploads of another event, and the task before it uploads of the same instance and event.
     */
    private void writeData() throws Exception {
        final String readTask = taskId(SERVICE_ID, READ_TASK_MINUTE);
        final String otherTask = taskId(SERVICE_ID, -20);
        final String cpu = JFREventType.EXECUTION_SAMPLE.name();
        final String lock = JFREventType.LOCK.name();
        for (int i = 0; i < UPLOADS; i++) {
            jfrData.add(jfr(readTask, INSTANCE_ID, cpu, i));
            pprofData.add(pprof(readTask, INSTANCE_ID, i));
        }
        for (int i = 0; i < 3; i++) {
            jfrData.add(jfr(readTask, OTHER_INSTANCE_ID, cpu, i));
            pprofData.add(pprof(readTask, OTHER_INSTANCE_ID, i));
        }
        for (int i = 0; i < 2; i++) {
            jfrData.add(jfr(readTask, INSTANCE_ID, lock, i));
            jfrData.add(jfr(otherTask, INSTANCE_ID, cpu, i));
            pprofData.add(pprof(otherTask, INSTANCE_ID, i));
        }
        support.writeRecords(jfrDataModel, new JFRProfilingDataRecord.Builder(), jfrData);
        support.writeRecords(pprofDataModel, new PprofProfilingDataRecord.Builder(), pprofData);

        final String largeTask = taskId(SERVICE_ID, LARGE_UPLOAD_TASK_MINUTE);
        final Random random = new Random(LARGE_UPLOAD_BYTES);
        for (int i = 0; i < LARGE_UPLOADS; i++) {
            final JFRProfilingDataRecord jfrUpload = jfr(largeTask, INSTANCE_ID, cpu, i);
            jfrUpload.setDataBinary(randomBytes(random));
            largeJfrData.add(jfrUpload);
            final PprofProfilingDataRecord pprofUpload = pprof(largeTask, INSTANCE_ID, i);
            pprofUpload.setDataBinary(randomBytes(random));
            largePprofData.add(pprofUpload);
        }
        support.writeRecords(jfrDataModel, new JFRProfilingDataRecord.Builder(), largeJfrData);
        support.writeRecords(pprofDataModel, new PprofProfilingDataRecord.Builder(), largePprofData);
    }

    private static byte[] randomBytes(final Random random) {
        final byte[] bytes = new byte[LARGE_UPLOAD_BYTES];
        random.nextBytes(bytes);
        return bytes;
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void listsTheTraceProfilingTasksOfAServiceLatestStartFirst() throws IOException {
        assertEquals(describe(scheduledForToday, startedToday, startedYesterday),
                     describe(traceProfilingDAO.getTaskList(SERVICE_ID, null, null, null, null)));
    }

    @Test
    public void listsTheTraceProfilingTasksOfAnEndpoint() throws IOException {
        assertEquals(describe(scheduledForToday, startedToday),
                     describe(traceProfilingDAO.getTaskList(SERVICE_ID, "/checkout", null, null, null)));
    }

    /**
     * The agent task cache reads the tasks of every service starting within a window of minute buckets, both ends
     * included.
     */
    @Test
    public void readsTheTraceProfilingTasksStartingWithinAWindow() throws IOException {
        final long first = TimeBucket.getMinuteTimeBucket(startedYesterday.getStartTime());
        final long last = TimeBucket.getMinuteTimeBucket(startedToday.getStartTime());
        assertEquals(describe(startedToday, startedYesterday),
                     describe(traceProfilingDAO.getTaskList(null, null, first, last, null)));
        assertEquals(describe(otherServiceTask, startedToday, startedYesterday),
                     describe(traceProfilingDAO.getTaskList(
                         null, null, first, TimeBucket.getMinuteTimeBucket(otherServiceTask.getStartTime()), null)));
        assertEquals(Collections.emptyList(), describe(traceProfilingDAO.getTaskList(
            null, null, TimeBucket.getMinuteTimeBucket(startedYesterday.getStartTime() + minutes(1)),
            TimeBucket.getMinuteTimeBucket(startedToday.getStartTime() - minutes(1)), null)));
    }

    /**
     * Creating a task checks the latest-starting task of the service that starts before the new one ends for an
     * overlap.
     */
    @Test
    public void readsTheLatestStartingTraceProfilingTaskBeforeAnEnd() throws IOException {
        final long newTaskEnd = TimeBucket.getMinuteTimeBucket(
            scheduledForToday.getStartTime() + minutes(5 + TRACE_PROFILING_MINUTES));
        assertEquals(describe(scheduledForToday),
                     describe(traceProfilingDAO.getTaskList(SERVICE_ID, null, null, newTaskEnd, 1)));
        assertEquals(describe(startedToday), describe(traceProfilingDAO.getTaskList(
            SERVICE_ID, null, null, TimeBucket.getMinuteTimeBucket(otherServiceTask.getStartTime()), 1)));
    }

    @Test
    public void readsATraceProfilingTaskById() throws IOException {
        assertEquals(describe(scheduledForToday),
                     describe(Collections.singletonList(traceProfilingDAO.getById(scheduledForToday.getTaskId()))));
        assertEquals(describe(startedYesterday),
                     describe(Collections.singletonList(traceProfilingDAO.getById(startedYesterday.getTaskId()))));
        assertNull(traceProfilingDAO.getById(taskId(SERVICE_ID, 0)));
        assertNull(traceProfilingDAO.getById(""));
    }

    /**
     * The trace-profiling service reads the logs of every task and matches them to the tasks it lists.
     */
    @Test
    public void readsTheLogsOfEveryTraceProfilingTaskNewestFirst() throws IOException {
        final List<String> expected = traceProfilingLogs.stream()
            .sorted(Comparator.comparingLong(ProfileTaskLogRecord::getOperationTime).reversed())
            .map(log -> String.join(
                "|", IndexController.INSTANCE.generateDocId(traceProfilingLogModel, log.id().build()),
                log.getTaskId(), log.getInstanceId(), ProfileTaskLogOperationType.parse(log.getOperationType()).name(),
                String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList());
        assertEquals(expected, traceProfilingLogDAO.getTaskLogList().stream()
            .map(log -> String.join("|", log.getId(), log.getTaskId(), log.getInstanceId(),
                                    log.getOperationType().name(), String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList()));
    }

    @Test
    public void listsTheAsyncProfilerTasksOfAServiceNewestFirst() throws IOException {
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, 20, 10, -10, -20, -30),
                     describeAsyncProfilerTasks(asyncProfilerDAO.getTaskList(SERVICE_ID, null, null, null)));
    }

    /**
     * The task list of a duration bounds the tasks' second buckets, both ends included, and cuts the newest of both
     * days to the limit.
     */
    @Test
    public void listsTheAsyncProfilerTasksCreatedWithinADuration() throws IOException {
        final long first = secondBucket(-20, 0);
        final long last = secondBucket(10, 0);
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, 10, -10, -20),
                     describeAsyncProfilerTasks(asyncProfilerDAO.getTaskList(SERVICE_ID, first, last, null)));
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, 10, -10),
                     describeAsyncProfilerTasks(asyncProfilerDAO.getTaskList(SERVICE_ID, first, last, 2)));
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, -10), describeAsyncProfilerTasks(
            asyncProfilerDAO.getTaskList(SERVICE_ID, secondBucket(-20, 1), secondBucket(10, -1), null)));
    }

    /**
     * The agent task cache reads the tasks of every service within its window; creating a task checks the newest
     * task of the service created up to now.
     */
    @Test
    public void readsTheAsyncProfilerTasksAsTheCacheAndTheTaskCreationDo() throws IOException {
        final List<String> cached = new ArrayList<>(describeAsyncProfilerTasks(SERVICE_ID, -10));
        cached.addAll(describeAsyncProfilerTasks(OTHER_SERVICE_ID, OTHER_SERVICE_CREATED_MINUTE));
        cached.addAll(describeAsyncProfilerTasks(SERVICE_ID, -20));
        assertEquals(cached, describeAsyncProfilerTasks(
            asyncProfilerDAO.getTaskList(null, secondBucket(-20, 0), secondBucket(-10, 0), null)));
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, -10), describeAsyncProfilerTasks(
            asyncProfilerDAO.getTaskList(SERVICE_ID, null, secondBucket(10, -1), 1)));
    }

    @Test
    public void readsAnAsyncProfilerTaskById() throws IOException {
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, -30), describeAsyncProfilerTasks(
            Collections.singletonList(asyncProfilerDAO.getById(taskId(SERVICE_ID, -30)))));
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, 20), describeAsyncProfilerTasks(
            Collections.singletonList(asyncProfilerDAO.getById(taskId(SERVICE_ID, 20)))));
        assertNull(asyncProfilerDAO.getById(taskId(SERVICE_ID, 0)));
    }

    @Test
    public void readsTheLogsOfOneAsyncProfilerTaskNewestFirst() throws IOException {
        final String task = taskId(SERVICE_ID, READ_TASK_MINUTE);
        final List<String> expected = asyncProfilerLogs.stream()
            .filter(log -> log.getTaskId().equals(task))
            .sorted(Comparator.comparingLong(AsyncProfilerTaskLogRecord::getOperationTime).reversed())
            .map(log -> String.join("|", log.getTaskId(), log.getInstanceId(),
                                    AsyncProfilerTaskLogOperationType.parse(log.getOperationType()).name(),
                                    String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList());
        assertEquals(2, expected.size());
        assertEquals(expected, describeAsyncProfilerLogs(asyncProfilerLogDAO.getTaskLogList(task)));
        assertEquals(Collections.emptyList(), asyncProfilerLogDAO.getTaskLogList(taskId(SERVICE_ID, 0)));
    }

    @Test
    public void listsThePprofTasksOfAServiceNewestFirst() throws IOException {
        assertEquals(describePprofTasks(SERVICE_ID, 20, 10, -10, -20, -30),
                     describePprofTasks(pprofDAO.getTaskList(SERVICE_ID, null, null, null)));
    }

    @Test
    public void listsThePprofTasksCreatedWithinADuration() throws IOException {
        final long first = secondBucket(-20, 0);
        final long last = secondBucket(10, 0);
        assertEquals(describePprofTasks(SERVICE_ID, 10, -10, -20),
                     describePprofTasks(pprofDAO.getTaskList(SERVICE_ID, first, last, null)));
        assertEquals(describePprofTasks(SERVICE_ID, 10, -10),
                     describePprofTasks(pprofDAO.getTaskList(SERVICE_ID, first, last, 2)));
        assertEquals(describePprofTasks(SERVICE_ID, -10), describePprofTasks(
            pprofDAO.getTaskList(SERVICE_ID, secondBucket(-20, 1), secondBucket(10, -1), null)));
    }

    /**
     * Creating a pprof task checks every task of the service created up to now, not only the newest.
     */
    @Test
    public void readsThePprofTasksAsTheCacheAndTheTaskCreationDo() throws IOException {
        final List<String> cached = new ArrayList<>(describePprofTasks(SERVICE_ID, -10));
        cached.addAll(describePprofTasks(OTHER_SERVICE_ID, OTHER_SERVICE_CREATED_MINUTE));
        cached.addAll(describePprofTasks(SERVICE_ID, -20));
        assertEquals(cached, describePprofTasks(
            pprofDAO.getTaskList(null, secondBucket(-20, 0), secondBucket(-10, 0), null)));
        assertEquals(describePprofTasks(SERVICE_ID, -10, -20, -30), describePprofTasks(
            pprofDAO.getTaskList(SERVICE_ID, null, secondBucket(10, -1), null)));
    }

    @Test
    public void readsAPprofTaskById() throws IOException {
        assertEquals(describePprofTasks(SERVICE_ID, -30),
                     describePprofTasks(Collections.singletonList(pprofDAO.getById(taskId(SERVICE_ID, -30)))));
        assertEquals(describePprofTasks(SERVICE_ID, 20),
                     describePprofTasks(Collections.singletonList(pprofDAO.getById(taskId(SERVICE_ID, 20)))));
        assertNull(pprofDAO.getById(taskId(SERVICE_ID, 0)));
    }

    @Test
    public void readsTheLogsOfOnePprofTaskNewestFirst() throws IOException {
        final String task = taskId(SERVICE_ID, READ_TASK_MINUTE);
        final List<String> expected = pprofLogs.stream()
            .filter(log -> log.getTaskId().equals(task))
            .sorted(Comparator.comparingLong(PprofTaskLogRecord::getOperationTime).reversed())
            .map(log -> String.join("|", log.getTaskId(), log.getInstanceId(),
                                    PprofTaskLogOperationType.parse(log.getOperationType()).name(),
                                    String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList());
        assertEquals(2, expected.size());
        assertEquals(expected, describePprofLogs(pprofLogDAO.getTaskLogList(task)));
        assertEquals(Collections.emptyList(), pprofLogDAO.getTaskLogList(taskId(SERVICE_ID, 0)));
        assertEquals(Collections.emptyList(), pprofLogDAO.getTaskLogList(""));
    }

    @Test
    public void readsEveryJFRDataOfAnInstanceAndEvent() throws IOException {
        final String task = taskId(SERVICE_ID, READ_TASK_MINUTE);
        final String cpu = JFREventType.EXECUTION_SAMPLE.name();
        assertEquals(describeJFR(jfrData, task, Collections.singletonList(INSTANCE_ID), cpu),
                     describeJFR(jfrDataDAO.getByTaskIdAndInstancesAndEvent(
                         task, Collections.singletonList(INSTANCE_ID), cpu)));
    }

    /**
     * No instance listed reads the uploads of every instance of the task.
     */
    @Test
    public void readsTheJFRDataOfTheListedInstances() throws IOException {
        final String task = taskId(SERVICE_ID, READ_TASK_MINUTE);
        final String cpu = JFREventType.EXECUTION_SAMPLE.name();
        final List<String> instances = Arrays.asList(INSTANCE_ID, OTHER_INSTANCE_ID);
        assertEquals(describeJFR(jfrData, task, instances, cpu),
                     describeJFR(jfrDataDAO.getByTaskIdAndInstancesAndEvent(task, instances, cpu)));
        assertEquals(describeJFR(jfrData, task, instances, cpu),
                     describeJFR(jfrDataDAO.getByTaskIdAndInstancesAndEvent(task, Collections.emptyList(), cpu)));
        final String lock = JFREventType.LOCK.name();
        assertEquals(describeJFR(jfrData, task, instances, lock),
                     describeJFR(jfrDataDAO.getByTaskIdAndInstancesAndEvent(task, instances, lock)));
        assertEquals(Collections.emptyList(), jfrDataDAO.getByTaskIdAndInstancesAndEvent(task, instances, ""));
    }

    @Test
    public void readsEveryPprofDataOfTheListedInstances() throws IOException {
        final String task = taskId(SERVICE_ID, READ_TASK_MINUTE);
        final List<String> instance = Collections.singletonList(INSTANCE_ID);
        assertEquals(describePprof(pprofData, task, instance),
                     describePprof(pprofDataDAO.getByTaskIdAndInstances(task, instance)));
        final List<String> instances = Arrays.asList(INSTANCE_ID, OTHER_INSTANCE_ID);
        assertEquals(describePprof(pprofData, task, instances),
                     describePprof(pprofDataDAO.getByTaskIdAndInstances(task, instances)));
        assertEquals(Collections.emptyList(), pprofDataDAO.getByTaskIdAndInstances("", instances));
    }

    /**
     * An upload is a frame tree of up to megabytes, which Elasticsearch returns as Base64 in the hit's source, and the
     * client refuses a response larger than Armeria's 10 MiB default. These uploads fit a response one at a time, but
     * not together.
     */
    @Test
    public void readsUploadsTooLargeToShareAResponse() throws IOException {
        final String task = taskId(SERVICE_ID, LARGE_UPLOAD_TASK_MINUTE);
        final List<String> instance = Collections.singletonList(INSTANCE_ID);
        final String cpu = JFREventType.EXECUTION_SAMPLE.name();
        assertEquals(fingerprints(largeJfrData, JFRProfilingDataRecord::getUploadTime,
                                  JFRProfilingDataRecord::getDataBinary),
                     fingerprints(jfrDataDAO.getByTaskIdAndInstancesAndEvent(task, instance, cpu),
                                  JFRProfilingDataRecord::getUploadTime, JFRProfilingDataRecord::getDataBinary));
        assertEquals(fingerprints(largePprofData, PprofProfilingDataRecord::getUploadTime,
                                  PprofProfilingDataRecord::getDataBinary),
                     fingerprints(pprofDataDAO.getByTaskIdAndInstances(task, instance),
                                  PprofProfilingDataRecord::getUploadTime, PprofProfilingDataRecord::getDataBinary));
    }

    /**
     * The GraphQL task lists accept a duration of seconds, and the query services bound the tasks by its seconds: of
     * two tasks created in the same minute, a duration ending between them, with a limit of one, lists the first.
     */
    @Test
    public void listsTheTasksOfADurationOfSecondsExactly() throws IOException {
        final ModuleManager moduleManager = storageServices();
        final AsyncProfilerQueryService async = new AsyncProfilerQueryService(moduleManager);
        final PprofQueryService pprof = new PprofQueryService(moduleManager);
        final long[] created = sameMinuteCreateTimes();
        final long minuteStart = createTime(SAME_MINUTE);
        final List<String> first = Collections.singletonList(taskId(SAME_MINUTE_SERVICE_ID, created[0]));
        final List<String> both = Arrays.asList(
            taskId(SAME_MINUTE_SERVICE_ID, created[1]), taskId(SAME_MINUTE_SERVICE_ID, created[0]));
        final Duration endingBetween = secondsDuration(minuteStart, minuteStart + 30_000L);
        assertEquals(first, asyncProfilerIds(async.queryTask(SAME_MINUTE_SERVICE_ID, endingBetween, 1)));
        assertEquals(first, pprofIds(pprof.queryTask(SAME_MINUTE_SERVICE_ID, endingBetween, 1)));
        final Duration onBoth = secondsDuration(created[0], created[1]);
        assertEquals(both, asyncProfilerIds(async.queryTask(SAME_MINUTE_SERVICE_ID, onBoth, null)));
        assertEquals(both, pprofIds(pprof.queryTask(SAME_MINUTE_SERVICE_ID, onBoth, null)));
        final Duration between = secondsDuration(created[0] + 1_000L, created[1] - 1_000L);
        assertEquals(Collections.emptyList(), async.queryTask(SAME_MINUTE_SERVICE_ID, between, null));
        assertEquals(Collections.emptyList(), pprof.queryTask(SAME_MINUTE_SERVICE_ID, between, null));
    }

    /**
     * A task created ten seconds ago with the second bucket the mutation services write, as earlier versions wrote it
     * too, runs: creating another task of the service is refused, as the check finds the running task by that bucket.
     */
    @Test
    public void refusesASecondTaskOfAServiceWhileTheFirstRuns() throws Exception {
        final long createTime = System.currentTimeMillis() - 10_000L;
        support.writeNoneStream(asyncProfilerModel, new AsyncProfilerTaskRecord.Builder(),
                                asyncProfilerTask(RUNNING_SERVICE_ID, createTime));
        support.writeNoneStream(pprofModel, new PprofTaskRecord.Builder(), pprofTask(RUNNING_SERVICE_ID, createTime));
        final ModuleManager moduleManager = storageServices();
        assertEquals(AsyncProfilerTaskCreationType.ALREADY_PROFILING_ERROR,
                     new AsyncProfilerMutationService(moduleManager).createTask(
                         RUNNING_SERVICE_ID, Collections.singletonList(INSTANCE_ID), 60,
                         Collections.singletonList(AsyncProfilerEventType.CPU), "").getCode());
        assertEquals(PprofTaskCreationType.ALREADY_PROFILING_ERROR,
                     new PprofMutationService(moduleManager).createTask(
                         RUNNING_SERVICE_ID, Collections.singletonList(INSTANCE_ID), 5, PprofEventType.CPU, 10
                     ).getCode());
    }

    /**
     * Every index of the task and log tables is a daily index, named after the day of its documents' time buckets,
     * which is what the TTL reads to delete the indices of past days. A task lies in the index of its time bucket's
     * day, whatever the bucket's precision: an async-profiler or pprof task carries a second bucket on a
     * minute-downsampled model.
     */
    @Test
    public void writesTheTasksAndTheirLogsIntoDailyIndices() {
        final String yesterday = String.valueOf(TimeBucket.getTimeBucket(MIDNIGHT - 1, DownSampling.Day));
        final String today = String.valueOf(TimeBucket.getTimeBucket(MIDNIGHT, DownSampling.Day));
        for (final Model model : Arrays.asList(traceProfilingModel, asyncProfilerModel, pprofModel,
                                               traceProfilingLogModel, asyncProfilerLogModel, pprofLogModel)) {
            final List<String> days = support.getClient()
                                             .retrievalIndexByAliases(IndexController.INSTANCE.getTableName(model))
                                             .stream()
                                             .map(index -> index.substring(index.lastIndexOf('-') + 1))
                                             .sorted()
                                             .collect(Collectors.toList());
            assertEquals(Collections.emptyList(),
                         days.stream().filter(day -> !day.matches("\\d{8}")).collect(Collectors.toList()),
                         model.getName());
            assertTrue(days.containsAll(Arrays.asList(yesterday, today)), model.getName() + " " + days);
        }
    }

    private static long minutes(final int minutes) {
        return TimeUnit.MINUTES.toMillis(minutes);
    }

    private static long createTime(final int minute) {
        return MIDNIGHT + minutes(minute);
    }

    private static long secondBucket(final int minute, final int seconds) {
        return TimeBucket.getRecordTimeBucket(createTime(minute) + TimeUnit.SECONDS.toMillis(seconds));
    }

    /**
     * The id the mutation services give a task: its creation time and its service.
     */
    private static String taskId(final String serviceId, final int minute) {
        return taskId(serviceId, createTime(minute));
    }

    private static String taskId(final String serviceId, final long createTime) {
        return createTime + Const.ID_CONNECTOR + serviceId;
    }

    /**
     * A duration of the second step, as the GraphQL task lists take one.
     */
    private static Duration secondsDuration(final long start, final long end) {
        final Duration duration = new Duration();
        duration.setStep(Step.SECOND);
        duration.setStart(new DateTime(start).toString("yyyy-MM-dd HHmmss"));
        duration.setEnd(new DateTime(end).toString("yyyy-MM-dd HHmmss"));
        return duration;
    }

    private static List<String> asyncProfilerIds(final List<AsyncProfilerTask> tasks) {
        return tasks.stream().map(AsyncProfilerTask::getId).collect(Collectors.toList());
    }

    private static List<String> pprofIds(final List<PprofTask> tasks) {
        return tasks.stream().map(PprofTask::getId).collect(Collectors.toList());
    }

    /**
     * Hands the profiling services this plugin's task DAOs, as the storage module would.
     */
    private ModuleManager storageServices() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(provider);
        when(provider.provider()).thenReturn(services);
        when(services.getService(IAsyncProfilerTaskQueryDAO.class)).thenReturn(asyncProfilerDAO);
        when(services.getService(IPprofTaskQueryDAO.class)).thenReturn(pprofDAO);
        return moduleManager;
    }

    private static List<String> describe(final ProfileTaskRecord... tasks) {
        return Arrays.stream(tasks)
                     .map(task -> String.join(
                         "|", task.getTaskId(), task.getServiceId(), task.getEndpointName(),
                         String.valueOf(task.getStartTime()), String.valueOf(task.getCreateTime()),
                         String.valueOf(task.getDuration()), String.valueOf(task.getMinDurationThreshold()),
                         String.valueOf(task.getDumpPeriod()), String.valueOf(task.getMaxSamplingCount())))
                     .collect(Collectors.toList());
    }

    private static List<String> describe(final List<ProfileTask> tasks) {
        return tasks.stream()
                    .map(task -> String.join(
                        "|", task.getId(), task.getServiceId(), task.getEndpointName(),
                        String.valueOf(task.getStartTime()), String.valueOf(task.getCreateTime()),
                        String.valueOf(task.getDuration()), String.valueOf(task.getMinDurationThreshold()),
                        String.valueOf(task.getDumpPeriod()), String.valueOf(task.getMaxSamplingCount())))
                    .collect(Collectors.toList());
    }

    private static List<String> describeAsyncProfilerTasks(final String serviceId, final int... minutes) {
        return describeAsyncProfilerTasks(Arrays.stream(minutes).mapToObj(minute -> {
            final AsyncProfilerTaskRecord record = asyncProfilerTask(serviceId, minute);
            return AsyncProfilerTask.builder()
                                    .id(record.getTaskId())
                                    .serviceId(record.getServiceId())
                                    .serviceInstanceIds(Collections.singletonList(INSTANCE_ID))
                                    .createTime(record.getCreateTime())
                                    .events(Arrays.asList(AsyncProfilerEventType.CPU, AsyncProfilerEventType.LOCK))
                                    .duration(record.getDuration())
                                    .execArgs(record.getExecArgs())
                                    .build();
        }).collect(Collectors.toList()));
    }

    private static List<String> describeAsyncProfilerTasks(final List<AsyncProfilerTask> tasks) {
        return tasks.stream()
                    .map(task -> String.join(
                        "|", task.getId(), task.getServiceId(), String.valueOf(task.getServiceInstanceIds()),
                        String.valueOf(task.getCreateTime()), String.valueOf(task.getEvents()),
                        String.valueOf(task.getDuration()), task.getExecArgs()))
                    .collect(Collectors.toList());
    }

    private static List<String> describePprofTasks(final String serviceId, final int... minutes) {
        return describePprofTasks(Arrays.stream(minutes).mapToObj(minute -> {
            final PprofTaskRecord record = pprofTask(serviceId, minute);
            return PprofTask.builder()
                            .id(record.getTaskId())
                            .serviceId(record.getServiceId())
                            .serviceInstanceIds(Collections.singletonList(INSTANCE_ID))
                            .createTime(record.getCreateTime())
                            .events(PprofEventType.CPU)
                            .duration(record.getDuration())
                            .dumpPeriod(record.getDumpPeriod())
                            .build();
        }).collect(Collectors.toList()));
    }

    private static List<String> describePprofTasks(final List<PprofTask> tasks) {
        return tasks.stream()
                    .map(task -> String.join(
                        "|", task.getId(), task.getServiceId(), String.valueOf(task.getServiceInstanceIds()),
                        String.valueOf(task.getCreateTime()), String.valueOf(task.getEvents()),
                        String.valueOf(task.getDuration()), String.valueOf(task.getDumpPeriod())))
                    .collect(Collectors.toList());
    }

    /**
     * A task log's id is the id of its task on every storage.
     */
    private static List<String> describeAsyncProfilerLogs(final List<AsyncProfilerTaskLog> logs) {
        return logs.stream()
                   .map(log -> String.join("|", log.getId(), log.getInstanceId(), log.getOperationType().name(),
                                           String.valueOf(log.getOperationTime())))
                   .collect(Collectors.toList());
    }

    private static List<String> describePprofLogs(final List<PprofTaskLog> logs) {
        return logs.stream()
                   .map(log -> String.join("|", log.getId(), log.getInstanceId(), log.getOperationType().name(),
                                           String.valueOf(log.getOperationTime())))
                   .collect(Collectors.toList());
    }

    /**
     * The data of a task in no promised order, sorted by instance and upload time.
     */
    private static List<String> describeJFR(final List<JFRProfilingDataRecord> data) {
        return data.stream()
                   .map(row -> String.join("|", row.getInstanceId(), String.valueOf(row.getUploadTime()),
                                           row.getTaskId(), row.getEventType(), String.valueOf(row.getTimeBucket()),
                                           new String(row.getDataBinary(), StandardCharsets.UTF_8)))
                   .sorted()
                   .collect(Collectors.toList());
    }

    private static List<String> describeJFR(final List<JFRProfilingDataRecord> data, final String taskId,
                                            final List<String> instanceIds, final String eventType) {
        return describeJFR(data.stream()
                               .filter(row -> row.getTaskId().equals(taskId)
                                   && instanceIds.contains(row.getInstanceId())
                                   && row.getEventType().equals(eventType))
                               .collect(Collectors.toList()));
    }

    private static List<String> describePprof(final List<PprofProfilingDataRecord> data) {
        return data.stream()
                   .map(row -> String.join("|", row.getInstanceId(), String.valueOf(row.getUploadTime()),
                                           row.getTaskId(), String.valueOf(row.getTimeBucket()),
                                           new String(row.getDataBinary(), StandardCharsets.UTF_8)))
                   .sorted()
                   .collect(Collectors.toList());
    }

    /**
     * Uploads by upload time, each with the length and the SHA-256 of its data, which is too large to compare as text.
     */
    private static <T> List<String> fingerprints(final List<T> uploads, final ToLongFunction<T> uploadTime,
                                                 final Function<T, byte[]> data) {
        return uploads.stream()
                      .sorted(Comparator.comparingLong(uploadTime))
                      .map(upload -> uploadTime.applyAsLong(upload) + "|" + data.apply(upload).length + "|"
                          + Hashing.sha256().hashBytes(data.apply(upload)))
                      .collect(Collectors.toList());
    }

    private static List<String> describePprof(final List<PprofProfilingDataRecord> data, final String taskId,
                                              final List<String> instanceIds) {
        return describePprof(data.stream()
                                 .filter(row -> row.getTaskId().equals(taskId)
                                     && instanceIds.contains(row.getInstanceId()))
                                 .collect(Collectors.toList()));
    }

    /**
     * A task as {@code ProfileTaskMutationService} writes it: its time bucket is the minute it starts.
     */
    private static ProfileTaskRecord traceProfilingTask(final String serviceId, final String endpointName,
                                                        final long createTime, final long startTime) {
        final ProfileTaskRecord task = new ProfileTaskRecord();
        task.setTaskId(createTime + Const.ID_CONNECTOR + serviceId);
        task.setServiceId(serviceId);
        task.setEndpointName(endpointName);
        task.setStartTime(startTime);
        task.setDuration(TRACE_PROFILING_MINUTES);
        task.setMinDurationThreshold(5);
        task.setDumpPeriod(10);
        task.setCreateTime(createTime);
        task.setMaxSamplingCount(9);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(startTime));
        return task;
    }

    /**
     * A task as {@code AsyncProfilerMutationService} writes it: its time bucket is the second it is created.
     */
    private static AsyncProfilerTaskRecord asyncProfilerTask(final String serviceId, final int minute) {
        return asyncProfilerTask(serviceId, createTime(minute));
    }

    private static AsyncProfilerTaskRecord asyncProfilerTask(final String serviceId, final long createTime) {
        final AsyncProfilerTaskRecord task = new AsyncProfilerTaskRecord();
        task.setTaskId(taskId(serviceId, createTime));
        task.setServiceId(serviceId);
        task.setServiceInstanceIdsFromList(Collections.singletonList(INSTANCE_ID));
        task.setDuration((int) TimeUnit.MINUTES.toSeconds(5));
        task.setEventsFromList(Arrays.asList(AsyncProfilerEventType.CPU.name(), AsyncProfilerEventType.LOCK.name()));
        task.setExecArgs("interval=10ms");
        task.setCreateTime(createTime);
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(createTime));
        return task;
    }

    /**
     * A task as {@code PprofMutationService} writes it: its time bucket is the second it is created.
     */
    private static PprofTaskRecord pprofTask(final String serviceId, final int minute) {
        return pprofTask(serviceId, createTime(minute));
    }

    private static PprofTaskRecord pprofTask(final String serviceId, final long createTime) {
        final PprofTaskRecord task = new PprofTaskRecord();
        task.setTaskId(taskId(serviceId, createTime));
        task.setServiceId(serviceId);
        task.setServiceInstanceIdsFromList(Collections.singletonList(INSTANCE_ID));
        task.setDuration(5);
        task.setEvents(PprofEventType.CPU.name());
        task.setDumpPeriod(10);
        task.setCreateTime(createTime);
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(createTime));
        return task;
    }

    private static ProfileTaskLogRecord traceProfilingLog(final ProfileTaskRecord task, final String instanceId,
                                                          final ProfileTaskLogOperationType operationType,
                                                          final long operationTime) {
        final ProfileTaskLogRecord log = new ProfileTaskLogRecord();
        log.setTaskId(task.getTaskId());
        log.setInstanceId(instanceId);
        log.setOperationType(operationType.getCode());
        log.setOperationTime(operationTime);
        log.setTimestamp(operationTime);
        log.setTimeBucket(TimeBucket.getRecordTimeBucket(operationTime));
        return log;
    }

    private static AsyncProfilerTaskLogRecord asyncProfilerLog(final String taskId, final long operationTime,
                                                               final int operationType) {
        final AsyncProfilerTaskLogRecord log = new AsyncProfilerTaskLogRecord();
        log.setTaskId(taskId);
        log.setInstanceId(INSTANCE_ID);
        log.setOperationType(operationType);
        log.setOperationTime(operationTime);
        log.setTimestamp(operationTime);
        log.setTimeBucket(TimeBucket.getRecordTimeBucket(operationTime));
        return log;
    }

    private static PprofTaskLogRecord pprofLog(final String taskId, final long operationTime,
                                               final int operationType) {
        final PprofTaskLogRecord log = new PprofTaskLogRecord();
        log.setTaskId(taskId);
        log.setInstanceId(INSTANCE_ID);
        log.setOperationType(operationType);
        log.setOperationTime(operationTime);
        log.setTimestamp(operationTime);
        log.setTimeBucket(TimeBucket.getRecordTimeBucket(operationTime));
        return log;
    }

    private static long uploadTime(final int upload) {
        return MIDNIGHT + TimeUnit.SECONDS.toMillis(upload - UPLOADS / 2);
    }

    /**
     * An upload as {@code JFRProfilingDataDispatcher} writes it, the data being the frame tree of one event.
     */
    private static JFRProfilingDataRecord jfr(final String taskId, final String instanceId, final String eventType,
                                              final int upload) {
        final JFRProfilingDataRecord data = new JFRProfilingDataRecord();
        data.setTaskId(taskId);
        data.setInstanceId(instanceId);
        data.setEventType(eventType);
        data.setUploadTime(uploadTime(upload));
        data.setDataBinary(("{\"frame\":\"" + eventType + " " + upload + "\"}").getBytes(StandardCharsets.UTF_8));
        data.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime(upload)));
        return data;
    }

    /**
     * An upload as {@code PprofProfilingDataDispatcher} writes it.
     */
    private static PprofProfilingDataRecord pprof(final String taskId, final String instanceId, final int upload) {
        final PprofProfilingDataRecord data = new PprofProfilingDataRecord();
        data.setTaskId(taskId);
        data.setInstanceId(instanceId);
        data.setUploadTime(uploadTime(upload));
        data.setDataBinary(("{\"frame\":\"pprof " + upload + "\"}").getBytes(StandardCharsets.UTF_8));
        data.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime(upload)));
        return data;
    }
}
