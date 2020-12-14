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

package org.apache.skywalking.oap.server.receiver.envoy;

import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.aop.server.receiver.mesh.TelemetryDataDispatcher;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.als.ALSHTTPAnalysis;
import org.apache.skywalking.oap.server.receiver.envoy.als.ProtoMessages;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.receiver.envoy.als.wrapper.Identifier;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class AccessLogServiceGRPCHandler {
    protected final List<ALSHTTPAnalysis> envoyHTTPAnalysisList;

    protected final CounterMetrics counter;

    protected final HistogramMetrics histogram;

    protected final CounterMetrics sourceDispatcherCounter;

    public AccessLogServiceGRPCHandler(ModuleManager manager, EnvoyMetricReceiverConfig config) throws ModuleStartException {
        ServiceLoader<ALSHTTPAnalysis> alshttpAnalyses = ServiceLoader.load(ALSHTTPAnalysis.class);
        envoyHTTPAnalysisList = new ArrayList<>();
        for (String httpAnalysisName : config.getAlsHTTPAnalysis()) {
            for (ALSHTTPAnalysis httpAnalysis : alshttpAnalyses) {
                if (httpAnalysisName.equals(httpAnalysis.name())) {
                    httpAnalysis.init(manager, config);
                    envoyHTTPAnalysisList.add(httpAnalysis);
                }
            }
        }

        log.debug("envoy HTTP analysis: " + envoyHTTPAnalysisList);

        MetricsCreator metricCreator = manager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
        counter = metricCreator.createCounter("envoy_als_in_count", "The count of envoy ALS metric received", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        histogram = metricCreator.createHistogramMetric("envoy_als_in_latency", "The process latency of service ALS metric receiver", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        sourceDispatcherCounter = metricCreator.createCounter("envoy_als_source_dispatch_count", "The count of envoy ALS metric received", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
    }

    private Role role;

    private volatile boolean isFirst = true;

    private Identifier identifier;

    protected void handle(Message message) {
        counter.inc();

        try (final HistogramMetrics.Timer ignored = histogram.createTimer()) {
            if (isFirst) {
                identifier = new Identifier(ProtoMessages.findField(message, "identifier", null));
                isFirst = false;
                role = Role.NONE;
                for (ALSHTTPAnalysis analysis : envoyHTTPAnalysisList) {
                    role = analysis.identify(identifier, role);
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Messaged is identified from Envoy[{}], role[{}]. Received msg {}", identifier, role, message);
            }

            final List<Message> logs = ProtoMessages.findField(message, "http_logs.log_entry", Collections.emptyList());
            final List<ServiceMeshMetric.Builder> sourceResult = new ArrayList<>();
            for (ALSHTTPAnalysis analysis : envoyHTTPAnalysisList) {
                logs.forEach(log -> sourceResult.addAll(analysis.analysis(identifier, log, role)));
            }

            sourceDispatcherCounter.inc(sourceResult.size());
            sourceResult.forEach(TelemetryDataDispatcher::process);
        }
    }

    public void reset() {
        role = null;
        isFirst = true;
        identifier = null;
    }
}
