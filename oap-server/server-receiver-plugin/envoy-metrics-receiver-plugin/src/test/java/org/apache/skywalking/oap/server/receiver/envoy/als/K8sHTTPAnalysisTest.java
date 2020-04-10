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

package org.apache.skywalking.oap.server.receiver.envoy.als;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.service.accesslog.v2.StreamAccessLogsMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.MetricServiceGRPCHandlerTestMain;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class K8sHTTPAnalysisTest {

    private MockK8sAnalysis analysis;

    @Before
    public void setUp() {
        analysis = new MockK8sAnalysis();
        analysis.init(null);
    }

    @Test
    public void testIngressRoleIdentify() throws IOException {
        try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-ingress.msg"))) {
            StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);
            Role identify = analysis.identify(requestBuilder.getIdentifier(), Role.NONE);

            Assert.assertEquals(Role.PROXY, identify);
        }
    }

    @Test
    public void testSidecarRoleIdentify() throws IOException {
        try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-mesh-server-sidecar.msg"))) {
            StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);
            Role identify = analysis.identify(requestBuilder.getIdentifier(), Role.NONE);

            Assert.assertEquals(Role.SIDECAR, identify);
        }
    }

    @Test
    public void testIngressMetric() throws IOException {
        try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-ingress.msg"))) {
            StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);

            analysis.analysis(requestBuilder.getIdentifier(), requestBuilder.getHttpLogs().getLogEntry(0), Role.PROXY);

            Assert.assertEquals(2, analysis.metrics.size());

            ServiceMeshMetric.Builder incoming = analysis.metrics.get(0);
            Assert.assertEquals("UNKNOWN", incoming.getSourceServiceName());
            Assert.assertEquals("ingress", incoming.getDestServiceName());
            Assert.assertEquals(DetectPoint.server, incoming.getDetectPoint());

            ServiceMeshMetric.Builder outgoing = analysis.metrics.get(1);
            Assert.assertEquals("ingress", outgoing.getSourceServiceName());
            Assert.assertEquals("productpage", outgoing.getDestServiceName());
            Assert.assertEquals(DetectPoint.client, outgoing.getDetectPoint());
        }
    }

    @Test
    public void testIngress2SidecarMetric() throws IOException {
        try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-ingress2sidecar.msg"))) {
            StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);

            analysis.analysis(requestBuilder.getIdentifier(), requestBuilder.getHttpLogs()
                                                                            .getLogEntry(0), Role.SIDECAR);

            Assert.assertEquals(1, analysis.metrics.size());

            ServiceMeshMetric.Builder incoming = analysis.metrics.get(0);
            Assert.assertEquals("", incoming.getSourceServiceName());
            Assert.assertEquals("productpage", incoming.getDestServiceName());
            Assert.assertEquals(DetectPoint.server, incoming.getDetectPoint());
        }
    }

    @Test
    public void testSidecar2SidecarServerMetric() throws IOException {
        try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-mesh-server-sidecar.msg"))) {
            StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);

            analysis.analysis(requestBuilder.getIdentifier(), requestBuilder.getHttpLogs()
                                                                            .getLogEntry(0), Role.SIDECAR);

            Assert.assertEquals(1, analysis.metrics.size());

            ServiceMeshMetric.Builder incoming = analysis.metrics.get(0);
            Assert.assertEquals("productpage", incoming.getSourceServiceName());
            Assert.assertEquals("review", incoming.getDestServiceName());
            Assert.assertEquals(DetectPoint.server, incoming.getDetectPoint());
        }
    }

    @Test
    public void testSidecar2SidecarClientMetric() throws IOException {
        try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-mesh-client-sidecar.msg"))) {
            StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);

            analysis.analysis(requestBuilder.getIdentifier(), requestBuilder.getHttpLogs()
                                                                            .getLogEntry(0), Role.SIDECAR);

            Assert.assertEquals(1, analysis.metrics.size());

            ServiceMeshMetric.Builder incoming = analysis.metrics.get(0);
            Assert.assertEquals("productpage", incoming.getSourceServiceName());
            Assert.assertEquals("detail", incoming.getDestServiceName());
            Assert.assertEquals(DetectPoint.client, incoming.getDetectPoint());
        }
    }

    public static class MockK8sAnalysis extends K8sALSServiceMeshHTTPAnalysis {
        private List<ServiceMeshMetric.Builder> metrics = new ArrayList<>();

        @Override
        public void init(EnvoyMetricReceiverConfig config) {
            getIpServiceMap().set(
                ImmutableMap.of("10.44.2.56", new ServiceMetaInfo("ingress", "ingress-Inst"), "10.44.2.54",
                                new ServiceMetaInfo("productpage", "productpage-Inst"), "10.44.6.66",
                                new ServiceMetaInfo("detail", "detail-Inst"), "10.44.2.55",
                                new ServiceMetaInfo("review", "detail-Inst")
                ));
        }

        @Override
        protected void forward(ServiceMeshMetric.Builder metric) {
            metrics.add(metric);
        }
    }

    private static InputStream getResourceAsStream(final String resource) {
        final InputStream in = getContextClassLoader().getResourceAsStream(resource);
        return in == null ? MetricServiceGRPCHandlerTestMain.class.getResourceAsStream(resource) : in;
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
