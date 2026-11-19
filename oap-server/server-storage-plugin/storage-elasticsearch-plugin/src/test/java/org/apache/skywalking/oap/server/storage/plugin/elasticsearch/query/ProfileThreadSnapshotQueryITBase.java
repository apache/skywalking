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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileLanguageType;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads trace-profiling thread snapshots the way the profile analysis does on Elasticsearch and OpenSearch: a task's
 * profiled segments (the snapshots of sequence 0), latest dump first; a segment's sequence range in a dump-time
 * window, both ends included, -1 when there is none; and the snapshots of {@code [minSequence, maxSequence)}, which
 * the analysis reads in adjacent batches that meet at their ends. Each snapshot is read with its language, so a Go
 * profile is told from a Java one. A nested class of a server's IT names the server and the storage mode.
 *
 * <p>The Java segment is dumped on both sides of a midnight, so its snapshots lie in two daily indices. A segment of
 * another task is dumped within the same windows, with sequences both inside and beyond the Java segment's.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ProfileThreadSnapshotQueryITBase {
    /** A fixed local midnight, so the daily indices the fixture is written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 3, 10, 0, 0).getMillis();
    private static final String JAVA_TASK =
        (MIDNIGHT - 600_000L) + Const.ID_CONNECTOR + IDManager.ServiceID.buildId("checkout", true);
    private static final String GO_TASK =
        (MIDNIGHT - 300_000L) + Const.ID_CONNECTOR + IDManager.ServiceID.buildId("payment", true);
    private static final String OTHER_TASK =
        (MIDNIGHT - 900_000L) + Const.ID_CONNECTOR + IDManager.ServiceID.buildId("inventory", true);
    private static final String JAVA_SEGMENT = "segment-java";
    /** The Java agent dumps the thread of a profiled segment every dump period, the first three before midnight. */
    private static final int JAVA_SNAPSHOTS = 6;
    private static final long DUMP_PERIOD = 10L;
    /** The Go agent reports one snapshot per segment, at the time of its profile. */
    private static final String GO_SEGMENT_YESTERDAY = "segment-go-1";
    private static final String GO_SEGMENT_TODAY = "segment-go-2";
    private static final String OTHER_SEGMENT = "segment-other";
    private static final int OTHER_SNAPSHOTS = 12;

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private ProfileThreadSnapshotQueryEsDAO dao;

    ProfileThreadSnapshotQueryITBase(final ElasticsearchContainer server,
                                     final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeSnapshots() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        final Model model = support.installRecord(ProfileThreadSnapshotRecord.class);
        final List<ProfileThreadSnapshotRecord> snapshots = new ArrayList<>();
        for (int sequence = 0; sequence < JAVA_SNAPSHOTS; sequence++) {
            snapshots.add(javaSnapshot(sequence));
        }
        snapshots.add(goSnapshot(GO_SEGMENT_YESTERDAY, MIDNIGHT - 5_000L));
        snapshots.add(goSnapshot(GO_SEGMENT_TODAY, MIDNIGHT + 5_000L));
        for (int sequence = 0; sequence < OTHER_SNAPSHOTS; sequence++) {
            snapshots.add(snapshot(OTHER_TASK, OTHER_SEGMENT, sequence, dumpTime(0) + sequence * DUMP_PERIOD / 2,
                                   ProfileLanguageType.JAVA));
        }
        support.writeRecords(model, new ProfileThreadSnapshotRecord.Builder(), snapshots);
        dao = new ProfileThreadSnapshotQueryEsDAO(
            support.getClient(), support.getConfig().getProfileTaskQueryMaxSize());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsTheProfiledSegmentsOfATaskLatestDumpFirst() throws IOException {
        assertEquals(describe(Arrays.asList(goSnapshot(GO_SEGMENT_TODAY, MIDNIGHT + 5_000L),
                                            goSnapshot(GO_SEGMENT_YESTERDAY, MIDNIGHT - 5_000L))),
                     describe(dao.queryRecords(GO_TASK)));
        assertEquals(describe(Collections.singletonList(javaSnapshot(0))), describe(dao.queryRecords(JAVA_TASK)));
        assertEquals(Collections.emptyList(), dao.queryRecords("task-missing"));
    }

    /**
     * The analysis of a span reads the sequences dumped within the span's time, which here crosses midnight.
     */
    @Test
    public void findsTheSequenceRangeOfADumpTimeWindow() throws IOException {
        assertEquals(Arrays.asList(1, 4), sequenceRange(JAVA_SEGMENT, dumpTime(1), dumpTime(4)));
        assertEquals(Arrays.asList(2, 3), sequenceRange(JAVA_SEGMENT, dumpTime(1) + 1, dumpTime(4) - 1));
        assertEquals(Arrays.asList(4, 5), sequenceRange(JAVA_SEGMENT, dumpTime(4), dumpTime(JAVA_SNAPSHOTS)));
    }

    /**
     * The Go fallback of the analysis reads the whole sequence range of a segment, whatever its dump times.
     */
    @Test
    public void findsTheWholeSequenceRangeOfASegment() throws IOException {
        assertEquals(Arrays.asList(0, JAVA_SNAPSHOTS - 1), sequenceRange(JAVA_SEGMENT, 0L, Long.MAX_VALUE));
        assertEquals(Arrays.asList(0, 0), sequenceRange(GO_SEGMENT_TODAY, 0L, Long.MAX_VALUE));
    }

    @Test
    public void findsNoSequenceInAnEmptyWindow() throws IOException {
        final long afterLastDump = dumpTime(JAVA_SNAPSHOTS - 1) + 1;
        assertEquals(Arrays.asList(-1, -1), sequenceRange(JAVA_SEGMENT, afterLastDump, afterLastDump + 1_000L));
        assertEquals(Arrays.asList(-1, -1), sequenceRange("segment-missing", 0L, Long.MAX_VALUE));
    }

    @Test
    public void readsAdjacentSequenceBatchesWithoutOverlap() throws IOException {
        assertEquals(describe(Arrays.asList(javaSnapshot(0), javaSnapshot(1), javaSnapshot(2))),
                     describeBySequence(dao.queryRecords(JAVA_SEGMENT, 0, 3)));
        assertEquals(describe(Arrays.asList(javaSnapshot(3), javaSnapshot(4), javaSnapshot(5))),
                     describeBySequence(dao.queryRecords(JAVA_SEGMENT, 3, 6)));
        assertEquals(Collections.emptyList(), dao.queryRecords(JAVA_SEGMENT, JAVA_SNAPSHOTS, JAVA_SNAPSHOTS + 3));
    }

    /**
     * The analysis falls back to the Go profile of a segment, which it keeps only when the snapshot reads as Go.
     */
    @Test
    public void readsTheGoSnapshotOfASegmentWhole() throws IOException {
        assertEquals(describe(Collections.singletonList(goSnapshot(GO_SEGMENT_TODAY, MIDNIGHT + 5_000L))),
                     describe(dao.queryRecords(GO_SEGMENT_TODAY, 0, 1)));
    }

    private List<Integer> sequenceRange(final String segmentId, final long start, final long end) {
        return Arrays.asList(dao.queryMinSequence(segmentId, start, end), dao.queryMaxSequence(segmentId, start, end));
    }

    private static List<String> describe(final List<ProfileThreadSnapshotRecord> snapshots) {
        return snapshots.stream()
                        .map(snapshot -> String.join(
                            "|", snapshot.getTaskId(), snapshot.getSegmentId(),
                            String.valueOf(snapshot.getSequence()), String.valueOf(snapshot.getDumpTime()),
                            new String(snapshot.getStackBinary(), StandardCharsets.UTF_8),
                            snapshot.getLanguage().name(), String.valueOf(snapshot.getTimeBucket())))
                        .collect(Collectors.toList());
    }

    /**
     * A batch of sequences has no promised order.
     */
    private static List<String> describeBySequence(final List<ProfileThreadSnapshotRecord> snapshots) {
        return describe(snapshots.stream()
                                 .sorted(Comparator.comparingInt(ProfileThreadSnapshotRecord::getSequence))
                                 .collect(Collectors.toList()));
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
        snapshot.setStackBinary(("stack " + segmentId + " " + sequence).getBytes(StandardCharsets.UTF_8));
        snapshot.setLanguage(language);
        snapshot.setTimeBucket(TimeBucket.getRecordTimeBucket(dumpTime));
        return snapshot;
    }
}
