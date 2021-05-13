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

package org.apache.skywalking.oap.server.tool.profile.exporter;

import com.google.common.primitives.Ints;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileAnalyzeSnapshotDAO implements IProfileThreadSnapshotQueryDAO {

    private final List<ThreadSnapshot> snapshots;

    public ProfileAnalyzeSnapshotDAO(List<ThreadSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        return null;
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        return snapshots.stream().sorted(Comparator.comparingInt(ThreadSnapshot::getSequence)).findFirst().get().getSequence();
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        return snapshots.stream().sorted((s1, s2) -> -Ints.compare(s1.getSequence(), s2.getSequence())).findFirst().get().getSequence();
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException {
        return snapshots.parallelStream()
                .filter(s -> s.getSequence() >= minSequence && s.getSequence() < maxSequence)
                .map(this::buildFromSnapshot)
                .collect(Collectors.toList());
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        return null;
    }

    private ProfileThreadSnapshotRecord buildFromSnapshot(ThreadSnapshot snapshot) {
        final ProfileThreadSnapshotRecord record = new ProfileThreadSnapshotRecord();
        record.setStackBinary(snapshot.getStack().toByteArray());
        record.setDumpTime(snapshot.getTime());
        record.setSequence(snapshot.getSequence());
        return record;
    }
}
