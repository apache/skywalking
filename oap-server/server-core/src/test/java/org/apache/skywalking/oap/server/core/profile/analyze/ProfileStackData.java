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

package org.apache.skywalking.oap.server.core.profile.analyze;

import com.google.common.base.Splitter;
import lombok.Data;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadStack;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzeTimeRange;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProfileStackData {

    private int limit;
    private String timeRanges;
    private List<String> snapshots;

    public List<ProfileThreadSnapshotRecord> transformSnapshots() {
        ArrayList<ProfileThreadSnapshotRecord> result = new ArrayList<>(snapshots.size());

        for (int i = 0; i < snapshots.size(); i++) {
            ProfileThreadSnapshotRecord stack = new ProfileThreadSnapshotRecord();
            stack.setSequence(i);
            stack.setDumpTime(i * limit);
            ThreadStack stackData = ThreadStack.newBuilder().addAllCodeSignatures(Splitter.on("-").splitToList(snapshots.get(i))).build();
            stack.setStackBinary(stackData.toByteArray());
            result.add(stack);
        }

        return result;
    }

    public List<ProfileAnalyzeTimeRange> transformTimeRanges() {
        final String[] timeRangeString = this.timeRanges.split(",");
        final ArrayList<ProfileAnalyzeTimeRange> ranges = new ArrayList<>();
        for (String timeRange : timeRangeString) {
            final ProfileAnalyzeTimeRange range = new ProfileAnalyzeTimeRange();
            final String[] startEndTimes = timeRange.split("-");

            range.setStart(Integer.parseInt(startEndTimes[0]) * limit);
            range.setEnd(Integer.parseInt(startEndTimes[1]) * limit);
            ranges.add(range);
        }

        return ranges;
    }

}
