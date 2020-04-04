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

import io.envoyproxy.envoy.api.v2.core.Node;
import io.envoyproxy.envoy.service.metrics.v2.MetricsServiceGrpc;
import io.envoyproxy.envoy.service.metrics.v2.StreamMetricsMessage;
import io.envoyproxy.envoy.service.metrics.v2.StreamMetricsResponse;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Metrics;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.source.EnvoyInstanceMetric;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceUpdate;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class MetricServiceGRPCHandler extends MetricsServiceGrpc.MetricsServiceImplBase {
    private final SourceReceiver sourceReceiver;
    private CounterMetrics counter;
    private HistogramMetrics histogram;

    public MetricServiceGRPCHandler(ModuleManager moduleManager) {
        sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        counter = metricsCreator.createCounter(
            "envoy_metric_in_count", "The count of envoy service metrics received", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE
        );
        histogram = metricsCreator.createHistogramMetric(
            "envoy_metric_in_latency", "The process latency of service metrics receiver", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE
        );
    }

    @Override
    public StreamObserver<StreamMetricsMessage> streamMetrics(StreamObserver<StreamMetricsResponse> responseObserver) {
        return new StreamObserver<StreamMetricsMessage>() {
            private volatile boolean isFirst = true;
            private String serviceName = null;
            private String serviceInstanceName = null;

            @Override
            public void onNext(StreamMetricsMessage message) {
                if (log.isDebugEnabled()) {
                    log.debug("Received msg {}", message);
                }

                if (isFirst) {
                    isFirst = false;
                    StreamMetricsMessage.Identifier identifier = message.getIdentifier();
                    Node node = identifier.getNode();
                    if (node != null) {
                        String nodeId = node.getId();
                        if (!StringUtil.isEmpty(nodeId)) {
                            serviceInstanceName = nodeId;
                        }
                        String cluster = node.getCluster();
                        if (!StringUtil.isEmpty(cluster)) {
                            serviceName = cluster;
                            if (serviceInstanceName == null) {
                                serviceInstanceName = serviceName;
                            }
                        }
                    }

                    if (serviceName == null) {
                        serviceName = serviceInstanceName;
                    }
                }

                if (log.isDebugEnabled()) {
                    log.debug(
                        "Envoy metrics reported from service[{}], service instance[{}]", serviceName,
                        serviceInstanceName
                    );
                }

                if (StringUtil.isNotEmpty(serviceName) && StringUtil.isNotEmpty(serviceInstanceName)) {
                    List<Metrics.MetricFamily> list = message.getEnvoyMetricsList();
                    boolean needHeartbeatUpdate = true;
                    for (int i = 0; i < list.size(); i++) {
                        counter.inc();

                        final String serviceId = IDManager.ServiceID.buildId(serviceName, NodeType.Normal);
                        final String serviceInstanceId = IDManager.ServiceInstanceID.buildId(
                            serviceId, serviceInstanceName);

                        HistogramMetrics.Timer timer = histogram.createTimer();
                        try {
                            Metrics.MetricFamily metricFamily = list.get(i);
                            double value = 0;
                            long timestamp = 0;
                            switch (metricFamily.getType()) {
                                case GAUGE:
                                    for (Metrics.Metric metrics : metricFamily.getMetricList()) {
                                        timestamp = metrics.getTimestampMs();
                                        value = metrics.getGauge().getValue();

                                        if (timestamp > 1000000000000000000L) {
                                            /**
                                             * Several versions of envoy in istio.deps send timestamp in nanoseconds,
                                             * instead of milliseconds(protocol says).
                                             *
                                             * Sadly, but have to fix it forcedly.
                                             *
                                             * An example of timestamp is '1552303033488741055', clearly it is not in milliseconds.
                                             *
                                             * This should be removed in the future.
                                             */
                                            timestamp /= 1_000_000;
                                        }

                                        EnvoyInstanceMetric metricSource = new EnvoyInstanceMetric();
                                        metricSource.setServiceId(serviceId);
                                        metricSource.setServiceName(serviceName);
                                        metricSource.setId(serviceInstanceId);
                                        metricSource.setName(serviceInstanceName);
                                        metricSource.setMetricName(metricFamily.getName());
                                        metricSource.setValue(value);
                                        metricSource.setTimeBucket(TimeBucket.getMinuteTimeBucket(timestamp));
                                        sourceReceiver.receive(metricSource);
                                    }
                                    break;
                                default:
                                    continue;
                            }
                            if (needHeartbeatUpdate) {
                                // Send heartbeat
                                ServiceInstanceUpdate serviceInstanceUpdate = new ServiceInstanceUpdate();
                                serviceInstanceUpdate.setName(serviceInstanceName);
                                serviceInstanceUpdate.setServiceId(serviceId);
                                serviceInstanceUpdate.setTimeBucket(TimeBucket.getMinuteTimeBucket(timestamp));
                                sourceReceiver.receive(serviceInstanceUpdate);
                                needHeartbeatUpdate = false;
                            }
                        } finally {
                            timer.finish();
                        }
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Error in receiving metrics from envoy", throwable);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(StreamMetricsResponse.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
