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

package org.apache.skywalking.banyandb.v1.client;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import io.grpc.util.MutableHandlerRegistry;
import org.apache.skywalking.banyandb.v1.Banyandb;
import org.apache.skywalking.banyandb.v1.trace.BanyandbTrace;
import org.apache.skywalking.banyandb.v1.trace.TraceServiceGrpc;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BanyanDBClientWriteTest {
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private final MutableHandlerRegistry serviceRegistry = new MutableHandlerRegistry();

    private BanyanDBClient client;
    private TraceBulkWriteProcessor traceBulkWriteProcessor;

    @Before
    public void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();

        Server server = InProcessServerBuilder
                .forName(serverName).fallbackHandlerRegistry(serviceRegistry).directExecutor().build();
        grpcCleanup.register(server.start());

        ManagedChannel channel = grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());

        client = new BanyanDBClient("127.0.0.1", server.getPort(), "default");
        client.connect(channel);
        traceBulkWriteProcessor = client.buildTraceWriteProcessor(1000, 1, 1);
    }

    @After
    public void shutdown() throws IOException {
        traceBulkWriteProcessor.close();
    }

    @Test
    public void testWrite() throws Exception {
        final CountDownLatch allRequestsDelivered = new CountDownLatch(1);
        final List<BanyandbTrace.WriteRequest> writeRequestDelivered = new ArrayList<>();

        // implement the fake service
        final TraceServiceGrpc.TraceServiceImplBase serviceImpl =
                new TraceServiceGrpc.TraceServiceImplBase() {
                    @Override
                    public StreamObserver<BanyandbTrace.WriteRequest> write(StreamObserver<BanyandbTrace.WriteResponse> responseObserver) {
                        return new StreamObserver<BanyandbTrace.WriteRequest>() {
                            @Override
                            public void onNext(BanyandbTrace.WriteRequest value) {
                                writeRequestDelivered.add(value);
                                responseObserver.onNext(BanyandbTrace.WriteResponse.newBuilder().build());
                            }

                            @Override
                            public void onError(Throwable t) {
                            }

                            @Override
                            public void onCompleted() {
                                responseObserver.onCompleted();
                                allRequestsDelivered.countDown();
                            }
                        };
                    }
                };
        serviceRegistry.addService(serviceImpl);

        String segmentId = "1231.dfd.123123ssf";
        String traceId = "trace_id-xxfff.111323";
        String serviceId = "webapp_id";
        String serviceInstanceId = "10.0.0.1_id";
        String endpointId = "home_id";
        int latency = 200;
        int state = 1;
        Instant now = Instant.now();
        byte[] byteData = new byte[]{14};
        String broker = "172.16.10.129:9092";
        String topic = "topic_1";
        String queue = "queue_2";
        String httpStatusCode = "200";
        String dbType = "SQL";
        String dbInstance = "127.0.0.1:3306";

        TraceWrite traceWrite = TraceWrite.builder()
                .entityId(segmentId)
                .binary(byteData)
                .timestamp(now.toEpochMilli())
                .name("sw")
                .field(Field.stringField(traceId)) // 0
                .field(Field.stringField(serviceId))
                .field(Field.stringField(serviceInstanceId))
                .field(Field.stringField(endpointId))
                .field(Field.longField(latency)) // 4
                .field(Field.longField(state))
                .field(Field.stringField(httpStatusCode))
                .field(Field.nullField()) // 7
                .field(Field.stringField(dbType))
                .field(Field.stringField(dbInstance))
                .field(Field.stringField(broker))
                .field(Field.stringField(topic))
                .field(Field.stringField(queue)) // 12
                .build();

        traceBulkWriteProcessor.add(traceWrite);

        if (allRequestsDelivered.await(5, TimeUnit.SECONDS)) {
            Assert.assertEquals(1, writeRequestDelivered.size());
            final BanyandbTrace.WriteRequest request = writeRequestDelivered.get(0);
            Assert.assertEquals(13, request.getEntity().getFieldsCount());
            Assert.assertEquals(traceId, request.getEntity().getFields(0).getStr().getValue());
            Assert.assertEquals(latency, request.getEntity().getFields(4).getInt().getValue());
            Assert.assertEquals(request.getEntity().getFields(7).getValueTypeCase(), Banyandb.Field.ValueTypeCase.NULL);
            Assert.assertEquals(queue, request.getEntity().getFields(12).getStr().getValue());
        } else {
            Assert.fail();
        }
    }
}
