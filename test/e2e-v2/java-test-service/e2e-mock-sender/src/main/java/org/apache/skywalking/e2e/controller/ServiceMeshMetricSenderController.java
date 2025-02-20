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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.v3.HTTPServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.v3.HTTPServiceMeshMetrics;
import org.apache.skywalking.apm.network.servicemesh.v3.MeshProbeDownstream;
import org.apache.skywalking.apm.network.servicemesh.v3.Protocol;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetrics;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetricServiceGrpc;
import org.apache.skywalking.e2e.E2EConfiguration;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceMeshMetricSenderController {
    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;
    private static final boolean SUCCESS = true;
    private final ServiceMeshMetricServiceGrpc.ServiceMeshMetricServiceStub grpcStub;

    public ServiceMeshMetricSenderController(final E2EConfiguration configuration) {
        final ManagedChannel channel = NettyChannelBuilder.forAddress(
            configuration.getOapHost(), Integer.parseInt(configuration.getOapGrpcPort()))
                                                          .nameResolverFactory(new DnsNameResolverProvider())
                                                          .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                                                          .usePlaintext()
                                                          .build();

        grpcStub = ServiceMeshMetricServiceGrpc.newStub(channel);
    }

    @PostMapping("/sendMetrics4TTL/{metricsTTL}")
    public String sendMetrics4TTL(@PathVariable("metricsTTL") int metricsTTL) throws Exception {
        final HTTPServiceMeshMetric.Builder builder =
            HTTPServiceMeshMetric
                .newBuilder()
                .setSourceServiceName("e2e-test-source-service")
                .setSourceServiceInstance("e2e-test-source-service-instance")
                .setDestServiceName("e2e-test-dest-service")
                .setDestServiceInstance("e2e-test-dest-service-instance")
                .setEndpoint("e2e/test")
                .setLatency(2000)
                .setResponseCode(200)
                .setStatus(SUCCESS)
                .setProtocol(Protocol.HTTP)
                .setDetectPoint(DetectPoint.server);

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final LocalDateTime startTime = now.minusDays(metricsTTL + 1);
        final LocalDateTime endTime = startTime.plusMinutes(1);

        sendMetrics(ServiceMeshMetrics
            .newBuilder()
            .setHttpMetrics(
                HTTPServiceMeshMetrics
                    .newBuilder()
                    .addMetrics(
                        builder
                            .setStartTime(startTime.toEpochSecond(ZoneOffset.UTC) * 1000)
                            .setEndTime(endTime.toEpochSecond(ZoneOffset.UTC) * 1000)))
            .build());

        return "Metrics send success!";
    }

    @PostMapping("/sendMetrics4Predict/{days}")
    public String sendMetrics4Predict(@PathVariable("days") int days) throws Exception {
        final HTTPServiceMeshMetric.Builder builder =
            HTTPServiceMeshMetric
                .newBuilder()
                .setSourceServiceName("e2e-test-source-service")
                .setSourceServiceInstance("e2e-test-source-service-instance")
                .setDestServiceName("e2e-test-dest-service")
                .setDestServiceInstance("e2e-test-dest-service-instance")
                .setEndpoint("e2e/test")
                .setLatency(2000)
                .setResponseCode(200)
                .setStatus(SUCCESS)
                .setProtocol(Protocol.HTTP)
                .setDetectPoint(DetectPoint.server);

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime startTime = now.minusDays(days + 1);

        while (!startTime.isAfter(now)) {
            startTime = startTime.plusMinutes(1);
            final LocalDateTime endTime = startTime.plusMinutes(1);
            sendMetrics(ServiceMeshMetrics
                .newBuilder()
                .setHttpMetrics(
                    HTTPServiceMeshMetrics
                        .newBuilder()
                        .addMetrics(
                            builder
                                .setStartTime(startTime.toEpochSecond(ZoneOffset.UTC) * 1000)
                                .setEndTime(endTime.toEpochSecond(ZoneOffset.UTC) * 1000)))
                .build());
        }

        return "Metrics send success!";
    }

    void sendMetrics(final ServiceMeshMetrics metrics) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<ServiceMeshMetrics> collect = grpcStub.collect(new StreamObserver<MeshProbeDownstream>() {
            @Override
            public void onNext(final MeshProbeDownstream meshProbeDownstream) {

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
