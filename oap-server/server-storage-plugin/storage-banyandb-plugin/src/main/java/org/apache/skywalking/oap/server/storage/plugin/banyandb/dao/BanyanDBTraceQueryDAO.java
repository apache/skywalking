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
import com.google.protobuf.Timestamp;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BanyanDBTraceQueryDAO extends BanyanDBGrpcClient implements ITraceQueryDAO {
    private final BanyanDBSchema schema;

    public BanyanDBTraceQueryDAO(BanyanDBStorageConfig config, BanyanDBSchema schema) {
        super(config.getHost(), config.getPort());
        this.schema = schema;
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration, String endpointName, String serviceId, String serviceInstanceId, String endpointId, String traceId, int limit, int from, TraceState traceState, QueryOrder queryOrder, List<Tag> tags) throws IOException {
        Set<String> projections = new HashSet<>();
        projections.add("duration");
        projections.add("state");
        Query.QueryRequest.Builder queryBuilder = Query.QueryRequest.newBuilder();
        if (startSecondTB != 0 && endSecondTB != 0) {
            queryBuilder.setTimeRange(Query.TimeRange.newBuilder()
                    .setBegin(Timestamp.newBuilder().setSeconds(startSecondTB))
                    .setEnd(Timestamp.newBuilder().setSeconds(endSecondTB)).build());
        }
        if (minDuration != 0) {
            // duration >= minDuration
            queryBuilder.addFields(Query.PairQuery.newBuilder()
                    .setOp(Query.PairQuery.BinaryOp.BINARY_OP_GE)
                    .setCondition(Query.TypedPair.newBuilder().setIntPair(Query.IntPair.newBuilder().setKey("duration").addValues(minDuration).build()))
                    .build());
        }
        if (maxDuration != 0) {
            // duration <= maxDuration
            queryBuilder.addFields(Query.PairQuery.newBuilder()
                    .setOp(Query.PairQuery.BinaryOp.BINARY_OP_LE)
                    .setCondition(Query.TypedPair.newBuilder().setIntPair(Query.IntPair.newBuilder().setKey("duration").addValues(maxDuration).build()))
                    .build());
        }

        if (!Strings.isNullOrEmpty(endpointName)) {
            queryBuilder.addFields(Query.PairQuery.newBuilder()
                    .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                    .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey("endpoint_name").addValues(endpointName).build()))
                    .build());
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            queryBuilder.addFields(Query.PairQuery.newBuilder()
                    .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                    .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey("service_id").addValues(serviceId).build()))
                    .build());
        }

        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            queryBuilder.addFields(Query.PairQuery.newBuilder()
                    .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                    .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey("service_instance_id").addValues(serviceInstanceId).build()))
                    .build());
        }

        if (!Strings.isNullOrEmpty(endpointId)) {
            queryBuilder.addFields(Query.PairQuery.newBuilder()
                    .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                    .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey("endpoint_id").addValues(endpointId).build()))
                    .build());
        }

        switch (traceState) {
            case ERROR:
                queryBuilder.addFields(Query.PairQuery.newBuilder()
                        .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                        .setCondition(Query.TypedPair.newBuilder().setIntPair(Query.IntPair.newBuilder().setKey("state").addValues(2).build()))
                        .build());
                break;
            case SUCCESS:
                queryBuilder.addFields(Query.PairQuery.newBuilder()
                        .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                        .setCondition(Query.TypedPair.newBuilder().setIntPair(Query.IntPair.newBuilder().setKey("state").addValues(1).build()))
                        .build());
                break;
            default:
                queryBuilder.addFields(Query.PairQuery.newBuilder()
                        .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                        .setCondition(Query.TypedPair.newBuilder().setIntPair(Query.IntPair.newBuilder().setKey("state").addValues(0).build()))
                        .build());
                break;
        }

        switch (queryOrder) {
            case BY_START_TIME:
                queryBuilder.setOrderBy(Query.QueryOrder.newBuilder()
                        .setKeyName("start_time")
                        .setSort(Query.QueryOrder.Sort.SORT_DESC)
                        .build());
                projections.add("start_time");
                break;
            case BY_DURATION:
                queryBuilder.setOrderBy(Query.QueryOrder.newBuilder()
                        .setKeyName("duration")
                        .setSort(Query.QueryOrder.Sort.SORT_DESC)
                        .build());
                break;
        }

        // TODO: add other indexed tags from tags

        queryBuilder.setProjection(Query.Projection.newBuilder()
                .addAllKeyNames(projections)
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
        Query.QueryRequest.Builder queryBuilder = Query.QueryRequest.newBuilder();
        queryBuilder.addFields(Query.PairQuery.newBuilder()
                .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey("trace_id").addValues(traceId).build()))
                .build());
        queryBuilder.setProjection(Query.Projection.newBuilder()
                // add all keys
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
}
