package org.apache.skywalking.banyandb.client.impl;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.Query;
import org.apache.skywalking.banyandb.TraceServiceGrpc;
import org.apache.skywalking.banyandb.Write;
import org.apache.skywalking.banyandb.client.BanyanDBService;
import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.banyandb.client.response.BanyanDBEntity;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBSchema;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class BanyanDBGrpcClient implements BanyanDBService {
    private static final Set<String> DEFAULT_PROJECTION = ImmutableSet.of("duration", "state", "start_time", "trace_id");

    private final TraceServiceGrpc.TraceServiceBlockingStub stub;
    private final TraceServiceGrpc.TraceServiceStub asyncStub;

    public BanyanDBGrpcClient(ManagedChannel managedChannel) {
        this.stub = TraceServiceGrpc.newBlockingStub(managedChannel);
        this.asyncStub = TraceServiceGrpc.newStub(managedChannel);
    }

    @Override
    public BanyanDBQueryResponse queryBasicTraces(TraceSearchRequest request) {
        Query.QueryRequest.Builder queryBuilder = Query.QueryRequest.newBuilder().setMetadata(BanyanDBSchema.METADATA);
        if (request.getTimeRange() != null && request.getTimeRange().getStartTime() != 0 && request.getTimeRange().getEndTime() != 0) {
            queryBuilder.setTimeRange(Query.TimeRange.newBuilder()
                    .setBegin(Timestamp.newBuilder().setSeconds(request.getTimeRange().getStartTime()))
                    .setEnd(Timestamp.newBuilder().setSeconds(request.getTimeRange().getEndTime())).build());
        }

        if (request.getMinDuration() != 0) {
            // duration >= minDuration
            queryBuilder.addFields(buildInt("duration", Query.PairQuery.BinaryOp.BINARY_OP_GE, request.getMinDuration()));
        }
        if (request.getMaxDuration() != 0) {
            // duration <= maxDuration
            queryBuilder.addFields(buildInt("duration", Query.PairQuery.BinaryOp.BINARY_OP_LE, request.getMaxDuration()));
        }

        if (!Strings.isNullOrEmpty(request.getEndpointName())) {
            queryBuilder.addFields(buildStr("endpoint_name", Query.PairQuery.BinaryOp.BINARY_OP_EQ, request.getEndpointName()));
        }

        if (!Strings.isNullOrEmpty(request.getServiceId())) {
            queryBuilder.addFields(buildStr("service_id", Query.PairQuery.BinaryOp.BINARY_OP_EQ, request.getServiceId()));
        }

        if (!Strings.isNullOrEmpty(request.getServiceInstanceId())) {
            queryBuilder.addFields(buildStr("service_instance_id", Query.PairQuery.BinaryOp.BINARY_OP_EQ, request.getServiceInstanceId()));
        }

        if (!Strings.isNullOrEmpty(request.getEndpointId())) {
            queryBuilder.addFields(buildStr("endpoint_id", Query.PairQuery.BinaryOp.BINARY_OP_EQ, request.getEndpointId()));
        }

        switch (request.getTraceState()) {
            case ERROR:
                queryBuilder.addFields(buildInt("state", Query.PairQuery.BinaryOp.BINARY_OP_EQ, TraceSearchRequest.TraceState.ERROR.getState()));
                break;
            case SUCCESS:
                queryBuilder.addFields(buildInt("state", Query.PairQuery.BinaryOp.BINARY_OP_EQ, TraceSearchRequest.TraceState.SUCCESS.getState()));
                break;
            default:
                queryBuilder.addFields(buildInt("state", Query.PairQuery.BinaryOp.BINARY_OP_EQ, TraceSearchRequest.TraceState.ALL.getState()));
                break;
        }

        queryBuilder.setOrderBy(Query.QueryOrder.newBuilder()
                .setKeyName(request.getQueryOrderField())
                .setSort(request.getQueryOrderSort().getSort())
                .build());

        queryBuilder.setProjection(Query.Projection.newBuilder()
                .addAllKeyNames(DEFAULT_PROJECTION)
                .build());
        queryBuilder.setLimit(request.getLimit());
        queryBuilder.setOffset(request.getOffset());

        return convertToResponse(this.stub.query(queryBuilder.build()));
    }

    @Override
    public BanyanDBQueryResponse queryByTraceId(String traceId) {
        Query.QueryRequest.Builder queryBuilder = Query.QueryRequest.newBuilder().setMetadata(BanyanDBSchema.METADATA);
        queryBuilder.addFields(Query.PairQuery.newBuilder()
                .setOp(Query.PairQuery.BinaryOp.BINARY_OP_EQ)
                .setCondition(Query.TypedPair.newBuilder().setStrPair(Query.StrPair.newBuilder().setKey("trace_id").addValues(traceId).build()))
                .build());
        queryBuilder.setProjection(Query.Projection.newBuilder()
                // add all keys
                .addAllKeyNames(BanyanDBSchema.FIELD_NAMES)
                // fetch binary part
                .addKeyNames("data_binary")
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
            observer.onNext(Write.WriteRequest.newBuilder().setMetadata(BanyanDBSchema.METADATA).setEntity(entityValue).build());
        }
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
