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

package org.apache.skywalking.e2e.controller;

import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CountDownLatch;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterDataCollection;
import org.apache.skywalking.apm.network.language.agent.v3.MeterReportServiceGrpc;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;
import org.apache.skywalking.e2e.E2EConfiguration;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeterMetricSenderController {
    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;
    private final MeterReportServiceGrpc.MeterReportServiceStub grpcStub;

    public MeterMetricSenderController(final E2EConfiguration configuration) {
        final ManagedChannel channel = NettyChannelBuilder.forAddress(
            configuration.getOapHost(), Integer.parseInt(configuration.getOapGrpcPort()))
                                                          .nameResolverFactory(new DnsNameResolverProvider())
                                                          .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                                                          .usePlaintext()
                                                          .build();

        grpcStub = MeterReportServiceGrpc.newStub(channel);
    }

    @PostMapping("/sendBatchMetrics")
    public String sendBatchMetrics() throws Exception {
        final MeterDataCollection.Builder builder =
            MeterDataCollection.newBuilder()
                               .addMeterData(MeterData.newBuilder()
                                                      .setService("test-service")
                                                      .setTimestamp(System.currentTimeMillis())
                                                      .setServiceInstance("test-instance")
                                                      .setSingleValue(MeterSingleValue.newBuilder()
                                                                                      .setName("batch_test")
                                                                                      .setValue(100)
                                                                                      .build())
                                                      .build());

        sendMetrics(builder.build());

        return "Metrics send success!";
    }

    @PostMapping("/sendBatchMetrics/{timestamp}/{value}")
    public String sendBatchMetrics(@PathVariable("timestamp") long timestamp, @PathVariable("value") double value) throws Exception {
        final MeterDataCollection.Builder builder =
            MeterDataCollection.newBuilder()
                               .addMeterData(MeterData.newBuilder()
                                                      .setService("test-service")
                                                      .setTimestamp(timestamp)
                                                      .setServiceInstance("test-instance")
                                                      .setSingleValue(MeterSingleValue.newBuilder()
                                                                                      .setName("batch_test")
                                                                                      .setValue(value)
                                                                                      .build())
                                                      .build());

        sendMetrics(builder.build());

        return "Metrics send success!";
    }

    void sendMetrics(final MeterDataCollection metrics) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<MeterDataCollection> collect = grpcStub.collectBatch(new StreamObserver<Commands>() {
            @Override
            public void onNext(final Commands commands) {

            }

            @Override
            public void onError(final Throwable throwable) {
                throwable.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        collect.onNext(metrics);

        collect.onCompleted();

        latch.await();
    }
}
