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
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import lombok.Getter;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
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
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.SegmentRecordBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BanyanDBTraceQueryDAO extends AbstractBanyanDBDAO implements ITraceQueryDAO {
    public BanyanDBTraceQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        final QueryBuilder q = new QueryBuilder() {
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
                        query.appendCondition(eq(SegmentRecord.IS_ERROR, TraceStateStorage.ERROR.getState()));
                        break;
                    case SUCCESS:
                        query.appendCondition(eq(SegmentRecord.IS_ERROR, TraceStateStorage.SUCCESS.getState()));
                        break;
                    default:
                        query.appendCondition(eq(SegmentRecord.IS_ERROR, TraceStateStorage.ALL.getState()));
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
                        if (SegmentRecordBuilder.INDEXED_TAGS.contains(tag.getKey())) {
                            query.appendCondition(eq(tag.getKey(), tag.getValue()));
                        }
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
                ImmutableList.of("trace_id", "is_error", "endpoint_id", "latency", "start_time"), tsRange, q);

        List<BasicTrace> basicTraces = resp.getElements().stream().map(new BasicTraceDeserializer()).collect(Collectors.toList());

        TraceBrief brief = new TraceBrief();
        brief.setTotal(basicTraces.size());
        brief.getTraces().addAll(basicTraces);
        return brief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        StreamQueryResponse resp = query(SegmentRecord.INDEX_NAME,
                ImmutableList.of("trace_id", "is_error", "service_id", "service_instance_id", "endpoint_id", "latency", "start_time"),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(Collections.singletonList("data_binary"));
                        query.appendCondition(eq(SegmentRecord.TRACE_ID, traceId));
                    }
                });

        return resp.getElements().stream().map(new SegmentRecordDeserializer()).collect(Collectors.toList());
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }

    public enum TraceStateStorage {
        ALL(0), SUCCESS(1), ERROR(2);

        @Getter
        private final int state;

        TraceStateStorage(int state) {
            this.state = state;
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
}
