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

import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.service.accesslog.v2.StreamAccessLogsMessage;
import java.io.*;
import java.util.*;
import org.apache.skywalking.apm.network.servicemesh.ServiceMeshMetric;
import org.apache.skywalking.oap.server.receiver.envoy.MetricServiceGRPCHandlerTestMain;
import org.junit.*;

public class K8sHTTPAnalysisTest {
    @Test
    public void testIngressRoleIdentify() throws IOException {
        MockK8sAnalysis analysis = new MockK8sAnalysis();
        try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-ingress.msg"))) {
            StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);
            Role identify = analysis.identify(requestBuilder.getIdentifier(), Role.NONE);

            Assert.assertEquals(Role.PROXY, identify);
        }
    }

    @Test
    public void testSidecarRoleIdentify() throws IOException {
        MockK8sAnalysis analysis = new MockK8sAnalysis();
        try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-mesh-server-sidecar.msg"))) {
            StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);
            Role identify = analysis.identify(requestBuilder.getIdentifier(), Role.NONE);

            Assert.assertEquals(Role.SIDECAR, identify);
        }
    }

    @Test
    public void testIngressMetric() throws IOException {
        MockK8sAnalysis analysis = new MockK8sAnalysis();
        try (InputStreamReader isr = new InputStreamReader(getResourceAsStream("envoy-ingress.msg"))) {
            StreamAccessLogsMessage.Builder requestBuilder = StreamAccessLogsMessage.newBuilder();
            JsonFormat.parser().merge(isr, requestBuilder);

            analysis.analysis(requestBuilder.getIdentifier(), requestBuilder.getHttpLogs().getLogEntry(0), Role.PROXY);

            Assert.assertEquals(2, analysis.metrics.size());

            ServiceMeshMetric incoming = analysis.metrics.get(0);
            Assert.assertEquals("UNKNOWN", incoming.getSourceServiceName());
            Assert.assertEquals("ingress", incoming.getDestServiceName());

            ServiceMeshMetric outgoing = analysis.metrics.get(1);
            Assert.assertEquals("ingress", outgoing.getSourceServiceName());
            Assert.assertEquals("productpage", outgoing.getDestServiceName());
        }
    }

    public static class MockK8sAnalysis extends K8sALSServiceMeshHTTPAnalysis {
        private List<ServiceMeshMetric> metrics = new ArrayList<>();

        @Override
        protected void forward(ServiceMeshMetric metric) {
            metrics.add(metric);
        }

        @Override
        protected ServiceMetaInfo find(String ip, int port) {
            switch (ip) {
                case "10.44.2.56":
                    return new ServiceMetaInfo("ingress", "ingress-Inst");
                case "10.44.2.54":
                    return new ServiceMetaInfo("productpage", "productpage-Inst");
                case "10.44.6.66":
                    return new ServiceMetaInfo("detail", "detail-Inst");
            }
            return ServiceMetaInfo.UNKNOWN;
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
