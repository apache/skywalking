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

import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class IoTDBProfileThreadSnapshotQueryDAO implements IProfileThreadSnapshotQueryDAO {
    private final IoTDBClient client;
    private final StorageHashMapBuilder<ProfileThreadSnapshotRecord> storageBuilder = new ProfileThreadSnapshotRecord.Builder();

    public IoTDBProfileThreadSnapshotQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" from ")
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ProfileThreadSnapshotRecord.INDEX_NAME)
                .append(" where ").append(ProfileThreadSnapshotRecord.TASK_ID).append(" = '").append(taskId).append("'")
                .append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" = 0");

        // TODO why LinkedList
        List<String> segments = new LinkedList<>();
        List<? super StorageData> storageDataList = client.queryForList(ProfileThreadSnapshotRecord.INDEX_NAME,
                query.toString(), storageBuilder);
        storageDataList.forEach(storageData -> segments.add((String) storageData));
        if (segments.isEmpty()) {
            return Collections.emptyList();
        }

        query = new StringBuilder();
        query.append("select bottom_k(").append(SegmentRecord.START_TIME).append(", 'k'='")
                .append(segments.size()).append("') from ")
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(SegmentRecord.INDEX_NAME)
                .append(" where ");
        for (int i = 0; i < segments.size(); i++) {
            query.append(i > 0 ? " or " : "").append(SegmentRecord.SEGMENT_ID).append(" = '").append(segments.get(i)).append("'");
        }
        final List<Long> startTimeList = client.queryWithSelect(SegmentRecord.INDEX_NAME, query.toString());

        query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(SegmentRecord.INDEX_NAME)
                .append(" where ").append(SegmentRecord.START_TIME).append(" in (");
        for (int i = 0; i < startTimeList.size(); i++) {
            if (i == 0) {
                query.append(startTimeList.get(i));
            } else {
                query.append(", ").append(startTimeList.get(i));
            }
        }
        query.append(")");

        List<BasicTrace> result = new ArrayList<>(segments.size());
        storageDataList = client.queryForList(ProfileThreadSnapshotRecord.INDEX_NAME, query.toString(), storageBuilder);
        storageDataList.forEach(storageData -> {
            BasicTrace basicTrace = new BasicTrace();
            SegmentRecord segmentRecord = (SegmentRecord) storageData;

            basicTrace.setSegmentId(segmentRecord.getSegmentId());
            basicTrace.setStart(String.valueOf(segmentRecord.getStartTime()));
            basicTrace.getEndpointNames().add(IDManager.EndpointID.analysisId(segmentRecord.getEndpointId()).getEndpointName());
            basicTrace.setDuration(segmentRecord.getLatency());
            basicTrace.setError(BooleanUtils.valueToBoolean(segmentRecord.getIsError()));
            basicTrace.getTraceIds().add(segmentRecord.getSegmentId());

            result.add(basicTrace);
        });
        // resort by self, because of the select query result order by time.
        result.sort((a, b) -> Long.compare(Long.parseLong(b.getStart()), Long.parseLong(a.getStart())));
        return result;
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg("max_value", segmentId, start, end);
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg("min_value", segmentId, start, end);
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT)
                .append(ProfileThreadSnapshotRecord.INDEX_NAME).append(" where 1=1")
                .append(" and ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" = '").append(segmentId).append("'")
                .append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" >= ").append(minSequence)
                .append(" and ").append(ProfileThreadSnapshotRecord.SEQUENCE).append(" <= ").append(maxSequence);

        List<ProfileThreadSnapshotRecord> profileThreadSnapshotRecordList = new ArrayList<>();
        List<? super StorageData> storageDataList = client.queryForList(ProfileThreadSnapshotRecord.INDEX_NAME,
                query.toString(), storageBuilder);
        storageDataList.forEach(storageData -> profileThreadSnapshotRecordList.add((ProfileThreadSnapshotRecord) storageData));
        return profileThreadSnapshotRecordList;
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT)
                .append(ProfileThreadSnapshotRecord.INDEX_NAME).append(" where ")
                .append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" = '").append(segmentId).append("'");

        List<? super StorageData> storageDataList = client.queryForList(SegmentRecord.INDEX_NAME,
                query.toString(), storageBuilder);
        if (storageDataList.isEmpty()) {
            return null;
        }
        return (SegmentRecord) storageDataList.get(0);
    }

    private int querySequenceWithAgg(String aggType, String segmentId, long start, long end) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select ")
                .append(aggType)
                .append("(")
                .append(ProfileThreadSnapshotRecord.SEQUENCE)
                .append(") from ")
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ProfileThreadSnapshotRecord.INDEX_NAME)
                .append(" where ");
        query.append(" 1=1 ");
        query.append(" and ").append(ProfileThreadSnapshotRecord.SEGMENT_ID).append(" = '").append(segmentId).append("'");
        query.append(" and ").append(ProfileThreadSnapshotRecord.DUMP_TIME).append(" >= ").append(start);
        query.append(" and ").append(ProfileThreadSnapshotRecord.DUMP_TIME).append(" <= ").append(end);

        return client.queryWithAgg(ProfileThreadSnapshotRecord.INDEX_NAME, query.toString());
    }
}
