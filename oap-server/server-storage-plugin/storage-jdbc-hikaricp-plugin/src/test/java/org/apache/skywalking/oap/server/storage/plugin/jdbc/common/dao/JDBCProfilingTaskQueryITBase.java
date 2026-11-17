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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerEventType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskContinuousProfiling;
import org.apache.skywalking.oap.server.core.query.type.PprofEventType;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads profiling tasks the way the profiling services and the agent task caches do. OAP writes a task into the
 * table of the day it creates it, so the tasks created before midnight are in yesterday's table and the others in
 * today's. A subclass names the server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCProfilingTaskQueryITBase {
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-1");
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
        final ProfileTaskRecord task = new ProfileTaskRecord();
        task.setTaskId(createTime + Const.ID_CONNECTOR + SERVICE_ID);
        task.setServiceId(SERVICE_ID);
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
        final AsyncProfilerTaskRecord task = new AsyncProfilerTaskRecord();
        task.setTaskId(createTime + Const.ID_CONNECTOR + SERVICE_ID);
        task.setServiceId(SERVICE_ID);
        task.setServiceInstanceIdsFromList(Collections.singletonList(INSTANCE_ID));
        task.setDuration((int) TimeUnit.MINUTES.toSeconds(5));
        task.setEventsFromList(Collections.singletonList(AsyncProfilerEventType.CPU.toString()));
        task.setCreateTime(createTime);
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(createTime));
        return task;
    }

    private static PprofTaskRecord pprofTask(final long createTime) {
        final PprofTaskRecord task = new PprofTaskRecord();
        task.setTaskId(createTime + Const.ID_CONNECTOR + SERVICE_ID);
        task.setServiceId(SERVICE_ID);
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
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
        task.setContinuousProfilingJson(GSON.toJson(continuousProfiling));
        task.generateLogicalId();
        return task;
    }
}
