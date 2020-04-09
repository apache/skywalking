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
 */

package org.apache.skywalking.e2e.ttl;

import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.v3.MeshProbeDownstream;
import org.apache.skywalking.apm.network.servicemesh.v3.Protocol;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetricServiceGrpc;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.metrics.AllOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsQuery;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_RESP_TIME;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SkyWalkingE2E
public class StorageTTLE2E extends SkyWalkingTestAdapter {
    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/ttl/docker-compose.yml",
        "docker/ttl/docker-compose.${SW_STORAGE}.yml",
    })
    protected DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 8080)
    private HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 11800)
    private HostAndPort oapHostPort;

    private static final int SW_CORE_RECORD_DATA_TTL = 5;

    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;
    private static final boolean SUCCESS = true;

    private ServiceMeshMetricServiceGrpc.ServiceMeshMetricServiceStub grpcStub;

    @BeforeAll
    public void setUp() {
        queryClient(swWebappHostPort);

        final ManagedChannel channel = NettyChannelBuilder.forAddress(oapHostPort.host(), oapHostPort.port())
                                                          .nameResolverFactory(new DnsNameResolverProvider())
                                                          .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                                                          .usePlaintext()
                                                          .build();

        grpcStub = ServiceMeshMetricServiceGrpc.newStub(channel);
    }

    @RetryableTest
    public void dayMetricsDataShouldBeRemovedAfterTTL() throws Exception {
        final ServiceMeshMetric.Builder builder =
            ServiceMeshMetric.newBuilder()
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

        final LocalDateTime now = now();
        final LocalDateTime startTime = now.minusDays(SW_CORE_RECORD_DATA_TTL + 1);
        final LocalDateTime endTime = startTime.plusMinutes(1);

        final LocalDateTime queryEnd = now.minusDays(SW_CORE_RECORD_DATA_TTL);

        ensureSendingMetricsWorks(
            builder, startTime.toEpochSecond(ZoneOffset.UTC) * 1000, endTime.toEpochSecond(ZoneOffset.UTC) * 1000,
            startTime, queryEnd, "DAY"
        );

        shouldBeEmptyBetweenTimeRange(startTime, queryEnd, "DAY");
    }

    private void shouldBeEmptyBetweenTimeRange(final LocalDateTime queryStart,
                                               final LocalDateTime queryEnd,
                                               final String step) throws InterruptedException {

        boolean valid = false;
        for (int i = 0; i < 10 && !valid; i++) {
            try {
                final Metrics serviceMetrics = queryMetrics(queryStart, queryEnd, step);

                LOGGER.info("ServiceMetrics: {}", serviceMetrics);

                AllOfMetricsMatcher instanceRespTimeMatcher = new AllOfMetricsMatcher();
                MetricsValueMatcher equalsZero = new MetricsValueMatcher();
                equalsZero.setValue("eq 0");
                instanceRespTimeMatcher.setValue(equalsZero);
                try {
                    assert serviceMetrics != null;
                    instanceRespTimeMatcher.verify(serviceMetrics);
                    valid = true;
                } catch (Throwable t) {
                    LOGGER.warn("History metrics data are not deleted yet, {}", t.getMessage());
                    Thread.sleep(6000);
                }
            } catch (Throwable t) {
                LOGGER.warn("History metrics data are not deleted yet", t);
                Thread.sleep(6000);
            }
        }
    }

    private void ensureSendingMetricsWorks(final ServiceMeshMetric.Builder builder,
                                           final long startTime,
                                           final long endTime,
                                           final LocalDateTime queryStart,
                                           final LocalDateTime queryEnd,
                                           final String step) throws Exception {
        boolean prepared = false;
        while (!prepared) {
            sendMetrics(builder.setStartTime(startTime).setEndTime(endTime).build());
            final Metrics serviceMetrics = queryMetrics(queryStart, queryEnd, step);
            final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            try {
                assert serviceMetrics != null;
                instanceRespTimeMatcher.verify(serviceMetrics);
                prepared = true;
            } catch (Throwable ignored) {
                sendMetrics(builder.setStartTime(startTime).setEndTime(endTime).build());
                Thread.sleep(10000);
            }
        }
    }

    private Metrics queryMetrics(final LocalDateTime queryStart,
                                 final LocalDateTime queryEnd,
                                 final String step) throws Exception {
        for (int i = 0; i < 10; i++) {
            try {
                final List<Service> services = graphql.services(
                    new ServicesQuery().start(now().minusDays(SW_CORE_RECORD_DATA_TTL)).end(now())
                );

                LOGGER.info("Services: {}", services);

                assertThat(services).isNotEmpty();

                final String serviceId = services.stream()
                                                 .filter(it -> "e2e-test-dest-service".equals(it.getLabel()))
                                                 .findFirst()
                                                 .orElseThrow(NullPointerException::new)
                                                 .getKey();

                return graphql.metrics(new MetricsQuery().id(serviceId)
                                                         .metricsName(SERVICE_RESP_TIME)
                                                         .step(step)
                                                         .start(queryStart)
                                                         .end(queryEnd));
            } catch (Throwable ignored) {
                Thread.sleep(10000);
            }
        }
        return null;
    }

    void sendMetrics(final ServiceMeshMetric metrics) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<ServiceMeshMetric> collect = grpcStub.collect(new StreamObserver<MeshProbeDownstream>() {
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
