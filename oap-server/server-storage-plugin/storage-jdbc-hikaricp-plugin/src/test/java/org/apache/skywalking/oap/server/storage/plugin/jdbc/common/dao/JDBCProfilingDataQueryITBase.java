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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileLanguageType;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
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
 * Reads trace-profiling thread snapshots the way the profile analysis does, and eBPF profiling data the way the eBPF
 * analysis does, on data dumped and uploaded on both sides of midnight, so in two day tables. A subclass names the
 * server.
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

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCProfileThreadSnapshotQueryDAO snapshotDAO;
    private ProfileThreadSnapshotRecord goSnapshotToday;

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

        final Model ebpfData = support.install(
            EBPFProfilingDataRecord.class, DefaultScopeDefine.EBPF_PROFILING_DATA, EBPFProfilingDataRecord.INDEX_NAME,
            DownSampling.Second);
        support.writeRecords(ebpfData, new EBPFProfilingDataRecord.Builder(), Arrays.asList(
            ebpfData("stack-1", MIDNIGHT - 3_000L), ebpfData("stack-2", MIDNIGHT + 3_000L)));
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
}
