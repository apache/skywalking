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

package org.apache.skywalking.oap.server.exporter.provider.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.util.MutableHandlerRegistry;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.exporter.ExportData;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.exporter.grpc.MetricExportServiceGrpc;
import org.apache.skywalking.oap.server.exporter.provider.ExporterSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.apache.skywalking.oap.server.core.exporter.ExportEvent.EventType.INCREMENT;

public class GRPCExporterTest {

    private GRPCMetricsExporter exporter;

    private MetricExportServiceGrpc.MetricExportServiceImplBase service = new MockMetricExportServiceImpl();
    private MetricsMetaInfo metaInfo = new MetricsMetaInfo("mock-metrics", DefaultScopeDefine.SERVICE);

    private MetricExportServiceGrpc.MetricExportServiceBlockingStub stub;

    private Server server;
    private ManagedChannel channel;
    private MutableHandlerRegistry serviceRegistry;

    @BeforeEach
    public void setUp() throws Exception {
        serviceRegistry = new MutableHandlerRegistry();
        final String name = UUID.randomUUID().toString();
        InProcessServerBuilder serverBuilder =
                InProcessServerBuilder
                        .forName(name)
                        .fallbackHandlerRegistry(serviceRegistry);

        server = serverBuilder.build();
        server.start();

        channel = InProcessChannelBuilder.forName(name).build();

        ExporterSetting setting = new ExporterSetting();
        setting.setGRPCTargetHost("localhost");
        setting.setGRPCTargetPort(9870);
        exporter = new GRPCMetricsExporter(setting);
        serviceRegistry.addService(service);
        stub = MetricExportServiceGrpc.newBlockingStub(channel);
        Whitebox.setInternalState(exporter, "blockingStub", stub);
        exporter.start();
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
    public void export() {
        ExportEvent event = new ExportEvent(new MockExporterMetrics(), ExportEvent.EventType.TOTAL);
        exporter.export(event);
    }

    public static class MockExporterMetrics extends MockMetrics implements WithMetadata {
        @Override
        public MetricsMetaInfo getMeta() {
            return new MetricsMetaInfo("mock-metrics", DefaultScopeDefine.SERVICE);
        }
    }

    @Test
    public void initSubscriptionList() {
        exporter.fetchSubscriptionList();
    }

    @Test
    public void init() {
        exporter.init(null);
    }

    @Test
    public void consume() {

        exporter.consume(dataList());
        exporter.consume(Collections.emptyList());
    }

    @Test
    public void onError() {
        Exception e = new IllegalArgumentException("something wrong");
        exporter.onError(Collections.emptyList(), e);
        exporter.onError(dataList(), e);
    }

    @Test
    public void onExit() {
        exporter.onExit();
    }

    private List<ExportData> dataList() {
        List<ExportData> dataList = new LinkedList<>();
        dataList.add(new ExportData(metaInfo, new MockMetrics(), INCREMENT));
        dataList.add(new ExportData(metaInfo, new MockIntValueMetrics(), INCREMENT));
        dataList.add(new ExportData(metaInfo, new MockLongValueMetrics(), INCREMENT));
        dataList.add(new ExportData(metaInfo, new MockDoubleValueMetrics(), INCREMENT));
        return dataList;
    }
}
