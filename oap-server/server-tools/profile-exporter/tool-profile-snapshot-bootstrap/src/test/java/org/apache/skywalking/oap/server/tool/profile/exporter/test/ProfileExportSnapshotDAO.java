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

package org.apache.skywalking.oap.server.tool.profile.exporter.test;

import org.apache.skywalking.apm.network.language.profile.v3.ThreadStack;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProfileExportSnapshotDAO implements IProfileThreadSnapshotQueryDAO {

    private final ExportedData exportedData;

    public ProfileExportSnapshotDAO(ExportedData exportedData) {
        this.exportedData = exportedData;
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        final BasicTrace basicTrace = new BasicTrace();
        basicTrace.setSegmentId(exportedData.getSegmentId());
        basicTrace.getTraceIds().add(exportedData.getTraceId());
        basicTrace.setStart(exportedData.getSpans().get(0).getStart() + "");
        basicTrace.setDuration(exportedData.getSpans().get(0).getEnd());
        return Collections.singletonList(basicTrace);
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        for (int i = 0; i < exportedData.getSnapshots().size(); i++) {
            if (i * exportedData.getLimit() >= start) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        for (int i = exportedData.getSnapshots().size() - 1; i >= 0; i--) {
            if (i * exportedData.getLimit() <= end) {
                return i + 1;
            }
        }
        return 0;
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException {
        final ArrayList<ProfileThreadSnapshotRecord> records = new ArrayList<>();
        for (int i = 0; i < exportedData.getSnapshots().size(); i++) {
            if (i >= minSequence && i < maxSequence) {
                final ProfileThreadSnapshotRecord record = new ProfileThreadSnapshotRecord();
                record.setSequence(i);
                record.setDumpTime(i * exportedData.getLimit());
                final ThreadStack.Builder stack = ThreadStack.newBuilder().addAllCodeSignatures(Arrays.asList(exportedData.getSnapshots().get(i).split("-")));
                record.setStackBinary(stack.build().toByteArray());

                records.add(record);
            }
        }
        return records;
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        return null;
    }
}
