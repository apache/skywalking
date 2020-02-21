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

package org.apache.skywalking.e2e;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.MeshProbeDownstream;
import org.apache.skywalking.apm.network.servicemesh.Protocol;
import org.apache.skywalking.apm.network.servicemesh.ServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.ServiceMeshMetricServiceGrpc;
import org.apache.skywalking.e2e.metrics.AllOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsQuery;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_RESP_TIME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kezhenxu94
 */
@Slf4j
public class StorageTTLITCase {

    //TODO Make TTL ES7 test stable. Ref https://github.com/apache/skywalking/pull/3978, https://github.com/apache/skywalking/issues/4018

    private static final int SW_STORAGE_ES_MONTH_METRIC_DATA_TTL = 4;
    private static final int SW_STORAGE_ES_OTHER_METRIC_DATA_TTL = 5;

    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;
    private static final boolean USE_PLAIN_TEXT = true;
    private static final boolean SUCCESS = true;

    private SimpleQueryClient queryClient;

    private ServiceMeshMetricServiceGrpc.ServiceMeshMetricServiceStub grpcStub;

    @Before
    public void setUp() {

        final String swWebappHost = System.getProperty("sw.webapp.host", "127.0.0.1");
        final String swWebappPort = System.getProperty("sw.webapp.port", "32789");
        final String oapPort = System.getProperty("oap.port", "32788");
        queryClient = new SimpleQueryClient(swWebappHost, swWebappPort);

        final ManagedChannelBuilder builder =
            NettyChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(oapPort))
                .nameResolverFactory(new DnsNameResolverProvider())
                .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                .usePlaintext(USE_PLAIN_TEXT);

        final ManagedChannel channel = builder.build();

        grpcStub = ServiceMeshMetricServiceGrpc.newStub(channel);
    }

    @Test(timeout = 360000)
    public void dayMetricsDataShouldBeRemovedAfterTTL() throws Exception {

        final ServiceMeshMetric.Builder builder = ServiceMeshMetric
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

        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime startTime = now.minusDays(SW_STORAGE_ES_OTHER_METRIC_DATA_TTL + 1);
        final LocalDateTime endTime = startTime.plusMinutes(1);

        final LocalDateTime queryStart = startTime;
        final LocalDateTime queryEnd = now.minusDays(SW_STORAGE_ES_OTHER_METRIC_DATA_TTL);

        ensureSendingMetricsWorks(
            builder,
            startTime.toEpochSecond(ZoneOffset.UTC) * 1000,
            endTime.toEpochSecond(ZoneOffset.UTC) * 1000,
            queryStart,
            queryEnd,
            "DAY"
        );

        shouldBeEmptyBetweenTimeRange(queryStart, queryEnd, "DAY");
    }

    @Test(timeout = 360000)
    public void monthMetricsDataShouldBeRemovedAfterTTL() throws Exception {

        final ServiceMeshMetric.Builder builder = ServiceMeshMetric
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

        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime startTime = now.minusMonths(SW_STORAGE_ES_MONTH_METRIC_DATA_TTL + 1);
        final LocalDateTime endTime = startTime.plusMinutes(1);

        final LocalDateTime queryStart = startTime;
        final LocalDateTime queryEnd = now.minusMonths(SW_STORAGE_ES_MONTH_METRIC_DATA_TTL);

        ensureSendingMetricsWorks(
            builder,
            startTime.toEpochSecond(ZoneOffset.UTC) * 1000,
            endTime.toEpochSecond(ZoneOffset.UTC) * 1000,
            queryStart,
            queryEnd,
            "MONTH"
        );

        shouldBeEmptyBetweenTimeRange(queryStart, queryEnd, "MONTH");
    }

    private void shouldBeEmptyBetweenTimeRange(
        final LocalDateTime queryStart,
        final LocalDateTime queryEnd,
        final String step
    ) throws InterruptedException {

        boolean valid = false;
        for (int i = 0; i < 10 && !valid; i++) {
            try {
                final Metrics serviceMetrics = queryMetrics(queryStart, queryEnd, step);

                log.info("ServiceMetrics: {}", serviceMetrics);

                AllOfMetricsMatcher instanceRespTimeMatcher = new AllOfMetricsMatcher();
                MetricsValueMatcher equalsZero = new MetricsValueMatcher();
                equalsZero.setValue("eq 0");
                instanceRespTimeMatcher.setValue(equalsZero);
                try {
                    instanceRespTimeMatcher.verify(serviceMetrics);
                    valid = true;
                } catch (Throwable t) {
                    log.warn("History metrics data are not deleted yet, {}", t.getMessage());
                    Thread.sleep(60000);
                }
            } catch (Throwable t) {
                log.warn("History metrics data are not deleted yet", t);
                Thread.sleep(60000);
            }
        }
    }

    private void ensureSendingMetricsWorks(
        final ServiceMeshMetric.Builder builder,
        final long startTime,
        final long endTime,
        final LocalDateTime queryStart,
        final LocalDateTime queryEnd,
        final String step
    ) throws Exception {
        boolean prepared = false;
        while (!prepared) {
            sendMetrics(
                builder
                    .setStartTime(startTime)
                    .setEndTime(endTime)
                    .build()
            );
            final Metrics serviceMetrics = queryMetrics(queryStart, queryEnd, step);
            final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            try {
                instanceRespTimeMatcher.verify(serviceMetrics);
                prepared = true;
            } catch (Throwable ignored) {
                sendMetrics(
                    builder
                        .setStartTime(startTime)
                        .setEndTime(endTime)
                        .build()
                );
                Thread.sleep(10000);
            }
        }
    }

    private Metrics queryMetrics(
        final LocalDateTime queryStart,
        final LocalDateTime queryEnd,
        final String step
    ) throws Exception {
        for (int i = 0; i < 10; i++) {
            try {
                final List<Service> services = queryClient.services(
                    new ServicesQuery()
                        .start(LocalDateTime.now().minusDays(SW_STORAGE_ES_OTHER_METRIC_DATA_TTL))
                        .end(LocalDateTime.now())
                );

                log.info("Services: {}", services);

                assertThat(services).isNotEmpty();

                String serviceId = services.stream().filter(it -> "e2e-test-dest-service".equals(it.getLabel())).findFirst().get().getKey();

                return queryClient.metrics(
                    new MetricsQuery()
                        .id(serviceId)
                        .metricsName(SERVICE_RESP_TIME)
                        .step(step)
                        .start(queryStart)
                        .end(queryEnd)
                );
            } catch (Throwable ignored) {
                Thread.sleep(10000);
            }
        }
        return null;
    }

    private void sendMetrics(final ServiceMeshMetric metrics) throws InterruptedException {
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
