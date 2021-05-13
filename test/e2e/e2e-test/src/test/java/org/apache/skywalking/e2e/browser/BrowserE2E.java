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

package org.apache.skywalking.e2e.browser;

import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfServiceGrpc;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;
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
import org.apache.skywalking.e2e.service.endpoint.Endpoint;
import org.apache.skywalking.e2e.service.endpoint.EndpointQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoints;
import org.apache.skywalking.e2e.service.endpoint.EndpointsMatcher;
import org.apache.skywalking.e2e.service.instance.Instance;
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesMatcher;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.ALL_BROWSER_METRICS;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.ALL_BROWSER_PAGE_METRICS;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.ALL_BROWSER_PAGE_MULTIPLE_LINEAR_METRICS;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.ALL_BROWSER_SINGLE_VERSION_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsMatcher.verifyMetrics;
import static org.apache.skywalking.e2e.metrics.MetricsMatcher.verifyPercentileMetrics;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class BrowserE2E extends SkyWalkingTestAdapter {
    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;

    private static final String BROWSER_NAME = "e2e";

    private static final String BROWSER_SINGLE_VERSION_NAME = "v1.0.0";

    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/browser/docker-compose.${SW_STORAGE}.yml",
    })
    private DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 12800)
    private HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 11800)
    private HostAndPort oapHostPort;

    private BrowserPerfServiceGrpc.BrowserPerfServiceStub browserPerfServiceStub;

    @BeforeAll
    public void setUp() {
        queryClient(swWebappHostPort);

        final ManagedChannel channel =
            NettyChannelBuilder.forAddress(oapHostPort.host(), oapHostPort.port())
                               .nameResolverFactory(new DnsNameResolverProvider())
                               .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                               .usePlaintext()
                               .build();

        browserPerfServiceStub = BrowserPerfServiceGrpc.newStub(channel);

        generateTraffic();
    }

    @AfterAll
    public void tearDown() {
        trafficController.stop();
    }

    @RetryableTest
    public void verifyBrowserData() throws Exception {
        final List<Service> services = graphql.browserServices(
            new ServicesQuery().start(startTime).end(now()));
        LOGGER.info("services: {}", services);

        load("expected/browser/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service version: {}", service);
            // browser metrics
            verifyBrowserMetrics(service);

            // browser single version
            verifyBrowserSingleVersion(service);

            // browser page path
            verifyBrowserPagePath(service);
        }
    }

    @RetryableTest
    public void errorLogs() throws Exception {
        List<BrowserErrorLog> logs = graphql.browserErrorLogs(new BrowserErrorLogQuery().start(startTime).end(now()));

        LOGGER.info("errorLogs: {}", logs);

        load("expected/browser/error-log.yml").as(BrowserErrorLogsMatcher.class).verifyLoosely(logs);
    }

    private void verifyBrowserMetrics(final Service service) throws Exception {
        for (String metricName : ALL_BROWSER_METRICS) {
            verifyMetrics(graphql, metricName, service.getKey(), startTime);
        }
    }

    private void verifyBrowserSingleVersion(final Service service) throws Exception {
        Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
        );
        LOGGER.info("instances: {}", instances);
        load("expected/browser/version.yml").as(InstancesMatcher.class).verify(instances);
        // service version metrics
        for (Instance instance : instances.getInstances()) {
            verifyBrowserSingleVersionMetrics(instance);
        }
    }

    private void verifyBrowserSingleVersionMetrics(Instance instance) throws Exception {
        for (String metricName : ALL_BROWSER_SINGLE_VERSION_METRICS) {
            verifyMetrics(graphql, metricName, instance.getKey(), startTime);
        }
    }

    private void verifyBrowserPagePath(final Service service) throws Exception {
        Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(String.valueOf(service.getKey())));
        LOGGER.info("endpoints: {}", endpoints);
        load("expected/browser/page-path.yml").as(EndpointsMatcher.class).verify(endpoints);
        // service page metrics
        for (Endpoint endpoint : endpoints.getEndpoints()) {
            verifyBrowserPagePathMetrics(endpoint);
        }
    }

    private void verifyBrowserPagePathMetrics(Endpoint endpoint) throws Exception {
        for (String metricName : ALL_BROWSER_PAGE_METRICS) {
            verifyMetrics(graphql, metricName, endpoint.getKey(), startTime);
        }

        for (String metricName : ALL_BROWSER_PAGE_MULTIPLE_LINEAR_METRICS) {
            verifyPercentileMetrics(graphql, metricName, endpoint.getKey(), startTime);
        }
    }

    private void generateTraffic() {
        trafficController = TrafficController.builder()
                                             .sender(this::sendBrowserData)
                                             .build()
                                             .start();
    }

    private boolean sendBrowserData() {
        try {
            BrowserPerfData.Builder builder = BrowserPerfData.newBuilder()
                                                             .setService(BROWSER_NAME)
                                                             .setServiceVersion(BROWSER_SINGLE_VERSION_NAME)
                                                             .setPagePath("/e2e-browser")
                                                             .setRedirectTime(10)
                                                             .setDnsTime(10)
                                                             .setTtfbTime(10)
                                                             .setTcpTime(10)
                                                             .setTransTime(10)
                                                             .setDomAnalysisTime(10)
                                                             .setFptTime(10)
                                                             .setDomReadyTime(10)
                                                             .setLoadPageTime(10)
                                                             .setResTime(10)
                                                             .setSslTime(10)
                                                             .setTtlTime(10)
                                                             .setFirstPackTime(10)
                                                             .setFmpTime(10);

            sendBrowserPerfData(builder.build());

            for (ErrorCategory category : ErrorCategory.values()) {
                if (category == ErrorCategory.UNRECOGNIZED) {
                    continue;
                }
                org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog.Builder errorLogBuilder = org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog
                    .newBuilder()
                    .setUniqueId(UUID.randomUUID().toString())
                    .setService(BROWSER_NAME)
                    .setServiceVersion(BROWSER_SINGLE_VERSION_NAME)
                    .setPagePath("/e2e-browser")
                    .setCategory(category)
                    .setMessage("test")
                    .setLine(1)
                    .setCol(1)
                    .setStack("e2e")
                    .setErrorUrl("/e2e-browser");
                if (category == ErrorCategory.js) {
                    errorLogBuilder.setFirstReportedError(true);
                }
                sendBrowserErrorLog(errorLogBuilder.build());
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            return false;
        }
    }

    private void sendBrowserPerfData(BrowserPerfData browserPerfData) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        browserPerfServiceStub.collectPerfData(browserPerfData, new StreamObserver<Commands>() {
            @Override
            public void onNext(Commands commands) {

            }

            @Override
            public void onError(Throwable throwable) {
                LOGGER.warn(throwable.getMessage(), throwable);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });
        latch.await();
    }

    private void sendBrowserErrorLog(org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog browserErrorLog) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog> collectStream = browserPerfServiceStub
            .collectErrorLogs(
                new StreamObserver<Commands>() {
                    @Override
                    public void onNext(Commands commands) {

                    }

                    @Override
                    public void onError(Throwable throwable) {
                        LOGGER.warn(throwable.getMessage(), throwable);
                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });
        collectStream.onNext(browserErrorLog);
        collectStream.onCompleted();
        latch.await();
    }
}
