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

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.profiling.pprof.PprofMutationService;
import org.apache.skywalking.oap.server.core.profiling.pprof.PprofQueryService;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerEventType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskCreationType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskLogOperationType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskContinuousProfiling;
import org.apache.skywalking.oap.server.core.query.type.PprofEventType;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskCreationType;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskLogOperationType;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reads profiling tasks and their logs the way the profiling services, the mutation services and the agent task
 * caches do: task lists, a task by id, and task logs. OAP writes a task into the table of the day it creates it, so
 * the tasks created before midnight are in yesterday's table and the others in today's; a log goes into the table of
 * the day it is reported, and the logs read lie on both sides of midnight. Every filter has a near miss: a task of
 * another service or endpoint, a task created or starting just outside a time range, a log of another task. A
 * subclass names the server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCProfilingTaskQueryITBase {
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-1");
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("payment", true);
    private static final String OTHER_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-2");
    /** When the other service's async-profiler and pprof task was created, in minutes from midnight. */
    private static final int OTHER_SERVICE_CREATED_MINUTE = -15;
    /** A service with two async-profiler and two pprof tasks created in the same minute, half a minute apart. */
    private static final String SAME_MINUTE_SERVICE_ID = IDManager.ServiceID.buildId("inventory", true);
    private static final int SAME_MINUTE = 5;
    /** A service whose tasks the creation tests write as running now. */
    private static final String RUNNING_SERVICE_ID = IDManager.ServiceID.buildId("shipping", true);
    /** The async-profiler and pprof task whose logs are read, with a log on each side of midnight. */
    private static final int READ_TASK_MINUTE = -10;
    private static final int TRACE_PROFILING_MINUTES = 10;
    /** When each async-profiler and each pprof task was created, in minutes from midnight. */
    private static final int[] CREATED_MINUTES = {-30, -20, -10, 10, 20};
    private static final Gson GSON = new Gson();

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    /** Created yesterday evening to start today at 10:00. */
    private ProfileTaskRecord scheduledForToday;
    /** Created and started a minute after midnight. */
    private ProfileTaskRecord startedToday;
    /** Created today to start tomorrow at 10:00. */
    private ProfileTaskRecord scheduledForTomorrow;
    private JDBCProfileTaskQueryDAO traceProfilingDAO;
    private Duration aroundMidnight;
    /** The other service's task, started five minutes before midnight and finished after it. */
    private ProfileTaskRecord otherServiceTask;
    private Model traceProfilingLogModel;
    private Model asyncProfilerModel;
    private Model pprofModel;
    private final List<ProfileTaskLogRecord> traceProfilingLogs = new ArrayList<>();
    private final List<AsyncProfilerTaskLogRecord> asyncProfilerLogs = new ArrayList<>();
    private final List<PprofTaskLogRecord> pprofLogs = new ArrayList<>();

    JDBCProfilingTaskQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeTasks() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);

        final Model traceProfiling = support.install(
            ProfileTaskRecord.class, DefaultScopeDefine.PROFILE_TASK, ProfileTaskRecord.INDEX_NAME, DownSampling.Minute);
        scheduledForToday = traceProfilingTask(
            "/checkout", MIDNIGHT - TimeUnit.HOURS.toMillis(2), MIDNIGHT + TimeUnit.HOURS.toMillis(10));
        startedToday = traceProfilingTask("/pay", MIDNIGHT + 60_000L, MIDNIGHT + 60_000L);
        scheduledForTomorrow = traceProfilingTask(
            "/checkout", MIDNIGHT + 120_000L, MIDNIGHT + TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(10));
        for (final ProfileTaskRecord task : Arrays.asList(scheduledForToday, startedToday, scheduledForTomorrow)) {
            support.writeNoneStream(traceProfiling, new ProfileTaskRecord.Builder(), task, task.getCreateTime());
        }
        traceProfilingDAO = new JDBCProfileTaskQueryDAO(support.getClient(), support.getModuleManager());

        final Model asyncProfiler = support.install(
            AsyncProfilerTaskRecord.class, DefaultScopeDefine.ASYNC_PROFILER_TASK, AsyncProfilerTaskRecord.INDEX_NAME,
            DownSampling.Minute);
        final Model pprof = support.install(
            PprofTaskRecord.class, DefaultScopeDefine.PPROF_TASK, PprofTaskRecord.INDEX_NAME, DownSampling.Minute);
        for (final int minute : CREATED_MINUTES) {
            final long createTime = MIDNIGHT + minute * 60_000L;
            support.writeNoneStream(
                asyncProfiler, new AsyncProfilerTaskRecord.Builder(), asyncProfilerTask(createTime), createTime);
            support.writeNoneStream(pprof, new PprofTaskRecord.Builder(), pprofTask(createTime), createTime);
        }
        writeOtherServiceTasksAndLogs(traceProfiling, asyncProfiler, pprof);

        final Model ebpf = support.install(
            EBPFProfilingTaskRecord.class, DefaultScopeDefine.EBPF_PROFILING_TASK, EBPFProfilingTaskRecord.INDEX_NAME,
            DownSampling.Minute);
        for (final EBPFProfilingTaskRecord ebpfTask : Arrays.asList(
            continuousProfilingTask(EBPFProfilingTargetType.ON_CPU, MIDNIGHT + 30_000L),
            continuousProfilingTask(EBPFProfilingTargetType.OFF_CPU, MIDNIGHT + 60_000L))) {
            support.writeNoneStream(ebpf, new EBPFProfilingTaskRecord.Builder(), ebpfTask, ebpfTask.getCreateTime());
        }

        aroundMidnight = JDBCQueryITSupport.duration(
            MIDNIGHT - TimeUnit.HOURS.toMillis(1), MIDNIGHT + TimeUnit.HOURS.toMillis(1), Step.MINUTE);
    }

    /**
     * A task of another service, started five minutes before midnight, and the logs of the tasks: the other
     * service's task is notified before midnight and finishes after it, the task started today is notified to two
     * instances. The async-profiler and pprof task read has a log on each side of midnight, and the tasks before
     * and after it have logs of the same instance.
     */
    private void writeOtherServiceTasksAndLogs(final Model traceProfiling, final Model asyncProfiler,
                                               final Model pprof) throws Exception {
        asyncProfilerModel = asyncProfiler;
        pprofModel = pprof;
        otherServiceTask = traceProfilingTask(
            OTHER_SERVICE_ID, "/checkout", MIDNIGHT - TimeUnit.MINUTES.toMillis(5),
            MIDNIGHT - TimeUnit.MINUTES.toMillis(5));
        support.writeNoneStream(
            traceProfiling, new ProfileTaskRecord.Builder(), otherServiceTask, otherServiceTask.getCreateTime());
        final long otherCreateTime = createTime(OTHER_SERVICE_CREATED_MINUTE);
        support.writeNoneStream(asyncProfiler, new AsyncProfilerTaskRecord.Builder(),
                                asyncProfilerTask(OTHER_SERVICE_ID, otherCreateTime), otherCreateTime);
        support.writeNoneStream(
            pprof, new PprofTaskRecord.Builder(), pprofTask(OTHER_SERVICE_ID, otherCreateTime), otherCreateTime);
        for (final long createTime : sameMinuteCreateTimes()) {
            support.writeNoneStream(asyncProfiler, new AsyncProfilerTaskRecord.Builder(),
                                    asyncProfilerTask(SAME_MINUTE_SERVICE_ID, createTime), createTime);
            support.writeNoneStream(
                pprof, new PprofTaskRecord.Builder(), pprofTask(SAME_MINUTE_SERVICE_ID, createTime), createTime);
        }

        traceProfilingLogs.add(traceProfilingLog(otherServiceTask, INSTANCE_ID, ProfileTaskLogOperationType.NOTIFIED,
                                                 createTime(-4)));
        traceProfilingLogs.add(traceProfilingLog(otherServiceTask, INSTANCE_ID,
                                                 ProfileTaskLogOperationType.EXECUTION_FINISHED, createTime(6)));
        traceProfilingLogs.add(traceProfilingLog(startedToday, INSTANCE_ID, ProfileTaskLogOperationType.NOTIFIED,
                                                 MIDNIGHT + 70_000L));
        traceProfilingLogs.add(traceProfilingLog(startedToday, OTHER_INSTANCE_ID,
                                                 ProfileTaskLogOperationType.NOTIFIED, MIDNIGHT + 80_000L));
        traceProfilingLogModel = support.install(
            ProfileTaskLogRecord.class, DefaultScopeDefine.PROFILE_TASK_LOG, ProfileTaskLogRecord.INDEX_NAME,
            DownSampling.Second);
        support.writeRecords(traceProfilingLogModel, new ProfileTaskLogRecord.Builder(), traceProfilingLogs);

        // Each log: the minute its task was created, the minute it is reported, its operation type.
        for (final int[] log : new int[][] {
            {READ_TASK_MINUTE, -9, 1}, {READ_TASK_MINUTE, 1, 2}, {-20, -19, 1}, {-20, -14, 4}, {10, 11, 1}}) {
            asyncProfilerLogs.add(asyncProfilerLog(taskId(log[0]), createTime(log[1]), log[2]));
            pprofLogs.add(pprofLog(taskId(log[0]), createTime(log[1]), log[2]));
        }
        support.writeRecords(
            support.install(AsyncProfilerTaskLogRecord.class, DefaultScopeDefine.ASYNC_PROFILER_TASK_LOG,
                            AsyncProfilerTaskLogRecord.INDEX_NAME, DownSampling.Second),
            new AsyncProfilerTaskLogRecord.Builder(), asyncProfilerLogs);
        support.writeRecords(
            support.install(PprofTaskLogRecord.class, DefaultScopeDefine.PPROF_TASK_LOG, PprofTaskLogRecord.INDEX_NAME,
                            DownSampling.Second),
            new PprofTaskLogRecord.Builder(), pprofLogs);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * The async-profiler task list of a time range, cut to a limit, is the newest tasks of both day tables ranked
     * together.
     */
    @Test
    public void listsTheNewestAsyncProfilerTasksAcrossMidnight() throws Exception {
        final JDBCAsyncProfilerTaskQueryDAO dao =
            new JDBCAsyncProfilerTaskQueryDAO(support.getClient(), support.getTableHelper());
        final long start = aroundMidnight.getStartTimeBucketInSec();
        final long end = aroundMidnight.getEndTimeBucketInSec();
        assertEquals(Arrays.asList(20, 10),
                     createdMinutes(dao.getTaskList(SERVICE_ID, start, end, 2), AsyncProfilerTask::getCreateTime));
        assertEquals(Arrays.asList(20, 10, -10),
                     createdMinutes(dao.getTaskList(SERVICE_ID, start, end, 3), AsyncProfilerTask::getCreateTime));
    }

    @Test
    public void listsTheNewestPprofTasksAcrossMidnight() throws Exception {
        final JDBCPprofTaskQueryDAO dao = new JDBCPprofTaskQueryDAO(support.getClient(), support.getTableHelper());
        final long start = aroundMidnight.getStartTimeBucketInSec();
        final long end = aroundMidnight.getEndTimeBucketInSec();
        assertEquals(Arrays.asList(20, 10),
                     createdMinutes(dao.getTaskList(SERVICE_ID, start, end, 2), PprofTask::getCreateTime));
        assertEquals(Arrays.asList(20, 10, -10),
                     createdMinutes(dao.getTaskList(SERVICE_ID, start, end, 3), PprofTask::getCreateTime));
    }

    /**
     * The task list of a service is latest start first across both day tables: the task created yesterday for today
     * comes before the task started today, though it is in yesterday's table.
     */
    @Test
    public void listsTraceProfilingTasksLatestStartFirst() {
        assertEquals(ids(scheduledForTomorrow, scheduledForToday, startedToday),
                     ids(traceProfilingDAO.getTaskList(SERVICE_ID, null, null, null, null)));
    }

    /**
     * Creating a task checks the latest-starting task that starts before the new one ends for an overlap. A new task
     * five minutes into the one scheduled for today must find that one, not the one started after midnight.
     */
    @Test
    public void checksANewTraceProfilingTaskAgainstTheLatestStartingTask() {
        final long newTaskEnd = scheduledForToday.getStartTime()
            + TimeUnit.MINUTES.toMillis(5 + TRACE_PROFILING_MINUTES);
        assertEquals(ids(scheduledForToday), ids(traceProfilingDAO.getTaskList(
            SERVICE_ID, null, null, TimeBucket.getMinuteTimeBucket(newTaskEnd), 1)));
    }

    /**
     * The agent task cache reads the tasks that start within five minutes of now. A task is in the table of the day
     * it was created while its time bucket is its start, so the task created today for tomorrow is in today's table.
     */
    @Test
    public void findsATaskCreatedTodayForALaterDayAroundItsStart() {
        assertEquals(ids(scheduledForTomorrow), ids(readAroundStart(scheduledForTomorrow)));
    }

    /**
     * The same read on the day a task is scheduled for, as the cache makes it today for the task created yesterday.
     */
    @Test
    public void findsATaskCreatedYesterdayForTodayAroundItsStart() {
        assertEquals(ids(scheduledForToday), ids(readAroundStart(scheduledForToday)));
    }

    /**
     * A continuous-profiling policy saved without targets asks for the tasks of no target type.
     */
    @Test
    public void readsNoContinuousProfilingTaskForAPolicyWithoutTargets() throws Exception {
        final JDBCEBPFProfilingTaskDAO dao = new JDBCEBPFProfilingTaskDAO(support.getClient(), support.getTableHelper());
        final long triggeredSince = MIDNIGHT - TimeUnit.HOURS.toMillis(48);
        final List<EBPFProfilingTargetType> policyTargets =
            Arrays.asList(EBPFProfilingTargetType.ON_CPU, EBPFProfilingTargetType.OFF_CPU);
        assertEquals(Arrays.asList(EBPFProfilingTargetType.ON_CPU.value(), EBPFProfilingTargetType.OFF_CPU.value()),
                     dao.queryTasksByTargets(SERVICE_ID, null, policyTargets,
                                             EBPFProfilingTriggerType.CONTINUOUS_PROFILING, triggeredSince, 0)
                        .stream()
                        .map(EBPFProfilingTaskRecord::getTargetType)
                        .sorted()
                        .collect(Collectors.toList()));
        assertEquals(Collections.emptyList(),
                     dao.queryTasksByTargets(SERVICE_ID, null, Collections.emptyList(),
                                             EBPFProfilingTriggerType.CONTINUOUS_PROFILING, triggeredSince, 0));
    }

    @Test
    public void listsTheTraceProfilingTasksOfAnEndpoint() {
        assertEquals(ids(scheduledForTomorrow, scheduledForToday),
                     ids(traceProfilingDAO.getTaskList(SERVICE_ID, "/checkout", null, null, null)));
    }

    /**
     * The agent task cache reads the tasks of every service starting within a window of minute buckets, both ends
     * included.
     */
    @Test
    public void readsTheTraceProfilingTasksOfEveryServiceStartingWithinAWindow() {
        final long first = otherServiceTask.getStartTime();
        final long last = startedToday.getStartTime();
        assertEquals(describe(startedToday, otherServiceTask), describe(traceProfilingDAO.getTaskList(
            null, null, TimeBucket.getMinuteTimeBucket(first), TimeBucket.getMinuteTimeBucket(last), null)));
        assertEquals(Collections.emptyList(), traceProfilingDAO.getTaskList(
            null, null, TimeBucket.getMinuteTimeBucket(first + 60_000L), TimeBucket.getMinuteTimeBucket(last - 60_000L),
            null));
    }

    /**
     * The analysis reads a task by id: one created yesterday is in yesterday's table, though it starts today.
     */
    @Test
    public void readsATraceProfilingTaskById() {
        assertEquals(describe(scheduledForToday),
                     describe(Collections.singletonList(traceProfilingDAO.getById(scheduledForToday.getTaskId()))));
        assertEquals(describe(scheduledForTomorrow), describe(
            Collections.singletonList(traceProfilingDAO.getById(scheduledForTomorrow.getTaskId()))));
        assertNull(traceProfilingDAO.getById(MIDNIGHT + Const.ID_CONNECTOR + SERVICE_ID));
        assertNull(traceProfilingDAO.getById(""));
    }

    /**
     * The trace-profiling service reads the logs of every task and matches them to the tasks it lists.
     */
    @Test
    public void readsTheLogsOfEveryTraceProfilingTaskNewestFirst() throws Exception {
        final JDBCProfileTaskLogQueryDAO dao =
            new JDBCProfileTaskLogQueryDAO(support.getClient(), support.getTableHelper());
        final List<String> expected = traceProfilingLogs.stream()
            .sorted(Comparator.comparingLong(ProfileTaskLogRecord::getOperationTime).reversed())
            .map(log -> String.join(
                "|", TableHelper.generateId(traceProfilingLogModel, log.id().build()), log.getTaskId(),
                log.getInstanceId(), ProfileTaskLogOperationType.parse(log.getOperationType()).name(),
                String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList());
        assertEquals(expected, dao.getTaskLogList().stream()
            .map(log -> String.join("|", log.getId(), log.getTaskId(), log.getInstanceId(),
                                    log.getOperationType().name(), String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList()));
    }

    /**
     * The task list of a duration bounds the tasks' second buckets, both ends included; the agent task cache reads
     * the tasks of every service; creating a task checks the newest task of the service created up to now.
     */
    @Test
    public void listsTheAsyncProfilerTasksCreatedWithinADuration() throws Exception {
        final JDBCAsyncProfilerTaskQueryDAO dao =
            new JDBCAsyncProfilerTaskQueryDAO(support.getClient(), support.getTableHelper());
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, 10, -10, -20), describeAsyncProfilerTasks(
            dao.getTaskList(SERVICE_ID, secondBucket(-20, 0), secondBucket(10, 0), null)));
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, -10), describeAsyncProfilerTasks(
            dao.getTaskList(SERVICE_ID, secondBucket(-20, 1), secondBucket(10, -1), null)));
        final List<String> cached = new ArrayList<>(describeAsyncProfilerTasks(SERVICE_ID, -10));
        cached.addAll(describeAsyncProfilerTasks(OTHER_SERVICE_ID, OTHER_SERVICE_CREATED_MINUTE));
        cached.addAll(describeAsyncProfilerTasks(SERVICE_ID, -20));
        assertEquals(cached, describeAsyncProfilerTasks(
            dao.getTaskList(null, secondBucket(-20, 0), secondBucket(-10, 0), null)));
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, -10), describeAsyncProfilerTasks(
            dao.getTaskList(SERVICE_ID, null, secondBucket(10, -1), 1)));
    }

    @Test
    public void readsAnAsyncProfilerTaskById() throws Exception {
        final JDBCAsyncProfilerTaskQueryDAO dao =
            new JDBCAsyncProfilerTaskQueryDAO(support.getClient(), support.getTableHelper());
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, -30),
                     describeAsyncProfilerTasks(Collections.singletonList(dao.getById(taskId(-30)))));
        assertEquals(describeAsyncProfilerTasks(SERVICE_ID, 20),
                     describeAsyncProfilerTasks(Collections.singletonList(dao.getById(taskId(20)))));
        assertNull(dao.getById(taskId(0)));
    }

    /**
     * A task log's id is the id of its task.
     */
    @Test
    public void readsTheLogsOfOneAsyncProfilerTaskNewestFirst() throws Exception {
        final JDBCAsyncProfilerTaskLogQueryDAO dao =
            new JDBCAsyncProfilerTaskLogQueryDAO(support.getClient(), support.getTableHelper());
        final String task = taskId(READ_TASK_MINUTE);
        final List<String> expected = asyncProfilerLogs.stream()
            .filter(log -> log.getTaskId().equals(task))
            .sorted(Comparator.comparingLong(AsyncProfilerTaskLogRecord::getOperationTime).reversed())
            .map(log -> String.join("|", log.getTaskId(), log.getInstanceId(),
                                    AsyncProfilerTaskLogOperationType.parse(log.getOperationType()).name(),
                                    String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList());
        assertEquals(2, expected.size());
        assertEquals(expected, dao.getTaskLogList(task).stream()
            .map(log -> String.join("|", log.getId(), log.getInstanceId(), log.getOperationType().name(),
                                    String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList()));
        assertEquals(Collections.emptyList(), dao.getTaskLogList(taskId(0)));
    }

    @Test
    public void listsThePprofTasksCreatedWithinADuration() throws Exception {
        final JDBCPprofTaskQueryDAO dao = new JDBCPprofTaskQueryDAO(support.getClient(), support.getTableHelper());
        assertEquals(describePprofTasks(SERVICE_ID, 10, -10, -20), describePprofTasks(
            dao.getTaskList(SERVICE_ID, secondBucket(-20, 0), secondBucket(10, 0), null)));
        assertEquals(describePprofTasks(SERVICE_ID, -10), describePprofTasks(
            dao.getTaskList(SERVICE_ID, secondBucket(-20, 1), secondBucket(10, -1), null)));
        final List<String> cached = new ArrayList<>(describePprofTasks(SERVICE_ID, -10));
        cached.addAll(describePprofTasks(OTHER_SERVICE_ID, OTHER_SERVICE_CREATED_MINUTE));
        cached.addAll(describePprofTasks(SERVICE_ID, -20));
        assertEquals(cached, describePprofTasks(
            dao.getTaskList(null, secondBucket(-20, 0), secondBucket(-10, 0), null)));
        assertEquals(describePprofTasks(SERVICE_ID, -10, -20, -30), describePprofTasks(
            dao.getTaskList(SERVICE_ID, null, secondBucket(10, -1), null)));
    }

    /**
     * The GraphQL task lists accept a duration of seconds, and the query services bound the tasks by its seconds: of
     * two tasks created in the same minute, a duration ending between them, with a limit of one, lists the first.
     */
    @Test
    public void listsTheTasksOfADurationOfSecondsExactly() throws Exception {
        final ModuleManager moduleManager = storageServices();
        final AsyncProfilerQueryService async = new AsyncProfilerQueryService(moduleManager);
        final PprofQueryService pprof = new PprofQueryService(moduleManager);
        final long[] created = sameMinuteCreateTimes();
        final long minuteStart = createTime(SAME_MINUTE);
        final List<String> first = Collections.singletonList(created[0] + Const.ID_CONNECTOR + SAME_MINUTE_SERVICE_ID);
        final List<String> both = Arrays.asList(created[1] + Const.ID_CONNECTOR + SAME_MINUTE_SERVICE_ID,
                                                created[0] + Const.ID_CONNECTOR + SAME_MINUTE_SERVICE_ID);
        final Duration endingBetween = JDBCQueryITSupport.duration(minuteStart, minuteStart + 30_000L, Step.SECOND);
        assertEquals(first, asyncProfilerIds(async.queryTask(SAME_MINUTE_SERVICE_ID, endingBetween, 1)));
        assertEquals(first, pprofIds(pprof.queryTask(SAME_MINUTE_SERVICE_ID, endingBetween, 1)));
        final Duration onBoth = JDBCQueryITSupport.duration(created[0], created[1], Step.SECOND);
        assertEquals(both, asyncProfilerIds(async.queryTask(SAME_MINUTE_SERVICE_ID, onBoth, null)));
        assertEquals(both, pprofIds(pprof.queryTask(SAME_MINUTE_SERVICE_ID, onBoth, null)));
        final Duration between = JDBCQueryITSupport.duration(created[0] + 1_000L, created[1] - 1_000L, Step.SECOND);
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
                                asyncProfilerTask(RUNNING_SERVICE_ID, createTime), createTime);
        support.writeNoneStream(
            pprofModel, new PprofTaskRecord.Builder(), pprofTask(RUNNING_SERVICE_ID, createTime), createTime);
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

    @Test
    public void readsAPprofTaskById() throws Exception {
        final JDBCPprofTaskQueryDAO dao = new JDBCPprofTaskQueryDAO(support.getClient(), support.getTableHelper());
        assertEquals(describePprofTasks(SERVICE_ID, -30),
                     describePprofTasks(Collections.singletonList(dao.getById(taskId(-30)))));
        assertEquals(describePprofTasks(SERVICE_ID, 20),
                     describePprofTasks(Collections.singletonList(dao.getById(taskId(20)))));
        assertNull(dao.getById(taskId(0)));
    }

    @Test
    public void readsTheLogsOfOnePprofTaskNewestFirst() throws Exception {
        final JDBCPprofTaskLogQueryDAO dao =
            new JDBCPprofTaskLogQueryDAO(support.getClient(), support.getTableHelper());
        final String task = taskId(READ_TASK_MINUTE);
        final List<String> expected = pprofLogs.stream()
            .filter(log -> log.getTaskId().equals(task))
            .sorted(Comparator.comparingLong(PprofTaskLogRecord::getOperationTime).reversed())
            .map(log -> String.join("|", log.getTaskId(), log.getInstanceId(),
                                    PprofTaskLogOperationType.parse(log.getOperationType()).name(),
                                    String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList());
        assertEquals(2, expected.size());
        assertEquals(expected, dao.getTaskLogList(task).stream()
            .map(log -> String.join("|", log.getId(), log.getInstanceId(), log.getOperationType().name(),
                                    String.valueOf(log.getOperationTime())))
            .collect(Collectors.toList()));
        assertEquals(Collections.emptyList(), dao.getTaskLogList(taskId(0)));
        assertEquals(Collections.emptyList(), dao.getTaskLogList(""));
    }

    private List<ProfileTask> readAroundStart(final ProfileTaskRecord task) {
        final long start = task.getStartTime();
        return traceProfilingDAO.getTaskList(
            null, null,
            TimeBucket.getMinuteTimeBucket(start - TimeUnit.MINUTES.toMillis(5)),
            TimeBucket.getMinuteTimeBucket(start + TimeUnit.MINUTES.toMillis(5)), null);
    }

    private static <T> List<Integer> createdMinutes(final List<T> tasks, final ToLongFunction<T> createTime) {
        return tasks.stream()
                    .map(task -> (int) ((createTime.applyAsLong(task) - MIDNIGHT) / 60_000L))
                    .collect(Collectors.toList());
    }

    private static List<String> ids(final ProfileTaskRecord... tasks) {
        return Arrays.stream(tasks).map(ProfileTaskRecord::getTaskId).collect(Collectors.toList());
    }

    private static List<String> ids(final List<ProfileTask> tasks) {
        return tasks.stream().map(ProfileTask::getId).collect(Collectors.toList());
    }

    private static ProfileTaskRecord traceProfilingTask(final String endpointName, final long createTime,
                                                        final long startTime) {
        return traceProfilingTask(SERVICE_ID, endpointName, createTime, startTime);
    }

    private static ProfileTaskRecord traceProfilingTask(final String serviceId, final String endpointName,
                                                        final long createTime, final long startTime) {
        final ProfileTaskRecord task = new ProfileTaskRecord();
        task.setTaskId(createTime + Const.ID_CONNECTOR + serviceId);
        task.setServiceId(serviceId);
        task.setEndpointName(endpointName);
        task.setStartTime(startTime);
        task.setDuration(TRACE_PROFILING_MINUTES);
        task.setMinDurationThreshold(0);
        task.setDumpPeriod(10);
        task.setCreateTime(createTime);
        task.setMaxSamplingCount(5);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(startTime));
        return task;
    }

    private static AsyncProfilerTaskRecord asyncProfilerTask(final long createTime) {
        return asyncProfilerTask(SERVICE_ID, createTime);
    }

    private static AsyncProfilerTaskRecord asyncProfilerTask(final String serviceId, final long createTime) {
        final AsyncProfilerTaskRecord task = new AsyncProfilerTaskRecord();
        task.setTaskId(createTime + Const.ID_CONNECTOR + serviceId);
        task.setServiceId(serviceId);
        task.setServiceInstanceIdsFromList(Collections.singletonList(INSTANCE_ID));
        task.setDuration((int) TimeUnit.MINUTES.toSeconds(5));
        task.setEventsFromList(Collections.singletonList(AsyncProfilerEventType.CPU.toString()));
        task.setExecArgs("interval=10ms");
        task.setCreateTime(createTime);
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(createTime));
        return task;
    }

    private static PprofTaskRecord pprofTask(final long createTime) {
        return pprofTask(SERVICE_ID, createTime);
    }

    private static PprofTaskRecord pprofTask(final String serviceId, final long createTime) {
        final PprofTaskRecord task = new PprofTaskRecord();
        task.setTaskId(createTime + Const.ID_CONNECTOR + serviceId);
        task.setServiceId(serviceId);
        task.setServiceInstanceIdsFromList(Collections.singletonList(INSTANCE_ID));
        task.setDuration(5);
        task.setEvents(PprofEventType.CPU.toString());
        task.setDumpPeriod(0);
        task.setCreateTime(createTime);
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(createTime));
        return task;
    }

    /**
     * A task as the eBPF agent reports one when a continuous-profiling policy triggers on its process.
     */
    private static EBPFProfilingTaskRecord continuousProfilingTask(final EBPFProfilingTargetType targetType,
                                                                   final long time) {
        final EBPFProfilingTaskContinuousProfiling continuousProfiling = new EBPFProfilingTaskContinuousProfiling();
        continuousProfiling.setProcessId(IDManager.ProcessID.buildId(INSTANCE_ID, "checkout"));
        continuousProfiling.setProcessName("checkout");
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(SERVICE_ID);
        task.setProcessLabelsJson(Const.EMPTY_STRING);
        task.setInstanceId(INSTANCE_ID);
        task.setStartTime(time);
        task.setTriggerType(EBPFProfilingTriggerType.CONTINUOUS_PROFILING.value());
        task.setFixedTriggerDuration(60);
        task.setTargetType(targetType.value());
        task.setCreateTime(time);
        task.setLastUpdateTime(time);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(time));
        task.setContinuousProfilingJson(GSON.toJson(continuousProfiling));
        task.generateLogicalId();
        return task;
    }

    private static long createTime(final int minute) {
        return MIDNIGHT + TimeUnit.MINUTES.toMillis(minute);
    }

    private static long[] sameMinuteCreateTimes() {
        return new long[] {createTime(SAME_MINUTE) + 10_000L, createTime(SAME_MINUTE) + 40_000L};
    }

    private static List<String> asyncProfilerIds(final List<AsyncProfilerTask> tasks) {
        return tasks.stream().map(AsyncProfilerTask::getId).collect(Collectors.toList());
    }

    private static List<String> pprofIds(final List<PprofTask> tasks) {
        return tasks.stream().map(PprofTask::getId).collect(Collectors.toList());
    }

    /**
     * Hands the profiling services this storage's task DAOs, as the storage provider registers them.
     */
    private ModuleManager storageServices() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder provider = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(provider);
        when(provider.provider()).thenReturn(services);
        when(services.getService(IAsyncProfilerTaskQueryDAO.class)).thenReturn(
            new JDBCAsyncProfilerTaskQueryDAO(support.getClient(), support.getTableHelper()));
        when(services.getService(IPprofTaskQueryDAO.class)).thenReturn(
            new JDBCPprofTaskQueryDAO(support.getClient(), support.getTableHelper()));
        return moduleManager;
    }

    private static long secondBucket(final int minute, final int seconds) {
        return TimeBucket.getRecordTimeBucket(createTime(minute) + TimeUnit.SECONDS.toMillis(seconds));
    }

    /**
     * The id the mutation services give a task of the service created at the minute.
     */
    private static String taskId(final int minute) {
        return createTime(minute) + Const.ID_CONNECTOR + SERVICE_ID;
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
            final AsyncProfilerTaskRecord record = asyncProfilerTask(serviceId, createTime(minute));
            return AsyncProfilerTask.builder()
                                    .id(record.getTaskId())
                                    .serviceId(record.getServiceId())
                                    .serviceInstanceIds(Collections.singletonList(INSTANCE_ID))
                                    .createTime(record.getCreateTime())
                                    .events(Collections.singletonList(AsyncProfilerEventType.CPU))
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
            final PprofTaskRecord record = pprofTask(serviceId, createTime(minute));
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
}
