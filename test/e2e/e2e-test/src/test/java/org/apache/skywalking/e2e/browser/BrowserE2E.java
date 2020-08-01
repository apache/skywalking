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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfServiceGrpc;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.base.TrafficController;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class BrowserE2E extends SkyWalkingTestAdapter {

    private static final int MAX_INBOUND_MESSAGE_SIZE = 1024 * 1024 * 50;

    private static final String BROWSER_NAME = "e2e";

    private static final String BROWSER_SINGLE_VERSION_NAME = "v1.0.0";

//    @DockerCompose("docker/browser/docker-compose.yml")
//    private DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 12800)
    private HostAndPort swWebappHostPort = HostAndPort.builder().host("127.0.0.1").port(12800).build();

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 11800)
    private HostAndPort oapHostPort = HostAndPort.builder().host("127.0.0.1").port(11800).build();

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
        final List<Service> services = graphql.browserServices(new ServicesQuery().start(startTime).end(now()));
        LOGGER.info("services: {}", services);

        load("expected/browser/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service version: {}", service);
            // browser metrics
            //            verifyBrowserMetrics(service);

            // browser single version
            //            verifyBrowserSingleVersion(service);

            // browser page path
            //            verifyBrowserPagePath(service);
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
            Random random = new Random();
            BrowserPerfData.Builder builder = BrowserPerfData.newBuilder()
                                                             .setService(BROWSER_NAME)
                                                             .setServiceVersion(BROWSER_SINGLE_VERSION_NAME)
                                                             .setPagePath("/e2e-browser")
                                                             .setRedirectTime(random.nextInt(10))
                                                             .setDnsTime(random.nextInt(10))
                                                             .setReqTime(random.nextInt(10))
                                                             .setDomAnalysisTime(random.nextInt(10))
                                                             .setDomReadyTime(random.nextInt(10))
                                                             .setBlankTime(random.nextInt(10));

            sendBrowserPerfData(builder.build());

            for (ErrorCategory category : ErrorCategory.values()) {
                if (category == ErrorCategory.UNRECOGNIZED) {
                    continue;
                }
                BrowserErrorLog.Builder errorLogBuilder = BrowserErrorLog.newBuilder()
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
                throwable.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });
        latch.await();
    }

    private void sendBrowserErrorLog(BrowserErrorLog browserErrorLog) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<BrowserErrorLog> collectStream = browserPerfServiceStub.collectErrorLogs(
            new StreamObserver<Commands>() {
                @Override
                public void onNext(Commands commands) {

                }

                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
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
