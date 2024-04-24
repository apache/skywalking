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
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.exporter.ExportData;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.exporter.grpc.ExportMetricValue;
import org.apache.skywalking.oap.server.exporter.grpc.KeyValue;
import org.apache.skywalking.oap.server.exporter.grpc.MetricExportServiceGrpc;
import org.apache.skywalking.oap.server.exporter.grpc.SubscriptionMetric;
import org.apache.skywalking.oap.server.exporter.provider.ExporterSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.apache.skywalking.oap.server.core.exporter.ExportEvent.EventType.INCREMENT;
import static org.mockito.Mockito.when;

public class GRPCExporterTest {

    private GRPCMetricsExporter exporter;

    private MetricExportServiceGrpc.MetricExportServiceImplBase service = new MockMetricExportServiceImpl();
    private MetricsMetaInfo metaInfo = new MetricsMetaInfo(
        "first-mock-metrics", DefaultScopeDefine.SERVICE, IDManager.ServiceID.buildId("mock-service", true));

    private MetricExportServiceGrpc.MetricExportServiceBlockingStub blockingStub;
    private MetricExportServiceGrpc.MetricExportServiceStub futureStub;
    private Server server;
    private ManagedChannel channel;
    private MutableHandlerRegistry serviceRegistry;
    private MockedStatic<DefaultScopeDefine> defineMockedStatic;

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
        exporter.start();
        serviceRegistry.addService(service);
        blockingStub = MetricExportServiceGrpc.newBlockingStub(channel);
        futureStub = MetricExportServiceGrpc.newStub(channel);
        Whitebox.setInternalState(exporter, "blockingStub", blockingStub);
        Whitebox.setInternalState(exporter, "exportServiceFutureStub", futureStub);
        defineMockedStatic = Mockito.mockStatic(DefaultScopeDefine.class);
        when(DefaultScopeDefine.inServiceCatalog(1)).thenReturn(true);
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
            defineMockedStatic.close();
        }
    }

    @Test
    public void export() {
        exporter.fetchSubscriptionList();
        ExportEvent event = new ExportEvent(new MockExporterMetrics(), INCREMENT);
        exporter.export(event);
        List<SubscriptionMetric> subscriptionList = Whitebox.getInternalState(exporter, "subscriptionList");
        Assertions.assertEquals("mock-metrics", subscriptionList.get(0).getMetricName());
        Assertions.assertEquals("int-mock-metrics", subscriptionList.get(1).getMetricName());
        Assertions.assertEquals("long-mock-metrics", subscriptionList.get(2).getMetricName());
        Assertions.assertEquals("labeled-mock-metrics", subscriptionList.get(3).getMetricName());
    }

    public static class MockExporterMetrics extends MockLabeledValueMetrics implements WithMetadata {
        @Override
        public MetricsMetaInfo getMeta() {
            return new MetricsMetaInfo(
                "labeled-mock-metrics", DefaultScopeDefine.SERVICE, IDManager.ServiceID.buildId("mock-service", true));
        }
    }

    @Test
    public void initSubscriptionList() {
        exporter.fetchSubscriptionList();
        List<SubscriptionMetric> subscriptionList = Whitebox.getInternalState(exporter, "subscriptionList");
        Assertions.assertEquals("mock-metrics", subscriptionList.get(0).getMetricName());
        Assertions.assertEquals("int-mock-metrics", subscriptionList.get(1).getMetricName());
        Assertions.assertEquals("long-mock-metrics", subscriptionList.get(2).getMetricName());
        Assertions.assertEquals("labeled-mock-metrics", subscriptionList.get(3).getMetricName());
    }

    @Test
    public void init() {
        exporter.init(null);
    }

    @Test
    public void consume() {
        exporter.consume(dataList());
        exporter.consume(Collections.emptyList());
        List<ExportMetricValue> exportMetricValues = ((MockMetricExportServiceImpl) service).exportMetricValues;
        Assertions.assertEquals(3, exportMetricValues.size());
        Assertions.assertEquals(12, exportMetricValues.get(0).getMetricValues(0).getLongValue());
        Assertions.assertEquals(1234567891234563312L, exportMetricValues.get(1).getMetricValues(0).getLongValue());
        Assertions.assertEquals(1000L, exportMetricValues.get(2).getMetricValues(0).getLongValue());
        Assertions.assertEquals(KeyValue.newBuilder().setKey("labelName").setValue("labelValue").build(), exportMetricValues.get(2).getMetricValues(0).getLabels(0));
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
        dataList.add(new ExportData(new MetricsMetaInfo(
            "mock-metrics", DefaultScopeDefine.SERVICE, IDManager.ServiceID.buildId("mock-service", true)), new MockMetrics(), INCREMENT));
        dataList.add(new ExportData(new MetricsMetaInfo(
            "int-mock-metrics", DefaultScopeDefine.SERVICE, IDManager.ServiceID.buildId("mock-service", true)), new MockIntValueMetrics(), INCREMENT));
        dataList.add(new ExportData(new MetricsMetaInfo(
            "long-mock-metrics", DefaultScopeDefine.SERVICE, IDManager.ServiceID.buildId("mock-service", true)), new MockLongValueMetrics(), INCREMENT));
        dataList.add(new ExportData(new MetricsMetaInfo(
            "labeled-mock-metrics", DefaultScopeDefine.SERVICE, IDManager.ServiceID.buildId("mock-service", true)), new MockLabeledValueMetrics(), INCREMENT));
        return dataList;
    }
}
