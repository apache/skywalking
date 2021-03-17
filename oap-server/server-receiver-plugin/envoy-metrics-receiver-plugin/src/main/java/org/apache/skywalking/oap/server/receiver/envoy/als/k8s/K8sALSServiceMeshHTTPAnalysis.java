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

package org.apache.skywalking.oap.server.receiver.envoy.als.k8s;

import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.AbstractALSAnalyzer;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;

import static org.apache.skywalking.oap.server.library.util.CollectionUtils.isNotEmpty;
import static org.apache.skywalking.oap.server.receiver.envoy.als.LogEntry2MetricsAdapter.NON_TLS;

/**
 * Analysis log based on ingress and mesh scenarios.
 */
@Slf4j
public class K8sALSServiceMeshHTTPAnalysis extends AbstractALSAnalyzer {
    protected K8SServiceRegistry serviceRegistry;

    @Override
    public String name() {
        return "k8s-mesh";
    }

    @Override
    @SneakyThrows
    public void init(ModuleManager manager, EnvoyMetricReceiverConfig config) {
        serviceRegistry = new K8SServiceRegistry(config);
        serviceRegistry.start();
    }

    @Override
    public List<ServiceMeshMetric.Builder> analysis(
        final List<ServiceMeshMetric.Builder> result,
        final StreamAccessLogsMessage.Identifier identifier,
        final HTTPAccessLogEntry entry,
        final Role role
    ) {
        if (isNotEmpty(result)) {
            return result;
        }
        if (serviceRegistry.isEmpty()) {
            return Collections.emptyList();
        }
        switch (role) {
            case PROXY:
                return analyzeProxy(entry);
            case SIDECAR:
                return analyzeSideCar(entry);
        }

        return Collections.emptyList();
    }

    protected List<ServiceMeshMetric.Builder> analyzeSideCar(final HTTPAccessLogEntry entry) {
        final AccessLogCommon properties = entry.getCommonProperties();
        if (properties == null) {
            return Collections.emptyList();
        }
        final String cluster = properties.getUpstreamCluster();
        if (cluster == null) {
            return Collections.emptyList();
        }

        final List<ServiceMeshMetric.Builder> sources = new ArrayList<>();

        final Address downstreamRemoteAddress =
            properties.hasDownstreamDirectRemoteAddress()
                ? properties.getDownstreamDirectRemoteAddress()
                : properties.getDownstreamRemoteAddress();
        final ServiceMetaInfo downstreamService = find(downstreamRemoteAddress.getSocketAddress().getAddress());
        final Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
        final ServiceMetaInfo localService = find(downstreamLocalAddress.getSocketAddress().getAddress());

        if (cluster.startsWith("inbound|")) {
            // Server side
            final ServiceMeshMetric.Builder metrics;
            if (downstreamService.equals(ServiceMetaInfo.UNKNOWN)) {
                // Ingress -> sidecar(server side)
                // Mesh telemetry without source, the relation would be generated.
                metrics = newAdapter(entry, null, localService).adaptToDownstreamMetrics();

                log.debug("Transformed ingress->sidecar inbound mesh metrics {}", metrics);
            } else {
                // sidecar -> sidecar(server side)
                metrics = newAdapter(entry, downstreamService, localService).adaptToDownstreamMetrics();

                log.debug("Transformed sidecar->sidecar(server side) inbound mesh metrics {}", metrics);
            }
            sources.add(metrics);
        } else if (cluster.startsWith("outbound|")) {
            // sidecar(client side) -> sidecar
            final Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
            final ServiceMetaInfo destService = find(upstreamRemoteAddress.getSocketAddress().getAddress());

            final ServiceMeshMetric.Builder metric = newAdapter(entry, downstreamService, destService).adaptToUpstreamMetrics();

            log.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
            sources.add(metric);
        }

        return sources;
    }

    protected List<ServiceMeshMetric.Builder> analyzeProxy(final HTTPAccessLogEntry entry) {
        final AccessLogCommon properties = entry.getCommonProperties();
        if (properties == null) {
            return Collections.emptyList();
        }
        final Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
        final Address downstreamRemoteAddress = properties.hasDownstreamDirectRemoteAddress() ?
            properties.getDownstreamDirectRemoteAddress() : properties.getDownstreamRemoteAddress();
        final Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
        if (downstreamLocalAddress == null || downstreamRemoteAddress == null || upstreamRemoteAddress == null) {
            return Collections.emptyList();
        }

        final List<ServiceMeshMetric.Builder> result = new ArrayList<>(2);
        final SocketAddress downstreamRemoteAddressSocketAddress = downstreamRemoteAddress.getSocketAddress();
        final ServiceMetaInfo outside = find(downstreamRemoteAddressSocketAddress.getAddress());

        final SocketAddress downstreamLocalAddressSocketAddress = downstreamLocalAddress.getSocketAddress();
        final ServiceMetaInfo ingress = find(downstreamLocalAddressSocketAddress.getAddress());

        final ServiceMeshMetric.Builder metric = newAdapter(entry, outside, ingress).adaptToDownstreamMetrics();

        log.debug("Transformed ingress inbound mesh metric {}", metric);
        result.add(metric);

        final SocketAddress upstreamRemoteAddressSocketAddress = upstreamRemoteAddress.getSocketAddress();
        final ServiceMetaInfo targetService = find(upstreamRemoteAddressSocketAddress.getAddress());

        final ServiceMeshMetric.Builder outboundMetric =
            newAdapter(entry, ingress, targetService)
                .adaptToUpstreamMetrics()
                // Can't parse it from tls properties, leave it to Server side.
                .setTlsMode(NON_TLS);

        log.debug("Transformed ingress outbound mesh metric {}", outboundMetric);
        result.add(outboundMetric);

        return result;
    }

    /**
     * @return found service info, or {@link ServiceMetaInfo#UNKNOWN} to represent not found.
     */
    protected ServiceMetaInfo find(String ip) {
        return serviceRegistry.findService(ip);
    }

}
