/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.log;

import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogReportServiceGrpc;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.base.TrafficController;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.endpoint.EndpointQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoints;
import org.apache.skywalking.e2e.service.endpoint.EndpointsMatcher;
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesMatcher;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.apache.skywalking.e2e.utils.Times;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class LogE2E extends SkyWalkingTestAdapter {

    private final static int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;

    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/log/docker-compose.${SW_STORAGE}.yml"
    })
    private DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 12800)
    private HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 11800)
    private HostAndPort oapHostPost;

    private LogReportServiceGrpc.LogReportServiceStub logReportServiceStub;

    @BeforeAll
    public void setUp() {
        queryClient(swWebappHostPort);
        final ManagedChannel channel = NettyChannelBuilder.forAddress(oapHostPost.host(), oapHostPost.port())
                                                          .nameResolverFactory(new DnsNameResolverProvider())
                                                          .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                                                          .usePlaintext().build();
        logReportServiceStub = LogReportServiceGrpc.newStub(channel);
        generateTraffic();
    }

    @AfterAll
    public void tearDown() {
        trafficController.stop();
    }

    @RetryableTest
    public void verifyService() throws Exception {
        List<Service> services = graphql.services(
            new ServicesQuery().start(startTime).end(Times.now()));
        services = services.stream().filter(s -> !s.getLabel().equals("oap::oap-server")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);

        load("expected/log/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instance: {}", service);
            // instance
            verifyServiceInstances(service);
            // endpoint
            verifyServiceEndpoints(service);
        }
    }

    @RetryableTest
    public void verifyLog() throws Exception {
        LogsQuery logsQuery = new LogsQuery().serviceId("ZTJl.1")
                                             .serviceInstanceId("ZTJl.1_ZTJlLWluc3RhbmNl")
                                             .endpointId("ZTJl.1_L3RyYWZmaWM=")
                                             .endpointName("/traffic")
                                             .traceId("ac81b308-0d66-4c69-a7af-a023a536bd3e")
                                             .segmentId(
                                                 "6024a2b1fcff48e4a641d69d388bac53.41.16088574455279608")
                                             .spanId("0")
                                             .tag("status_code", "200")
                                             .start(startTime)
                                             .end(Times.now());
        if (graphql.supportQueryLogsByKeywords()) {
            logsQuery.keywordsOfContent("main", "INFO")
                     .excludingKeywordsOfContent("ERROR");
        }
        final List<Log> logs = graphql.logs(logsQuery);
        LOGGER.info("logs: {}", logs);

        load("expected/log/logs.yml").as(LogsMatcher.class).verifyLoosely(logs);
    }

    private void verifyServiceInstances(final Service service) throws Exception {
        final Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(Times.now()));

        LOGGER.info("instances: {}", instances);
        load("expected/log/instances.yml").as(InstancesMatcher.class).verify(instances);
    }

    private void verifyServiceEndpoints(final Service service) throws Exception {
        final Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(service.getKey()));
        LOGGER.info("endpoints: {}", endpoints);

        load("expected/log/endpoints.yml").as(EndpointsMatcher.class).verify(endpoints);
    }

    private void generateTraffic() {
        trafficController = TrafficController.builder()
                                             .sender(this::sendLog)
                                             .build();
        trafficController.start();
    }

    private boolean sendLog() {
        try {
            LogData logData = LogData.newBuilder()
                                     .setService("e2e")
                                     .setServiceInstance("e2e-instance")
                                     .setEndpoint("/traffic")
                                     .setBody(
                                         LogDataBody.newBuilder()
                                                    .setText(TextLog.newBuilder()
                                                                    .setText(
                                                                        "[main] INFO log message")
                                                                    .build())
                                                    .build())
                                     .setTags(LogTags.newBuilder()
                                                     .addData(KeyStringValuePair.newBuilder()
                                                                                .setKey("status_code")
                                                                                .setValue("200")
                                                                                .build())
                                                     .build())
                                     .setTraceContext(TraceContext.newBuilder()
                                                                  .setTraceId("ac81b308-0d66-4c69-a7af-a023a536bd3e")
                                                                  .setTraceSegmentId(
                                                                      "6024a2b1fcff48e4a641d69d388bac53.41.16088574455279608")
                                                                  .setSpanId(0)
                                                                  .build())
                                     .build();

            CountDownLatch downLatch = new CountDownLatch(1);

            StreamObserver<LogData> streamObserver = logReportServiceStub.collect(new StreamObserver<Commands>() {
                @Override
                public void onNext(final Commands commands) {

                }

                @Override
                public void onError(final Throwable throwable) {
                    LOGGER.error(throwable.getMessage(), throwable);
                    downLatch.countDown();
                }

                @Override
                public void onCompleted() {
                    downLatch.countDown();
                }
            });

            streamObserver.onNext(logData);
            streamObserver.onCompleted();
            downLatch.await();
            return true;
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            return false;
        }
    }
}
