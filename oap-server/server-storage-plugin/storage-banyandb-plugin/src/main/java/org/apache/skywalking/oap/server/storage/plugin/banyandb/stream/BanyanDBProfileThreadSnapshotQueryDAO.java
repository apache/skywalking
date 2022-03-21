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
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.StreamMetadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ProfileThreadSnapshotRecord} is a stream
 */
public class BanyanDBProfileThreadSnapshotQueryDAO extends AbstractBanyanDBDAO implements IProfileThreadSnapshotQueryDAO {
    protected final ProfileThreadSnapshotRecord.Builder builder =
            new ProfileThreadSnapshotRecord.Builder();

    private final StreamMetadata profileThreadSnapshotMetadata =
            MetadataRegistry.INSTANCE.findStreamMetadata(ProfileThreadSnapshotRecord.INDEX_NAME);

    private final StreamMetadata segmentRecordMetadata =
            MetadataRegistry.INSTANCE.findStreamMetadata(SegmentRecord.INDEX_NAME);

    public BanyanDBProfileThreadSnapshotQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        StreamQueryResponse resp = query(profileThreadSnapshotMetadata,
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

        final List<String> segmentIds = new LinkedList<>();
        for (final RowEntity rowEntity : resp.getElements()) {
            segmentIds.add(rowEntity.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, ProfileThreadSnapshotRecord.SEGMENT_ID));
        }

        // TODO: support `IN` or `OR` logic operation in BanyanDB
        List<BasicTrace> basicTraces = new ArrayList<>();
        for (String segmentID : segmentIds) {
            final StreamQueryResponse segmentRecordResp = query(segmentRecordMetadata,
                    ImmutableList.of(SegmentRecord.TRACE_ID, SegmentRecord.IS_ERROR, SegmentRecord.ENDPOINT_ID, SegmentRecord.LATENCY, SegmentRecord.START_TIME),
                    new QueryBuilder() {
                        @Override
                        public void apply(StreamQuery traceQuery) {
                            traceQuery.appendCondition(eq(SegmentRecord.SEGMENT_ID, segmentID));
                        }
                    });

            for (final RowEntity row : segmentRecordResp.getElements()) {
                BasicTrace basicTrace = new BasicTrace();

                basicTrace.setSegmentId(row.getId());
                basicTrace.setStart(String.valueOf(row.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.START_TIME)));
                basicTrace.getEndpointNames().add(IDManager.EndpointID.analysisId(
                        row.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.ENDPOINT_ID)
                ).getEndpointName());
                basicTrace.setDuration(((Number) row.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.LATENCY)).intValue());
                basicTrace.setError(BooleanUtils.valueToBoolean(
                        ((Number) row.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.IS_ERROR)).intValue()
                ));
                basicTrace.getTraceIds().add(row.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.TRACE_ID));

                basicTraces.add(basicTrace);
            }
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
        StreamQueryResponse resp = query(profileThreadSnapshotMetadata,
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

        List<ProfileThreadSnapshotRecord> result = new ArrayList<>(maxSequence - minSequence);
        for (final RowEntity rowEntity : resp.getElements()) {
            ProfileThreadSnapshotRecord record = this.builder.storage2Entity(
                    new BanyanDBConverter.StreamToEntity(profileThreadSnapshotMetadata, rowEntity));

            result.add(record);
        }
        return result;
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        StreamQueryResponse resp = query(segmentRecordMetadata,
                ImmutableList.of(SegmentRecord.TRACE_ID, SegmentRecord.IS_ERROR, SegmentRecord.SERVICE_ID, SegmentRecord.SERVICE_INSTANCE_ID, SegmentRecord.ENDPOINT_ID, SegmentRecord.LATENCY, SegmentRecord.START_TIME),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(Collections.singletonList(SegmentRecord.DATA_BINARY));
                        query.appendCondition(eq(SegmentRecord.INDEX_NAME, segmentId));
                    }
                });

        if (resp.size() == 0) {
            return null;
        }

        final RowEntity rowEntity = resp.getElements().iterator().next();
        final SegmentRecord segmentRecord = new SegmentRecord();
        segmentRecord.setSegmentId(rowEntity.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.SEGMENT_ID));
        segmentRecord.setTraceId(rowEntity.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.TRACE_ID));
        segmentRecord.setServiceId(rowEntity.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.SERVICE_ID));
        segmentRecord.setStartTime(
                ((Number) rowEntity.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.START_TIME)).longValue());
        segmentRecord.setLatency(
                ((Number) rowEntity.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.LATENCY)).intValue());
        segmentRecord.setIsError(
                ((Number) rowEntity.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE, SegmentRecord.IS_ERROR)).intValue());
        byte[] dataBinary = rowEntity.getValue(StreamMetadata.TAG_FAMILY_DATA, SegmentRecord.DATA_BINARY);
        if (dataBinary != null && dataBinary.length > 0) {
            segmentRecord.setDataBinary(dataBinary);
        }
        return segmentRecord;
    }

    private int querySequenceWithAgg(AggType aggType, String segmentId, long start, long end) {
        StreamQueryResponse resp = query(profileThreadSnapshotMetadata,
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

        List<ProfileThreadSnapshotRecord> records = new ArrayList<>();
        for (final RowEntity rowEntity : resp.getElements()) {
            ProfileThreadSnapshotRecord record = this.builder.storage2Entity(
                    new BanyanDBConverter.StreamToEntity(profileThreadSnapshotMetadata, rowEntity));

            records.add(record);
        }

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
}