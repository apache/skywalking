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

package org.apache.skywalking.oap.server.receiver.ebpf.provider.handler;

import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.common.v3.Instant;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogConnection;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogConnectionTLSMode;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogHTTPProtocol;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogHTTPProtocolRequest;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogHTTPProtocolRequestMethod;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogHTTPProtocolResponse;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogHTTPProtocolVersion;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogProtocolType;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.ConnectionAddress;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFAccessLogNodeInfo;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFOffsetTimestamp;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFTimestamp;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.KubernetesProcessAddress;
import org.apache.skywalking.apm.network.servicemesh.v3.HTTPServiceMeshMetric;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

public class AccessLogServiceHandlerMeshMetricsTest {

    private AccessLogServiceHandler handler;
    private NamingControl namingControl;

    @BeforeEach
    public void setUp() throws Exception {
        // Avoid the production constructor (scheduled drop-reason printer + ModuleManager).
        handler = mock(AccessLogServiceHandler.class, CALLS_REAL_METHODS);
        namingControl = new NamingControl(512, 512, 512, new EndpointNameGrouping());
        Field namingControlField = AccessLogServiceHandler.class.getDeclaredField("namingControl");
        namingControlField.setAccessible(true);
        namingControlField.set(handler, namingControl);
    }

    @Test
    public void generateHTTPServiceMeshMetricsKeepsSubSecondLatencyAcrossSecondBoundary() {
        // boot 1000s + 500ms; start 1.9s / end 2.023s after boot → 123ms wall latency.
        // Old second-truncation path would have reported 1000ms (offsets floor to 1s and 2s).
        final AccessLogServiceHandler.NodeInfo node = new AccessLogServiceHandler.NodeInfo(
            EBPFAccessLogNodeInfo.newBuilder()
                .setName("node-1")
                .setBootTime(Instant.newBuilder().setSeconds(1000L).setNanos(500_000_000).build())
                .build());
        final AccessLogServiceHandler.ConnectionInfo connection = handler.new ConnectionInfo(
            namingControl, node, AccessLogConnection.newBuilder()
                .setRole(DetectPoint.client)
                .setTlsMode(AccessLogConnectionTLSMode.Plain)
                .setProtocol(AccessLogProtocolType.HTTP_1)
                .setLocal(k8sAddress("productpage.default", "productpage-0"))
                .setRemote(k8sAddress("details.default", "details-0"))
                .build());

        final AccessLogHTTPProtocol http = AccessLogHTTPProtocol.newBuilder()
            .setStartTime(offset(1_900_000_000L))
            .setEndTime(offset(2_023_000_000L))
            .setVersion(AccessLogHTTPProtocolVersion.HTTP1)
            .setRequest(AccessLogHTTPProtocolRequest.newBuilder()
                .setMethod(AccessLogHTTPProtocolRequestMethod.Get)
                .setPath("/details")
                .build())
            .setResponse(AccessLogHTTPProtocolResponse.newBuilder().setStatusCode(200).build())
            .build();

        final HTTPServiceMeshMetric metric = handler.generateHTTPServiceMeshMetrics(
            node, connection, http, AccessLogConnectionTLSMode.Plain.name());

        assertEquals(1_002_400L, metric.getStartTime());
        assertEquals(1_002_523L, metric.getEndTime());
        assertEquals(123, metric.getLatency());
        assertEquals("productpage.default", metric.getSourceServiceName());
        assertEquals("details.default", metric.getDestServiceName());
        assertEquals(200, metric.getResponseCode());
    }

    private static ConnectionAddress k8sAddress(String service, String pod) {
        return ConnectionAddress.newBuilder()
            .setKubernetes(KubernetesProcessAddress.newBuilder()
                .setServiceName(service)
                .setPodName(pod)
                .setContainerName("app")
                .setProcessName("app")
                .build())
            .build();
    }

    private static EBPFTimestamp offset(long nanosSinceBoot) {
        return EBPFTimestamp.newBuilder()
            .setOffset(EBPFOffsetTimestamp.newBuilder().setOffset(nanosSinceBoot).build())
            .build();
    }
}
