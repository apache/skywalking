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

import com.google.common.base.Strings;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.envoyproxy.envoy.api.v2.core.Address;
import io.envoyproxy.envoy.api.v2.core.Node;
import io.envoyproxy.envoy.api.v2.core.SocketAddress;
import io.envoyproxy.envoy.data.accesslog.v2.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v2.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v2.HTTPRequestProperties;
import io.envoyproxy.envoy.data.accesslog.v2.HTTPResponseProperties;
import io.envoyproxy.envoy.data.accesslog.v2.TLSProperties;
import io.envoyproxy.envoy.service.accesslog.v2.StreamAccessLogsMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.aop.server.receiver.mesh.TelemetryDataDispatcher;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.v3.Protocol;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.ALSHTTPAnalysis;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;

/**
 * Analysis log based on ingress and mesh scenarios.
 */
@Slf4j
public class K8sALSServiceMeshHTTPAnalysis implements ALSHTTPAnalysis {
    private static final String NON_TLS = "NONE";

    private static final String M_TLS = "mTLS";

    private static final String TLS = "TLS";

    protected K8SServiceRegistry serviceRegistry;

    @Override
    public String name() {
        return "k8s-mesh";
    }

    @Override
    @SneakyThrows
    public void init(EnvoyMetricReceiverConfig config) {
        serviceRegistry = new K8SServiceRegistry(config);
        serviceRegistry.start();
    }

    @Override
    public List<Source> analysis(StreamAccessLogsMessage.Identifier identifier, HTTPAccessLogEntry entry, Role role) {
        if (serviceRegistry.isEmpty()) {
            return Collections.emptyList();
        }
        switch (role) {
            case PROXY:
                analysisProxy(identifier, entry);
                break;
            case SIDECAR:
                return analysisSideCar(identifier, entry);
        }

        return Collections.emptyList();
    }

    protected List<Source> analysisSideCar(StreamAccessLogsMessage.Identifier identifier, HTTPAccessLogEntry entry) {
        List<Source> sources = new ArrayList<>();
        AccessLogCommon properties = entry.getCommonProperties();
        if (properties != null) {
            String cluster = properties.getUpstreamCluster();
            if (cluster != null) {
                long startTime = formatAsLong(properties.getStartTime());
                long duration = formatAsLong(properties.getTimeToLastDownstreamTxByte());

                HTTPRequestProperties request = entry.getRequest();
                String endpoint = "/";
                Protocol protocol = Protocol.HTTP;
                if (request != null) {
                    endpoint = request.getPath();
                    String schema = request.getScheme();
                    if ("http".equals(schema) || "https".equals(schema)) {
                        protocol = Protocol.HTTP;
                    } else {
                        protocol = Protocol.gRPC;
                    }
                }
                HTTPResponseProperties response = entry.getResponse();
                int responseCode = 200;
                if (response != null) {
                    responseCode = response.getResponseCode().getValue();
                }
                boolean status = responseCode >= 200 && responseCode < 400;

                Address downstreamRemoteAddress = properties.getDownstreamRemoteAddress();
                ServiceMetaInfo downstreamService = find(downstreamRemoteAddress.getSocketAddress().getAddress());
                Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
                ServiceMetaInfo localService = find(downstreamLocalAddress.getSocketAddress().getAddress());
                String tlsMode = parseTLS(properties.getTlsProperties());

                ServiceMeshMetric.Builder metric = null;
                if (cluster.startsWith("inbound|")) {
                    // Server side
                    if (downstreamService.equals(ServiceMetaInfo.UNKNOWN)) {
                        // Ingress -> sidecar(server side)
                        // Mesh telemetry without source, the relation would be generated.
                        metric = ServiceMeshMetric.newBuilder()
                                                  .setStartTime(startTime)
                                                  .setEndTime(startTime + duration)
                                                  .setDestServiceName(localService.getServiceName())
                                                  .setDestServiceInstance(localService.getServiceInstanceName())
                                                  .setEndpoint(endpoint)
                                                  .setLatency((int) duration)
                                                  .setResponseCode(Math.toIntExact(responseCode))
                                                  .setStatus(status)
                                                  .setProtocol(protocol)
                                                  .setTlsMode(tlsMode)
                                                  .setDetectPoint(DetectPoint.server);

                        log.debug("Transformed ingress->sidecar inbound mesh metric {}", metric);
                    } else {
                        // sidecar -> sidecar(server side)
                        metric = ServiceMeshMetric.newBuilder()
                                                  .setStartTime(startTime)
                                                  .setEndTime(startTime + duration)
                                                  .setSourceServiceName(downstreamService.getServiceName())
                                                  .setSourceServiceInstance(downstreamService.getServiceInstanceName())
                                                  .setDestServiceName(localService.getServiceName())
                                                  .setDestServiceInstance(localService.getServiceInstanceName())
                                                  .setEndpoint(endpoint)
                                                  .setLatency((int) duration)
                                                  .setResponseCode(Math.toIntExact(responseCode))
                                                  .setStatus(status)
                                                  .setProtocol(protocol)
                                                  .setTlsMode(tlsMode)
                                                  .setDetectPoint(DetectPoint.server);

                        log.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
                    }
                } else if (cluster.startsWith("outbound|")) {
                    // sidecar(client side) -> sidecar
                    Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
                    ServiceMetaInfo destService = find(upstreamRemoteAddress.getSocketAddress().getAddress());

                    metric = ServiceMeshMetric.newBuilder()
                                              .setStartTime(startTime)
                                              .setEndTime(startTime + duration)
                                              .setSourceServiceName(downstreamService.getServiceName())
                                              .setSourceServiceInstance(downstreamService.getServiceInstanceName())
                                              .setDestServiceName(destService.getServiceName())
                                              .setDestServiceInstance(destService.getServiceInstanceName())
                                              .setEndpoint(endpoint)
                                              .setLatency((int) duration)
                                              .setResponseCode(Math.toIntExact(responseCode))
                                              .setStatus(status)
                                              .setProtocol(protocol)
                                              .setTlsMode(tlsMode)
                                              .setDetectPoint(DetectPoint.client);

                    log.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
                }

                Optional.ofNullable(metric).ifPresent(this::forward);
            }
        }
        return sources;
    }

    private String parseTLS(TLSProperties properties) {
        if (properties == null) {
            return NON_TLS;
        }
        if (Strings.isNullOrEmpty(Optional.ofNullable(properties.getLocalCertificateProperties())
                                          .orElse(TLSProperties.CertificateProperties.newBuilder().build()).getSubject())) {
            return NON_TLS;
        }
        if (Strings.isNullOrEmpty(Optional.ofNullable(properties.getPeerCertificateProperties())
                                          .orElse(TLSProperties.CertificateProperties.newBuilder().build()).getSubject())) {
            return TLS;
        }
        return M_TLS;
    }

    protected void analysisProxy(StreamAccessLogsMessage.Identifier identifier, HTTPAccessLogEntry entry) {
        AccessLogCommon properties = entry.getCommonProperties();
        if (properties != null) {
            Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
            Address downstreamRemoteAddress = properties.getDownstreamRemoteAddress();
            Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
            if (downstreamLocalAddress != null && downstreamRemoteAddress != null && upstreamRemoteAddress != null) {
                SocketAddress downstreamRemoteAddressSocketAddress = downstreamRemoteAddress.getSocketAddress();
                ServiceMetaInfo outside = find(downstreamRemoteAddressSocketAddress.getAddress());

                SocketAddress downstreamLocalAddressSocketAddress = downstreamLocalAddress.getSocketAddress();
                ServiceMetaInfo ingress = find(downstreamLocalAddressSocketAddress.getAddress());

                long startTime = formatAsLong(properties.getStartTime());
                long duration = formatAsLong(properties.getTimeToLastDownstreamTxByte());

                HTTPRequestProperties request = entry.getRequest();
                String endpoint = "/";
                Protocol protocol = Protocol.HTTP;
                if (request != null) {
                    endpoint = request.getPath();
                    String schema = request.getScheme();
                    if ("http".equals(schema) || "https".equals(schema)) {
                        protocol = Protocol.HTTP;
                    } else {
                        protocol = Protocol.gRPC;
                    }
                }
                HTTPResponseProperties response = entry.getResponse();
                int responseCode = 200;
                if (response != null) {
                    responseCode = response.getResponseCode().getValue();
                }
                boolean status = responseCode >= 200 && responseCode < 400;
                String tlsMode = parseTLS(properties.getTlsProperties());

                ServiceMeshMetric.Builder metric = ServiceMeshMetric.newBuilder()
                                                                    .setStartTime(startTime)
                                                                    .setEndTime(startTime + duration)
                                                                    .setSourceServiceName(outside.getServiceName())
                                                                    .setSourceServiceInstance(
                                                                        outside.getServiceInstanceName())
                                                                    .setDestServiceName(ingress.getServiceName())
                                                                    .setDestServiceInstance(
                                                                        ingress.getServiceInstanceName())
                                                                    .setEndpoint(endpoint)
                                                                    .setLatency((int) duration)
                                                                    .setResponseCode(Math.toIntExact(responseCode))
                                                                    .setStatus(status)
                                                                    .setProtocol(protocol)
                                                                    .setTlsMode(tlsMode)
                                                                    .setDetectPoint(DetectPoint.server);

                log.debug("Transformed ingress inbound mesh metric {}", metric);
                forward(metric);

                SocketAddress upstreamRemoteAddressSocketAddress = upstreamRemoteAddress.getSocketAddress();
                ServiceMetaInfo targetService = find(upstreamRemoteAddressSocketAddress.getAddress());

                long outboundStartTime = startTime + formatAsLong(properties.getTimeToFirstUpstreamTxByte());
                long outboundEndTime = startTime + formatAsLong(properties.getTimeToLastUpstreamRxByte());

                ServiceMeshMetric.Builder outboundMetric = ServiceMeshMetric.newBuilder()
                                                                            .setStartTime(outboundStartTime)
                                                                            .setEndTime(outboundEndTime)
                                                                            .setSourceServiceName(
                                                                                ingress.getServiceName())
                                                                            .setSourceServiceInstance(
                                                                                ingress.getServiceInstanceName())
                                                                            .setDestServiceName(
                                                                                targetService.getServiceName())
                                                                            .setDestServiceInstance(
                                                                                targetService.getServiceInstanceName())
                                                                            .setEndpoint(endpoint)
                                                                            .setLatency(
                                                                                (int) (outboundEndTime - outboundStartTime))
                                                                            .setResponseCode(
                                                                                Math.toIntExact(responseCode))
                                                                            .setStatus(status)
                                                                            .setProtocol(protocol)
                                                                            // Can't parse it from tls properties, leave
                                                                            // it to Server side.
                                                                            .setTlsMode(NON_TLS)
                                                                            .setDetectPoint(DetectPoint.client);

                log.debug("Transformed ingress outbound mesh metric {}", outboundMetric);
                forward(outboundMetric);
            }
        }
    }

    @Override
    public Role identify(StreamAccessLogsMessage.Identifier alsIdentifier, Role prev) {
        if (alsIdentifier != null) {
            Node node = alsIdentifier.getNode();
            if (node != null) {
                String id = node.getId();
                if (id.startsWith("router~")) {
                    return Role.PROXY;
                } else if (id.startsWith("sidecar~")) {
                    return Role.SIDECAR;
                }
            }
        }

        return prev;
    }

    /**
     * @return found service info, or {@link ServiceMetaInfo#UNKNOWN} to represent not found.
     */
    protected ServiceMetaInfo find(String ip) {
        return serviceRegistry.findService(ip);
    }

    protected void forward(ServiceMeshMetric.Builder metric) {
        TelemetryDataDispatcher.process(metric);
    }

    private long formatAsLong(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toEpochMilli();
    }

    private long formatAsLong(Duration duration) {
        return Instant.ofEpochSecond(duration.getSeconds(), duration.getNanos()).toEpochMilli();
    }
}
