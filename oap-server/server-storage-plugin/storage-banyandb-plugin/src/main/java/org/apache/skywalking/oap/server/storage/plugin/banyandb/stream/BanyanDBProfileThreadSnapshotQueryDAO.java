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

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * {@link ProfileThreadSnapshotRecord} is a stream
 */
public class BanyanDBProfileThreadSnapshotQueryDAO extends AbstractBanyanDBDAO implements IProfileThreadSnapshotQueryDAO {
    private static final Set<String> TAGS_BASIC = ImmutableSet.of(ProfileThreadSnapshotRecord.TASK_ID, ProfileThreadSnapshotRecord.SEGMENT_ID,
            ProfileThreadSnapshotRecord.DUMP_TIME, ProfileThreadSnapshotRecord.SEQUENCE);

    private static final Set<String> TAGS_ALL = ImmutableSet.of(ProfileThreadSnapshotRecord.TASK_ID,
            ProfileThreadSnapshotRecord.SEGMENT_ID,
            ProfileThreadSnapshotRecord.DUMP_TIME,
            ProfileThreadSnapshotRecord.SEQUENCE,
            ProfileThreadSnapshotRecord.STACK_BINARY);

    private static final Set<String> TAGS_TRACE = ImmutableSet.of(SegmentRecord.TRACE_ID,
            SegmentRecord.IS_ERROR,
            SegmentRecord.SERVICE_ID,
            SegmentRecord.SERVICE_INSTANCE_ID,
            SegmentRecord.ENDPOINT_ID,
            SegmentRecord.LATENCY,
            SegmentRecord.START_TIME);

    private static final Set<String> TAGS_TRACE_ALL = ImmutableSet.of(SegmentRecord.TRACE_ID,
            SegmentRecord.IS_ERROR,
            SegmentRecord.SERVICE_ID,
            SegmentRecord.SERVICE_INSTANCE_ID,
            SegmentRecord.ENDPOINT_ID,
            SegmentRecord.LATENCY,
            SegmentRecord.START_TIME,
            SegmentRecord.DATA_BINARY);

    private final int querySegmentMaxSize;

    protected final ProfileThreadSnapshotRecord.Builder builder =
            new ProfileThreadSnapshotRecord.Builder();

    public BanyanDBProfileThreadSnapshotQueryDAO(BanyanDBStorageClient client, int profileTaskQueryMaxSize) {
        super(client);
        this.querySegmentMaxSize = profileTaskQueryMaxSize;
    }

    @Override
    public List<String> queryProfiledSegmentIdList(String taskId) throws IOException {
        StreamQueryResponse resp = query(ProfileThreadSnapshotRecord.INDEX_NAME,
                TAGS_BASIC,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.and(eq(ProfileThreadSnapshotRecord.TASK_ID, taskId))
                                .and(eq(ProfileThreadSnapshotRecord.SEQUENCE, 0L));
                        query.setOrderBy(new StreamQuery.OrderBy(AbstractQuery.Sort.DESC));
                        query.setLimit(querySegmentMaxSize);
                    }
                });

        if (resp.getElements().isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> segmentIds = new LinkedList<>();
        for (final RowEntity rowEntity : resp.getElements()) {
            segmentIds.add(rowEntity.getTagValue(ProfileThreadSnapshotRecord.SEGMENT_ID));
        }

        return segmentIds;
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
                TAGS_ALL,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.and(eq(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                                .and(lte(ProfileThreadSnapshotRecord.SEQUENCE, maxSequence))
                                .and(gte(ProfileThreadSnapshotRecord.SEQUENCE, minSequence));
                    }
                });

        List<ProfileThreadSnapshotRecord> result = new ArrayList<>(maxSequence - minSequence);
        for (final RowEntity rowEntity : resp.getElements()) {
            ProfileThreadSnapshotRecord record = this.builder.storage2Entity(
                    new BanyanDBConverter.StorageToStream(ProfileThreadSnapshotRecord.INDEX_NAME, rowEntity));
            result.add(record);
        }
        return result;
    }

    private int querySequenceWithAgg(AggType aggType, String segmentId, long start, long end) throws IOException {
        StreamQueryResponse resp = query(ProfileThreadSnapshotRecord.INDEX_NAME,
                TAGS_ALL, new TimestampRange(start, end),
                new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.and(eq(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId));
                    }
                });

        List<ProfileThreadSnapshotRecord> records = new ArrayList<>();
        for (final RowEntity rowEntity : resp.getElements()) {
            ProfileThreadSnapshotRecord record = this.builder.storage2Entity(
                    new BanyanDBConverter.StorageToStream(ProfileThreadSnapshotRecord.INDEX_NAME, rowEntity));

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
