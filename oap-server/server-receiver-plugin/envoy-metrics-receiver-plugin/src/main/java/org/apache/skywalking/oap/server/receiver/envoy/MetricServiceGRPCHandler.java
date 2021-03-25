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

import io.envoyproxy.envoy.service.metrics.v2.MetricsServiceGrpc;
import io.envoyproxy.envoy.service.metrics.v3.StreamMetricsMessage;
import io.envoyproxy.envoy.service.metrics.v3.StreamMetricsResponse;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Metrics;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.meter.analyzer.prometheus.PrometheusMetricConverter;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Metric;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.metrics.adapters.ProtoMetricFamily2MetricsAdapter;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class MetricServiceGRPCHandler extends MetricsServiceGrpc.MetricsServiceImplBase {
    private final CounterMetrics counter;
    private final HistogramMetrics histogram;
    private final List<PrometheusMetricConverter> converters;

    private final EnvoyMetricReceiverConfig config;

    public MetricServiceGRPCHandler(final ModuleManager moduleManager, final EnvoyMetricReceiverConfig config) throws ModuleStartException {
        this.config = config;

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

        final MeterSystem meterSystem = moduleManager.find(CoreModule.NAME).provider().getService(MeterSystem.class);

        converters = config.rules()
                           .stream()
                           .map(rule -> new PrometheusMetricConverter(rule, meterSystem))
                           .collect(Collectors.toList());
    }

    @Override
    public StreamObserver<StreamMetricsMessage> streamMetrics(StreamObserver<StreamMetricsResponse> responseObserver) {
        return new StreamObserver<StreamMetricsMessage>() {
            private volatile boolean isFirst = true;
            private ServiceMetaInfo service;

            @Override
            @SneakyThrows
            public void onNext(StreamMetricsMessage message) {
                if (log.isDebugEnabled()) {
                    log.debug("Received msg {}", message);
                }

                if (isFirst) {
                    isFirst = false;
                    service = config.serviceMetaInfoFactory().fromStruct(message.getIdentifier().getNode().getMetadata());
                }

                if (log.isDebugEnabled()) {
                    log.debug("Envoy metrics reported from service[{}]", service);
                }

                if (service != null && StringUtil.isNotEmpty(service.getServiceName()) && StringUtil.isNotEmpty(service.getServiceInstanceName())) {
                    List<Metrics.MetricFamily> list = message.getEnvoyMetricsList();

                    for (final Metrics.MetricFamily metricFamily : list) {
                        counter.inc();

                        try (final HistogramMetrics.Timer ignored = histogram.createTimer()) {
                            final ProtoMetricFamily2MetricsAdapter adapter = new ProtoMetricFamily2MetricsAdapter(metricFamily);
                            final Stream<Metric> metrics = adapter.adapt().peek(it -> {
                                it.getLabels().putIfAbsent("cluster", service.getServiceName());
                                it.getLabels().putIfAbsent("instance", service.getServiceInstanceName());
                            });
                            converters.forEach(converter -> converter.toMeter(metrics));
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
