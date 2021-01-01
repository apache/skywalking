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

package org.apache.skywalking.oap.server.receiver.envoy.als.mx;

import com.google.protobuf.Any;
import com.google.protobuf.TextFormat;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.AbstractALSAnalyzer;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;

import static org.apache.skywalking.oap.server.receiver.envoy.als.LogEntry2MetricsAdapter.NON_TLS;
import static org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo.UNKNOWN;

@Slf4j
public class MetaExchangeALSHTTPAnalyzer extends AbstractALSAnalyzer {

    public static final String UPSTREAM_KEY = "wasm.upstream_peer";

    public static final String DOWNSTREAM_KEY = "wasm.downstream_peer";

    protected String fieldMappingFile = "metadata-service-mapping.yaml";

    @Override
    public String name() {
        return "mx-mesh";
    }

    @Override
    public void init(ModuleManager manager, EnvoyMetricReceiverConfig config) throws ModuleStartException {
        try {
            FieldsHelper.SINGLETON.init(fieldMappingFile);
        } catch (final Exception e) {
            throw new ModuleStartException("Failed to load metadata-service-mapping.yaml", e);
        }
    }

    @Override
    public List<ServiceMeshMetric.Builder> analysis(StreamAccessLogsMessage.Identifier identifier, HTTPAccessLogEntry entry, Role role) {
        final AccessLogCommon properties = entry.getCommonProperties();
        if (properties == null) {
            return Collections.emptyList();
        }
        final Map<String, Any> stateMap = properties.getFilterStateObjectsMap();
        if (stateMap == null) {
            return Collections.emptyList();
        }
        final ServiceMetaInfo currSvc;
        try {
            currSvc = adaptToServiceMetaInfo(identifier);
        } catch (Exception e) {
            log.error("Failed to inflate the ServiceMetaInfo from identifier.node.metadata. ", e);
            return Collections.emptyList();
        }

        final List<ServiceMeshMetric.Builder> result = new ArrayList<>();
        final AtomicBoolean downstreamExists = new AtomicBoolean();
        stateMap.forEach((key, value) -> {
            if (!key.equals(UPSTREAM_KEY) && !key.equals(DOWNSTREAM_KEY)) {
                return;
            }
            final ServiceMetaInfo svc;
            try {
                svc = adaptToServiceMetaInfo(value);
            } catch (Exception e) {
                log.error("Fail to parse metadata {} to FlatNode", Base64.getEncoder().encode(value.toByteArray()));
                return;
            }
            final ServiceMeshMetric.Builder metrics;
            switch (key) {
                case UPSTREAM_KEY:
                    metrics = newAdapter(entry, currSvc, svc).adaptToUpstreamMetrics().setTlsMode(NON_TLS);
                    if (log.isDebugEnabled()) {
                        log.debug("Transformed a {} outbound mesh metrics {}", role, TextFormat.shortDebugString(metrics));
                    }
                    result.add(metrics);
                    break;
                case DOWNSTREAM_KEY:
                    metrics = newAdapter(entry, svc, currSvc).adaptToDownstreamMetrics();
                    if (log.isDebugEnabled()) {
                        log.debug("Transformed a {} inbound mesh metrics {}", role, TextFormat.shortDebugString(metrics));
                    }
                    result.add(metrics);
                    downstreamExists.set(true);
                    break;
            }
        });
        if (role.equals(Role.PROXY) && !downstreamExists.get()) {
            final ServiceMeshMetric.Builder metric = newAdapter(entry, UNKNOWN, currSvc).adaptToDownstreamMetrics();
            if (log.isDebugEnabled()) {
                log.debug("Transformed a {} inbound mesh metric {}", role, TextFormat.shortDebugString(metric));
            }
            result.add(metric);
        }
        return result;
    }

    protected ServiceMetaInfo adaptToServiceMetaInfo(final Any value) throws Exception {
        return new ServiceMetaInfoAdapter(value);
    }

    protected ServiceMetaInfo adaptToServiceMetaInfo(final StreamAccessLogsMessage.Identifier identifier) throws Exception {
        return new ServiceMetaInfoAdapter(identifier.getNode().getMetadata());
    }

}
