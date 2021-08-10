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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.banyandb.Query;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.client.BanyanDBGrpcClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BanyanDBTraceQueryDAO extends BanyanDBGrpcClient implements ITraceQueryDAO {
    /**
     * The magic numbers used below can be found here
     * https://github.com/apache/skywalking-banyandb/blob/b7845b0cd9fbebf71d3fc990ad6eae5f3ec16771/banyand/series/series.go#L33-L40,
     * 0 -> TraceStateDefault = TraceStateSuccess + TraceStateError
     * 1 -> TraceStateSuccess
     * 2 -> TraceStateError
     */
    static final int TRACE_STATE_DEFAULT = 0;
    static final int TRACE_STATE_SUCCESS = 1;
    static final int TRACE_STATE_ERROR = 2;

    private static final Set<String> DEFAULT_PROJECTION = ImmutableSet.of("duration", "state", "start_time", "trace_id");

    private final BanyanDBSchema schema;

    public BanyanDBTraceQueryDAO(BanyanDBStorageConfig config, BanyanDBSchema schema) {
        this(config.createChannel(), schema);
    }

    BanyanDBTraceQueryDAO(ManagedChannel channel, BanyanDBSchema schema) {
        super(channel);
        this.schema = schema;
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration, String endpointName, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        Query.QueryRequest.Builder queryBuilder = Query.QueryRequest.newBuilder().setMetadata(this.schema.getMetadata());
        if (startSecondTB != 0 && endSecondTB != 0) {
            queryBuilder.setTimeRange(Query.TimeRange.newBuilder()
                    .setBegin(Timestamp.newBuilder().setSeconds(startSecondTB))
                    .setEnd(Timestamp.newBuilder().setSeconds(endSecondTB)).build());
        }
        if (minDuration != 0) {
            // duration >= minDuration
            queryBuilder.addFields(buildInt("duration", Query.PairQuery.BinaryOp.BINARY_OP_GE, minDuration));
        }
        if (maxDuration != 0) {
            // duration <= maxDuration
            queryBuilder.addFields(buildInt("duration", Query.PairQuery.BinaryOp.BINARY_OP_LE, maxDuration));
        }

        if (!Strings.isNullOrEmpty(endpointName)) {
            queryBuilder.addFields(buildStr("endpoint_name", Query.PairQuery.BinaryOp.BINARY_OP_EQ, endpointName));
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            queryBuilder.addFields(buildStr("service_id", Query.PairQuery.BinaryOp.BINARY_OP_EQ, serviceId));
        }

        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            queryBuilder.addFields(buildStr("service_instance_id", Query.PairQuery.BinaryOp.BINARY_OP_EQ, serviceInstanceId));
        }

        if (!Strings.isNullOrEmpty(endpointId)) {
            queryBuilder.addFields(buildStr("endpoint_id", Query.PairQuery.BinaryOp.BINARY_OP_EQ, endpointId));
        }

        switch (traceState) {
            case ERROR:
                queryBuilder.addFields(buildInt("state", Query.PairQuery.BinaryOp.BINARY_OP_EQ, TRACE_STATE_ERROR));
                break;
            case SUCCESS:
                queryBuilder.addFields(buildInt("state", Query.PairQuery.BinaryOp.BINARY_OP_EQ, TRACE_STATE_SUCCESS));
                break;
            default:
                queryBuilder.addFields(buildInt("state", Query.PairQuery.BinaryOp.BINARY_OP_EQ, TRACE_STATE_DEFAULT));
                break;
        }

        switch (queryOrder) {
            case BY_START_TIME:
                queryBuilder.setOrderBy(Query.QueryOrder.newBuilder()
                        .setKeyName("start_time")
                        .setSort(Query.QueryOrder.Sort.SORT_DESC)
                        .build());
                break;
            case BY_DURATION:
                queryBuilder.setOrderBy(Query.QueryOrder.newBuilder()
                        .setKeyName("duration")
                        .setSort(Query.QueryOrder.Sort.SORT_DESC)
                        .build());
                break;
        }

        queryBuilder.setProjection(Query.Projection.newBuilder()
                .addAllKeyNames(DEFAULT_PROJECTION)
                .build());
        queryBuilder.setLimit(limit);
        queryBuilder.setOffset(from);

        // build request
        Query.QueryResponse response = this.query(queryBuilder.build());
        TraceBrief brief = new TraceBrief();
        brief.setTotal(response.getEntitiesCount());
        brief.getTraces().addAll(response.getEntitiesList().stream().map(entity -> {
            BasicTrace trace = new BasicTrace();
            trace.setDuration(this.schema.getDuration(entity));
            trace.setStart(String.valueOf(entity.getTimestamp().getSeconds()));
            trace.setSegmentId(entity.getEntityId());
            trace.setError(this.schema.isErrorEntity(entity));
            return trace;
        }).collect(Collectors.toList()));
        return brief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        Query.QueryRequest.Builder queryBuilder = Query.QueryRequest.newBuilder().setMetadata(this.schema.getMetadata());
        queryBuilder.addFields(Query.PairQuery.newBuilder()
                .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey("trace_id").addValues(traceId).build()))
                .build());
        queryBuilder.setProjection(Query.Projection.newBuilder()
                // add all keysx
                .addAllKeyNames(this.schema.getFieldNames())
                // fetch binary part
                .addKeyNames("data_binary")
                .build());
        Query.QueryResponse response = this.query(queryBuilder.build());
        return response.getEntitiesList().stream().map(entity -> {
            Map<String, Object> entityMap = new HashMap<>();
            for (final Query.TypedPair typedPair : entity.getFieldsList()) {
                if (typedPair.hasIntPair()) {
                    final Query.IntPair pair = typedPair.getIntPair();
                    entityMap.put(pair.getKey(), pair.getValuesList().get(0));
                } else if (typedPair.hasStrPair()) {
                    final Query.StrPair pair = typedPair.getStrPair();
                    entityMap.put(pair.getKey(), pair.getValuesList().get(0));
                }
            }
            return convertToSegmentRecord(entity, entityMap);
        }).collect(Collectors.toList());
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return Collections.emptyList();
    }

    static SegmentRecord convertToSegmentRecord(Query.Entity entity, Map<String, Object> entityMap) {
        SegmentRecord record = new SegmentRecord();
        record.setSegmentId(entity.getEntityId());
        record.setTraceId((String) entityMap.get("trace_id"));
        record.setServiceId((String) entityMap.get("service_id"));
        record.setServiceInstanceId((String) entityMap.get("service_instance_id"));
        record.setEndpointName((String) entityMap.get("endpoint_name"));
        record.setEndpointId((String) entityMap.get("endpoint_id"));
        record.setStartTime(((Number) entityMap.get("start_time")).longValue());
        record.setLatency(((Number) entityMap.get("duration")).intValue());
        record.setIsError(((Number) entityMap.get("state")).intValue());
        record.setDataBinary(entity.getDataBinary().toByteArray());
        record.setTimeBucket(entity.getTimestamp().getSeconds());
        return record;
    }

    static Query.PairQuery buildInt(String key, Query.PairQuery.BinaryOp op, int value) {
        return Query.PairQuery.newBuilder()
                .setOp(op)
                .setCondition(Query.TypedPair.newBuilder().setIntPair(Query.IntPair.newBuilder().setKey(key).addValues(value).build()).build())
                .build();
    }

    static Query.PairQuery buildInt(String key, Query.PairQuery.BinaryOp op, long value) {
        return Query.PairQuery.newBuilder()
                .setOp(op)
                .setCondition(Query.TypedPair.newBuilder().setIntPair(Query.IntPair.newBuilder().setKey(key).addValues(value).build()).build())
                .build();
    }

    static Query.PairQuery buildStr(String key, Query.PairQuery.BinaryOp op, String value) {
        return Query.PairQuery.newBuilder()
                .setOp(op)
                .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey(key).addValues(value).build()).build())
                .build();
    }
}
