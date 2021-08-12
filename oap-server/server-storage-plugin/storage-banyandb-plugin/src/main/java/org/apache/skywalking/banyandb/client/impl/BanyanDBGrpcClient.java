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

package org.apache.skywalking.banyandb.client.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.Database;
import org.apache.skywalking.banyandb.Query;
import org.apache.skywalking.banyandb.TraceServiceGrpc;
import org.apache.skywalking.banyandb.Write;
import org.apache.skywalking.banyandb.client.BanyanDBService;
import org.apache.skywalking.banyandb.client.request.TraceFetchRequest;
import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.banyandb.client.response.BanyanDBEntity;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class BanyanDBGrpcClient implements BanyanDBService {
    private final TraceServiceGrpc.TraceServiceBlockingStub stub;
    private final TraceServiceGrpc.TraceServiceStub asyncStub;

    public BanyanDBGrpcClient(ManagedChannel managedChannel) {
        this.stub = TraceServiceGrpc.newBlockingStub(managedChannel);
        this.asyncStub = TraceServiceGrpc.newStub(managedChannel);
    }

    @Override
    public BanyanDBQueryResponse queryBasicTraces(TraceSearchRequest request) {
        Query.QueryRequest.Builder queryBuilder = Query.QueryRequest.newBuilder()
                .setMetadata(Database.Metadata.newBuilder().setGroup(request.getGroup()).setName(request.getName()).build());

        if (request.getTimeRange() != null && request.getTimeRange().getStartTime() != 0 && request.getTimeRange().getEndTime() != 0) {
            queryBuilder.setTimeRange(Query.TimeRange.newBuilder()
                    .setBegin(Timestamp.newBuilder().setSeconds(request.getTimeRange().getStartTime()))
                    .setEnd(Timestamp.newBuilder().setSeconds(request.getTimeRange().getEndTime())).build());
        }

        request.getQueries().forEach(q -> queryBuilder.addFields(q.toPairQuery()));

        queryBuilder.setOrderBy(Query.QueryOrder.newBuilder()
                .setKeyName(request.getQueryOrderField())
                .setSort(request.getQueryOrderSort().getSort())
                .build());

        queryBuilder.setProjection(Query.Projection.newBuilder()
                .addAllKeyNames(request.getProjections())
                .build());
        queryBuilder.setLimit(request.getLimit());
        queryBuilder.setOffset(request.getOffset());

        return convertToResponse(this.stub.query(queryBuilder.build()));
    }

    @Override
    public BanyanDBQueryResponse queryByTraceId(TraceFetchRequest traceFetchRequest) {
        Query.QueryRequest.Builder queryBuilder = Query.QueryRequest.newBuilder()
                .setMetadata(Database.Metadata.newBuilder().setGroup(traceFetchRequest.getGroup()).setName(traceFetchRequest.getName()).build())
                .addFields(Query.PairQuery.newBuilder()
                        .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                        .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey("trace_id").addValues(traceFetchRequest.getTraceId()).build()))
                        .build())
                .setProjection(Query.Projection.newBuilder()
                        .addAllKeyNames(traceFetchRequest.getProjections())
                        .build());
        return convertToResponse(this.stub.query(queryBuilder.build()));
    }

    static BanyanDBQueryResponse convertToResponse(Query.QueryResponse response) {
        BanyanDBQueryResponse r = new BanyanDBQueryResponse();
        r.setTotal(response.getEntitiesCount());
        for (final Query.Entity entity : response.getEntitiesList()) {
            BanyanDBEntity.BanyanDBEntityBuilder entityBuilder = BanyanDBEntity.builder();
            for (final Query.TypedPair typedPair : entity.getFieldsList()) {
                if (typedPair.hasIntPair()) {
                    final Query.IntPair pair = typedPair.getIntPair();
                    entityBuilder.field(pair.getKey(), pair.getValuesList().get(0));
                } else if (typedPair.hasStrPair()) {
                    final Query.StrPair pair = typedPair.getStrPair();
                    entityBuilder.field(pair.getKey(), pair.getValuesList().get(0));
                }
            }
            entityBuilder.entityId(entity.getEntityId());
            entityBuilder.binaryData(entity.getDataBinary().toByteArray());
            entityBuilder.timestampSeconds(entity.getTimestamp().getSeconds());
            entityBuilder.timestampNanoSeconds(entity.getTimestamp().getNanos());
            r.getEntities().add(entityBuilder.build());
        }

        return r;
    }

    @Override
    public void writeEntity(List<TraceWriteRequest> data) {
        StreamObserver<org.apache.skywalking.banyandb.Write.WriteRequest> observer = this.asyncStub.withDeadlineAfter(30, TimeUnit.SECONDS)
                .write(new StreamObserver<Write.WriteResponse>() {
                    @Override
                    public void onNext(Write.WriteResponse writeResponse) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("fail to send request", throwable);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });

        for (final TraceWriteRequest entity : data) {
            Write.EntityValue entityValue = Write.EntityValue.newBuilder()
                    .addAllFields(buildWriteFields(entity.getFields()))
                    .setDataBinary(ByteString.copyFrom(entity.getDataBinary()))
                    .setTimestamp(Timestamp.newBuilder().setSeconds(entity.getTimestampSeconds()).setNanos(entity.getTimestampNanos()).build())
                    .setEntityId(entity.getEntityId()).build();
            observer.onNext(Write.WriteRequest.newBuilder()
                    .setMetadata(Database.Metadata.newBuilder().setName(entity.getName()).setGroup(entity.getGroup()).build())
                    .setEntity(entityValue).build());
        }
    }

    static List<Write.Field> buildWriteFields(List<Object> fieldList) {
        return fieldList.stream().map(BanyanDBGrpcClient::buildField).collect(Collectors.toList());
    }

    static Write.Field buildField(Object value) {
        if (value == null) {
            return Write.Field.newBuilder().setNull(NullValue.NULL_VALUE).build();
        }
        if (value instanceof String) {
            return Write.Field.newBuilder().setStr(Write.Str.newBuilder().setValue((String) value).build()).build();
        } else if (value instanceof Integer) {
            return Write.Field.newBuilder().setInt(Write.Int.newBuilder().setValue((Integer) value).build()).build();
        } else if (value instanceof Long) {
            return Write.Field.newBuilder().setInt(Write.Int.newBuilder().setValue((Long) value).build()).build();
        } else {
            throw new IllegalStateException("should not reach here");
        }
    }
}
