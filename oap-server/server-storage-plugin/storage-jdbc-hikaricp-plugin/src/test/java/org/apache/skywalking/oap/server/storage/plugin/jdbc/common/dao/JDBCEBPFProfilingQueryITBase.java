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
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskContinuousProfiling;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads eBPF profiling tasks, schedules and data the way the eBPF receiver, the profiling query and mutation
 * services and the profiling analysis do, from tables OAP installed and rows OAP's write paths stored. A subclass
 * names the server.
 *
 * <p>The tasks are created on both sides of midnight, so in two day tables: fixed-time tasks of two services, a
 * network task of an instance with the copy that extends it after midnight (a second row with the same logical id),
 * and continuous-profiling tasks of two instances. Each filter has a near miss, and the start and update times lie
 * on and just outside the bounds asked for. The schedules of a task start on both sides of midnight beside a
 * schedule of another task. The eBPF data of a schedule is uploaded every half second through a window that spans
 * midnight, with uploads just before the window and on its end, beside the uploads of other schedules.
 *
 * <p>The task and schedule DAOs promise no order, and their callers combine and sort the rows themselves, so rows
 * are compared regardless of order.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCEBPFProfilingQueryITBase {
    private static final Gson GSON = new Gson();
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("payment", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-1");
    private static final String OTHER_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-2");

    private static final EBPFProfilingTaskRecord ON_CPU = fixedTimeTask(
        SERVICE_ID, Collections.emptyList(), EBPFProfilingTargetType.ON_CPU, minutes(-30), minutes(-30));
    /** Created five minutes after midnight to start five minutes later, for processes with a label. */
    private static final EBPFProfilingTaskRecord OFF_CPU = fixedTimeTask(
        SERVICE_ID, Collections.singletonList("env:prod"), EBPFProfilingTargetType.OFF_CPU, minutes(5), minutes(10));
    private static final EBPFProfilingTaskRecord OTHER_SERVICE_ON_CPU = fixedTimeTask(
        OTHER_SERVICE_ID, Collections.emptyList(), EBPFProfilingTargetType.ON_CPU, minutes(-10), minutes(-10));
    private static final EBPFProfilingTaskRecord NETWORK = networkTask(INSTANCE_ID, minutes(-20));
    private static final EBPFProfilingTaskRecord NETWORK_EXTENDED = extended(NETWORK, minutes(1));
    private static final EBPFProfilingTaskRecord CONTINUOUS_ON_CPU = continuousProfilingTask(
        INSTANCE_ID, EBPFProfilingTargetType.ON_CPU, minutes(-5));
    private static final EBPFProfilingTaskRecord CONTINUOUS_OFF_CPU = continuousProfilingTask(
        OTHER_INSTANCE_ID, EBPFProfilingTargetType.OFF_CPU, minutes(3));
    private static final List<EBPFProfilingTaskRecord> TASKS = Arrays.asList(
        ON_CPU, OFF_CPU, OTHER_SERVICE_ON_CPU, NETWORK, NETWORK_EXTENDED, CONTINUOUS_ON_CPU, CONTINUOUS_OFF_CPU);
    private static final List<EBPFProfilingTargetType> ALL_TARGETS = Arrays.asList(EBPFProfilingTargetType.values());
    private static final List<EBPFProfilingTargetType> CPU =
        Arrays.asList(EBPFProfilingTargetType.ON_CPU, EBPFProfilingTargetType.OFF_CPU);

    private static final EBPFProfilingScheduleRecord FIRST_SCHEDULE =
        schedule(NETWORK.getLogicalId(), INSTANCE_ID, "checkout", minutes(-15), minutes(-14));
    /** Started before midnight and still running after it, so stored in today's table. */
    private static final EBPFProfilingScheduleRecord ACROSS_MIDNIGHT_SCHEDULE =
        schedule(NETWORK.getLogicalId(), INSTANCE_ID, "checkout-worker", minutes(-3), minutes(2));
    private static final EBPFProfilingScheduleRecord LAST_SCHEDULE =
        schedule(NETWORK.getLogicalId(), INSTANCE_ID, "checkout", minutes(5), minutes(6));
    private static final EBPFProfilingScheduleRecord OTHER_TASK_SCHEDULE =
        schedule(ON_CPU.getLogicalId(), INSTANCE_ID, "checkout", minutes(-1), minutes(0));

    private static final String SCHEDULE = "schedule-read";
    private static final String OTHER_SCHEDULE = "schedule-also-read";
    private static final String UNASKED_SCHEDULE = "schedule-not-read";
    private static final long WINDOW_BEGIN = MIDNIGHT - 5_000L;
    private static final long WINDOW_END = MIDNIGHT + 5_000L;
    private static final int UPLOADS = 20;
    private static final int STACKS_PER_UPLOAD = 3;

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCEBPFProfilingTaskDAO taskDAO;
    private JDBCEBPFProfilingScheduleDAO scheduleDAO;
    private JDBCEBPFProfilingDataDAO dataDAO;
    private final List<EBPFProfilingDataRecord> windowOfSchedule = new ArrayList<>();
    private final List<EBPFProfilingDataRecord> windowOfOtherSchedule = new ArrayList<>();
    private final List<EBPFProfilingDataRecord> beforeWindow = new ArrayList<>();
    private final List<EBPFProfilingDataRecord> onWindowEnd = new ArrayList<>();

    JDBCEBPFProfilingQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeProfiling() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);

        final Model tasks = support.install(
            EBPFProfilingTaskRecord.class, DefaultScopeDefine.EBPF_PROFILING_TASK, EBPFProfilingTaskRecord.INDEX_NAME,
            DownSampling.Minute);
        for (final EBPFProfilingTaskRecord task : TASKS) {
            support.writeNoneStream(tasks, new EBPFProfilingTaskRecord.Builder(), task, task.getCreateTime());
        }

        final Model schedules = support.install(
            EBPFProfilingScheduleRecord.class, DefaultScopeDefine.EBPF_PROFILING_SCHEDULE,
            EBPFProfilingScheduleRecord.INDEX_NAME, DownSampling.Minute);
        support.writeMetrics(schedules, new EBPFProfilingScheduleRecord.Builder(), Arrays.asList(
            ACROSS_MIDNIGHT_SCHEDULE, OTHER_TASK_SCHEDULE, FIRST_SCHEDULE, LAST_SCHEDULE));

        for (int upload = 0; upload < UPLOADS; upload++) {
            for (int stack = 0; stack < STACKS_PER_UPLOAD; stack++) {
                windowOfSchedule.add(data(SCHEDULE, upload + "-" + stack, WINDOW_BEGIN + upload * 500L));
            }
        }
        for (int stack = 0; stack < STACKS_PER_UPLOAD; stack++) {
            beforeWindow.add(data(SCHEDULE, "before-" + stack, WINDOW_BEGIN - 1));
            onWindowEnd.add(data(SCHEDULE, "end-" + stack, WINDOW_END));
            windowOfOtherSchedule.add(data(OTHER_SCHEDULE, "other-" + stack, MIDNIGHT - 1_000L));
            windowOfOtherSchedule.add(data(OTHER_SCHEDULE, "other-later-" + stack, MIDNIGHT + 1_000L));
        }
        final List<EBPFProfilingDataRecord> data = new ArrayList<>(windowOfSchedule);
        data.addAll(windowOfOtherSchedule);
        data.addAll(beforeWindow);
        data.addAll(onWindowEnd);
        data.add(data(UNASKED_SCHEDULE, "unasked", MIDNIGHT));
        final Model dataModel = support.install(
            EBPFProfilingDataRecord.class, DefaultScopeDefine.EBPF_PROFILING_DATA, EBPFProfilingDataRecord.INDEX_NAME,
            DownSampling.Second);
        support.writeRecords(dataModel, new EBPFProfilingDataRecord.Builder(), data);

        taskDAO = new JDBCEBPFProfilingTaskDAO(support.getClient(), support.getTableHelper());
        scheduleDAO = new JDBCEBPFProfilingScheduleDAO(support.getClient(), support.getTableHelper());
        dataDAO = new JDBCEBPFProfilingDataDAO(support.getClient(), support.getTableHelper());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * The rover asks for the fixed-time tasks of the services of its processes: neither the tasks of another
     * service nor the continuous-profiling tasks, which the rover triggers itself.
     */
    @Test
    public void readsTheFixedTimeTasksOfTheRoversServices() throws Exception {
        assertEquals(describe(ON_CPU, OFF_CPU, NETWORK, NETWORK_EXTENDED), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, 0, 0)));
        assertEquals(describe(TASKS), describe(taskDAO.queryTasksByServices(
            Arrays.asList(SERVICE_ID, OTHER_SERVICE_ID), null, 0, 0)));
    }

    /**
     * The rover asks for the tasks updated after its last poll: a task updated at that time is not read again, and
     * the copy extending the network task after midnight is.
     */
    @Test
    public void readsTheTasksUpdatedAfterTheRoversLastPoll() throws Exception {
        assertEquals(describe(OFF_CPU, NETWORK_EXTENDED), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, 0,
            NETWORK.getLastUpdateTime())));
        assertEquals(describe(OFF_CPU, NETWORK_EXTENDED), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, 0,
            NETWORK_EXTENDED.getLastUpdateTime() - 1)));
        assertEquals(describe(OFF_CPU), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, 0,
            NETWORK_EXTENDED.getLastUpdateTime())));
    }

    @Test
    public void readsTheTasksStartingFromATime() throws Exception {
        assertEquals(describe(OFF_CPU, NETWORK, NETWORK_EXTENDED), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, NETWORK.getStartTime(), 0)));
        assertEquals(describe(OFF_CPU), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, NETWORK.getStartTime() + 1,
            0)));
    }

    /**
     * The task list of a service, as the profiling page asks for it, by target type.
     */
    @Test
    public void readsTheTasksOfTheTargetTypesAsked() throws Exception {
        assertEquals(describe(ON_CPU, OFF_CPU, NETWORK, NETWORK_EXTENDED, CONTINUOUS_ON_CPU, CONTINUOUS_OFF_CPU),
                     describe(taskDAO.queryTasksByTargets(SERVICE_ID, null, ALL_TARGETS, null, 0, 0)));
        assertEquals(describe(ON_CPU, CONTINUOUS_ON_CPU), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, Collections.singletonList(EBPFProfilingTargetType.ON_CPU), null, 0, 0)));
        assertEquals(describe(ON_CPU, OFF_CPU, CONTINUOUS_ON_CPU, CONTINUOUS_OFF_CPU),
                     describe(taskDAO.queryTasksByTargets(SERVICE_ID, null, CPU, null, 0, 0)));
        assertEquals(describe(ON_CPU, OTHER_SERVICE_ON_CPU, CONTINUOUS_ON_CPU), describe(taskDAO.queryTasksByTargets(
            null, null, Collections.singletonList(EBPFProfilingTargetType.ON_CPU), null, 0, 0)));
    }

    @Test
    public void readsTheTasksOfAnInstance() throws Exception {
        assertEquals(describe(NETWORK, NETWORK_EXTENDED, CONTINUOUS_ON_CPU),
                     describe(taskDAO.queryTasksByTargets(SERVICE_ID, INSTANCE_ID, ALL_TARGETS, null, 0, 0)));
        assertEquals(describe(CONTINUOUS_OFF_CPU),
                     describe(taskDAO.queryTasksByTargets(null, OTHER_INSTANCE_ID, ALL_TARGETS, null, 0, 0)));
    }

    /**
     * The continuous-profiling page counts the tasks a policy triggered since a time, creating a fixed-time task
     * looks for the tasks of its target starting from its own start, and the task list of a duration asks for the
     * tasks updated after its end.
     */
    @Test
    public void readsTheTasksOfATriggerTypeByTheirStartAndUpdateTimes() throws Exception {
        final EBPFProfilingTriggerType continuous = EBPFProfilingTriggerType.CONTINUOUS_PROFILING;
        assertEquals(describe(CONTINUOUS_ON_CPU, CONTINUOUS_OFF_CPU), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, CPU, continuous, CONTINUOUS_ON_CPU.getStartTime(), 0)));
        assertEquals(describe(CONTINUOUS_OFF_CPU), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, CPU, continuous, CONTINUOUS_ON_CPU.getStartTime() + 1, 0)));
        assertEquals(describe(NETWORK, NETWORK_EXTENDED), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, Collections.singletonList(EBPFProfilingTargetType.NETWORK),
            EBPFProfilingTriggerType.FIXED_TIME, NETWORK.getStartTime(), 0)));
        assertEquals(describe(OFF_CPU), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, CPU, EBPFProfilingTriggerType.FIXED_TIME, 0, NETWORK_EXTENDED.getLastUpdateTime())));
    }

    /**
     * Keeping a network task alive reads every row of the task, the one the task was created with and the copies
     * that extend it, whichever day table each is in.
     */
    @Test
    public void readsEveryRowOfATaskById() throws Exception {
        assertEquals(describe(NETWORK, NETWORK_EXTENDED), describe(taskDAO.getTaskRecord(NETWORK.getLogicalId())));
        assertEquals(describe(OFF_CPU), describe(taskDAO.getTaskRecord(OFF_CPU.getLogicalId())));
        assertEquals(Collections.emptyList(), taskDAO.getTaskRecord("missing-task"));
    }

    @Test
    public void readsTheSchedulesOfOneTask() throws Exception {
        assertEquals(describeSchedules(LAST_SCHEDULE, ACROSS_MIDNIGHT_SCHEDULE, FIRST_SCHEDULE),
                     describeSchedules(scheduleDAO.querySchedules(NETWORK.getLogicalId())));
        assertEquals(describeSchedules(OTHER_TASK_SCHEDULE),
                     describeSchedules(scheduleDAO.querySchedules(ON_CPU.getLogicalId())));
        assertEquals(Collections.emptyList(), scheduleDAO.querySchedules("missing-task"));
    }

    /**
     * The analysis reads every stack a schedule uploaded in a window, from both day tables, and none uploaded just
     * before the window or on its end.
     */
    @Test
    public void readsEveryStackOfAScheduleInTheWindow() throws Exception {
        assertEquals(describeData(windowOfSchedule),
                     describeData(dataDAO.queryData(Collections.singletonList(SCHEDULE), WINDOW_BEGIN, WINDOW_END)));
    }

    @Test
    public void readsTheStacksOfTheSchedulesAskedOnly() throws Exception {
        final List<EBPFProfilingDataRecord> both = new ArrayList<>(windowOfSchedule);
        both.addAll(windowOfOtherSchedule);
        assertEquals(describeData(both), describeData(
            dataDAO.queryData(Arrays.asList(SCHEDULE, OTHER_SCHEDULE), WINDOW_BEGIN, WINDOW_END)));
    }

    /**
     * The analysis asks for windows that meet at their ends: an upload on a window's end belongs to the next one.
     */
    @Test
    public void readsTheUploadsOnAWindowsBoundsInOneWindowOnly() throws Exception {
        assertEquals(describeData(onWindowEnd), describeData(dataDAO.queryData(
            Collections.singletonList(SCHEDULE), WINDOW_END, WINDOW_END + 10_000L)));
        assertEquals(describeData(beforeWindow), describeData(dataDAO.queryData(
            Collections.singletonList(SCHEDULE), WINDOW_BEGIN - 10_000L, WINDOW_BEGIN)));
    }

    private static long minutes(final int fromMidnight) {
        return MIDNIGHT + TimeUnit.MINUTES.toMillis(fromMidnight);
    }

    /**
     * Every column of each task, as the task workers stored it, in no order.
     */
    private static List<String> describe(final List<EBPFProfilingTaskRecord> tasks) {
        final EBPFProfilingTaskRecord.Builder builder = new EBPFProfilingTaskRecord.Builder();
        return tasks.stream().map(task -> {
            final HashMapConverter.ToStorage columns = new HashMapConverter.ToStorage();
            builder.entity2Storage(task, columns);
            return new TreeMap<>(columns.obtain()).toString();
        }).sorted().collect(Collectors.toList());
    }

    private static List<String> describe(final EBPFProfilingTaskRecord... tasks) {
        return describe(Arrays.asList(tasks));
    }

    private static List<String> describeSchedules(final List<EBPFProfilingSchedule> schedules) {
        return schedules.stream()
                        .map(schedule -> String.join("|", schedule.getScheduleId(), schedule.getTaskId(),
                                                     schedule.getProcessId(), String.valueOf(schedule.getStartTime()),
                                                     String.valueOf(schedule.getEndTime())))
                        .sorted()
                        .collect(Collectors.toList());
    }

    private static List<String> describeSchedules(final EBPFProfilingScheduleRecord... records) {
        return describeSchedules(Arrays.stream(records).map(record -> {
            final EBPFProfilingSchedule schedule = new EBPFProfilingSchedule();
            schedule.setScheduleId(record.getScheduleId());
            schedule.setTaskId(record.getTaskId());
            schedule.setProcessId(record.getProcessId());
            schedule.setStartTime(record.getStartTime());
            schedule.setEndTime(record.getEndTime());
            return schedule;
        }).collect(Collectors.toList()));
    }

    /**
     * Every field of each stack the analysis uses, in no order.
     */
    private static List<String> describeData(final List<EBPFProfilingDataRecord> data) {
        return data.stream()
                   .map(row -> String.join("|", row.getScheduleId(), row.getTaskId(), row.getStackIdList(),
                                           String.valueOf(row.getTargetType()),
                                           Base64.getEncoder().encodeToString(row.getDataBinary()),
                                           String.valueOf(row.getUploadTime())))
                   .sorted()
                   .collect(Collectors.toList());
    }

    /**
     * A fixed-time task as the profiling page creates it ({@code EBPFProfilingMutationService#createTask}).
     */
    private static EBPFProfilingTaskRecord fixedTimeTask(final String serviceId, final List<String> processLabels,
                                                         final EBPFProfilingTargetType targetType,
                                                         final long createTime, final long startTime) {
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(serviceId);
        task.setProcessLabelsJson(processLabels.isEmpty() ? Const.EMPTY_STRING : GSON.toJson(processLabels));
        task.setStartTime(startTime);
        task.setTriggerType(EBPFProfilingTriggerType.FIXED_TIME.value());
        task.setFixedTriggerDuration(60);
        task.setTargetType(targetType.value());
        task.setCreateTime(createTime);
        task.setLastUpdateTime(createTime);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(createTime));
        task.generateLogicalId();
        task.setExtensionConfigJson(Const.EMPTY_STRING);
        return task;
    }

    /**
     * A network-profiling task of an instance, created to start at once.
     */
    private static EBPFProfilingTaskRecord networkTask(final String instanceId, final long createTime) {
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(IDManager.ServiceInstanceID.analysisId(instanceId).getServiceId());
        task.setProcessLabelsJson(Const.EMPTY_STRING);
        task.setInstanceId(instanceId);
        task.setStartTime(createTime);
        task.setTriggerType(EBPFProfilingTriggerType.FIXED_TIME.value());
        task.setFixedTriggerDuration(600);
        task.setTargetType(EBPFProfilingTargetType.NETWORK.value());
        task.setCreateTime(createTime);
        task.setLastUpdateTime(createTime);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(createTime));
        task.setExtensionConfigJson("{\"networkSamplings\":[{\"uriRegex\":\"/checkout\",\"minDuration\":0}]}");
        task.generateLogicalId();
        return task;
    }

    /**
     * The copy {@code EBPFProfilingMutationService#keepEBPFNetworkProfiling} writes to keep a network task alive:
     * the same logical id, ten more minutes, created and updated at the time it is kept alive.
     */
    private static EBPFProfilingTaskRecord extended(final EBPFProfilingTaskRecord task, final long keptAt) {
        final EBPFProfilingTaskRecord copy = new EBPFProfilingTaskRecord();
        copy.setLogicalId(task.getLogicalId());
        copy.setServiceId(task.getServiceId());
        copy.setProcessLabelsJson(Const.EMPTY_STRING);
        copy.setInstanceId(task.getInstanceId());
        copy.setStartTime(task.getStartTime());
        copy.setTriggerType(task.getTriggerType());
        copy.setFixedTriggerDuration(task.getFixedTriggerDuration() + 600);
        copy.setTargetType(EBPFProfilingTargetType.NETWORK.value());
        copy.setCreateTime(keptAt);
        copy.setLastUpdateTime(keptAt);
        copy.setTimeBucket(TimeBucket.getMinuteTimeBucket(keptAt));
        copy.setExtensionConfigJson(Const.EMPTY_STRING);
        return copy;
    }

    /**
     * A task as the eBPF agent reports one when a continuous-profiling policy triggers on a process of an instance.
     */
    private static EBPFProfilingTaskRecord continuousProfilingTask(final String instanceId,
                                                                   final EBPFProfilingTargetType targetType,
                                                                   final long time) {
        final EBPFProfilingTaskContinuousProfiling continuousProfiling = new EBPFProfilingTaskContinuousProfiling();
        continuousProfiling.setProcessId(IDManager.ProcessID.buildId(instanceId, "checkout"));
        continuousProfiling.setProcessName("checkout");
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(IDManager.ServiceInstanceID.analysisId(instanceId).getServiceId());
        task.setProcessLabelsJson(Const.EMPTY_STRING);
        task.setInstanceId(instanceId);
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

    /**
     * A schedule as the eBPF receiver's dispatcher stores it, at the minute of its latest report.
     */
    private static EBPFProfilingScheduleRecord schedule(final String taskId, final String instanceId,
                                                        final String processName, final long startTime,
                                                        final long endTime) {
        final EBPFProfilingScheduleRecord schedule = new EBPFProfilingScheduleRecord();
        schedule.setTaskId(taskId);
        schedule.setProcessId(IDManager.ProcessID.buildId(instanceId, processName));
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setScheduleId(taskId + "-" + processName + "-" + startTime);
        schedule.setTimeBucket(TimeBucket.getMinuteTimeBucket(endTime));
        return schedule;
    }

    /**
     * A stack of an upload, as the eBPF profiling data dispatcher writes it.
     */
    private static EBPFProfilingDataRecord data(final String scheduleId, final String stack, final long uploadTime) {
        final EBPFProfilingDataRecord data = new EBPFProfilingDataRecord();
        data.setScheduleId(scheduleId);
        data.setTaskId(NETWORK.getLogicalId());
        data.setStackIdList("stack-" + stack);
        data.setTargetType(EBPFProfilingTargetType.ON_CPU.value());
        data.setDataBinary(("stack " + stack).getBytes());
        data.setUploadTime(uploadTime);
        data.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime));
        return data;
    }
}
