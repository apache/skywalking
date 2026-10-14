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

import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogConnection;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogConnectionTLSMode;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogProtocolType;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.ConnectionAddress;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFAccessLogNodeInfo;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.IPAddress;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.KubernetesProcessAddress;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.receiver.ebpf.provider.handler.address.K8sProcessAddress;
import org.apache.skywalking.oap.server.receiver.ebpf.provider.handler.address.ProcessAddress;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AccessLogServiceHandlerTest {
    /**
     * What the Kubernetes metadata of each IP resolves to, once read; an IP not in it is unknown.
     */
    private final Map<String, ProcessAddress> readMetadata = new HashMap<>();
    private final NamingControl namingControl = mock(NamingControl.class);
    private AccessLogServiceHandler handler;
    private AccessLogServiceHandler.NodeInfo node;

    @BeforeEach
    public void setUp() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder core = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(core);
        when(core.provider()).thenReturn(coreServices);
        when(coreServices.getService(SourceReceiver.class)).thenReturn(mock(SourceReceiver.class));
        when(coreServices.getService(NamingControl.class)).thenReturn(namingControl);
        final ModuleProviderHolder telemetry = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder telemetryServices = mock(ModuleServiceHolder.class);
        final MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetry);
        when(telemetry.provider()).thenReturn(telemetryServices);
        when(telemetryServices.getService(MetricsCreator.class)).thenReturn(metricsCreator);
        when(metricsCreator.createCounter(anyString(), anyString(), any(), any())).thenReturn(mock(CounterMetrics.class));
        when(metricsCreator.createHistogramMetric(anyString(), anyString(), any(), any()))
            .thenReturn(mock(HistogramMetrics.class));

        handler = new AccessLogServiceHandler(moduleManager) {
            @Override
            protected ProcessAddress buildProcessAddressByIP(final NodeInfo nodeInfo,
                                                             final AccessLogConnection connection,
                                                             final boolean isLocal,
                                                             final IPAddress ipAddress) {
                return readMetadata.getOrDefault(ipAddress.getHost(), buildUnknownAddress());
            }
        };
        node = new AccessLogServiceHandler.NodeInfo(EBPFAccessLogNodeInfo.newBuilder().setName("node-1").build());
    }

    @Test
    public void aConnectionBetweenIPsNotReadYetResolvesOnItsLaterMessages() {
        final AccessLogServiceHandler.ConnectionInfo cold = handler.new ConnectionInfo(
            namingControl, node, connection("10.0.0.1", "10.0.0.2"));
        // Both unknown: taken for traffic within one service, which is not analyzed.
        assertFalse(cold.isValid());
        assertSame(cold, cold.resolveUnknownAddresses());

        readMetadata.put("10.0.0.1", address("svc-a.default"));
        readMetadata.put("10.0.0.2", address("svc-b.default"));
        final AccessLogServiceHandler.ConnectionInfo resolved = cold.resolveUnknownAddresses();

        assertTrue(resolved.isValid());
        assertEquals("svc-a.default", resolved.getLocal().getServiceName());
        assertEquals("svc-b.default", resolved.getRemote().getServiceName());
    }

    @Test
    public void aResolvedConnectionIsNotResolvedAgain() {
        readMetadata.put("10.0.0.1", address("svc-a.default"));
        readMetadata.put("10.0.0.2", address("svc-b.default"));
        final AccessLogServiceHandler.ConnectionInfo resolved = handler.new ConnectionInfo(
            namingControl, node, connection("10.0.0.1", "10.0.0.2"));

        readMetadata.clear();
        assertSame(resolved, resolved.resolveUnknownAddresses());
    }

    @Test
    public void aResolvedAddressIsKeptWhenItsMetadataIsGone() {
        readMetadata.put("10.0.0.1", address("svc-a.default"));
        final AccessLogServiceHandler.ConnectionInfo halfResolved = handler.new ConnectionInfo(
            namingControl, node, connection("10.0.0.1", "10.0.0.2"));
        assertTrue(halfResolved.isValid());

        // The known address's metadata expired meanwhile; the unknown one is still not read.
        readMetadata.clear();
        assertSame(halfResolved, halfResolved.resolveUnknownAddresses());

        readMetadata.put("10.0.0.2", address("svc-b.default"));
        final AccessLogServiceHandler.ConnectionInfo resolved = halfResolved.resolveUnknownAddresses();
        assertEquals("svc-a.default", resolved.getLocal().getServiceName());
        assertEquals("svc-b.default", resolved.getRemote().getServiceName());
        assertTrue(resolved.isValid());
    }

    private static AccessLogConnection connection(final String localIP, final String remoteIP) {
        return AccessLogConnection.newBuilder()
                                  .setLocal(ConnectionAddress.newBuilder()
                                                             .setIp(IPAddress.newBuilder().setHost(localIP).setPort(80)))
                                  .setRemote(ConnectionAddress.newBuilder()
                                                              .setIp(IPAddress.newBuilder().setHost(remoteIP).setPort(40000)))
                                  .setRole(DetectPoint.server)
                                  .setTlsMode(AccessLogConnectionTLSMode.Plain)
                                  .setProtocol(AccessLogProtocolType.HTTP_1)
                                  .build();
    }

    private static ProcessAddress address(final String serviceName) {
        return new K8sProcessAddress(KubernetesProcessAddress.newBuilder().setServiceName(serviceName).build());
    }
}
