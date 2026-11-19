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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JFRProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileLanguageType;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.jfr.type.JFREventType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads trace-profiling thread snapshots the way the profile analysis does, eBPF profiling data the way the eBPF
 * analysis does, and async-profiler (JFR) and pprof data the way their flame-graph analyses do, on data dumped and
 * uploaded on both sides of midnight, so in two day tables. A subclass names the server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCProfilingDataQueryITBase {
    private static final String JAVA_TASK =
        (MIDNIGHT - 600_000L) + Const.ID_CONNECTOR + IDManager.ServiceID.buildId("checkout", true);
    private static final String GO_TASK =
        (MIDNIGHT - 300_000L) + Const.ID_CONNECTOR + IDManager.ServiceID.buildId("payment", true);
    private static final String JAVA_SEGMENT = "segment-java";
    /** The Java agent dumps the thread of a profiled segment every dump period, the first three before midnight. */
    private static final int JAVA_SNAPSHOTS = 6;
    private static final long DUMP_PERIOD = 10L;
    /** The Go agent reports one snapshot per segment, at the time of its profile. */
    private static final String GO_SEGMENT_YESTERDAY = "segment-go-1";
    private static final String GO_SEGMENT_TODAY = "segment-go-2";
    private static final String SCHEDULE = "schedule-checkout";
    private static final String CHECKOUT_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(CHECKOUT_ID, "checkout-1");
    private static final String OTHER_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(CHECKOUT_ID, "checkout-2");
    private static final String ASYNC_PROFILER_TASK = (MIDNIGHT - 120_000L) + Const.ID_CONNECTOR + CHECKOUT_ID;
    private static final String OTHER_ASYNC_PROFILER_TASK = (MIDNIGHT - 1_200_000L) + Const.ID_CONNECTOR + CHECKOUT_ID;
    private static final String PPROF_TASK = (MIDNIGHT - 180_000L) + Const.ID_CONNECTOR + CHECKOUT_ID;
    private static final String OTHER_PPROF_TASK = (MIDNIGHT - 1_500_000L) + Const.ID_CONNECTOR + CHECKOUT_ID;

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCProfileThreadSnapshotQueryDAO snapshotDAO;
    private ProfileThreadSnapshotRecord goSnapshotToday;
    private final List<JFRProfilingDataRecord> jfrData = new ArrayList<>();
    private final List<PprofProfilingDataRecord> pprofData = new ArrayList<>();

    JDBCProfilingDataQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeData() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        final Model snapshots = support.install(
            ProfileThreadSnapshotRecord.class, DefaultScopeDefine.PROFILE_TASK_SEGMENT_SNAPSHOT,
            ProfileThreadSnapshotRecord.INDEX_NAME, DownSampling.Second);
        final List<ProfileThreadSnapshotRecord> written = new ArrayList<>();
        for (int sequence = 0; sequence < JAVA_SNAPSHOTS; sequence++) {
            written.add(javaSnapshot(sequence));
        }
        goSnapshotToday = goSnapshot(GO_SEGMENT_TODAY, MIDNIGHT + 5_000L);
        written.add(goSnapshot(GO_SEGMENT_YESTERDAY, MIDNIGHT - 5_000L));
        written.add(goSnapshotToday);
        support.writeRecords(snapshots, new ProfileThreadSnapshotRecord.Builder(), written);
        snapshotDAO = new JDBCProfileThreadSnapshotQueryDAO(support.getClient(), support.getTableHelper());
        writeAsyncProfilerAndPprofData();

        final Model ebpfData = support.install(
            EBPFProfilingDataRecord.class, DefaultScopeDefine.EBPF_PROFILING_DATA, EBPFProfilingDataRecord.INDEX_NAME,
            DownSampling.Second);
        support.writeRecords(ebpfData, new EBPFProfilingDataRecord.Builder(), Arrays.asList(
            ebpfData("stack-1", MIDNIGHT - 3_000L), ebpfData("stack-2", MIDNIGHT + 3_000L)));
    }

    /**
     * Uploads a second or two before and after midnight, so a task's data is in two day tables. The async-profiler
     * task also has uploads by its other instance and of another event, and another task has an upload of the same
     * instance and event; the same holds for the pprof task, without events.
     */
    private void writeAsyncProfilerAndPprofData() throws Exception {
        final String cpu = JFREventType.EXECUTION_SAMPLE.name();
        for (final int second : new int[] {-2, -1, 1, 2}) {
            jfrData.add(jfr(ASYNC_PROFILER_TASK, INSTANCE_ID, cpu, second));
            pprofData.add(pprof(PPROF_TASK, INSTANCE_ID, second));
        }
        for (final int second : new int[] {-1, 1}) {
            jfrData.add(jfr(ASYNC_PROFILER_TASK, OTHER_INSTANCE_ID, cpu, second));
            pprofData.add(pprof(PPROF_TASK, OTHER_INSTANCE_ID, second));
        }
        jfrData.add(jfr(ASYNC_PROFILER_TASK, INSTANCE_ID, JFREventType.LOCK.name(), 1));
        jfrData.add(jfr(OTHER_ASYNC_PROFILER_TASK, INSTANCE_ID, cpu, 1));
        pprofData.add(pprof(OTHER_PPROF_TASK, INSTANCE_ID, 1));
        support.writeRecords(
            support.install(JFRProfilingDataRecord.class, DefaultScopeDefine.JFR_PROFILING_DATA,
                            JFRProfilingDataRecord.INDEX_NAME, DownSampling.Second),
            new JFRProfilingDataRecord.Builder(), jfrData);
        support.writeRecords(
            support.install(PprofProfilingDataRecord.class, DefaultScopeDefine.PPROF_PROFILING_DATA,
                            PprofProfilingDataRecord.INDEX_NAME, DownSampling.Second),
            new PprofProfilingDataRecord.Builder(), pprofData);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * The analysis of a span reads the sequences dumped within the span's time. The segment has snapshots in both
     * day tables, but none of the window in one of them.
     */
    @Test
    public void findsTheSequenceRangeOfAWindowOnEachSideOfMidnight() throws Exception {
        assertEquals(Arrays.asList(1, 2), sequenceRange(JAVA_SEGMENT, dumpTime(1), dumpTime(2)));
        assertEquals(Arrays.asList(3, 5), sequenceRange(JAVA_SEGMENT, dumpTime(3), dumpTime(5)));
    }

    @Test
    public void findsNoSequenceInAWindowWithoutSnapshots() throws Exception {
        final long afterLastDump = dumpTime(JAVA_SNAPSHOTS - 1) + 1;
        assertEquals(Arrays.asList(-1, -1), sequenceRange(JAVA_SEGMENT, afterLastDump, afterLastDump + 1_000L));
    }

    /**
     * The profiled segments of a task, each with the language of its agent.
     */
    @Test
    public void readsTheLanguageOfTheProfiledSegmentsOfATask() throws Exception {
        assertEquals(Collections.singletonList(JAVA_SEGMENT + "|JAVA"),
                     segmentLanguages(snapshotDAO.queryRecords(JAVA_TASK)));
        assertEquals(Arrays.asList(GO_SEGMENT_YESTERDAY + "|GO", GO_SEGMENT_TODAY + "|GO"),
                     segmentLanguages(snapshotDAO.queryRecords(GO_TASK)));
    }

    /**
     * The analysis falls back to the Go profile of a segment, which it keeps only when the snapshot reads as Go.
     */
    @Test
    public void readsTheSnapshotsOfASegmentWhole() throws Exception {
        final List<String> java = new ArrayList<>();
        for (int sequence = 0; sequence < JAVA_SNAPSHOTS; sequence++) {
            java.add(describe(javaSnapshot(sequence)));
        }
        assertEquals(java, describe(snapshotDAO.queryRecords(JAVA_SEGMENT, 0, JAVA_SNAPSHOTS)));
        assertEquals(Collections.singletonList(describe(goSnapshotToday)),
                     describe(snapshotDAO.queryRecords(GO_SEGMENT_TODAY, 0, 1)));
    }

    /**
     * An eBPF analysis asked for no schedule reads nothing.
     */
    @Test
    public void readsNoEBPFProfilingDataForNoSchedule() throws Exception {
        final JDBCEBPFProfilingDataDAO dao = new JDBCEBPFProfilingDataDAO(support.getClient(), support.getTableHelper());
        final long begin = MIDNIGHT - 10_000L;
        final long end = MIDNIGHT + 10_000L;
        assertEquals(Arrays.asList("stack-1", "stack-2"), dao.queryData(Collections.singletonList(SCHEDULE), begin, end)
                                                             .stream()
                                                             .map(EBPFProfilingDataRecord::getStackIdList)
                                                             .sorted()
                                                             .collect(Collectors.toList()));
        assertEquals(Collections.emptyList(), dao.queryData(Collections.emptyList(), begin, end));
    }

    /**
     * A window's bounds are dump times of the segment: one a millisecond inside them leaves those snapshots out.
     */
    @Test
    public void findsNoSequenceJustOutsideAWindow() throws Exception {
        assertEquals(Arrays.asList(2, 3), sequenceRange(JAVA_SEGMENT, dumpTime(1) + 1, dumpTime(4) - 1));
    }

    /**
     * The analysis reads a segment's snapshots in batches of {@code [minSequence, maxSequence)}, here one per day
     * table.
     */
    @Test
    public void readsAdjacentSequenceBatchesWithoutOverlap() throws Exception {
        assertEquals(Arrays.asList(describe(javaSnapshot(0)), describe(javaSnapshot(1)), describe(javaSnapshot(2))),
                     describe(snapshotDAO.queryRecords(JAVA_SEGMENT, 0, 3)));
        assertEquals(Arrays.asList(describe(javaSnapshot(3)), describe(javaSnapshot(4)), describe(javaSnapshot(5))),
                     describe(snapshotDAO.queryRecords(JAVA_SEGMENT, 3, JAVA_SNAPSHOTS)));
        assertEquals(Collections.emptyList(),
                     snapshotDAO.queryRecords(JAVA_SEGMENT, JAVA_SNAPSHOTS, JAVA_SNAPSHOTS + 3));
    }

    @Test
    public void readsTheJFRDataOfAnInstanceAndEventFromEveryDayTable() throws Exception {
        final JDBCJFRDataQueryDAO dao = new JDBCJFRDataQueryDAO(support.getClient(), support.getTableHelper());
        final String cpu = JFREventType.EXECUTION_SAMPLE.name();
        final List<String> instance = Collections.singletonList(INSTANCE_ID);
        assertEquals(describeJFR(ASYNC_PROFILER_TASK, instance, cpu),
                     describeJFR(dao.getByTaskIdAndInstancesAndEvent(ASYNC_PROFILER_TASK, instance, cpu)));
    }

    /**
     * No instance listed reads the uploads of every instance of the task.
     */
    @Test
    public void readsTheJFRDataOfTheListedInstances() throws Exception {
        final JDBCJFRDataQueryDAO dao = new JDBCJFRDataQueryDAO(support.getClient(), support.getTableHelper());
        final String cpu = JFREventType.EXECUTION_SAMPLE.name();
        final List<String> instances = Arrays.asList(INSTANCE_ID, OTHER_INSTANCE_ID);
        assertEquals(describeJFR(ASYNC_PROFILER_TASK, instances, cpu),
                     describeJFR(dao.getByTaskIdAndInstancesAndEvent(ASYNC_PROFILER_TASK, instances, cpu)));
        assertEquals(describeJFR(ASYNC_PROFILER_TASK, instances, cpu), describeJFR(
            dao.getByTaskIdAndInstancesAndEvent(ASYNC_PROFILER_TASK, Collections.emptyList(), cpu)));
        final String lock = JFREventType.LOCK.name();
        assertEquals(describeJFR(ASYNC_PROFILER_TASK, instances, lock),
                     describeJFR(dao.getByTaskIdAndInstancesAndEvent(ASYNC_PROFILER_TASK, instances, lock)));
        assertEquals(Collections.emptyList(), dao.getByTaskIdAndInstancesAndEvent(ASYNC_PROFILER_TASK, instances, ""));
        assertEquals(Collections.emptyList(), dao.getByTaskIdAndInstancesAndEvent("", instances, cpu));
    }

    /**
     * No instance listed reads the uploads of every instance of the task.
     */
    @Test
    public void readsThePprofDataOfTheListedInstancesFromEveryDayTable() throws Exception {
        final JDBCPprofDataQueryDAO dao = new JDBCPprofDataQueryDAO(support.getClient(), support.getTableHelper());
        final List<String> instance = Collections.singletonList(INSTANCE_ID);
        assertEquals(describePprof(PPROF_TASK, instance),
                     describePprof(dao.getByTaskIdAndInstances(PPROF_TASK, instance)));
        final List<String> instances = Arrays.asList(INSTANCE_ID, OTHER_INSTANCE_ID);
        assertEquals(describePprof(PPROF_TASK, instances),
                     describePprof(dao.getByTaskIdAndInstances(PPROF_TASK, instances)));
        assertEquals(describePprof(PPROF_TASK, instances),
                     describePprof(dao.getByTaskIdAndInstances(PPROF_TASK, Collections.emptyList())));
        assertEquals(Collections.emptyList(), dao.getByTaskIdAndInstances("", instances));
    }

    private List<Integer> sequenceRange(final String segmentId, final long start, final long end) throws Exception {
        return Arrays.asList(snapshotDAO.queryMinSequence(segmentId, start, end),
                             snapshotDAO.queryMaxSequence(segmentId, start, end));
    }

    private static List<String> segmentLanguages(final List<ProfileThreadSnapshotRecord> snapshots) {
        return snapshots.stream()
                        .map(snapshot -> snapshot.getSegmentId() + "|" + snapshot.getLanguage())
                        .sorted()
                        .collect(Collectors.toList());
    }

    private static List<String> describe(final List<ProfileThreadSnapshotRecord> snapshots) {
        return snapshots.stream()
                        .sorted(Comparator.comparingInt(ProfileThreadSnapshotRecord::getSequence))
                        .map(JDBCProfilingDataQueryITBase::describe)
                        .collect(Collectors.toList());
    }

    private static String describe(final ProfileThreadSnapshotRecord snapshot) {
        return String.join("|", snapshot.getTaskId(), snapshot.getSegmentId(), String.valueOf(snapshot.getSequence()),
                           String.valueOf(snapshot.getDumpTime()), new String(snapshot.getStackBinary()),
                           snapshot.getLanguage().name());
    }

    private static long dumpTime(final int sequence) {
        return MIDNIGHT + (sequence - JAVA_SNAPSHOTS / 2) * DUMP_PERIOD;
    }

    private static ProfileThreadSnapshotRecord javaSnapshot(final int sequence) {
        return snapshot(JAVA_TASK, JAVA_SEGMENT, sequence, dumpTime(sequence), ProfileLanguageType.JAVA);
    }

    private static ProfileThreadSnapshotRecord goSnapshot(final String segmentId, final long dumpTime) {
        return snapshot(GO_TASK, segmentId, 0, dumpTime, ProfileLanguageType.GO);
    }

    /**
     * A snapshot as the profile receiver writes it, the stack being the dumped thread stack of a Java agent or the
     * segment's samples of a Go agent's pprof.
     */
    private static ProfileThreadSnapshotRecord snapshot(final String taskId, final String segmentId, final int sequence,
                                                        final long dumpTime, final ProfileLanguageType language) {
        final ProfileThreadSnapshotRecord snapshot = new ProfileThreadSnapshotRecord();
        snapshot.setTaskId(taskId);
        snapshot.setSegmentId(segmentId);
        snapshot.setDumpTime(dumpTime);
        snapshot.setSequence(sequence);
        snapshot.setStackBinary(("stack " + segmentId + " " + sequence).getBytes());
        snapshot.setLanguage(language);
        snapshot.setTimeBucket(TimeBucket.getRecordTimeBucket(dumpTime));
        return snapshot;
    }

    /**
     * A stack as the eBPF profiling data dispatcher writes it.
     */
    private static EBPFProfilingDataRecord ebpfData(final String stackIdList, final long uploadTime) {
        final EBPFProfilingDataRecord data = new EBPFProfilingDataRecord();
        data.setScheduleId(SCHEDULE);
        data.setTaskId("ebpf-task-checkout");
        data.setStackIdList(stackIdList);
        data.setTargetType(EBPFProfilingTargetType.ON_CPU.value());
        data.setDataBinary(new byte[] {1});
        data.setUploadTime(uploadTime);
        data.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime));
        return data;
    }

    /**
     * A task's data in no promised order, sorted by instance and upload time.
     */
    private static List<String> describeJFR(final List<JFRProfilingDataRecord> data) {
        return data.stream()
                   .map(row -> String.join("|", row.getInstanceId(), String.valueOf(row.getUploadTime()),
                                           row.getTaskId(), row.getEventType(), String.valueOf(row.getTimeBucket()),
                                           new String(row.getDataBinary(), StandardCharsets.UTF_8)))
                   .sorted()
                   .collect(Collectors.toList());
    }

    private List<String> describeJFR(final String taskId, final List<String> instanceIds, final String eventType) {
        return describeJFR(jfrData.stream()
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

    private List<String> describePprof(final String taskId, final List<String> instanceIds) {
        return describePprof(pprofData.stream()
                                      .filter(row -> row.getTaskId().equals(taskId)
                                          && instanceIds.contains(row.getInstanceId()))
                                      .collect(Collectors.toList()));
    }

    /**
     * An upload as {@code JFRProfilingDataDispatcher} writes it, the data being the frame tree of one event.
     */
    private static JFRProfilingDataRecord jfr(final String taskId, final String instanceId, final String eventType,
                                              final int second) {
        final long uploadTime = MIDNIGHT + second * 1_000L;
        final JFRProfilingDataRecord data = new JFRProfilingDataRecord();
        data.setTaskId(taskId);
        data.setInstanceId(instanceId);
        data.setEventType(eventType);
        data.setUploadTime(uploadTime);
        data.setDataBinary(("{\"frame\":\"" + eventType + " " + second + "\"}").getBytes(StandardCharsets.UTF_8));
        data.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime));
        return data;
    }

    /**
     * An upload as {@code PprofProfilingDataDispatcher} writes it.
     */
    private static PprofProfilingDataRecord pprof(final String taskId, final String instanceId, final int second) {
        final long uploadTime = MIDNIGHT + second * 1_000L;
        final PprofProfilingDataRecord data = new PprofProfilingDataRecord();
        data.setTaskId(taskId);
        data.setInstanceId(instanceId);
        data.setUploadTime(uploadTime);
        data.setDataBinary(("{\"frame\":\"pprof " + second + "\"}").getBytes(StandardCharsets.UTF_8));
        data.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime));
        return data;
    }
}
