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

import com.google.protobuf.Message;
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
import org.apache.skywalking.oap.server.receiver.envoy.als.wrapper.Identifier;

import static org.apache.skywalking.apm.util.StringUtil.isEmpty;
import static org.apache.skywalking.apm.util.StringUtil.isNotEmpty;
import static org.apache.skywalking.oap.server.receiver.envoy.als.LogEntry2MetricsAdapter.NON_TLS;
import static org.apache.skywalking.oap.server.receiver.envoy.als.ProtoMessages.findField;

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
    public List<ServiceMeshMetric.Builder> analysis(Identifier identifier, Message entry, Role role) {
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

    protected List<ServiceMeshMetric.Builder> analyzeSideCar(final Message entry) {
        final String cluster = findField(entry, "common_properties.upstream_cluster", "");
        final String downstreamLocalAddress = findField(entry, "common_properties.downstream_local_address.socket_address.address", null);
        final String downstreamDirectRemoteAddress = findField(entry, "common_properties.downstream_direct_remote_address.socket_address.address", null);
        String downstreamRemoteAddress =
            isNotEmpty(downstreamDirectRemoteAddress)
                ? downstreamDirectRemoteAddress
                : findField(entry, "common_properties.downstream_remote_address.socket_address.address", "");

        if (isEmpty(cluster)) {
            return Collections.emptyList();
        }

        final List<ServiceMeshMetric.Builder> sources = new ArrayList<>();

        final ServiceMetaInfo downstreamService = find(downstreamRemoteAddress);
        final ServiceMetaInfo localService = find(downstreamLocalAddress);

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
            final String upstreamRemoteAddress = findField(entry, "common_properties.upstream_remote_address.socket_address.address", null);
            final ServiceMetaInfo destService = find(upstreamRemoteAddress);

            final ServiceMeshMetric.Builder metric = newAdapter(entry, downstreamService, destService).adaptToUpstreamMetrics();

            log.debug("Transformed sidecar->sidecar(server side) inbound mesh metric {}", metric);
            sources.add(metric);
        }

        return sources;
    }

    protected List<ServiceMeshMetric.Builder> analyzeProxy(final Message entry) {
        final String downstreamLocalAddress = findField(entry, "common_properties.downstream_local_address.socket_address.address", null);
        final String downstreamDirectRemoteAddress = findField(entry, "common_properties.downstream_direct_remote_address.socket_address.address", null);
        String downstreamRemoteAddress =
            isNotEmpty(downstreamDirectRemoteAddress)
                ? downstreamDirectRemoteAddress
                : findField(entry, "common_properties.downstream_remote_address.socket_address.address", null);

        final String upstreamRemoteAddress = findField(entry, "common_properties.upstream_remote_address.socket_address.address", null);
        if (isEmpty(downstreamLocalAddress) || isEmpty(downstreamRemoteAddress) || isEmpty(upstreamRemoteAddress)) {
            return Collections.emptyList();
        }

        final List<ServiceMeshMetric.Builder> result = new ArrayList<>(2);
        final ServiceMetaInfo outside = find(downstreamRemoteAddress);

        final ServiceMetaInfo ingress = find(downstreamLocalAddress);

        final ServiceMeshMetric.Builder metric = newAdapter(entry, outside, ingress).adaptToDownstreamMetrics();

        log.debug("Transformed ingress inbound mesh metric {}", metric);
        result.add(metric);

        final ServiceMetaInfo targetService = find(upstreamRemoteAddress);

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
