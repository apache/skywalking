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

import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import java.util.List;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;

/**
 * Analysis source metrics from ALS
 */
public interface ALSHTTPAnalysis {
    String name();

    void init(ModuleManager manager, EnvoyMetricReceiverConfig config) throws ModuleStartException;

    /**
     * The method works as a chain of analyzers. Logs are processed sequentially by analyzers one by one, the results of the previous analyzer are passed into the current one.
     *
     * To do fast-success, the analyzer could simply check the results of the previous analyzer and return if not empty.
     *
     * @param result of the previous analyzer.
     * @param identifier of the Envoy node where the logs are emitted.
     * @param entry the log entry.
     * @param role the role of the Envoy node where the logs are emitted.
     * @return the analysis results.
     */
    List<ServiceMeshMetric.Builder> analysis(
        final List<ServiceMeshMetric.Builder> result,
        final StreamAccessLogsMessage.Identifier identifier,
        final HTTPAccessLogEntry entry,
        final Role role
    );

    Role identify(StreamAccessLogsMessage.Identifier alsIdentifier, Role prev);
}
