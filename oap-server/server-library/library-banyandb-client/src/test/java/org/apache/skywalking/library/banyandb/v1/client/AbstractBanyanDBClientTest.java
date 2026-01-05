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

package org.apache.skywalking.library.banyandb.v1.client;

import io.grpc.BindableService;
import io.grpc.ForwardingServerBuilder;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.util.MutableHandlerRegistry;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.database.v1.IndexRuleBindingRegistryServiceGrpc;
import org.apache.skywalking.banyandb.database.v1.IndexRuleRegistryServiceGrpc;
import org.apache.skywalking.banyandb.database.v1.MeasureRegistryServiceGrpc;
import org.apache.skywalking.banyandb.database.v1.StreamRegistryServiceGrpc;
import org.apache.skywalking.banyandb.database.v1.TopNAggregationRegistryServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.util.TimeUtils;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

public class AbstractBanyanDBClientTest {
    // play as an in-memory registry
    protected Map<String, BanyandbDatabase.IndexRuleBinding> indexRuleBindingRegistry;

    private final IndexRuleBindingRegistryServiceGrpc.IndexRuleBindingRegistryServiceImplBase indexRuleBindingServiceImpl =
            mock(IndexRuleBindingRegistryServiceGrpc.IndexRuleBindingRegistryServiceImplBase.class, delegatesTo(
                    new IndexRuleBindingRegistryServiceGrpc.IndexRuleBindingRegistryServiceImplBase() {
                        @Override
                        public void create(BanyandbDatabase.IndexRuleBindingRegistryServiceCreateRequest request, StreamObserver<BanyandbDatabase.IndexRuleBindingRegistryServiceCreateResponse> responseObserver) {
                            BanyandbDatabase.IndexRuleBinding s = request.getIndexRuleBinding().toBuilder()
                                    .setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .build();
                            indexRuleBindingRegistry.put(s.getMetadata().getName(), s);
                            responseObserver.onNext(BanyandbDatabase.IndexRuleBindingRegistryServiceCreateResponse.newBuilder().build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void update(BanyandbDatabase.IndexRuleBindingRegistryServiceUpdateRequest request, StreamObserver<BanyandbDatabase.IndexRuleBindingRegistryServiceUpdateResponse> responseObserver) {
                            BanyandbDatabase.IndexRuleBinding s = request.getIndexRuleBinding().toBuilder()
                                    .setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .build();
                            indexRuleBindingRegistry.put(s.getMetadata().getName(), s);
                            responseObserver.onNext(BanyandbDatabase.IndexRuleBindingRegistryServiceUpdateResponse.newBuilder().build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void delete(BanyandbDatabase.IndexRuleBindingRegistryServiceDeleteRequest request, StreamObserver<BanyandbDatabase.IndexRuleBindingRegistryServiceDeleteResponse> responseObserver) {
                            BanyandbDatabase.IndexRuleBinding oldIndexRuleBinding = indexRuleBindingRegistry.remove(request.getMetadata().getName());
                            responseObserver.onNext(BanyandbDatabase.IndexRuleBindingRegistryServiceDeleteResponse.newBuilder()
                                    .setDeleted(oldIndexRuleBinding != null)
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void get(BanyandbDatabase.IndexRuleBindingRegistryServiceGetRequest request, StreamObserver<BanyandbDatabase.IndexRuleBindingRegistryServiceGetResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.IndexRuleBindingRegistryServiceGetResponse.newBuilder()
                                    .setIndexRuleBinding(indexRuleBindingRegistry.get(request.getMetadata().getName()))
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void list(BanyandbDatabase.IndexRuleBindingRegistryServiceListRequest request, StreamObserver<BanyandbDatabase.IndexRuleBindingRegistryServiceListResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.IndexRuleBindingRegistryServiceListResponse.newBuilder()
                                    .addAllIndexRuleBinding(indexRuleBindingRegistry.values())
                                    .build());
                            responseObserver.onCompleted();
                        }
                    }));

    // play as an in-memory registry
    protected Map<String, BanyandbDatabase.IndexRule> indexRuleRegistry;

    private final IndexRuleRegistryServiceGrpc.IndexRuleRegistryServiceImplBase indexRuleServiceImpl =
            mock(IndexRuleRegistryServiceGrpc.IndexRuleRegistryServiceImplBase.class, delegatesTo(
                    new IndexRuleRegistryServiceGrpc.IndexRuleRegistryServiceImplBase() {
                        @Override
                        public void create(BanyandbDatabase.IndexRuleRegistryServiceCreateRequest request, StreamObserver<BanyandbDatabase.IndexRuleRegistryServiceCreateResponse> responseObserver) {
                            BanyandbDatabase.IndexRule s = request.getIndexRule().toBuilder().setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .build();
                            indexRuleRegistry.put(s.getMetadata().getName(), s);
                            responseObserver.onNext(BanyandbDatabase.IndexRuleRegistryServiceCreateResponse.newBuilder().build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void update(BanyandbDatabase.IndexRuleRegistryServiceUpdateRequest request, StreamObserver<BanyandbDatabase.IndexRuleRegistryServiceUpdateResponse> responseObserver) {
                            BanyandbDatabase.IndexRule s = request.getIndexRule().toBuilder().setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .build();
                            indexRuleRegistry.put(s.getMetadata().getName(), s);
                            responseObserver.onNext(BanyandbDatabase.IndexRuleRegistryServiceUpdateResponse.newBuilder().build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void delete(BanyandbDatabase.IndexRuleRegistryServiceDeleteRequest request, StreamObserver<BanyandbDatabase.IndexRuleRegistryServiceDeleteResponse> responseObserver) {
                            BanyandbDatabase.IndexRule oldIndexRule = indexRuleRegistry.remove(request.getMetadata().getName());
                            responseObserver.onNext(BanyandbDatabase.IndexRuleRegistryServiceDeleteResponse.newBuilder()
                                    .setDeleted(oldIndexRule != null)
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void get(BanyandbDatabase.IndexRuleRegistryServiceGetRequest request, StreamObserver<BanyandbDatabase.IndexRuleRegistryServiceGetResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.IndexRuleRegistryServiceGetResponse.newBuilder()
                                    .setIndexRule(indexRuleRegistry.get(request.getMetadata().getName()))
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void list(BanyandbDatabase.IndexRuleRegistryServiceListRequest request, StreamObserver<BanyandbDatabase.IndexRuleRegistryServiceListResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.IndexRuleRegistryServiceListResponse.newBuilder()
                                    .addAllIndexRule(indexRuleRegistry.values())
                                    .build());
                            responseObserver.onCompleted();
                        }
                    }));

    // stream registry
    protected Map<String, BanyandbDatabase.Stream> streamRegistry;

    private final StreamRegistryServiceGrpc.StreamRegistryServiceImplBase streamRegistryServiceImpl =
            mock(StreamRegistryServiceGrpc.StreamRegistryServiceImplBase.class, delegatesTo(
                    new StreamRegistryServiceGrpc.StreamRegistryServiceImplBase() {
                        private final AtomicLong revisionGenerator = new AtomicLong(0);

                        @Override
                        public void create(BanyandbDatabase.StreamRegistryServiceCreateRequest request, StreamObserver<BanyandbDatabase.StreamRegistryServiceCreateResponse> responseObserver) {
                            BanyandbDatabase.Stream s = request.getStream().toBuilder()
                                    .setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .setMetadata(request.getStream().getMetadata().toBuilder()
                                            .setModRevision(revisionGenerator.incrementAndGet())
                                            .build())
                                    .build();
                            streamRegistry.put(s.getMetadata().getName(), s);
                            responseObserver.onNext(BanyandbDatabase.StreamRegistryServiceCreateResponse.newBuilder().setModRevision(s.getMetadata().getModRevision()).build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void update(BanyandbDatabase.StreamRegistryServiceUpdateRequest request, StreamObserver<BanyandbDatabase.StreamRegistryServiceUpdateResponse> responseObserver) {
                            BanyandbDatabase.Stream s = request.getStream().toBuilder()
                                    .setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .setMetadata(request.getStream().getMetadata().toBuilder()
                                            .setModRevision(revisionGenerator.incrementAndGet())
                                            .build())
                                    .build();
                            streamRegistry.put(s.getMetadata().getName(), s);
                            responseObserver.onNext(BanyandbDatabase.StreamRegistryServiceUpdateResponse.newBuilder().setModRevision(s.getMetadata().getModRevision()).build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void delete(BanyandbDatabase.StreamRegistryServiceDeleteRequest request, StreamObserver<BanyandbDatabase.StreamRegistryServiceDeleteResponse> responseObserver) {
                            BanyandbDatabase.Stream oldStream = streamRegistry.remove(request.getMetadata().getName());
                            responseObserver.onNext(BanyandbDatabase.StreamRegistryServiceDeleteResponse.newBuilder()
                                    .setDeleted(oldStream != null)
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void get(BanyandbDatabase.StreamRegistryServiceGetRequest request, StreamObserver<BanyandbDatabase.StreamRegistryServiceGetResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.StreamRegistryServiceGetResponse.newBuilder()
                                    .setStream(streamRegistry.get(request.getMetadata().getName()))
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void list(BanyandbDatabase.StreamRegistryServiceListRequest request, StreamObserver<BanyandbDatabase.StreamRegistryServiceListResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.StreamRegistryServiceListResponse.newBuilder()
                                    .addAllStream(streamRegistry.values())
                                    .build());
                            responseObserver.onCompleted();
                        }
                    }));

    // measure registry
    protected Map<String, BanyandbDatabase.Measure> measureRegistry;

    private final MeasureRegistryServiceGrpc.MeasureRegistryServiceImplBase measureRegistryServiceImpl =
            mock(MeasureRegistryServiceGrpc.MeasureRegistryServiceImplBase.class, delegatesTo(
                    new MeasureRegistryServiceGrpc.MeasureRegistryServiceImplBase() {
                        private final AtomicLong revisionGenerator = new AtomicLong(0);

                        @Override
                        public void create(BanyandbDatabase.MeasureRegistryServiceCreateRequest request, StreamObserver<BanyandbDatabase.MeasureRegistryServiceCreateResponse> responseObserver) {
                            BanyandbDatabase.Measure s = request.getMeasure().toBuilder()
                                    .setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .setMetadata(request.getMeasure().getMetadata().toBuilder()
                                            .setModRevision(revisionGenerator.incrementAndGet())
                                            .build())
                                    .build();
                            measureRegistry.put(s.getMetadata().getName(), s);
                            responseObserver.onNext(BanyandbDatabase.MeasureRegistryServiceCreateResponse.newBuilder().setModRevision(s.getMetadata().getModRevision()).build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void update(BanyandbDatabase.MeasureRegistryServiceUpdateRequest request, StreamObserver<BanyandbDatabase.MeasureRegistryServiceUpdateResponse> responseObserver) {
                            BanyandbDatabase.Measure s = request.getMeasure().toBuilder()
                                    .setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .setMetadata(request.getMeasure().getMetadata().toBuilder()
                                            .setModRevision(revisionGenerator.incrementAndGet())
                                            .build())
                                    .build();
                            measureRegistry.put(s.getMetadata().getName(), s);
                            responseObserver.onNext(BanyandbDatabase.MeasureRegistryServiceUpdateResponse.newBuilder().setModRevision(s.getMetadata().getModRevision()).build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void delete(BanyandbDatabase.MeasureRegistryServiceDeleteRequest request, StreamObserver<BanyandbDatabase.MeasureRegistryServiceDeleteResponse> responseObserver) {
                            BanyandbDatabase.Measure oldMeasure = measureRegistry.remove(request.getMetadata().getName());
                            responseObserver.onNext(BanyandbDatabase.MeasureRegistryServiceDeleteResponse.newBuilder()
                                    .setDeleted(oldMeasure != null)
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void get(BanyandbDatabase.MeasureRegistryServiceGetRequest request, StreamObserver<BanyandbDatabase.MeasureRegistryServiceGetResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.MeasureRegistryServiceGetResponse.newBuilder()
                                    .setMeasure(measureRegistry.get(request.getMetadata().getName()))
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void list(BanyandbDatabase.MeasureRegistryServiceListRequest request, StreamObserver<BanyandbDatabase.MeasureRegistryServiceListResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.MeasureRegistryServiceListResponse.newBuilder()
                                    .addAllMeasure(measureRegistry.values())
                                    .build());
                            responseObserver.onCompleted();
                        }
                    }));

    // measure registry
    protected Map<String, BanyandbDatabase.TopNAggregation> topNAggregationRegistry;

    private final TopNAggregationRegistryServiceGrpc.TopNAggregationRegistryServiceImplBase topNAggregationRegistryServiceImpl =
            mock(TopNAggregationRegistryServiceGrpc.TopNAggregationRegistryServiceImplBase.class, delegatesTo(
                    new TopNAggregationRegistryServiceGrpc.TopNAggregationRegistryServiceImplBase() {
                        @Override
                        public void create(BanyandbDatabase.TopNAggregationRegistryServiceCreateRequest request, StreamObserver<BanyandbDatabase.TopNAggregationRegistryServiceCreateResponse> responseObserver) {
                            BanyandbDatabase.TopNAggregation aggr = request.getTopNAggregation().toBuilder()
                                    .setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .build();
                            topNAggregationRegistry.put(aggr.getMetadata().getName(), aggr);
                            responseObserver.onNext(BanyandbDatabase.TopNAggregationRegistryServiceCreateResponse.newBuilder().build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void update(BanyandbDatabase.TopNAggregationRegistryServiceUpdateRequest request, StreamObserver<BanyandbDatabase.TopNAggregationRegistryServiceUpdateResponse> responseObserver) {
                            BanyandbDatabase.TopNAggregation aggr = request.getTopNAggregation().toBuilder()
                                    .setUpdatedAt(TimeUtils.buildTimestamp(ZonedDateTime.now()))
                                    .build();
                            topNAggregationRegistry.put(aggr.getMetadata().getName(), aggr);
                            responseObserver.onNext(BanyandbDatabase.TopNAggregationRegistryServiceUpdateResponse.newBuilder().build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void delete(BanyandbDatabase.TopNAggregationRegistryServiceDeleteRequest request, StreamObserver<BanyandbDatabase.TopNAggregationRegistryServiceDeleteResponse> responseObserver) {
                            BanyandbDatabase.TopNAggregation oldMeasure = topNAggregationRegistry.remove(request.getMetadata().getName());
                            responseObserver.onNext(BanyandbDatabase.TopNAggregationRegistryServiceDeleteResponse.newBuilder()
                                    .setDeleted(oldMeasure != null)
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void get(BanyandbDatabase.TopNAggregationRegistryServiceGetRequest request, StreamObserver<BanyandbDatabase.TopNAggregationRegistryServiceGetResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.TopNAggregationRegistryServiceGetResponse.newBuilder()
                                    .setTopNAggregation(topNAggregationRegistry.get(request.getMetadata().getName()))
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void list(BanyandbDatabase.TopNAggregationRegistryServiceListRequest request, StreamObserver<BanyandbDatabase.TopNAggregationRegistryServiceListResponse> responseObserver) {
                            responseObserver.onNext(BanyandbDatabase.TopNAggregationRegistryServiceListResponse.newBuilder()
                                    .addAllTopNAggregation(topNAggregationRegistry.values())
                                    .build());
                            responseObserver.onCompleted();
                        }
                    }));

    protected final MutableHandlerRegistry serviceRegistry = new MutableHandlerRegistry();

    protected BanyanDBClient client;

    protected ManagedChannel channel;

    protected void setUp(SetupFunction... setUpFunctions) throws IOException {
        indexRuleRegistry = new HashMap<>();
        serviceRegistry.addService(indexRuleServiceImpl);

        indexRuleBindingRegistry = new HashMap<>();
        serviceRegistry.addService(indexRuleBindingServiceImpl);
        indexRuleBindingRegistry = new HashMap<>();

        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        InProcessServerBuilder serverBuilder = InProcessServerBuilder
                .forName(serverName).directExecutor()
                .fallbackHandlerRegistry(serviceRegistry);
        for (final SetupFunction func : setUpFunctions) {
            func.apply(serverBuilder);
        }
        final Server s = serverBuilder.build();
        s.start();
        this.channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

        client = new BanyanDBClient(String.format("127.0.0.1:%d", s.getPort()));
        client.connect(this.channel);
    }

    protected interface SetupFunction {
        void apply(ForwardingServerBuilder<?> builder);
    }

    protected SetupFunction bindStreamRegistry() {
        return b -> {
            AbstractBanyanDBClientTest.this.streamRegistry = new HashMap<>();
            serviceRegistry.addService(streamRegistryServiceImpl);
        };
    }

    protected SetupFunction bindService(final BindableService bindableService) {
        return b -> serviceRegistry.addService(bindableService);
    }

    protected SetupFunction bindMeasureRegistry() {
        return b -> {
            AbstractBanyanDBClientTest.this.measureRegistry = new HashMap<>();
            serviceRegistry.addService(measureRegistryServiceImpl);
        };
    }

    protected SetupFunction bindTopNAggregationRegistry() {
        return b -> {
            AbstractBanyanDBClientTest.this.topNAggregationRegistry = new HashMap<>();
            serviceRegistry.addService(topNAggregationRegistryServiceImpl);
        };
    }
}
