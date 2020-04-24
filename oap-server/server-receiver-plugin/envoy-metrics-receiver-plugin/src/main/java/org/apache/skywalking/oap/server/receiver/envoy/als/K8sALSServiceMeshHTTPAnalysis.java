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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.envoyproxy.envoy.api.v2.core.Address;
import io.envoyproxy.envoy.api.v2.core.Node;
import io.envoyproxy.envoy.api.v2.core.SocketAddress;
import io.envoyproxy.envoy.data.accesslog.v2.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v2.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v2.HTTPRequestProperties;
import io.envoyproxy.envoy.data.accesslog.v2.HTTPResponseProperties;
import io.envoyproxy.envoy.service.accesslog.v2.StreamAccessLogsMessage;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1OwnerReference;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.util.Config;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.skywalking.aop.server.receiver.mesh.TelemetryDataDispatcher;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.v3.Protocol;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analysis log based on ingress and mesh scenarios.
 */
public class K8sALSServiceMeshHTTPAnalysis implements ALSHTTPAnalysis {
    private static final Logger logger = LoggerFactory.getLogger(K8sALSServiceMeshHTTPAnalysis.class);

    private static final String ADDRESS_TYPE_INTERNAL_IP = "InternalIP";

    private static final String VALID_PHASE = "Running";

    @Getter(AccessLevel.PROTECTED)
    private final AtomicReference<Map<String, ServiceMetaInfo>> ipServiceMap = new AtomicReference<>();

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder()
        .setNameFormat("load-pod-%d")
        .setDaemon(true)
        .build());

    @Override
    public String name() {
        return "k8s-mesh";
    }

    @Override
    public void init(EnvoyMetricReceiverConfig config) {
        executorService.scheduleAtFixedRate(this::loadPodInfo, 0, 15, TimeUnit.SECONDS);
    }

    private boolean invalidPodList() {
        Map<String, ServiceMetaInfo> map = ipServiceMap.get();
        return map == null || map.isEmpty();
    }

    private void loadPodInfo() {
        try {
            ApiClient client = Config.defaultClient();
            client.getHttpClient().setReadTimeout(20, TimeUnit.SECONDS);
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();
            V1PodList list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
            Map<String, ServiceMetaInfo> ipMap = new HashMap<>(list.getItems().size());
            long startTime = System.nanoTime();
            for (V1Pod item : list.getItems()) {
                if (!item.getStatus().getPhase().equals(VALID_PHASE)) {
                    logger.debug("Invalid pod {} is not in a valid phase {}", item.getMetadata()
                                                                                  .getName(), item.getStatus()
                                                                                                  .getPhase());
                    continue;
                }
                if (item.getStatus().getPodIP().equals(item.getStatus().getHostIP())) {
                    logger.debug("Pod {}.{} is removed because hostIP and podIP are identical ", item.getMetadata()
                                                                                                     .getName(), item.getMetadata()
                                                                                                                     .getNamespace());
                    continue;
                }
                ipMap.put(item.getStatus().getPodIP(), createServiceMetaInfo(item.getMetadata()));
            }
            logger.info("Load {} pods in {}ms", ipMap.size(), (System.nanoTime() - startTime) / 1_000_000);
            ipServiceMap.set(ipMap);
        } catch (Throwable th) {
            logger.error("run load pod error", th);
        }
    }

    private ServiceMetaInfo createServiceMetaInfo(final V1ObjectMeta podMeta) {
        ExtensionsV1beta1Api extensionsApi = new ExtensionsV1beta1Api();
        DependencyResource dr = new DependencyResource(podMeta);
        DependencyResource meta = dr.getOwnerResource("ReplicaSet", ownerReference -> extensionsApi.readNamespacedReplicaSet(ownerReference
            .getName(), podMeta.getNamespace(), "", true, true).getMetadata());
        ServiceMetaInfo result = new ServiceMetaInfo();
        if (meta.getMetadata().getOwnerReferences() != null && meta.getMetadata().getOwnerReferences().size() > 0) {
            V1OwnerReference owner = meta.getMetadata().getOwnerReferences().get(0);
            result.setServiceName(String.format("%s.%s", owner.getName(), meta.getMetadata().getNamespace()));
        } else {
            result.setServiceName(String.format("%s.%s", meta.getMetadata().getName(), meta.getMetadata()
                                                                                           .getNamespace()));
        }
        result.setServiceInstanceName(String.format("%s.%s", podMeta.getName(), podMeta.getNamespace()));
        result.setTags(transformLabelsToTags(podMeta.getLabels()));
        return result;
    }

    private List<ServiceMetaInfo.KeyValue> transformLabelsToTags(final Map<String, String> labels) {
        if (labels == null || labels.size() < 1) {
            return Collections.emptyList();
        }
        List<ServiceMetaInfo.KeyValue> result = new ArrayList<>(labels.size());
        for (Map.Entry<String, String> each : labels.entrySet()) {
            result.add(new ServiceMetaInfo.KeyValue(each.getKey(), each.getValue()));
        }
        return result;
    }

    @Override
    public List<Source> analysis(StreamAccessLogsMessage.Identifier identifier, HTTPAccessLogEntry entry, Role role) {
        if (invalidPodList()) {
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
                ServiceMetaInfo downstreamService = find(downstreamRemoteAddress.getSocketAddress()
                                                                                .getAddress(), downstreamRemoteAddress.getSocketAddress()
                                                                                                                      .getPortValue());
                Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
                ServiceMetaInfo localService = find(downstreamLocalAddress.getSocketAddress()
                                                                          .getAddress(), downstreamLocalAddress.getSocketAddress()
                                                                                                               .getPortValue());
                if (cluster.startsWith("inbound|")) {
                    // Server side
                    if (downstreamService.equals(ServiceMetaInfo.UNKNOWN)) {
                        // Ingress -> sidecar(server side)
                        // Mesh telemetry without source, the relation would be generated.
                        ServiceMeshMetric.Builder metric = ServiceMeshMetric.newBuilder()
                                                                    .setStartTime(startTime)
                                                                    .setEndTime(startTime + duration)
                                                                    .setDestServiceName(localService.getServiceName())
                                                                    .setDestServiceInstance(localService.getServiceInstanceName())
                                                                    .setEndpoint(endpoint)
                                                                    .setLatency((int) duration)
                                                                    .setResponseCode(Math.toIntExact(responseCode))
                                                                    .setStatus(status)
                                                                    .setProtocol(protocol)
                                                                    .setDetectPoint(DetectPoint.server);

                        logger.debug("Transformed ingress->sidecar inbound mesh metric {}", metric);
                        forward(metric);
                    } else {
                        // sidecar -> sidecar(server side)
                        ServiceMeshMetric.Builder metric = ServiceMeshMetric.newBuilder()
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
                                                                    .setDetectPoint(DetectPoint.server);

                        logger.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
                        forward(metric);
                    }
                } else if (cluster.startsWith("outbound|")) {
                    // sidecar(client side) -> sidecar
                    Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
                    ServiceMetaInfo destService = find(upstreamRemoteAddress.getSocketAddress()
                                                                            .getAddress(), upstreamRemoteAddress.getSocketAddress()
                                                                                                                .getPortValue());

                    ServiceMeshMetric.Builder metric = ServiceMeshMetric.newBuilder()
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
                                                                .setDetectPoint(DetectPoint.client);

                    logger.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
                    forward(metric);

                }
            }
        }
        return sources;
    }

    protected void analysisProxy(StreamAccessLogsMessage.Identifier identifier, HTTPAccessLogEntry entry) {
        AccessLogCommon properties = entry.getCommonProperties();
        if (properties != null) {
            Address downstreamLocalAddress = properties.getDownstreamLocalAddress();
            Address downstreamRemoteAddress = properties.getDownstreamRemoteAddress();
            Address upstreamRemoteAddress = properties.getUpstreamRemoteAddress();
            if (downstreamLocalAddress != null && downstreamRemoteAddress != null && upstreamRemoteAddress != null) {
                SocketAddress downstreamRemoteAddressSocketAddress = downstreamRemoteAddress.getSocketAddress();
                ServiceMetaInfo outside = find(downstreamRemoteAddressSocketAddress.getAddress(), downstreamRemoteAddressSocketAddress
                    .getPortValue());

                SocketAddress downstreamLocalAddressSocketAddress = downstreamLocalAddress.getSocketAddress();
                ServiceMetaInfo ingress = find(downstreamLocalAddressSocketAddress.getAddress(), downstreamLocalAddressSocketAddress
                    .getPortValue());

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

                ServiceMeshMetric.Builder metric = ServiceMeshMetric.newBuilder()
                                                            .setStartTime(startTime)
                                                            .setEndTime(startTime + duration)
                                                            .setSourceServiceName(outside.getServiceName())
                                                            .setSourceServiceInstance(outside.getServiceInstanceName())
                                                            .setDestServiceName(ingress.getServiceName())
                                                            .setDestServiceInstance(ingress.getServiceInstanceName())
                                                            .setEndpoint(endpoint)
                                                            .setLatency((int) duration)
                                                            .setResponseCode(Math.toIntExact(responseCode))
                                                            .setStatus(status)
                                                            .setProtocol(protocol)
                                                            .setDetectPoint(DetectPoint.server);

                logger.debug("Transformed ingress inbound mesh metric {}", metric);
                forward(metric);

                SocketAddress upstreamRemoteAddressSocketAddress = upstreamRemoteAddress.getSocketAddress();
                ServiceMetaInfo targetService = find(upstreamRemoteAddressSocketAddress.getAddress(), upstreamRemoteAddressSocketAddress
                    .getPortValue());

                long outboundStartTime = startTime + formatAsLong(properties.getTimeToFirstUpstreamTxByte());
                long outboundEndTime = startTime + formatAsLong(properties.getTimeToLastUpstreamRxByte());

                ServiceMeshMetric.Builder outboundMetric = ServiceMeshMetric.newBuilder()
                                                                    .setStartTime(outboundStartTime)
                                                                    .setEndTime(outboundEndTime)
                                                                    .setSourceServiceName(ingress.getServiceName())
                                                                    .setSourceServiceInstance(ingress.getServiceInstanceName())
                                                                    .setDestServiceName(targetService.getServiceName())
                                                                    .setDestServiceInstance(targetService.getServiceInstanceName())
                                                                    .setEndpoint(endpoint)
                                                                    .setLatency((int) (outboundEndTime - outboundStartTime))
                                                                    .setResponseCode(Math.toIntExact(responseCode))
                                                                    .setStatus(status)
                                                                    .setProtocol(protocol)
                                                                    .setDetectPoint(DetectPoint.client);

                logger.debug("Transformed ingress outbound mesh metric {}", outboundMetric);
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
    protected ServiceMetaInfo find(String ip, int port) {
        Map<String, ServiceMetaInfo> map = ipServiceMap.get();
        if (map == null) {
            logger.debug("Unknown ip {}, ip -> service is null", ip);
            return ServiceMetaInfo.UNKNOWN;
        }
        if (map.containsKey(ip)) {
            return map.get(ip);
        }
        logger.debug("Unknown ip {}, ip -> service is {}", ip, map);
        return ServiceMetaInfo.UNKNOWN;
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
