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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.Database;
import org.apache.skywalking.banyandb.Query;
import org.apache.skywalking.banyandb.TraceServiceGrpc;
import org.apache.skywalking.banyandb.Write;
import org.apache.skywalking.banyandb.client.BanyanDBService;
import org.apache.skywalking.banyandb.client.request.TraceFetchRequest;
import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.banyandb.client.request.WriteValue;
import org.apache.skywalking.banyandb.client.response.BanyanDBEntity;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BanyanDBGrpcClient implements BanyanDBService {
    private final TraceServiceGrpc.TraceServiceBlockingStub stub;
    private final TraceServiceGrpc.TraceServiceStub asyncStub;
    private final Database.Metadata metadata;

    private ManagedChannel channel;

    private BanyanDBGrpcClient(Builder builder) {
        ManagedChannel channel;
        if (builder.useSSL && builder.sslContext != null) {
            channel = NettyChannelBuilder.forAddress(builder.host, builder.port).sslContext(builder.sslContext).build();
        } else {
            channel = NettyChannelBuilder.forAddress(builder.host, builder.port).usePlaintext().build();
        }
        this.channel = channel;
        this.stub = TraceServiceGrpc.newBlockingStub(this.channel);
        this.asyncStub = TraceServiceGrpc.newStub(this.channel);
        this.metadata = Database.Metadata.newBuilder().setGroup(builder.group).setName(builder.name).build();
    }

    @VisibleForTesting
    public BanyanDBGrpcClient(ManagedChannel channel, String group, String name) {
        this.channel = channel;
        this.stub = TraceServiceGrpc.newBlockingStub(this.channel);
        this.asyncStub = TraceServiceGrpc.newStub(this.channel);
        this.metadata = Database.Metadata.newBuilder().setGroup(group).setName(name).build();
    }

    @Override
    public BanyanDBQueryResponse queryBasicTraces(TraceSearchRequest request) {
        Query.QueryRequest.Builder queryBuilder = Query.QueryRequest.newBuilder()
                .setMetadata(this.metadata);

        if (request.getTimeRange() != null && request.getTimeRange().getStartTime() != 0 && request.getTimeRange().getEndTime() != 0) {
            queryBuilder.setTimeRange(Query.TimeRange.newBuilder()
                    .setBegin(Timestamp.newBuilder().setSeconds(request.getTimeRange().getStartTime()))
                    .setEnd(Timestamp.newBuilder().setSeconds(request.getTimeRange().getEndTime())).build());
        }

        request.getQueries().forEach(q -> queryBuilder.addFields(q.toPairQuery()));

        queryBuilder.setOrderBy(Query.QueryOrder.newBuilder()
                .setKeyName(request.getOrderBy().getFieldName())
                .setSort(request.getOrderBy().getSort().getSort())
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
                .setMetadata(this.metadata)
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
            try {
                Write.EntityValue.Builder entityValueBuilder = Write.EntityValue.newBuilder();
                for (final WriteValue<?> writeValue : entity.getFields()) {
                    entityValueBuilder.addFields(writeValue.toWriteField());
                }
                Write.EntityValue entityValue = entityValueBuilder.setDataBinary(ByteString.copyFrom(entity.getDataBinary()))
                        .setTimestamp(Timestamp.newBuilder().setSeconds(entity.getTimestampSeconds()).setNanos(entity.getTimestampNanos()).build())
                        .setEntityId(entity.getEntityId()).build();
                observer.onNext(Write.WriteRequest.newBuilder()
                        .setMetadata(this.metadata)
                        .setEntity(entityValue).build());
            } catch (Throwable t) {
                log.error("fail to convert entityValue and send data");
            }
        }

        observer.onCompleted();
    }

    @Override
    public void close() throws IOException {
        try {
            this.channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            this.channel.shutdownNow();
        }
    }

    /**
     * @return a new builder for BanyanDBClient
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String host;
        private int port;

        private String group;
        private String name;

        private boolean useSSL = false;

        private File keyCertChainFile;
        private File keyFile;
        private String keyPassword;

        private SslContext sslContext;

        /**
         * Set host
         *
         * @param host the hostname of the target BanyanBD server
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Set port
         *
         * @param port the port of the target BanyanBD server
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Set ssl certificates with password which implies SSL should be used for building connections
         *
         * @param keyCertChainFile an X.509 certificate chain file in PEM format
         * @param keyFile          a PKCS#8 private key file in PEM format
         * @param keyPassword      the password of the {@code keyFile}, or {@code null} if it's not
         *                         password-protected
         */
        public Builder useSSL(File keyCertChainFile, File keyFile, String keyPassword) {
            this.useSSL = true;
            this.keyCertChainFile = keyCertChainFile;
            this.keyFile = keyFile;
            this.keyPassword = keyPassword;
            return this;
        }

        /**
         * Set metadata for the given client
         *
         * @param group group of the entity
         * @param name  name of the entity
         */
        public Builder metadata(String group, String name) {
            this.group = group;
            this.name = name;
            return this;
        }

        public BanyanDBService build() {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(host), "host must not be null or empty");
            Preconditions.checkArgument(port > 0, "port must be valid");

            Preconditions.checkArgument(!Strings.isNullOrEmpty(this.group), "group of the metadata must not be null or empty");
            Preconditions.checkArgument(!Strings.isNullOrEmpty(this.name), "name of the metadata must not be null or empty");

            if (this.useSSL) {
                try {
                    sslContext = SslContextBuilder.forClient()
                            .keyManager(this.keyCertChainFile, this.keyFile, this.keyPassword)
                            .build();
                } catch (SSLException sslEx) {
                    throw new IllegalArgumentException(sslEx);
                }
            }

            return new BanyanDBGrpcClient(this);
        }
    }
}
