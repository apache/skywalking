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

import io.grpc.testing.GrpcServerRule;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.exporter.ExportData;
import org.apache.skywalking.oap.server.core.exporter.ExportEvent;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.exporter.grpc.MetricExportServiceGrpc;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class GRPCExporterTest {

    private GRPCExporter exporter;

    @Rule
    public final GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();

    private MetricExportServiceGrpc.MetricExportServiceImplBase server = new MockMetricExportServiceImpl();
    private MetricsMetaInfo metaInfo = new MetricsMetaInfo("mock-metrics", DefaultScopeDefine.ALL);

    private MetricExportServiceGrpc.MetricExportServiceBlockingStub stub;

    @Before
    public void setUp() throws Exception {
        GRPCExporterSetting setting = new GRPCExporterSetting();
        setting.setTargetHost("localhost");
        setting.setTargetPort(9870);
        exporter = new GRPCExporter(setting);
        grpcServerRule.getServiceRegistry().addService(server);
        stub = MetricExportServiceGrpc.newBlockingStub(grpcServerRule.getChannel());
    }

    @Test
    public void export() {
        ExportEvent event = new ExportEvent(new MockExporterMetrics(), ExportEvent.EventType.TOTAL);
        exporter.export(event);
    }

    public static class MockExporterMetrics extends MockMetrics implements WithMetadata {
        @Override
        public MetricsMetaInfo getMeta() {
            return new MetricsMetaInfo("mock-metrics", DefaultScopeDefine.ALL);
        }
    }

    @Test
    public void initSubscriptionList() {
        Whitebox.setInternalState(exporter, "blockingStub", stub);
        exporter.initSubscriptionList();
    }

    @Test
    public void init() {
        exporter.init();
    }

    @Test
    public void consume() {

        exporter.consume(dataList());
        exporter.consume(Collections.emptyList());
    }

    @Test
    public void onError() {
        Exception e = new IllegalArgumentException("some something wrong");
        exporter.onError(Collections.emptyList(), e);
        exporter.onError(dataList(), e);
    }

    @Test
    public void onExit() {
        exporter.onExit();
    }

    private List<ExportData> dataList() {
        List<ExportData> dataList = new LinkedList<>();
        dataList.add(new ExportData(metaInfo, new MockMetrics()));
        dataList.add(new ExportData(metaInfo, new MockIntValueMetrics()));
        dataList.add(new ExportData(metaInfo, new MockLongValueMetrics()));
        dataList.add(new ExportData(metaInfo, new MockDoubleValueMetrics()));
        return dataList;
    }
}