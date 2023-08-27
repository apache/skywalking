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

package org.apache.skywalking.oap.server.receiver.envoy.als.tcp.k8s;

import com.google.common.base.Strings;
import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.servicemesh.v3.TCPServiceMeshMetric;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.ServiceMetaInfoFactory;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.als.istio.IstioServiceEntryRegistry;
import org.apache.skywalking.oap.server.receiver.envoy.als.k8s.K8SServiceRegistry;
import org.apache.skywalking.oap.server.receiver.envoy.als.tcp.AbstractTCPAccessLogAnalyzer;

import java.util.Objects;

import static org.apache.skywalking.oap.server.core.Const.TLS_MODE.NON_TLS;

/**
 * Analysis log based on ingress and mesh scenarios.
 */
@Slf4j
public class K8sALSServiceMeshTCPAnalysis extends AbstractTCPAccessLogAnalyzer {
    protected K8SServiceRegistry k8sServiceRegistry;
    protected IstioServiceEntryRegistry istioServiceRegistry;

    private EnvoyMetricReceiverConfig config;

    @Override
    public String name() {
        return "k8s-mesh";
    }

    @Override
    @SneakyThrows
    public void init(ModuleManager manager, EnvoyMetricReceiverConfig config) {
        this.config = config;
        k8sServiceRegistry = new K8SServiceRegistry(config);
        istioServiceRegistry = new IstioServiceEntryRegistry(config);
    }

    @Override
    public Result analysis(
        final Result previousResult,
        final StreamAccessLogsMessage.Identifier identifier,
        final TCPAccessLogEntry entry,
        final Role role
    ) {
        switch (role) {
            case PROXY:
                return analyzeProxy(previousResult, entry);
            case SIDECAR:
                if (previousResult.hasResult()) {
                    return previousResult;
                }
                return analyzeSideCar(previousResult, entry);
        }

        return previousResult;
    }

    protected Result analyzeSideCar(final Result previousResult, final TCPAccessLogEntry entry) {
        if (!entry.hasCommonProperties()) {
            return previousResult;
        }
        final AccessLogCommon properties = entry.getCommonProperties();
        final String cluster = properties.getUpstreamCluster();
        if (Strings.isNullOrEmpty(cluster)) {
            return previousResult;
        }

        final var newResult = previousResult.toBuilder();
        final var previousMetrics = previousResult.getMetrics();
        final var sources = previousMetrics.getTcpMetricsBuilder();

        final Address downstreamRemoteAddress =
            properties.hasDownstreamDirectRemoteAddress()
                ? properties.getDownstreamDirectRemoteAddress()
                : properties.getDownstreamRemoteAddress();
        final ServiceMetaInfo downstreamService = find(downstreamRemoteAddress.getSocketAddress().getAddress());
        final Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
        final ServiceMetaInfo localService = find(downstreamLocalAddress.getSocketAddress().getAddress());

        if (cluster.startsWith("inbound|")) {
            // Server side
            final TCPServiceMeshMetric metrics;
            if (downstreamService.equals(config.serviceMetaInfoFactory().unknown())) {
                // Ingress -> sidecar(server side)
                // Mesh telemetry without source, the relation would be generated.
                metrics = newAdapter(entry, null, localService).adaptToDownstreamMetrics().build();

                log.debug("Transformed ingress->sidecar inbound mesh metrics {}", metrics);
            } else {
                // sidecar -> sidecar(server side)
                metrics = newAdapter(entry, downstreamService, localService).adaptToDownstreamMetrics().build();

                log.debug("Transformed sidecar->sidecar(server side) inbound mesh metrics {}", metrics);
            }
            sources.addMetrics(metrics);
            newResult.hasDownstreamMetrics(true);
        } else if (cluster.startsWith("outbound|")) {
            // sidecar(client side) -> sidecar
            final Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
            final ServiceMetaInfo destService = find(upstreamRemoteAddress.getSocketAddress().getAddress());

            final TCPServiceMeshMetric metric = newAdapter(entry, downstreamService, destService).adaptToUpstreamMetrics().build();

            log.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
            sources.addMetrics(metric);
            newResult.hasUpstreamMetrics(true);
        }

        return newResult.metrics(previousMetrics.setTcpMetrics(sources)).service(localService).build();
    }

    protected Result analyzeProxy(final Result previousResult, final TCPAccessLogEntry entry) {
        if (!entry.hasCommonProperties()) {
            return previousResult;
        }
        if (previousResult.hasUpstreamMetrics() && previousResult.hasDownstreamMetrics()) {
            return previousResult;
        }

        final AccessLogCommon properties = entry.getCommonProperties();
        final Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
        final Address downstreamRemoteAddress = properties.hasDownstreamDirectRemoteAddress() ?
            properties.getDownstreamDirectRemoteAddress() : properties.getDownstreamRemoteAddress();
        final Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
        if (!properties.hasDownstreamLocalAddress() || !properties.hasDownstreamRemoteAddress() || !properties.hasUpstreamRemoteAddress()) {
            return previousResult;
        }

        final var downstreamLocalAddressSocketAddress = downstreamLocalAddress.getSocketAddress();
        final var ingress = find(downstreamLocalAddressSocketAddress.getAddress());

        final var newResult = previousResult.toBuilder();
        final var previousMetrics = previousResult.getMetrics();
        final var metrics = previousMetrics.getTcpMetricsBuilder();

        if (!previousResult.hasDownstreamMetrics()) {
            final SocketAddress downstreamRemoteAddressSocketAddress = downstreamRemoteAddress.getSocketAddress();
            final ServiceMetaInfo outside = find(downstreamRemoteAddressSocketAddress.getAddress());

            final TCPServiceMeshMetric.Builder metric = newAdapter(entry, outside, ingress).adaptToDownstreamMetrics();

            log.debug("Transformed ingress inbound mesh metric {}", metric);
            metrics.addMetrics(metric);
            newResult.hasDownstreamMetrics(true);
        }

        if (!previousResult.hasUpstreamMetrics()) {
            final SocketAddress upstreamRemoteAddressSocketAddress = upstreamRemoteAddress.getSocketAddress();
            final ServiceMetaInfo targetService = find(upstreamRemoteAddressSocketAddress.getAddress());

            final TCPServiceMeshMetric.Builder outboundMetric =
                newAdapter(entry, ingress, targetService)
                    .adaptToUpstreamMetrics()
                    // Can't parse it from tls properties, leave it to Server side.
                    .setTlsMode(NON_TLS);

            log.debug("Transformed ingress outbound mesh metric {}", outboundMetric);
            metrics.addMetrics(outboundMetric);
            newResult.hasUpstreamMetrics(true);
        }

        return newResult.metrics(previousMetrics.setTcpMetrics(metrics)).service(ingress).build();
    }

    /**
     * @return found service info, or {@link ServiceMetaInfoFactory#unknown()} to represent not found.
     */
    protected ServiceMetaInfo find(String ip) {
        final var istioService = istioServiceRegistry.findService(ip);
        if (!Objects.equals(config.serviceMetaInfoFactory().unknown(), istioService)) {
            return istioService;
        }

        return k8sServiceRegistry.findService(ip);
    }
}
