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

package org.apache.skywalking.oap.server.core.storage.profile;

import java.io.IOException;
import java.util.List;

import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.DAO;

/**
 * {@link ProfileThreadSnapshotRecord} database queries
 */
public interface IProfileThreadSnapshotQueryDAO extends DAO {

    /**
     * search all profiled segments, need appoint taskId and snapshot sequence equals 0 sort by segment start time
     *
     * @return it represents the segments having profile snapshot data.
     */
    List<BasicTrace> queryProfiledSegments(String taskId) throws IOException;

    /**
     * search snapshots min sequence
     * @return min sequence, return -1 if not found data
     */
    int queryMinSequence(String segmentId, long start, long end) throws IOException;

    /**
     * search snapshots max sequence
     * @return max sequence, return -1 if not found data
     */
    int queryMaxSequence(String segmentId, long start, long end) throws IOException;

    /**
     * search snapshots with sequence range
     * @param minSequence min sequence, include self
     * @param maxSequence max sequence, exclude self
     * @return snapshots
     */
    List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException;

    /**
     * search segment data
     */
    SegmentRecord getProfiledSegment(String segmentId) throws IOException;
}
