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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.profile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

@RequiredArgsConstructor
public class IoTDBProfileThreadSnapshotQueryDAO implements IProfileThreadSnapshotQueryDAO {
    private final IoTDBClient client;
    private final StorageBuilder<ProfileThreadSnapshotRecord> profileThreadSnapshotRecordBuilder = new ProfileThreadSnapshotRecord.Builder();
    private final StorageBuilder<SegmentRecord> segmentRecordBuilder = new SegmentRecord.Builder();

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ProfileThreadSnapshotRecord.INDEX_NAME);
        query = client.addQueryAsterisk(ProfileThreadSnapshotRecord.INDEX_NAME, query);
        query.append(" where ").append(ProfileThreadSnapshotRecord.TASK_ID).append(" = \"").append(taskId).append("\"")
                .append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" = 0")
                .append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ProfileThreadSnapshotRecord.INDEX_NAME,
                query.toString(), profileThreadSnapshotRecordBuilder);
        // We can insure the size of List, so use ArrayList to improve visit speed. (Other storage plugin use LinkedList)
        final List<String> segmentIds = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> segmentIds.add(((ProfileThreadSnapshotRecord) storageData).getSegmentId()));
        if (segmentIds.isEmpty()) {
            return Collections.emptyList();
        }

        // This method maybe have poor efficiency. It queries all data which meets a condition without select function.
        // https://github.com/apache/iotdb/discussions/3888
        query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, SegmentRecord.INDEX_NAME);
        query = client.addQueryAsterisk(SegmentRecord.INDEX_NAME, query);
        query.append(" where ").append(SegmentRecord.SEGMENT_ID).append(" in (");
        for (String segmentId : segmentIds) {
            query.append("\"").append(segmentId).append("\"").append(", ");
        }
        query.delete(query.length() - 2, query.length()).append(")").append(IoTDBClient.ALIGN_BY_DEVICE);

        storageDataList = client.filterQuery(SegmentRecord.INDEX_NAME, query.toString(), segmentRecordBuilder);
        List<SegmentRecord> segmentRecordList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> segmentRecordList.add((SegmentRecord) storageData));
        // resort by self, because of the select query result order by time.
        segmentRecordList.sort((SegmentRecord r1, SegmentRecord r2) -> Long.compare(r2.getStartTime(), r1.getStartTime()));

        List<BasicTrace> result = new ArrayList<>(segmentRecordList.size());
        segmentRecordList.forEach(segmentRecord -> {
            BasicTrace basicTrace = new BasicTrace();
            basicTrace.setSegmentId(segmentRecord.getSegmentId());
            basicTrace.setStart(String.valueOf(segmentRecord.getStartTime()));
            basicTrace.getEndpointNames().add(IDManager.EndpointID.analysisId(segmentRecord.getEndpointId()).getEndpointName());
            basicTrace.setDuration(segmentRecord.getLatency());
            basicTrace.setError(BooleanUtils.valueToBoolean(segmentRecord.getIsError()));
            basicTrace.getTraceIds().add(segmentRecord.getTraceId());
            result.add(basicTrace);
        });
        return result;
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg("min_value", segmentId, start, end);
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg("max_value", segmentId, start, end);
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ProfileThreadSnapshotRecord.INDEX_NAME);
        query = client.addQueryAsterisk(ProfileThreadSnapshotRecord.INDEX_NAME, query);
        query.append(" where ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" = \"").append(segmentId).append("\"")
                .append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" >= ").append(minSequence)
                .append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" <= ").append(maxSequence)
                .append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ProfileThreadSnapshotRecord.INDEX_NAME,
                query.toString(), profileThreadSnapshotRecordBuilder);
        List<ProfileThreadSnapshotRecord> profileThreadSnapshotRecordList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> profileThreadSnapshotRecordList.add((ProfileThreadSnapshotRecord) storageData));
        return profileThreadSnapshotRecordList;
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, SegmentRecord.INDEX_NAME);
        query = client.addQueryAsterisk(SegmentRecord.INDEX_NAME, query);
        query.append(" where ").append(SegmentRecord.SEGMENT_ID).append(" = \"").append(segmentId).append("\"")
                .append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(SegmentRecord.INDEX_NAME,
                query.toString(), segmentRecordBuilder);
        if (storageDataList.isEmpty()) {
            return null;
        }
        return (SegmentRecord) storageDataList.get(0);
    }

    private int querySequenceWithAgg(String aggType, String segmentId, long start, long end) throws IOException {
        // This method has poor efficiency. It queries all data which meets a condition without aggregation function
        // See https://github.com/apache/iotdb/discussions/3907
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ProfileThreadSnapshotRecord.INDEX_NAME);
        query = client.addQueryAsterisk(ProfileThreadSnapshotRecord.INDEX_NAME, query);
        query.append(" where ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" = \"").append(segmentId).append("\"")
                .append(" and ").append(ProfileThreadSnapshotRecord.DUMP_TIME).append(" >= ").append(start)
                .append(" and ").append(ProfileThreadSnapshotRecord.DUMP_TIME).append(" <= ").append(end)
                .append(IoTDBClient.ALIGN_BY_DEVICE);
        List<? super StorageData> storageDataList = client.filterQuery(ProfileThreadSnapshotRecord.INDEX_NAME,
                query.toString(), profileThreadSnapshotRecordBuilder);

        if (aggType.equals("min_value")) {
            int minValue = Integer.MAX_VALUE;
            for (Object storageData : storageDataList) {
                ProfileThreadSnapshotRecord profileThreadSnapshotRecord = (ProfileThreadSnapshotRecord) storageData;
                int sequence = profileThreadSnapshotRecord.getSequence();
                minValue = Math.min(minValue, sequence);
            }
            return minValue;
        } else if (aggType.equals("max_value")) {
            int maxValue = Integer.MIN_VALUE;
            for (Object storageData : storageDataList) {
                ProfileThreadSnapshotRecord profileThreadSnapshotRecord = (ProfileThreadSnapshotRecord) storageData;
                int sequence = profileThreadSnapshotRecord.getSequence();
                maxValue = Math.max(maxValue, sequence);
            }
            return maxValue;
        } else {
            throw new IOException("Wrong aggregation function");
        }
    }
}
