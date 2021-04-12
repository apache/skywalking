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

import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.service.accesslog.v2.AccessLogServiceGrpc;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsResponse;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.apache.skywalking.aop.server.receiver.mesh.TelemetryDataDispatcher;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.als.ALSHTTPAnalysis;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccessLogServiceGRPCHandler extends AccessLogServiceGrpc.AccessLogServiceImplBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessLogServiceGRPCHandler.class);
    private final List<ALSHTTPAnalysis> envoyHTTPAnalysisList;

    private final CounterMetrics counter;
    private final HistogramMetrics histogram;
    private final CounterMetrics sourceDispatcherCounter;

    public AccessLogServiceGRPCHandler(ModuleManager manager,
                                       EnvoyMetricReceiverConfig config) throws ModuleStartException {
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

        LOGGER.debug("envoy HTTP analysis: " + envoyHTTPAnalysisList);

        MetricsCreator metricCreator = manager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
        counter = metricCreator.createCounter(
            "envoy_als_in_count", "The count of envoy ALS metric received", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE
        );
        histogram = metricCreator.createHistogramMetric(
            "envoy_als_in_latency", "The process latency of service ALS metric receiver", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE
        );
        sourceDispatcherCounter = metricCreator.createCounter(
            "envoy_als_source_dispatch_count", "The count of envoy ALS metric received", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE
        );
    }

    @Override
    public StreamObserver<StreamAccessLogsMessage> streamAccessLogs(
        StreamObserver<StreamAccessLogsResponse> responseObserver) {
        return new StreamObserver<StreamAccessLogsMessage>() {
            private volatile boolean isFirst = true;
            private Role role;
            private StreamAccessLogsMessage.Identifier identifier;

            @Override
            public void onNext(StreamAccessLogsMessage message) {
                counter.inc();

                HistogramMetrics.Timer timer = histogram.createTimer();
                try {
                    if (isFirst) {
                        identifier = message.getIdentifier();
                        isFirst = false;
                        role = Role.NONE;
                        for (ALSHTTPAnalysis analysis : envoyHTTPAnalysisList) {
                            role = analysis.identify(identifier, role);
                        }
                    }

                    StreamAccessLogsMessage.LogEntriesCase logCase = message.getLogEntriesCase();

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                            "Messaged is identified from Envoy[{}], role[{}] in [{}]. Received msg {}", identifier
                                .getNode()
                                .getId(), role, logCase, message);
                    }

                    switch (logCase) {
                        case HTTP_LOGS:
                            StreamAccessLogsMessage.HTTPAccessLogEntries logs = message.getHttpLogs();

                            List<ServiceMeshMetric.Builder> sourceResult = new ArrayList<>();
                            for (final HTTPAccessLogEntry log : logs.getLogEntryList()) {
                                List<ServiceMeshMetric.Builder> result = new ArrayList<>();
                                for (ALSHTTPAnalysis analysis : envoyHTTPAnalysisList) {
                                    result = analysis.analysis(result, identifier, log, role);
                                }
                                sourceResult.addAll(result);
                            }

                            sourceDispatcherCounter.inc(sourceResult.size());
                            sourceResult.forEach(TelemetryDataDispatcher::process);
                            break;
                    }
                } finally {
                    timer.finish();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                LOGGER.error("Error in receiving access log from envoy", throwable);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(StreamAccessLogsResponse.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
