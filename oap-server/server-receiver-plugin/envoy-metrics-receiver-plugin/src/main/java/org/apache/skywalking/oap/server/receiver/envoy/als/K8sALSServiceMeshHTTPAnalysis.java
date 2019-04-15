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

import com.google.protobuf.*;
import io.envoyproxy.envoy.api.v2.core.*;
import io.envoyproxy.envoy.data.accesslog.v2.*;
import io.envoyproxy.envoy.service.accesslog.v2.StreamAccessLogsMessage;
import java.time.Instant;
import java.util.*;
import org.apache.skywalking.aop.server.receiver.mesh.TelemetryDataDispatcher;
import org.apache.skywalking.apm.network.common.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.*;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.slf4j.*;

/**
 * Analysis log based on ingress and mesh scenarios.
 *
 * @author wusheng
 */
public class K8sALSServiceMeshHTTPAnalysis implements ALSHTTPAnalysis {
    private static final Logger logger = LoggerFactory.getLogger(K8sALSServiceMeshHTTPAnalysis.class);

    @Override public String name() {
        return "k8s-mesh";
    }

    @Override public void init(EnvoyMetricReceiverConfig config) {
        //TODO: Start k8s metadata query timer.
    }

    @Override public List<Source> analysis(StreamAccessLogsMessage.Identifier identifier,
        HTTPAccessLogEntry entry, Role role) {
        switch (role) {
            case PROXY:
                analysisProxy(identifier, entry);
                break;
            case SIDECAR:
                return analysisSideCar(identifier, entry);
        }

        return Collections.emptyList();
    }

    protected List<Source> analysisSideCar(StreamAccessLogsMessage.Identifier identifier,
        HTTPAccessLogEntry entry) {
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
                    if (schema.equals("http") || schema.equals("https")) {
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
                ServiceMetaInfo downstreamService = find(downstreamRemoteAddress.getSocketAddress().getAddress(),
                    downstreamRemoteAddress.getSocketAddress().getPortValue());
                Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
                ServiceMetaInfo localService = find(downstreamLocalAddress.getSocketAddress().getAddress(),
                    downstreamLocalAddress.getSocketAddress().getPortValue());
                if (cluster.startsWith("inbound|")) {
                    // Server side
                    if (downstreamService.equals(ServiceMetaInfo.UNKNOWN)) {
                        // Ingress -> sidecar(server side)
                        // Mesh telemetry without source, the relation would be generated.
                        ServiceMeshMetric metric = ServiceMeshMetric.newBuilder().setStartTime(startTime)
                            .setEndTime(startTime + duration)
                            .setDestServiceName(localService.getServiceName())
                            .setDestServiceInstance(localService.getServiceInstanceName())
                            .setEndpoint(endpoint).setLatency((int)duration)
                            .setResponseCode(Math.toIntExact(responseCode))
                            .setStatus(status).setProtocol(protocol)
                            .setDetectPoint(DetectPoint.server)
                            .build();

                        logger.debug("Transformed ingress->sidecar inbound mesh metric {}", metric);
                        forward(metric);
                    } else {
                        // sidecar -> sidecar(server side)
                        ServiceMeshMetric metric = ServiceMeshMetric.newBuilder().setStartTime(startTime)
                            .setEndTime(startTime + duration)
                            .setSourceServiceName(downstreamService.getServiceName())
                            .setSourceServiceInstance(downstreamService.getServiceInstanceName())
                            .setDestServiceName(localService.getServiceName())
                            .setDestServiceInstance(localService.getServiceInstanceName())
                            .setEndpoint(endpoint).setLatency((int)duration)
                            .setResponseCode(Math.toIntExact(responseCode))
                            .setStatus(status).setProtocol(protocol)
                            .setDetectPoint(DetectPoint.server)
                            .build();

                        logger.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
                        forward(metric);
                    }
                } else if (cluster.startsWith("outbound|")) {
                    // sidecar(client side) -> sidecar
                    Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
                    ServiceMetaInfo destService = find(upstreamRemoteAddress.getSocketAddress().getAddress(),
                        upstreamRemoteAddress.getSocketAddress().getPortValue());

                    ServiceMeshMetric metric = ServiceMeshMetric.newBuilder().setStartTime(startTime)
                        .setEndTime(startTime + duration)
                        .setSourceServiceName(downstreamService.getServiceName())
                        .setSourceServiceInstance(downstreamService.getServiceInstanceName())
                        .setDestServiceName(destService.getServiceName())
                        .setDestServiceInstance(destService.getServiceInstanceName())
                        .setEndpoint(endpoint).setLatency((int)duration)
                        .setResponseCode(Math.toIntExact(responseCode))
                        .setStatus(status).setProtocol(protocol)
                        .setDetectPoint(DetectPoint.client)
                        .build();

                    logger.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
                    forward(metric);

                }
            }
        }
        return sources;
    }

    protected void analysisProxy(StreamAccessLogsMessage.Identifier identifier,
        HTTPAccessLogEntry entry) {
        AccessLogCommon properties = entry.getCommonProperties();
        if (properties != null) {
            Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
            Address downstreamRemoteAddress = properties.getDownstreamRemoteAddress();
            Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
            if (downstreamLocalAddress != null && downstreamRemoteAddress != null && upstreamRemoteAddress != null) {
                SocketAddress downstreamRemoteAddressSocketAddress = downstreamRemoteAddress.getSocketAddress();
                ServiceMetaInfo outside = find(downstreamRemoteAddressSocketAddress.getAddress(), downstreamRemoteAddressSocketAddress.getPortValue());

                SocketAddress downstreamLocalAddressSocketAddress = downstreamLocalAddress.getSocketAddress();
                ServiceMetaInfo ingress = find(downstreamLocalAddressSocketAddress.getAddress(), downstreamLocalAddressSocketAddress.getPortValue());

                long startTime = formatAsLong(properties.getStartTime());
                long duration = formatAsLong(properties.getTimeToLastDownstreamTxByte());

                HTTPRequestProperties request = entry.getRequest();
                String endpoint = "/";
                Protocol protocol = Protocol.HTTP;
                if (request != null) {
                    endpoint = request.getPath();
                    String schema = request.getScheme();
                    if (schema.equals("http") || schema.equals("https")) {
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

                ServiceMeshMetric metric = ServiceMeshMetric.newBuilder().setStartTime(startTime)
                    .setEndTime(startTime + duration)
                    .setSourceServiceName(outside.getServiceName())
                    .setSourceServiceInstance(outside.getServiceInstanceName())
                    .setDestServiceName(ingress.getServiceName())
                    .setDestServiceInstance(ingress.getServiceInstanceName())
                    .setEndpoint(endpoint).setLatency((int)duration)
                    .setResponseCode(Math.toIntExact(responseCode))
                    .setStatus(status).setProtocol(protocol)
                    .setDetectPoint(DetectPoint.server)
                    .build();

                logger.debug("Transformed ingress inbound mesh metric {}", metric);
                forward(metric);

                SocketAddress upstreamRemoteAddressSocketAddress = upstreamRemoteAddress.getSocketAddress();
                ServiceMetaInfo targetService = find(upstreamRemoteAddressSocketAddress.getAddress(), upstreamRemoteAddressSocketAddress.getPortValue());

                long outboundStartTime = startTime + formatAsLong(properties.getTimeToFirstUpstreamTxByte());
                long outboundEndTime = startTime + formatAsLong(properties.getTimeToLastUpstreamRxByte());

                ServiceMeshMetric outboundMetric = ServiceMeshMetric.newBuilder().setStartTime(outboundStartTime)
                    .setEndTime(outboundEndTime)
                    .setSourceServiceName(ingress.getServiceName())
                    .setSourceServiceInstance(ingress.getServiceInstanceName())
                    .setDestServiceName(targetService.getServiceName())
                    .setDestServiceInstance(targetService.getServiceInstanceName())
                    .setEndpoint(endpoint).setLatency((int)(outboundEndTime - outboundStartTime))
                    .setResponseCode(Math.toIntExact(responseCode))
                    .setStatus(status).setProtocol(protocol)
                    .setDetectPoint(DetectPoint.client)
                    .build();

                logger.debug("Transformed ingress outbound mesh metric {}", outboundMetric);
                forward(outboundMetric);
            }
        }
    }

    @Override public Role identify(StreamAccessLogsMessage.Identifier alsIdentifier,
        Role prev) {
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
     * @param ip
     * @param port
     * @return found service info, or {@link ServiceMetaInfo#UNKNOWN} to represent not found.
     */
    protected ServiceMetaInfo find(String ip, int port) {
        //TODO: go through API server to get target service info
        // If can't get service or service instance name, set `UNKNOWN` string.
        // Service instance name is pod name
        // Service name should be deployment name
        throw new UnsupportedOperationException("TODO");
    }

    protected void forward(ServiceMeshMetric metric) {
        TelemetryDataDispatcher.preProcess(metric);
    }

    private long formatAsLong(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toEpochMilli();
    }

    private long formatAsLong(Duration duration) {
        return Instant.ofEpochSecond(duration.getSeconds(), duration.getNanos()).toEpochMilli();
    }
}
