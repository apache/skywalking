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

import io.envoyproxy.envoy.service.accesslog.v2.AccessLogServiceGrpc;
import io.envoyproxy.envoy.service.accesslog.v2.StreamAccessLogsMessage;
import io.envoyproxy.envoy.service.accesslog.v2.StreamAccessLogsResponse;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
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
    private static final Logger logger = LoggerFactory.getLogger(AccessLogServiceGRPCHandler.class);
    private final List<ALSHTTPAnalysis> envoyHTTPAnalysisList;
    private final SourceReceiver sourceReceiver;
    private final CounterMetrics counter;
    private final HistogramMetrics histogram;
    private final CounterMetrics sourceDispatcherCounter;

    public AccessLogServiceGRPCHandler(ModuleManager manager, EnvoyMetricReceiverConfig config) {
        ServiceLoader<ALSHTTPAnalysis> alshttpAnalyses = ServiceLoader.load(ALSHTTPAnalysis.class);
        envoyHTTPAnalysisList = new ArrayList<>();
        for (String httpAnalysisName : config.getAlsHTTPAnalysis()) {
            for (ALSHTTPAnalysis httpAnalysis : alshttpAnalyses) {
                if (httpAnalysisName.equals(httpAnalysis.name())) {
                    httpAnalysis.init(config);
                    envoyHTTPAnalysisList.add(httpAnalysis);
                }
            }
        }

        logger.debug("envoy HTTP analysis: " + envoyHTTPAnalysisList);

        sourceReceiver = manager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);

        MetricsCreator metricCreator = manager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
        counter = metricCreator.createCounter("envoy_als_in_count", "The count of envoy ALS metric received", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        histogram = metricCreator.createHistogramMetric("envoy_als_in_latency", "The process latency of service ALS metric receiver", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        sourceDispatcherCounter = metricCreator.createCounter("envoy_als_source_dispatch_count", "The count of envoy ALS metric received", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
    }

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

                    if (logger.isDebugEnabled()) {
                        logger.debug("Messaged is identified from Envoy[{}], role[{}] in [{}]. Received msg {}", identifier
                            .getNode()
                            .getId(), role, logCase, message);
                    }

                    switch (logCase) {
                        case HTTP_LOGS:
                            StreamAccessLogsMessage.HTTPAccessLogEntries logs = message.getHttpLogs();

                            List<Source> sourceResult = new ArrayList<>();
                            for (ALSHTTPAnalysis analysis : envoyHTTPAnalysisList) {
                                logs.getLogEntryList().forEach(log -> {
                                    sourceResult.addAll(analysis.analysis(identifier, log, role));
                                });
                            }

                            sourceDispatcherCounter.inc(sourceResult.size());
                            sourceResult.forEach(sourceReceiver::receive);
                    }
                } finally {
                    timer.finish();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                logger.error("Error in receiving access log from envoy", throwable);
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
