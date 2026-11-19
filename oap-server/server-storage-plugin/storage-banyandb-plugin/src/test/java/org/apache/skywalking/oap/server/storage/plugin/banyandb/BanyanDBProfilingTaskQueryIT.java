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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JFRProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBAsyncProfilerTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBJFRDataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBPprofDataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBPprofTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileTaskQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads profiling tasks, their logs and their data the way the profiling query services do, on the task streams and
 * record streams OAP installs for trace profiling, async-profiler, pprof and eBPF profiling: newest first, by service,
 * endpoint, instance, event, trigger and target, by id, within time bounds, and every data row of a task (more than
 * BanyanDB returns without an explicit limit).
 */
@Testcontainers
public class BanyanDBProfilingTaskQueryIT {
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("profiling-it", true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("profiling-it-other", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "instance-0");
    private static final String OTHER_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "instance-1");
    private static final int DATA_ROWS = 120;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static long FIRST;

    @BeforeAll
    public static void writeProfiling() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        FIRST = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)) / 60_000L * 60_000L;

        SUPPORT.writeNoneStreams(SUPPORT.installNoneStream(ProfileTaskRecord.class, DefaultScopeDefine.PROFILE_TASK,
                                                           ProfileTaskRecord.INDEX_NAME),
                                 new ProfileTaskRecord.Builder(), Arrays.asList(
                profileTask("trace-task-0", SERVICE_ID, "/checkout", 0),
                profileTask("trace-task-1", SERVICE_ID, "/pay", 1),
                profileTask("trace-task-2", SERVICE_ID, "/checkout", 2),
                halfAMinuteLater(profileTask("trace-task-3", OTHER_SERVICE_ID, "/checkout", 3))));
        SUPPORT.writeNoneStreams(SUPPORT.installNoneStream(AsyncProfilerTaskRecord.class, DefaultScopeDefine.ASYNC_PROFILER_TASK,
                                                           AsyncProfilerTaskRecord.INDEX_NAME),
                                 new AsyncProfilerTaskRecord.Builder(), Arrays.asList(
                asyncTask("async-task-0", SERVICE_ID, 0), asyncTask("async-task-1", SERVICE_ID, 1),
                halfAMinuteLater(asyncTask("async-task-2", OTHER_SERVICE_ID, 2))));
        SUPPORT.writeNoneStreams(SUPPORT.installNoneStream(PprofTaskRecord.class, DefaultScopeDefine.PPROF_TASK,
                                                           PprofTaskRecord.INDEX_NAME),
                                 new PprofTaskRecord.Builder(), Arrays.asList(
                pprofTask("pprof-task-0", SERVICE_ID, 0), pprofTask("pprof-task-1", SERVICE_ID, 1),
                halfAMinuteLater(pprofTask("pprof-task-2", OTHER_SERVICE_ID, 2))));
        SUPPORT.writeNoneStreams(SUPPORT.installNoneStream(EBPFProfilingTaskRecord.class, DefaultScopeDefine.EBPF_PROFILING_TASK,
                                                           EBPFProfilingTaskRecord.INDEX_NAME),
                                 new EBPFProfilingTaskRecord.Builder(), Arrays.asList(
                ebpfTask("ebpf-task-0", 0, SERVICE_ID, EBPFProfilingTriggerType.FIXED_TIME, EBPFProfilingTargetType.ON_CPU, 0, 5),
                // The same task updated: a second version of it.
                ebpfTask("ebpf-task-0", 1, SERVICE_ID, EBPFProfilingTriggerType.FIXED_TIME, EBPFProfilingTargetType.ON_CPU, 0, 9),
                ebpfTask("ebpf-task-1", 0, SERVICE_ID, EBPFProfilingTriggerType.CONTINUOUS_PROFILING, EBPFProfilingTargetType.OFF_CPU, 2, 6),
                ebpfTask("ebpf-task-2", 0, OTHER_SERVICE_ID, EBPFProfilingTriggerType.FIXED_TIME, EBPFProfilingTargetType.ON_CPU, 3, 7)));

        SUPPORT.write(SUPPORT.install(ProfileTaskLogRecord.class, DefaultScopeDefine.PROFILE_TASK_LOG, ProfileTaskLogRecord.INDEX_NAME),
                      new ProfileTaskLogRecord.Builder(), Arrays.asList(
                taskLog(new ProfileTaskLogRecord(), "trace-task-0", INSTANCE_ID, 0),
                taskLog(new ProfileTaskLogRecord(), "trace-task-1", OTHER_INSTANCE_ID, 1)));
        SUPPORT.write(SUPPORT.install(AsyncProfilerTaskLogRecord.class, DefaultScopeDefine.ASYNC_PROFILER_TASK_LOG,
                                      AsyncProfilerTaskLogRecord.INDEX_NAME),
                      new AsyncProfilerTaskLogRecord.Builder(), Arrays.asList(
                taskLog(new AsyncProfilerTaskLogRecord(), "async-task-0", INSTANCE_ID, 0),
                taskLog(new AsyncProfilerTaskLogRecord(), "async-task-1", INSTANCE_ID, 1)));
        SUPPORT.write(SUPPORT.install(PprofTaskLogRecord.class, DefaultScopeDefine.PPROF_TASK_LOG, PprofTaskLogRecord.INDEX_NAME),
                      new PprofTaskLogRecord.Builder(), Arrays.asList(
                taskLog(new PprofTaskLogRecord(), "pprof-task-0", INSTANCE_ID, 0),
                taskLog(new PprofTaskLogRecord(), "pprof-task-1", INSTANCE_ID, 1)));

        final List<JFRProfilingDataRecord> jfr = new ArrayList<>();
        final List<PprofProfilingDataRecord> pprof = new ArrayList<>();
        for (int i = 0; i < DATA_ROWS; i++) {
            final String instance = i % 2 == 0 ? INSTANCE_ID : OTHER_INSTANCE_ID;
            final JFRProfilingDataRecord jfrRow = new JFRProfilingDataRecord();
            jfrRow.setTaskId("async-task-0");
            jfrRow.setInstanceId(instance);
            jfrRow.setEventType(i % 3 == 0 ? "LOCK" : "CPU");
            jfrRow.setUploadTime(FIRST + i * 100L);
            jfrRow.setDataBinary(("jfr " + i).getBytes());
            jfrRow.setTimeBucket(TimeBucket.getRecordTimeBucket(FIRST + i * 100L));
            jfr.add(jfrRow);
            final PprofProfilingDataRecord pprofRow = new PprofProfilingDataRecord();
            pprofRow.setTaskId("pprof-task-0");
            pprofRow.setInstanceId(instance);
            pprofRow.setUploadTime(FIRST + i * 100L);
            pprofRow.setDataBinary(("pprof " + i).getBytes());
            pprofRow.setTimeBucket(TimeBucket.getRecordTimeBucket(FIRST + i * 100L));
            pprof.add(pprofRow);
        }
        SUPPORT.write(SUPPORT.install(JFRProfilingDataRecord.class, DefaultScopeDefine.JFR_PROFILING_DATA,
                                      JFRProfilingDataRecord.INDEX_NAME), new JFRProfilingDataRecord.Builder(), jfr);
        SUPPORT.write(SUPPORT.install(PprofProfilingDataRecord.class, DefaultScopeDefine.PPROF_PROFILING_DATA,
                                      PprofProfilingDataRecord.INDEX_NAME), new PprofProfilingDataRecord.Builder(), pprof);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(DATA_ROWS,
            new BanyanDBPprofDataQueryDAO(SUPPORT.getClient()).getByTaskIdAndInstances("pprof-task-0", null).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void listsTraceProfilingTasks() throws IOException {
        final BanyanDBProfileTaskQueryDAO dao = new BanyanDBProfileTaskQueryDAO(SUPPORT.getClient(), 200);
        assertEquals(Arrays.asList("trace-task-2", "trace-task-1", "trace-task-0"),
                     ids(dao.getTaskList(SERVICE_ID, null, null, null, null)));
        assertEquals(Arrays.asList("trace-task-2", "trace-task-0"), ids(dao.getTaskList(SERVICE_ID, "/checkout", null, null, null)));
        assertEquals(Collections.singletonList("trace-task-2"), ids(dao.getTaskList(SERVICE_ID, null, null, null, 1)));
        assertEquals(Collections.singletonList("trace-task-1"),
                     ids(dao.getTaskList(SERVICE_ID, null, minuteBucket(1), minuteBucket(1), null)));
        assertEquals("trace-task-1|" + SERVICE_ID + "|/pay|" + startTime(1),
                     describe(dao.getById("trace-task-1")));
        assertNull(dao.getById("trace-task-missing"));
    }

    @Test
    public void listsAsyncProfilerAndPprofTasks() throws IOException {
        final BanyanDBAsyncProfilerTaskQueryDAO async = new BanyanDBAsyncProfilerTaskQueryDAO(SUPPORT.getClient(), 200);
        assertEquals(Arrays.asList("async-task-1", "async-task-0"), async.getTaskList(SERVICE_ID, null, null, null).stream()
            .map(t -> t.getId()).collect(Collectors.toList()));
        assertEquals("async-task-0|" + SERVICE_ID + "|[" + INSTANCE_ID + "]|[CPU, LOCK]",
                     String.join("|", async.getById("async-task-0").getId(), async.getById("async-task-0").getServiceId(),
                                 String.valueOf(async.getById("async-task-0").getServiceInstanceIds()),
                                 String.valueOf(async.getById("async-task-0").getEvents())));
        final BanyanDBPprofTaskQueryDAO pprof = new BanyanDBPprofTaskQueryDAO(SUPPORT.getClient(), 200);
        assertEquals(Arrays.asList("pprof-task-1", "pprof-task-0"), pprof.getTaskList(SERVICE_ID, null, null, null).stream()
            .map(t -> t.getId()).collect(Collectors.toList()));
        assertEquals("pprof-task-0|CPU|10", String.join("|", pprof.getById("pprof-task-0").getId(),
                                                       pprof.getById("pprof-task-0").getEvents().name(),
                                                       String.valueOf(pprof.getById("pprof-task-0").getDumpPeriod())));
        assertNull(pprof.getById("pprof-task-missing"));
    }

    /**
     * A read bounded by an end time bucket keeps the tasks of the whole bucket, as the JDBC and Elasticsearch task
     * DAOs keep the rows whose bucket equals it: the creation checks and the agent task caches bound their reads by
     * minute buckets. The other service's tasks start or are created half a minute into their minute.
     */
    @Test
    public void readsTheTasksOfTheWholeEndMinute() throws IOException {
        final BanyanDBProfileTaskQueryDAO trace = new BanyanDBProfileTaskQueryDAO(SUPPORT.getClient(), 200);
        assertEquals(Collections.singletonList("trace-task-3"),
                     ids(trace.getTaskList(OTHER_SERVICE_ID, null, null, minuteBucket(3), 1)));
        assertEquals(Collections.emptyList(), ids(trace.getTaskList(OTHER_SERVICE_ID, null, null, minuteBucket(2), 1)));
        final BanyanDBAsyncProfilerTaskQueryDAO async = new BanyanDBAsyncProfilerTaskQueryDAO(SUPPORT.getClient(), 200);
        assertEquals(Collections.singletonList("async-task-2"), async.getTaskList(
            OTHER_SERVICE_ID, null, minuteBucket(2), 1).stream().map(t -> t.getId()).collect(Collectors.toList()));
        assertEquals(Collections.emptyList(), async.getTaskList(OTHER_SERVICE_ID, null, minuteBucket(1), 1));
        final BanyanDBPprofTaskQueryDAO pprof = new BanyanDBPprofTaskQueryDAO(SUPPORT.getClient(), 200);
        assertEquals(Collections.singletonList("pprof-task-2"), pprof.getTaskList(
            OTHER_SERVICE_ID, null, minuteBucket(2), 1).stream().map(t -> t.getId()).collect(Collectors.toList()));
        assertEquals(Collections.emptyList(), pprof.getTaskList(OTHER_SERVICE_ID, null, minuteBucket(1), 1));
    }

    @Test
    public void readsTaskLogs() throws IOException {
        assertEquals(Arrays.asList("trace-task-0|" + INSTANCE_ID, "trace-task-1|" + OTHER_INSTANCE_ID),
                     new BanyanDBProfileTaskLogQueryDAO(SUPPORT.getClient(), 200).getTaskLogList().stream()
                         .map(l -> l.getTaskId() + "|" + l.getInstanceId()).sorted().collect(Collectors.toList()));
        // A task's logs, told apart by when each was written.
        assertEquals(Collections.singletonList(INSTANCE_ID + "|" + startTime(1)),
                     new BanyanDBAsyncProfilerTaskLogQueryDAO(SUPPORT.getClient(), 200).getTaskLogList("async-task-1")
                         .stream().map(l -> l.getInstanceId() + "|" + l.getOperationTime()).collect(Collectors.toList()));
        assertEquals(Collections.singletonList(INSTANCE_ID + "|" + startTime(0)),
                     new BanyanDBPprofTaskLogQueryDAO(SUPPORT.getClient(), 200).getTaskLogList("pprof-task-0")
                         .stream().map(l -> l.getInstanceId() + "|" + l.getOperationTime()).collect(Collectors.toList()));
    }

    @Test
    public void readsEveryDataRowOfATask() throws IOException {
        final BanyanDBJFRDataQueryDAO jfr = new BanyanDBJFRDataQueryDAO(SUPPORT.getClient());
        final List<String> cpuOfFirstInstance = new ArrayList<>();
        for (int i = 0; i < DATA_ROWS; i++) {
            if (i % 2 == 0 && i % 3 != 0) {
                cpuOfFirstInstance.add("jfr " + i);
            }
        }
        Collections.sort(cpuOfFirstInstance);
        assertEquals(cpuOfFirstInstance, jfr.getByTaskIdAndInstancesAndEvent(
            "async-task-0", Collections.singletonList(INSTANCE_ID), "CPU").stream()
            .map(r -> new String(r.getDataBinary())).sorted().collect(Collectors.toList()));
        assertEquals(DATA_ROWS - DATA_ROWS / 3,
                     jfr.getByTaskIdAndInstancesAndEvent("async-task-0", Collections.emptyList(), "CPU").size());
        final BanyanDBPprofDataQueryDAO pprof = new BanyanDBPprofDataQueryDAO(SUPPORT.getClient());
        assertEquals(DATA_ROWS / 2, pprof.getByTaskIdAndInstances("pprof-task-0", Collections.singletonList(OTHER_INSTANCE_ID)).size());
    }

    @Test
    public void readsEBPFProfilingTasks() throws IOException {
        final BanyanDBEBPFProfilingTaskDAO dao = new BanyanDBEBPFProfilingTaskDAO(SUPPORT.getClient());
        assertEquals(Arrays.asList("ebpf-task-1", "ebpf-task-0", "ebpf-task-0"),
                     logicalIds(dao.queryTasksByServices(Collections.singletonList(SERVICE_ID), null, 0, 0)));
        assertEquals(Collections.singletonList("ebpf-task-1"), logicalIds(dao.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.CONTINUOUS_PROFILING, 0, 0)));
        // Started at or after minute 2, and updated after minute 6.
        assertEquals(Collections.singletonList("ebpf-task-2"), logicalIds(dao.queryTasksByServices(
            Arrays.asList(SERVICE_ID, OTHER_SERVICE_ID), null, startTime(2), startTime(6))));
        assertEquals(Collections.singletonList("ebpf-task-1"), logicalIds(dao.queryTasksByTargets(
            SERVICE_ID, null, Collections.singletonList(EBPFProfilingTargetType.OFF_CPU), null, 0, 0)));
        assertEquals(Arrays.asList(5L, 9L), dao.getTaskRecord("ebpf-task-0").stream()
            .map(t -> (t.getLastUpdateTime() - FIRST) / 60_000L).sorted().collect(Collectors.toList()));
    }

    private static List<String> ids(List<ProfileTask> tasks) {
        return tasks.stream().map(ProfileTask::getId).collect(Collectors.toList());
    }

    private static List<String> logicalIds(List<EBPFProfilingTaskRecord> tasks) {
        return tasks.stream().map(EBPFProfilingTaskRecord::getLogicalId).collect(Collectors.toList());
    }

    private static String describe(ProfileTask task) {
        return String.join("|", task.getId(), task.getServiceId(), task.getEndpointName(), String.valueOf(task.getStartTime()));
    }

    private static long startTime(int minute) {
        return FIRST + minute * 60_000L;
    }

    private static long minuteBucket(int minute) {
        return TimeBucket.getMinuteTimeBucket(startTime(minute));
    }

    private static ProfileTaskRecord halfAMinuteLater(ProfileTaskRecord task) {
        task.setStartTime(task.getStartTime() + 30_000L);
        task.setCreateTime(task.getCreateTime() + 30_000L);
        return task;
    }

    private static AsyncProfilerTaskRecord halfAMinuteLater(AsyncProfilerTaskRecord task) {
        task.setCreateTime(task.getCreateTime() + 30_000L);
        return task;
    }

    private static PprofTaskRecord halfAMinuteLater(PprofTaskRecord task) {
        task.setCreateTime(task.getCreateTime() + 30_000L);
        return task;
    }

    private static ProfileTaskRecord profileTask(String id, String serviceId, String endpoint, int minute) {
        final ProfileTaskRecord task = new ProfileTaskRecord();
        task.setTaskId(id);
        task.setServiceId(serviceId);
        task.setEndpointName(endpoint);
        task.setStartTime(startTime(minute));
        task.setCreateTime(startTime(minute));
        task.setDuration(5);
        task.setMinDurationThreshold(10);
        task.setDumpPeriod(10);
        task.setMaxSamplingCount(5);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(startTime(minute)));
        return task;
    }

    private static AsyncProfilerTaskRecord asyncTask(String id, String serviceId, int minute) {
        final AsyncProfilerTaskRecord task = new AsyncProfilerTaskRecord();
        task.setTaskId(id);
        task.setServiceId(serviceId);
        task.setServiceInstanceIds("[\"" + INSTANCE_ID + "\"]");
        task.setCreateTime(startTime(minute));
        task.setDuration(60);
        task.setEvents("[\"CPU\",\"LOCK\"]");
        task.setExecArgs("");
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(startTime(minute)));
        return task;
    }

    private static PprofTaskRecord pprofTask(String id, String serviceId, int minute) {
        final PprofTaskRecord task = new PprofTaskRecord();
        task.setTaskId(id);
        task.setServiceId(serviceId);
        task.setServiceInstanceIds("[\"" + INSTANCE_ID + "\"]");
        task.setCreateTime(startTime(minute));
        task.setEvents("CPU");
        task.setDuration(60);
        task.setDumpPeriod(10);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(startTime(minute)));
        return task;
    }

    private static EBPFProfilingTaskRecord ebpfTask(String logicalId, int version, String serviceId,
                                                    EBPFProfilingTriggerType trigger, EBPFProfilingTargetType target,
                                                    int startMinute, int updateMinute) {
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setLogicalId(logicalId);
        task.setServiceId(serviceId);
        task.setProcessLabelsJson("[]");
        task.setInstanceId("");
        task.setStartTime(startTime(startMinute));
        task.setTriggerType(trigger.value());
        task.setFixedTriggerDuration(60);
        task.setTargetType(target.value());
        task.setCreateTime(startTime(startMinute) + version);
        task.setLastUpdateTime(startTime(updateMinute));
        task.setExtensionConfigJson("");
        task.setContinuousProfilingJson("");
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(startTime(startMinute)));
        return task;
    }

    private static <T> T taskLog(T log, String taskId, String instanceId, int minute) {
        final long time = startTime(minute);
        if (log instanceof ProfileTaskLogRecord) {
            final ProfileTaskLogRecord r = (ProfileTaskLogRecord) log;
            r.setTaskId(taskId);
            r.setInstanceId(instanceId);
            r.setOperationType(1);
            r.setOperationTime(time);
            r.setTimestamp(time);
            r.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
        } else if (log instanceof AsyncProfilerTaskLogRecord) {
            final AsyncProfilerTaskLogRecord r = (AsyncProfilerTaskLogRecord) log;
            r.setTaskId(taskId);
            r.setInstanceId(instanceId);
            r.setOperationType(1);
            r.setOperationTime(time);
            r.setTimestamp(time);
            r.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
        } else {
            final PprofTaskLogRecord r = (PprofTaskLogRecord) log;
            r.setTaskId(taskId);
            r.setInstanceId(instanceId);
            r.setOperationType(1);
            r.setOperationTime(time);
            r.setTimestamp(time);
            r.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
        }
        return log;
    }
}
