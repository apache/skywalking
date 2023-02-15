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

import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetrics;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import io.envoyproxy.envoy.config.core.v3.Node;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import lombok.Builder;
import lombok.Data;

public interface AccessLogAnalyzer<E> {
    String name();

    void init(ModuleManager manager, EnvoyMetricReceiverConfig config) throws ModuleStartException;

    /**
     * The method works as a chain of analyzers. Logs are processed sequentially by analyzers one by one, the results of the previous analyzer are passed into the current one.
     *
     * To do fast-success, the analyzer could simply check the results of the previous analyzer and return if not empty.
     *
     * @param result     of the previous analyzer.
     * @param identifier of the Envoy node where the logs are emitted.
     * @param entry      the log entry.
     * @param role       the role of the Envoy node where the logs are emitted.
     * @return the analysis results.
     */
    Result analysis(
        final Result result,
        final StreamAccessLogsMessage.Identifier identifier,
        final E entry,
        final Role role
    );

    default Role identify(StreamAccessLogsMessage.Identifier alsIdentifier, Role defaultRole) {
        if (alsIdentifier == null) {
            return defaultRole;
        }
        if (!alsIdentifier.hasNode()) {
            return defaultRole;
        }
        final Node node = alsIdentifier.getNode();
        final String id = node.getId();
        if (id.startsWith("router~")) {
            return Role.PROXY;
        } else if (id.startsWith("sidecar~")) {
            return Role.SIDECAR;
        }
        return defaultRole;
    }

    @Data
    @Builder
    static class Result {
        /**
         * The service representing the Envoy node.
         */
        private ServiceMetaInfo service;

        /**
         * The analyzed metrics result.
         */
        private ServiceMeshMetrics.Builder metrics;

        public boolean hasResult() {
            return metrics != null && (metrics.getHttpMetrics().getMetricsCount() > 0 || metrics.getTcpMetrics().getMetricsCount() > 0);
        }
    }
}
