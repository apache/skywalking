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

import com.google.protobuf.Value;
import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.servicemesh.v3.HTTPServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetrics;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.ServiceMetaInfoFactory;
import org.apache.skywalking.oap.server.receiver.envoy.als.AbstractALSAnalyzer;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.als.istio.IstioServiceEntryRegistry;

import java.util.Objects;

import static org.apache.skywalking.oap.server.core.Const.TLS_MODE.NON_TLS;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isBlank;
import static org.apache.skywalking.oap.server.receiver.envoy.als.k8s.Addresses.isValid;

/**
 * Analysis log based on ingress and mesh scenarios.
 */
@Slf4j
public class K8sALSServiceMeshHTTPAnalysis extends AbstractALSAnalyzer {
    protected K8SServiceRegistry k8sServiceRegistry;
    protected IstioServiceEntryRegistry istioServiceRegistry;

    protected EnvoyMetricReceiverConfig config;

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
        final Result result,
        final StreamAccessLogsMessage.Identifier identifier,
        final HTTPAccessLogEntry entry,
        final Role role
    ) {
        switch (role) {
            case PROXY:
                return analyzeProxy(result, entry);
            case SIDECAR:
                if (result.hasResult()) {
                    return result;
                }
                return analyzeSideCar(result, entry);
            case WAYPOINT:
                return analyzeWaypoint(result, identifier, entry);
            case NONE:
                return result;
        }

        return Result.builder().build();
    }

    protected Result analyzeSideCar(final Result previousResult, final HTTPAccessLogEntry entry) {
        if (!entry.hasCommonProperties()) {
            return previousResult;
        }
        final AccessLogCommon properties = entry.getCommonProperties();
        final String cluster = properties.getUpstreamCluster();
        if (isBlank(cluster)) {
            return previousResult;
        }

        final Address downstreamRemoteAddress =
            properties.hasDownstreamDirectRemoteAddress()
                ? properties.getDownstreamDirectRemoteAddress()
                : properties.getDownstreamRemoteAddress();
        final ServiceMetaInfo downstreamService = find(Addresses.getAddressIP(downstreamRemoteAddress));
        final Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
        if (!isValid(downstreamRemoteAddress) || !isValid(downstreamLocalAddress)) {
            return previousResult;
        }
        final ServiceMetaInfo localService = find(Addresses.getAddressIP(downstreamLocalAddress));

        final var result = Result.builder();
        final var previousMetrics = previousResult.getMetrics();
        final var sources = previousMetrics.getHttpMetricsBuilder();
        if (cluster.startsWith("inbound|")) {
            // Server side
            final HTTPServiceMeshMetric.Builder metrics;
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
            sources.addMetrics(metrics);
            result.hasDownstreamMetrics(true);
        } else if (cluster.startsWith("outbound|")) {
            // sidecar(client side) -> sidecar
            final Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
            if (!isValid(upstreamRemoteAddress)) {
                return result.metrics(ServiceMeshMetrics.newBuilder().setHttpMetrics(sources)).service(localService).build();
            }
            final ServiceMetaInfo destService = find(Addresses.getAddressIP(upstreamRemoteAddress));

            final HTTPServiceMeshMetric.Builder metric = newAdapter(entry, downstreamService, destService).adaptToUpstreamMetrics();

            log.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
            sources.addMetrics(metric);
            result.hasUpstreamMetrics(true);
        }

        return result.metrics(previousMetrics.setHttpMetrics(sources)).service(localService).build();
    }

    protected Result analyzeProxy(Result previousResult, final HTTPAccessLogEntry entry) {
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
        if (!isValid(downstreamLocalAddress) || !isValid(downstreamRemoteAddress) || !isValid(upstreamRemoteAddress)) {
            return previousResult;
        }

        return buildUpstreamDownstreamMetrics(previousResult, entry,
            Addresses.getAddressIP(downstreamRemoteAddress),
            Addresses.getAddressIP(downstreamLocalAddress),
            Addresses.getAddressIP(upstreamRemoteAddress), NON_TLS);
    }

    protected Result analyzeWaypoint(Result previousResult, StreamAccessLogsMessage.Identifier identifier, final HTTPAccessLogEntry entry) {
        if (!entry.hasCommonProperties()) {
            return previousResult;
        }
        if (previousResult.hasUpstreamMetrics() && previousResult.hasDownstreamMetrics()) {
            return previousResult;
        }

        final String waypointIP = getWaypointIP(identifier);
        final AccessLogCommon properties = entry.getCommonProperties();
        final Address downstreamRemoteAddress = properties.hasDownstreamDirectRemoteAddress() &&
            Addresses.isValid(properties.getDownstreamDirectRemoteAddress()) ?
                properties.getDownstreamDirectRemoteAddress() : properties.getDownstreamRemoteAddress();
        final Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
        if (!isValid(downstreamRemoteAddress) || !isValid(upstreamRemoteAddress)) {
            return previousResult;
        }

        return buildUpstreamDownstreamMetrics(previousResult, entry,
            Addresses.getAddressIP(downstreamRemoteAddress),
            waypointIP,
            Addresses.getAddressIP(upstreamRemoteAddress), null);
    }

    protected String getWaypointIP(StreamAccessLogsMessage.Identifier identifier) {
        final Value instanceIps = identifier.getNode().getMetadata().getFieldsMap().get("INSTANCE_IPS");
        if (instanceIps != null && instanceIps.hasStringValue()) {
            final String[] split = instanceIps.getStringValue().split(":", 2);
            if (split.length == 2) {
                return split[0];
            }
        }

        final String nodeId = identifier.getNode().getId();
        final String[] nodeInfo = nodeId.split("~", 3);
        if (nodeInfo.length != 3) {
            return null;
        }
        return nodeInfo[1];
    }

    protected Result buildUpstreamDownstreamMetrics(Result previousResult, final HTTPAccessLogEntry entry,
                                                    String downStreamRemoteAddr, String downStreamLocalAddr,
                                                    String upstreamRemoteAddr, String upstreamMetricsTLS) {
        if (StringUtil.isEmpty(downStreamRemoteAddr) || StringUtil.isEmpty(downStreamLocalAddr) || StringUtil.isEmpty(upstreamRemoteAddr)) {
            return previousResult;
        }

        final ServiceMetaInfo ingress = find(downStreamLocalAddr);

        final var newResult = previousResult.toBuilder();
        final var previousMetrics = previousResult.getMetrics();
        final var previousHttpMetrics = previousMetrics.getHttpMetricsBuilder();

        if (!previousResult.hasDownstreamMetrics()) {
            final ServiceMetaInfo outside = find(downStreamRemoteAddr);

            final HTTPServiceMeshMetric.Builder metric = newAdapter(entry, outside, ingress).adaptToDownstreamMetrics();

            log.debug("Transformed ingress inbound mesh metric {}", metric);
            previousHttpMetrics.addMetrics(metric);
            newResult.hasDownstreamMetrics(true);
        }

        if (!previousResult.hasUpstreamMetrics()) {
            final ServiceMetaInfo targetService = find(upstreamRemoteAddr);

            HTTPServiceMeshMetric.Builder outboundMetric =
                newAdapter(entry, ingress, targetService)
                    .adaptToUpstreamMetrics();
            if (StringUtil.isNotEmpty(upstreamMetricsTLS)) {
                // Can't parse it from tls properties, leave it to Server side.
                outboundMetric = outboundMetric.setTlsMode(NON_TLS);
            }

            log.debug("Transformed ingress outbound mesh metric {}", outboundMetric);
            previousHttpMetrics.addMetrics(outboundMetric);
            newResult.hasUpstreamMetrics(true);
        }

        return newResult.metrics(previousMetrics.setHttpMetrics(previousHttpMetrics)).service(ingress).build();
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
