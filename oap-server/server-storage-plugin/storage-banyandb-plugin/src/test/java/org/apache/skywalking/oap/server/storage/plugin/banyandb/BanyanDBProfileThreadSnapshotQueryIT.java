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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileLanguageType;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileThreadSnapshotQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads trace-profiling thread snapshots the way the profile analysis does, on the real
 * {@code profile_task_segment_snapshot} stream: a task's profiled segments, a segment's sequence range in a dump-time
 * window (-1 when there is none), and the snapshots of {@code [minSequence, maxSequence)} — the analysis reads
 * adjacent batches that meet at their ends — each with its language, so a Go profile is told from a Java one.
 */
@Testcontainers
public class BanyanDBProfileThreadSnapshotQueryIT {
    private static final String TASK = "task-read";
    private static final String JAVA_SEGMENT = "segment-java";
    private static final String GO_SEGMENT = "segment-go";
    private static final int JAVA_SNAPSHOTS = 6;
    private static final int GO_SNAPSHOTS = 3;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBProfileThreadSnapshotQueryDAO QUERY_DAO;
    private static long FIRST_DUMP;

    @BeforeAll
    public static void writeSnapshots() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(ProfileThreadSnapshotRecord.class,
                                            DefaultScopeDefine.PROFILE_TASK_SEGMENT_SNAPSHOT,
                                            ProfileThreadSnapshotRecord.INDEX_NAME);
        FIRST_DUMP = (System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)) / 1000L * 1000L;
        final List<ProfileThreadSnapshotRecord> snapshots = new ArrayList<>();
        for (int sequence = 0; sequence < JAVA_SNAPSHOTS; sequence++) {
            snapshots.add(snapshot(TASK, JAVA_SEGMENT, sequence, javaDump(sequence), ProfileLanguageType.JAVA));
        }
        for (int sequence = 0; sequence < GO_SNAPSHOTS; sequence++) {
            snapshots.add(snapshot(TASK, GO_SEGMENT, sequence, goDump(sequence), ProfileLanguageType.GO));
        }
        snapshots.add(snapshot("task-other", "segment-other", 0, FIRST_DUMP, ProfileLanguageType.JAVA));
        SUPPORT.write(model, new ProfileThreadSnapshotRecord.Builder(), snapshots);

        QUERY_DAO = new BanyanDBProfileThreadSnapshotQueryDAO(SUPPORT.getClient(), 200);
        await().atMost(30, TimeUnit.SECONDS)
               .untilAsserted(() -> assertEquals(2, QUERY_DAO.queryRecords(TASK).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsTheProfiledSegmentsOfATaskNewestFirst() throws IOException {
        assertEquals(Arrays.asList(GO_SEGMENT, JAVA_SEGMENT), QUERY_DAO.queryRecords(TASK).stream()
            .map(ProfileThreadSnapshotRecord::getSegmentId).collect(Collectors.toList()));
    }

    @Test
    public void findsTheSequenceRangeOfADumpTimeWindow() throws IOException {
        assertEquals(1, QUERY_DAO.queryMinSequence(JAVA_SEGMENT, javaDump(1), javaDump(4)));
        assertEquals(4, QUERY_DAO.queryMaxSequence(JAVA_SEGMENT, javaDump(1), javaDump(4)));
    }

    @Test
    public void findsNoSequenceInAnEmptyWindow() throws IOException {
        final long before = FIRST_DUMP - TimeUnit.MINUTES.toMillis(1);
        assertEquals(-1, QUERY_DAO.queryMinSequence(JAVA_SEGMENT, before, before + 1000L));
        assertEquals(-1, QUERY_DAO.queryMaxSequence(JAVA_SEGMENT, before, before + 1000L));
        assertEquals(-1, QUERY_DAO.queryMinSequence("segment-missing", 0L, Long.MAX_VALUE));
    }

    @Test
    public void readsAdjacentSequenceBatchesWithoutOverlap() throws IOException {
        assertEquals(Arrays.asList(0, 1, 2), sequences(QUERY_DAO.queryRecords(JAVA_SEGMENT, 0, 3)));
        assertEquals(Arrays.asList(3, 4, 5), sequences(QUERY_DAO.queryRecords(JAVA_SEGMENT, 3, 6)));
    }

    @Test
    public void readsEachSnapshotWhole() throws IOException {
        final List<String> expected = new ArrayList<>();
        for (int sequence = 0; sequence < GO_SNAPSHOTS; sequence++) {
            expected.add(describe(snapshot(TASK, GO_SEGMENT, sequence, goDump(sequence), ProfileLanguageType.GO)));
        }
        final List<String> read = QUERY_DAO.queryRecords(GO_SEGMENT, 0, GO_SNAPSHOTS).stream()
            .map(BanyanDBProfileThreadSnapshotQueryIT::describe).sorted().collect(Collectors.toList());
        assertEquals(expected, read);
    }

    private static List<Integer> sequences(List<ProfileThreadSnapshotRecord> snapshots) {
        return snapshots.stream().map(ProfileThreadSnapshotRecord::getSequence).sorted().collect(Collectors.toList());
    }

    private static String describe(ProfileThreadSnapshotRecord snapshot) {
        return String.join("|", snapshot.getTaskId(), snapshot.getSegmentId(), String.valueOf(snapshot.getSequence()),
                           String.valueOf(snapshot.getDumpTime()), new String(snapshot.getStackBinary()),
                           snapshot.getLanguage().name(), String.valueOf(snapshot.getTimeBucket()));
    }

    private static long javaDump(int sequence) {
        return FIRST_DUMP + sequence * 1000L;
    }

    private static long goDump(int sequence) {
        return FIRST_DUMP + 10_000L + sequence * 1000L;
    }

    private static ProfileThreadSnapshotRecord snapshot(String task, String segment, int sequence, long dumpTime,
                                                        ProfileLanguageType language) {
        final ProfileThreadSnapshotRecord snapshot = new ProfileThreadSnapshotRecord();
        snapshot.setTaskId(task);
        snapshot.setSegmentId(segment);
        snapshot.setSequence(sequence);
        snapshot.setDumpTime(dumpTime);
        snapshot.setStackBinary(("stack " + segment + " " + sequence).getBytes());
        snapshot.setLanguage(language);
        snapshot.setTimeBucket(TimeBucket.getRecordTimeBucket(dumpTime));
        return snapshot;
    }
}
