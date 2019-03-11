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
import io.envoyproxy.envoy.service.metrics.v2.*;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Metrics;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.register.service.*;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.TimeBucketUtils;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class MetricServiceGRPCHandler extends MetricsServiceGrpc.MetricsServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MetricServiceGRPCHandler.class);

    private final IServiceInventoryRegister serviceInventoryRegister;
    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final SourceReceiver sourceReceiver;
    private CounterMetric counter;
    private HistogramMetric histogram;

    public MetricServiceGRPCHandler(ModuleManager moduleManager) {
        serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
        serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
        sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        MetricCreator metricCreator = moduleManager.find(TelemetryModule.NAME).provider().getService(MetricCreator.class);
        counter = metricCreator.createCounter("envoy_metric_in_count", "The count of envoy service metric received",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
        histogram = metricCreator.createHistogramMetric("envoy_metric_in_latency", "The process latency of service metric receiver",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
    }

    @Override
    public StreamObserver<StreamMetricsMessage> streamMetrics(StreamObserver<StreamMetricsResponse> responseObserver) {
        return new StreamObserver<StreamMetricsMessage>() {
            private volatile boolean isFirst = true;
            private String serviceName = null;
            private int serviceId = Const.NONE;
            private String serviceInstanceName = null;
            private int serviceInstanceId = Const.NONE;

            @Override public void onNext(StreamMetricsMessage message) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Received msg {}", message);
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

                if (logger.isDebugEnabled()) {
                    logger.debug("Envoy metric reported from service[{}], service instance[{}]", serviceName, serviceInstanceName);
                }

                if (serviceInstanceId != Const.NONE) {
                    List<Metrics.MetricFamily> list = message.getEnvoyMetricsList();
                    boolean needHeartbeatUpdate = true;
                    for (int i = 0; i < list.size(); i++) {
                        counter.inc();
                        HistogramMetric.Timer timer = histogram.createTimer();
                        try {
                            Metrics.MetricFamily metricFamily = list.get(i);
                            double value = 0;
                            long timestamp = 0;
                            switch (metricFamily.getType()) {
                                case GAUGE:
                                    for (Metrics.Metric metric : metricFamily.getMetricList()) {
                                        timestamp = metric.getTimestampMs();
                                        value = metric.getGauge().getValue();

                                        EnvoyInstanceMetric metricSource = new EnvoyInstanceMetric();
                                        metricSource.setServiceId(serviceId);
                                        metricSource.setServiceName(serviceName);
                                        metricSource.setId(serviceInstanceId);
                                        metricSource.setName(serviceInstanceName);
                                        metricSource.setMetricName(metricFamily.getName());
                                        metricSource.setValue(value);
                                        metricSource.setTimeBucket(TimeBucketUtils.INSTANCE.getMinuteTimeBucket(timestamp));
                                        sourceReceiver.receive(metricSource);
                                    }
                                    break;
                                default:
                                    continue;
                            }
                            if (needHeartbeatUpdate) {
                                // Send heartbeat
                                serviceInventoryRegister.heartbeat(serviceId, timestamp);
                                serviceInstanceInventoryRegister.heartbeat(serviceInstanceId, timestamp);
                                needHeartbeatUpdate = false;
                            }
                        } finally {
                            timer.finish();
                        }
                    }
                } else if (serviceName != null && serviceInstanceName != null) {
                    if (serviceId == Const.NONE) {
                        logger.debug("Register envoy service [{}].", serviceName);
                        serviceId = serviceInventoryRegister.getOrCreate(serviceName, null);
                    }
                    if (serviceId != Const.NONE) {
                        logger.debug("Register envoy service instance [{}].", serviceInstanceName);
                        serviceInstanceId = serviceInstanceInventoryRegister.getOrCreate(serviceId, serviceInstanceName, serviceInstanceName, System.currentTimeMillis(), null);
                    }
                }
            }

            @Override public void onError(Throwable throwable) {
                logger.error("Error in receiving metric from envoy", throwable);
                responseObserver.onCompleted();
            }

            @Override public void onCompleted() {
                responseObserver.onNext(StreamMetricsResponse.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }
}
