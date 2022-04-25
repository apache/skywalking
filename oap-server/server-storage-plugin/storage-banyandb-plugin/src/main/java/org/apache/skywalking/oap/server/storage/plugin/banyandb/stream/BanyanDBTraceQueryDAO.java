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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BanyanDBTraceQueryDAO extends AbstractBanyanDBDAO implements ITraceQueryDAO {
    public BanyanDBTraceQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        final QueryBuilder<StreamQuery> q = new QueryBuilder<StreamQuery>() {
            @Override
            public void apply(StreamQuery query) {
                if (minDuration != 0) {
                    // duration >= minDuration
                    query.appendCondition(gte(SegmentRecord.LATENCY, minDuration));
                }
                if (maxDuration != 0) {
                    // duration <= maxDuration
                    query.appendCondition(lte(SegmentRecord.LATENCY, maxDuration));
                }

                if (!Strings.isNullOrEmpty(serviceId)) {
                    query.appendCondition(eq(SegmentRecord.SERVICE_ID, serviceId));
                }

                if (!Strings.isNullOrEmpty(serviceInstanceId)) {
                    query.appendCondition(eq(SegmentRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
                }

                if (!Strings.isNullOrEmpty(endpointId)) {
                    query.appendCondition(eq(SegmentRecord.ENDPOINT_ID, endpointId));
                }

                switch (traceState) {
                    case ERROR:
                        query.appendCondition(eq(SegmentRecord.IS_ERROR, BooleanUtils.TRUE));
                        break;
                    case SUCCESS:
                        query.appendCondition(eq(SegmentRecord.IS_ERROR, BooleanUtils.FALSE));
                        break;
                }

                switch (queryOrder) {
                    case BY_START_TIME:
                        query.setOrderBy(new StreamQuery.OrderBy(SegmentRecord.START_TIME, StreamQuery.OrderBy.Type.DESC));
                        break;
                    case BY_DURATION:
                        query.setOrderBy(new StreamQuery.OrderBy(SegmentRecord.LATENCY, StreamQuery.OrderBy.Type.DESC));
                        break;
                }

                if (CollectionUtils.isNotEmpty(tags)) {
                    for (final Tag tag : tags) {
                        // TODO: check if we have this tag indexed?
                        query.appendCondition(eq(tag.getKey(), tag.getValue()));
                    }
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
                ImmutableSet.of(SegmentRecord.TRACE_ID, // 0 - trace_id
                        SegmentRecord.IS_ERROR, // 1 - is_error
                        SegmentRecord.SERVICE_ID, // 2 - service_id
                        SegmentRecord.SERVICE_INSTANCE_ID, // 3 - service_instance_id
                        SegmentRecord.ENDPOINT_ID, // 4 - endpoint_id
                        SegmentRecord.LATENCY, // 5 - latency
                        SegmentRecord.START_TIME), // 6 - start_time
                tsRange, q);

        TraceBrief traceBrief = new TraceBrief();
        traceBrief.setTotal(resp.getElements().size());

        for (final RowEntity row : resp.getElements()) {
            BasicTrace basicTrace = new BasicTrace();

            basicTrace.setSegmentId(row.getId());
            basicTrace.setStart(String.valueOf(row.getTagValue(SegmentRecord.START_TIME)));
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
        StreamQueryResponse resp = query(SegmentRecord.INDEX_NAME,
                ImmutableSet.of(SegmentRecord.TRACE_ID,
                        SegmentRecord.IS_ERROR,
                        SegmentRecord.SERVICE_ID,
                        SegmentRecord.SERVICE_INSTANCE_ID,
                        SegmentRecord.ENDPOINT_ID,
                        SegmentRecord.LATENCY,
                        SegmentRecord.START_TIME,
                        SegmentRecord.DATA_BINARY),
                new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.appendCondition(eq(SegmentRecord.TRACE_ID, traceId));
                    }
                });

        List<SegmentRecord> segmentRecords = new ArrayList<>(resp.getElements().size());

        for (final RowEntity rowEntity : resp.getElements()) {
            SegmentRecord segmentRecord = new SegmentRecord.Builder().storage2Entity(
                    new BanyanDBConverter.StreamToEntity(MetadataRegistry.INSTANCE.findMetadata(SegmentRecord.INDEX_NAME), rowEntity));
            segmentRecords.add(segmentRecord);
        }

        return segmentRecords;
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }
}
