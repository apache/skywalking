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
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.ServiceMetaInfoFactory;
import org.apache.skywalking.oap.server.receiver.envoy.als.AbstractALSAnalyzer;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;

import static org.apache.skywalking.apm.util.StringUtil.isBlank;
import static org.apache.skywalking.oap.server.library.util.CollectionUtils.isNotEmpty;
import static org.apache.skywalking.oap.server.receiver.envoy.als.LogEntry2MetricsAdapter.NON_TLS;
import static org.apache.skywalking.oap.server.receiver.envoy.als.k8s.Addresses.isValid;

/**
 * Analysis log based on ingress and mesh scenarios.
 */
@Slf4j
public class K8sALSServiceMeshHTTPAnalysis extends AbstractALSAnalyzer {
    protected K8SServiceRegistry serviceRegistry;

    protected EnvoyMetricReceiverConfig config;

    @Override
    public String name() {
        return "k8s-mesh";
    }

    @Override
    @SneakyThrows
    public void init(ModuleManager manager, EnvoyMetricReceiverConfig config) {
        this.config = config;
        serviceRegistry = new K8SServiceRegistry(config);
        serviceRegistry.start();
    }

    @Override
    public Result analysis(
        final Result result,
        final StreamAccessLogsMessage.Identifier identifier,
        final HTTPAccessLogEntry entry,
        final Role role
    ) {
        if (isNotEmpty(result.getMetrics())) {
            return result;
        }
        if (serviceRegistry.isEmpty()) {
            return Result.builder().build();
        }
        switch (role) {
            case PROXY:
                return analyzeProxy(entry);
            case SIDECAR:
                return analyzeSideCar(entry);
        }

        return Result.builder().build();
    }

    protected Result analyzeSideCar(final HTTPAccessLogEntry entry) {
        if (!entry.hasCommonProperties()) {
            return Result.builder().build();
        }
        final AccessLogCommon properties = entry.getCommonProperties();
        final String cluster = properties.getUpstreamCluster();
        if (isBlank(cluster)) {
            return Result.builder().build();
        }

        final List<ServiceMeshMetric.Builder> sources = new ArrayList<>();

        final Address downstreamRemoteAddress =
            properties.hasDownstreamDirectRemoteAddress()
                ? properties.getDownstreamDirectRemoteAddress()
                : properties.getDownstreamRemoteAddress();
        final ServiceMetaInfo downstreamService = find(downstreamRemoteAddress.getSocketAddress().getAddress());
        final Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
        if (!isValid(downstreamRemoteAddress) || !isValid(downstreamLocalAddress)) {
            return Result.builder().build();
        }
        final ServiceMetaInfo localService = find(downstreamLocalAddress.getSocketAddress().getAddress());

        if (cluster.startsWith("inbound|")) {
            // Server side
            final ServiceMeshMetric.Builder metrics;
            if (downstreamService.equals(config.serviceMetaInfoFactory().unknown())) {
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
            if (!isValid(upstreamRemoteAddress)) {
                return Result.builder().metrics(sources).service(localService).build();
            }
            final ServiceMetaInfo destService = find(upstreamRemoteAddress.getSocketAddress().getAddress());

            final ServiceMeshMetric.Builder metric = newAdapter(entry, downstreamService, destService).adaptToUpstreamMetrics();

            log.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
            sources.add(metric);
        }

        return Result.builder().metrics(sources).service(localService).build();
    }

    protected Result analyzeProxy(final HTTPAccessLogEntry entry) {
        if (!entry.hasCommonProperties()) {
            return Result.builder().build();
        }
        final AccessLogCommon properties = entry.getCommonProperties();
        final Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
        final Address downstreamRemoteAddress = properties.hasDownstreamDirectRemoteAddress() ?
            properties.getDownstreamDirectRemoteAddress() : properties.getDownstreamRemoteAddress();
        final Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
        if (!isValid(downstreamLocalAddress) || !isValid(downstreamRemoteAddress) || !isValid(upstreamRemoteAddress)) {
            return Result.builder().build();
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

        return Result.builder().metrics(result).service(ingress).build();
    }

    /**
     * @return found service info, or {@link ServiceMetaInfoFactory#unknown()} to represent not found.
     */
    protected ServiceMetaInfo find(String ip) {
        return serviceRegistry.findService(ip);
    }

}
