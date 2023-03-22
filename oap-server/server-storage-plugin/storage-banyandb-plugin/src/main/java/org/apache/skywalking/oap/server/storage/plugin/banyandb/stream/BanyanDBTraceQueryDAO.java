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
import org.apache.skywalking.banyandb.v1.client.Element;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Objects.nonNull;

public class BanyanDBTraceQueryDAO extends AbstractBanyanDBDAO implements ITraceQueryDAO {
    private static final Set<String> BASIC_TAGS = ImmutableSet.of(SegmentRecord.TRACE_ID,
            SegmentRecord.IS_ERROR,
            SegmentRecord.SERVICE_ID,
            SegmentRecord.SERVICE_INSTANCE_ID,
            SegmentRecord.ENDPOINT_ID,
            SegmentRecord.LATENCY,
            SegmentRecord.START_TIME,
            SegmentRecord.TAGS
    );

    private static final Set<String> TAGS = ImmutableSet.of(SegmentRecord.TRACE_ID,
            SegmentRecord.IS_ERROR,
            SegmentRecord.SERVICE_ID,
            SegmentRecord.SERVICE_INSTANCE_ID,
            SegmentRecord.ENDPOINT_ID,
            SegmentRecord.LATENCY,
            SegmentRecord.START_TIME,
            SegmentRecord.SEGMENT_ID,
            SegmentRecord.DATA_BINARY);

    public BanyanDBTraceQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public TraceBrief queryBasicTraces(Duration duration, long minDuration, long maxDuration, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }
        final QueryBuilder<StreamQuery> q = new QueryBuilder<StreamQuery>() {
            @Override
            public void apply(StreamQuery query) {
                if (minDuration != 0) {
                    // duration >= minDuration
                    query.and(gte(SegmentRecord.LATENCY, minDuration));
                }
                if (maxDuration != 0) {
                    // duration <= maxDuration
                    query.and(lte(SegmentRecord.LATENCY, maxDuration));
                }

                if (StringUtil.isNotEmpty(serviceId)) {
                    query.and(eq(SegmentRecord.SERVICE_ID, serviceId));
                }

                if (StringUtil.isNotEmpty(serviceInstanceId)) {
                    query.and(eq(SegmentRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
                }

                if (StringUtil.isNotEmpty(endpointId)) {
                    query.and(eq(SegmentRecord.ENDPOINT_ID, endpointId));
                }

                switch (traceState) {
                    case ERROR:
                        query.and(eq(SegmentRecord.IS_ERROR, BooleanUtils.TRUE));
                        break;
                    case SUCCESS:
                        query.and(eq(SegmentRecord.IS_ERROR, BooleanUtils.FALSE));
                        break;
                }

                switch (queryOrder) {
                    case BY_START_TIME:
                        query.setOrderBy(new StreamQuery.OrderBy(SegmentRecord.START_TIME, AbstractQuery.Sort.DESC));
                        break;
                    case BY_DURATION:
                        query.setOrderBy(new StreamQuery.OrderBy(SegmentRecord.LATENCY, AbstractQuery.Sort.DESC));
                        break;
                }

                if (CollectionUtils.isNotEmpty(tags)) {
                    List<String> tagsConditions = new ArrayList<>(tags.size());
                    for (final Tag tag : tags) {
                        tagsConditions.add(tag.toString());
                    }
                    query.and(having(SegmentRecord.TAGS, tagsConditions));
                }

                query.setLimit(limit);
                query.setOffset(from);
            }
        };

        TimestampRange tsRange = null;

        if (startSecondTB > 0 && endSecondTB > 0) {
            tsRange = new TimestampRange(TimeBucket.getTimestamp(startSecondTB), TimeBucket.getTimestamp(endSecondTB));
        }

        StreamQueryResponse resp = query(SegmentRecord.INDEX_NAME,
                BASIC_TAGS,
                tsRange, q);

        TraceBrief traceBrief = new TraceBrief();

        if (resp.size() == 0) {
            return traceBrief;
        }

        for (final Element row : resp.getElements()) {
            BasicTrace basicTrace = new BasicTrace();

            basicTrace.setSegmentId(row.getId());
            basicTrace.setStart(String.valueOf((Number) row.getTagValue(SegmentRecord.START_TIME)));
            basicTrace.getEndpointNames().add(IDManager.EndpointID.analysisId(
                    row.getTagValue(SegmentRecord.ENDPOINT_ID)
            ).getEndpointName());
            basicTrace.setDuration(((Number) row.getTagValue(SegmentRecord.LATENCY)).intValue());
            basicTrace.setError(BooleanUtils.valueToBoolean(
                    ((Number) row.getTagValue(SegmentRecord.IS_ERROR)).intValue()
            ));
            basicTrace.getTraceIds().add(row.getTagValue(SegmentRecord.TRACE_ID));

            traceBrief.getTraces().add(basicTrace);
        }

        return traceBrief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        StreamQueryResponse resp = query(SegmentRecord.INDEX_NAME, TAGS,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.and(eq(SegmentRecord.TRACE_ID, traceId));
                    }
                });
        return buildRecords(resp);
    }

    @Override
    public List<SegmentRecord> queryBySegmentIdList(List<String> segmentIdList) throws IOException {
        StreamQueryResponse resp = query(SegmentRecord.INDEX_NAME, TAGS,
            new QueryBuilder<StreamQuery>() {
                @Override
                public void apply(StreamQuery query) {
                    query.and(in(SegmentRecord.SEGMENT_ID, segmentIdList));
                }
            });
        return buildRecords(resp);
    }

    @Override
    public List<SegmentRecord> queryByTraceIdWithInstanceId(List<String> traceIdList, List<String> instanceIdList) throws IOException {
        StreamQueryResponse resp = query(SegmentRecord.INDEX_NAME, TAGS,
            new QueryBuilder<StreamQuery>() {
                @Override
                public void apply(StreamQuery query) {
                    query.and(in(SegmentRecord.TRACE_ID, traceIdList));
                    query.and(in(SegmentRecord.SERVICE_INSTANCE_ID, instanceIdList));
                }
            });
        return buildRecords(resp);
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }

    private List<SegmentRecord> buildRecords(StreamQueryResponse resp) {
        List<SegmentRecord> segmentRecords = new ArrayList<>(resp.getElements().size());

        for (final RowEntity rowEntity : resp.getElements()) {
            SegmentRecord segmentRecord = new SegmentRecord.Builder().storage2Entity(
                new BanyanDBConverter.StorageToStream(SegmentRecord.INDEX_NAME, rowEntity));
            segmentRecords.add(segmentRecord);
        }

        return segmentRecords;
    }
}
