package org.apache.skywalking.oap.server.baseline;/*
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

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.util.MutableHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.baseline.service.BaselineQueryServiceImpl;
import org.apache.skywalking.oap.server.baseline.service.PredictServiceMetrics;
import org.apache.skywalking.oap.server.baseline.service.ServiceMetrics;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
public class BaselineServerTest {
    private Server server;
    private ManagedChannel channel;
    private MutableHandlerRegistry serviceRegistry;
    private BaselineQueryServiceImpl queryService;

    @BeforeEach
    public void before() throws IOException {
        serviceRegistry = new MutableHandlerRegistry();
        final String name = UUID.randomUUID().toString();
        InProcessServerBuilder serverBuilder =
            InProcessServerBuilder
                .forName(name)
                .fallbackHandlerRegistry(serviceRegistry);
        serverBuilder.addService(new BaselineQueryServer());
        server = serverBuilder.build();
        server.start();

        channel = InProcessChannelBuilder.forName(name).build();

        queryService = new BaselineQueryServiceImpl("", 0);
        org.apache.skywalking.apm.baseline.v3.AlarmBaselineServiceGrpc.AlarmBaselineServiceBlockingStub blockingStub = org.apache.skywalking.apm.baseline.v3.AlarmBaselineServiceGrpc.newBlockingStub(channel);
        Whitebox.setInternalState(queryService, "stub", blockingStub);
    }

    @AfterEach
    public void after() {
        channel.shutdown();
        server.shutdown();

        try {
            channel.awaitTermination(1L, TimeUnit.MINUTES);
            server.awaitTermination(1L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            channel.shutdownNow();
            channel = null;
            server.shutdownNow();
            server = null;
        }
    }

    @Test
    public void queryServices() throws Exception {
        final List<PredictServiceMetrics> metrics = queryService.queryPredictMetrics(Arrays.asList(ServiceMetrics.builder()
                .serviceName("test")
                .metricsNames(Arrays.asList("service_cpm")).build()),
            TimeBucket.getTimeBucket(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(4), DownSampling.Hour),
            TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Hour)
        );
        assertNotNull(metrics);
    }

}
