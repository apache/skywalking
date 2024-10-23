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

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.apache.skywalking.aop.server.receiver.mesh.TelemetryDataDispatcher;
import org.apache.skywalking.apm.network.servicemesh.v3.HTTPServiceMeshMetrics;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetrics;
import org.apache.skywalking.apm.network.servicemesh.v3.TCPServiceMeshMetrics;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.als.ALSHTTPAnalysis;
import org.apache.skywalking.oap.server.receiver.envoy.als.AccessLogAnalyzer;
import org.apache.skywalking.oap.server.receiver.envoy.als.Role;
import org.apache.skywalking.oap.server.receiver.envoy.als.tcp.TCPAccessLogAnalyzer;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import io.envoyproxy.envoy.service.accesslog.v2.AccessLogServiceGrpc;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsMessage;
import io.envoyproxy.envoy.service.accesslog.v3.StreamAccessLogsResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class AccessLogServiceGRPCHandler extends AccessLogServiceGrpc.AccessLogServiceImplBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessLogServiceGRPCHandler.class);
    private final List<ALSHTTPAnalysis> envoyHTTPAnalysisList;
    private final List<TCPAccessLogAnalyzer> envoyTCPAnalysisList;

    private final CounterMetrics counter;
    private final HistogramMetrics histogram;
    private final CounterMetrics sourceDispatcherCounter;

    public AccessLogServiceGRPCHandler(ModuleManager manager,
                                       EnvoyMetricReceiverConfig config) throws ModuleStartException {
        ServiceLoader<ALSHTTPAnalysis> alshttpAnalyses = ServiceLoader.load(ALSHTTPAnalysis.class);
        ServiceLoader<TCPAccessLogAnalyzer> alsTcpAnalyzers = ServiceLoader.load(TCPAccessLogAnalyzer.class);
        envoyHTTPAnalysisList = new ArrayList<>();
        for (String httpAnalysisName : config.getAlsHTTPAnalysis()) {
            for (ALSHTTPAnalysis httpAnalysis : alshttpAnalyses) {
                if (httpAnalysisName.equals(httpAnalysis.name())) {
                    httpAnalysis.init(manager, config);
                    envoyHTTPAnalysisList.add(httpAnalysis);
                }
            }
        }
        envoyTCPAnalysisList = new ArrayList<>();
        for (String analyzerName : config.getAlsTCPAnalysis()) {
            for (TCPAccessLogAnalyzer tcpAnalyzer : alsTcpAnalyzers) {
                if (analyzerName.equals(tcpAnalyzer.name())) {
                    tcpAnalyzer.init(manager, config);
                    envoyTCPAnalysisList.add(tcpAnalyzer);
                }
            }
        }

        LOGGER.debug("envoy HTTP analysis: {}, envoy TCP analysis: {}", envoyHTTPAnalysisList, envoyTCPAnalysisList);

        MetricsCreator metricCreator = manager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
        counter = metricCreator.createCounter(
            "envoy_als_in_count", "The count of envoy ALS message received", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE
        );
        histogram = metricCreator.createHistogramMetric(
            "envoy_als_in_latency_seconds", "The process latency of service ALS metric receiver", MetricsTag.EMPTY_KEY,
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
        return streamAccessLogs(responseObserver, false);
    }

    public StreamObserver<StreamAccessLogsMessage> streamAccessLogs(
        StreamObserver<StreamAccessLogsResponse> responseObserver, boolean alwaysAnalyzeIdentity) {
        return new StreamObserver<StreamAccessLogsMessage>() {
            private volatile boolean isFirst = true;
            private Role role;
            private StreamAccessLogsMessage.Identifier identifier;

            @Override
            public void onNext(StreamAccessLogsMessage message) {
                HistogramMetrics.Timer timer = histogram.createTimer();
                try {
                    if (isFirst || alwaysAnalyzeIdentity && message.hasIdentifier()) {
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

                    final ServiceMeshMetrics.Builder sourceResult = ServiceMeshMetrics.newBuilder();
                    switch (logCase) {
                        case HTTP_LOGS:
                            final HTTPServiceMeshMetrics.Builder httpMetrics = HTTPServiceMeshMetrics.newBuilder();
                            final StreamAccessLogsMessage.HTTPAccessLogEntries httpLogs = message.getHttpLogs();

                            counter.inc(httpLogs.getLogEntryCount());

                            for (final HTTPAccessLogEntry httpLog : httpLogs.getLogEntryList()) {
                                AccessLogAnalyzer.Result result = AccessLogAnalyzer.Result.builder().build();
                                for (ALSHTTPAnalysis analysis : envoyHTTPAnalysisList) {
                                    result = analysis.analysis(result, identifier, httpLog, role);
                                }
                                if (result.hasResult()) {
                                    httpMetrics.addAllMetrics(result.getMetrics().getHttpMetrics().getMetricsList());
                                }
                            }
                            sourceResult.setHttpMetrics(httpMetrics);
                            break;
                        case TCP_LOGS:
                            final TCPServiceMeshMetrics.Builder tcpMetrics = TCPServiceMeshMetrics.newBuilder();
                            final StreamAccessLogsMessage.TCPAccessLogEntries tcpLogs = message.getTcpLogs();

                            counter.inc(tcpLogs.getLogEntryCount());

                            for (final TCPAccessLogEntry tcpLog : tcpLogs.getLogEntryList()) {
                                AccessLogAnalyzer.Result result = AccessLogAnalyzer.Result.builder().build();
                                for (TCPAccessLogAnalyzer analyzer : envoyTCPAnalysisList) {
                                    result = analyzer.analysis(result, identifier, tcpLog, role);
                                }
                                if (result.hasResult()) {
                                    tcpMetrics.addAllMetrics(result.getMetrics().getTcpMetrics().getMetricsList());
                                }
                            }
                            sourceResult.setTcpMetrics(tcpMetrics);
                            break;
                        default: // Ignored
                    }
                    sourceDispatcherCounter.inc(
                        sourceResult.getHttpMetrics().getMetricsCount() +
                            sourceResult.getTcpMetrics().getMetricsCount());
                    TelemetryDataDispatcher.process(sourceResult.build());
                } finally {
                    timer.finish();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                Status status = Status.fromThrowable(throwable);
                if (Status.CANCELLED.getCode() == status.getCode()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Envoy client cancelled sending access logs", throwable);
                    }
                    return;
                }
                LOGGER.error("Error in receiving access log from envoy", throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(StreamAccessLogsResponse.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
