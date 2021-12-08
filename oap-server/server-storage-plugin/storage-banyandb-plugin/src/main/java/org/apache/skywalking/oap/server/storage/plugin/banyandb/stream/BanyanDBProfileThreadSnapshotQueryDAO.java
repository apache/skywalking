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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ProfileThreadSnapshotRecord} is a stream
 */
public class BanyanDBProfileThreadSnapshotQueryDAO extends AbstractBanyanDBDAO implements IProfileThreadSnapshotQueryDAO {
    public BanyanDBProfileThreadSnapshotQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        StreamQueryResponse resp = query(ProfileThreadSnapshotRecord.INDEX_NAME,
                ImmutableList.of(ProfileThreadSnapshotRecord.TASK_ID, ProfileThreadSnapshotRecord.SEGMENT_ID,
                        ProfileThreadSnapshotRecord.DUMP_TIME, ProfileThreadSnapshotRecord.SEQUENCE),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.appendCondition(eq(ProfileThreadSnapshotRecord.TASK_ID, taskId))
                                .appendCondition(eq(ProfileThreadSnapshotRecord.SEQUENCE, 0L));
                    }
                });

        if (resp.getElements().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> segmentIDs = resp.getElements().stream()
                .map(new ProfileThreadSnapshotRecordDeserializer())
                .map(ProfileThreadSnapshotRecord::getSegmentId)
                .collect(Collectors.toList());

        // TODO: support `IN` or `OR` logic operation in BanyanDB
        List<BasicTrace> basicTraces = new ArrayList<>();
        for (String segmentID : segmentIDs) {
            final StreamQueryResponse segmentRecordResp = query(SegmentRecord.INDEX_NAME, ImmutableList.of("trace_id", "state", "endpoint_id", "duration", "start_time"),
                    new QueryBuilder() {
                        @Override
                        public void apply(StreamQuery traceQuery) {
                            traceQuery.appendCondition(eq(SegmentRecord.SEGMENT_ID, segmentID));
                        }
                    });
            basicTraces.addAll(segmentRecordResp.getElements().stream().map(new BasicTraceDeserializer()).collect(Collectors.toList()));
        }

        // TODO: Sort in DB with DESC
        basicTraces = basicTraces.stream()
                // comparing start_time
                .sorted(Comparator.comparing((Function<BasicTrace, Long>) basicTrace -> Long.parseLong(basicTrace.getStart()))
                        // and sort in reverse order
                        .reversed())
                .collect(Collectors.toList());
        return basicTraces;
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg(AggType.MIN, segmentId, start, end);
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg(AggType.MAX, segmentId, start, end);
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException {
        StreamQueryResponse resp = query(ProfileThreadSnapshotRecord.INDEX_NAME,
                ImmutableList.of(ProfileThreadSnapshotRecord.TASK_ID, ProfileThreadSnapshotRecord.SEGMENT_ID,
                        ProfileThreadSnapshotRecord.DUMP_TIME, ProfileThreadSnapshotRecord.SEQUENCE),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(Collections.singletonList(ProfileThreadSnapshotRecord.STACK_BINARY));

                        query.appendCondition(eq(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                                .appendCondition(lte(ProfileThreadSnapshotRecord.SEQUENCE, maxSequence))
                                .appendCondition(gte(ProfileThreadSnapshotRecord.SEQUENCE, minSequence));
                    }
                });

        return resp.getElements().stream().map(new ProfileThreadSnapshotRecordDeserializer()).collect(Collectors.toList());
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        StreamQueryResponse resp = query(SegmentRecord.INDEX_NAME,
                ImmutableList.of("trace_id", "state", "service_id", "service_instance_id", "endpoint_id", "duration", "start_time"),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(Collections.singletonList("data_binary"));
                        query.appendCondition(eq(SegmentRecord.INDEX_NAME, segmentId));
                    }
                });

        return resp.getElements().stream().map(new SegmentRecordDeserializer()).findFirst().orElse(null);
    }

    private int querySequenceWithAgg(AggType aggType, String segmentId, long start, long end) {
        StreamQueryResponse resp = query(ProfileThreadSnapshotRecord.INDEX_NAME,
                ImmutableList.of(ProfileThreadSnapshotRecord.TASK_ID, ProfileThreadSnapshotRecord.SEGMENT_ID,
                        ProfileThreadSnapshotRecord.DUMP_TIME, ProfileThreadSnapshotRecord.SEQUENCE),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(Collections.singletonList(ProfileThreadSnapshotRecord.STACK_BINARY));

                        query.appendCondition(eq(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                                .appendCondition(lte(ProfileThreadSnapshotRecord.DUMP_TIME, end))
                                .appendCondition(gte(ProfileThreadSnapshotRecord.DUMP_TIME, start));
                    }
                });

        List<ProfileThreadSnapshotRecord> records = resp.getElements().stream().map(new ProfileThreadSnapshotRecordDeserializer()).collect(Collectors.toList());

        switch (aggType) {
            case MIN:
                int minValue = Integer.MAX_VALUE;
                for (final ProfileThreadSnapshotRecord record : records) {
                    int sequence = record.getSequence();
                    minValue = Math.min(minValue, sequence);
                }
                return minValue;
            case MAX:
                int maxValue = Integer.MIN_VALUE;
                for (ProfileThreadSnapshotRecord record : records) {
                    int sequence = record.getSequence();
                    maxValue = Math.max(maxValue, sequence);
                }
                return maxValue;
            default:
                throw new IllegalArgumentException("should not reach this line");
        }
    }

    enum AggType {
        MIN, MAX
    }

    public static class ProfileThreadSnapshotRecordDeserializer implements RowEntityDeserializer<ProfileThreadSnapshotRecord> {
        @Override
        public ProfileThreadSnapshotRecord apply(RowEntity row) {
            ProfileThreadSnapshotRecord record = new ProfileThreadSnapshotRecord();
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            record.setTaskId((String) searchable.get(0).getValue());
            record.setSegmentId((String) searchable.get(1).getValue());
            record.setDumpTime(((Number) searchable.get(2).getValue()).longValue());
            record.setSequence(((Number) searchable.get(3).getValue()).intValue());
            final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
            record.setStackBinary(((ByteString) data.get(0).getValue()).toByteArray());
            return record;
        }
    }

    public static class SegmentRecordDeserializer implements RowEntityDeserializer<SegmentRecord> {
        @Override
        public SegmentRecord apply(RowEntity row) {
            SegmentRecord record = new SegmentRecord();
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            record.setSegmentId(row.getId());
            record.setTraceId((String) searchable.get(0).getValue());
            record.setIsError(((Number) searchable.get(1).getValue()).intValue());
            record.setServiceId((String) searchable.get(2).getValue());
            record.setServiceInstanceId((String) searchable.get(3).getValue());
            record.setEndpointId((String) searchable.get(4).getValue());
            record.setLatency(((Number) searchable.get(5).getValue()).intValue());
            record.setStartTime(((Number) searchable.get(6).getValue()).longValue());
            final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
            record.setDataBinary(((ByteString) data.get(0).getValue()).toByteArray());
            return record;
        }
    }

    public static class BasicTraceDeserializer implements RowEntityDeserializer<BasicTrace> {
        @Override
        public BasicTrace apply(RowEntity row) {
            BasicTrace trace = new BasicTrace();
            trace.setSegmentId(row.getId());
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            trace.getTraceIds().add((String) searchable.get(0).getValue());
            trace.setError(((Long) searchable.get(1).getValue()).intValue() == 1);
            trace.getEndpointNames().add(IDManager.EndpointID.analysisId(
                    (String) searchable.get(2).getValue()
            ).getEndpointName());
            trace.setDuration(((Long) searchable.get(3).getValue()).intValue());
            trace.setStart(String.valueOf(searchable.get(4).getValue()));
            return trace;
        }
    }
}