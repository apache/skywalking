/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.metrics.v1.SummaryDataPoint;
import io.vavr.Function1;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.prometheus.PrometheusMetricConverter;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Counter;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Gauge;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Histogram;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Metric;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Summary;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;

import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@RequiredArgsConstructor
@Slf4j
public class OpenTelemetryMetricRequestProcessor implements Service {

    private final ModuleManager manager;

    private final OtelMetricReceiverConfig config;

    private static final Map<String, String> LABEL_MAPPINGS =
        ImmutableMap
            .<String, String>builder()
            .put("net.host.name", "node_identifier_host_name")
            .put("host.name", "node_identifier_host_name")
            .put("job", "job_name")
            .put("service.name", "job_name")
            .build();
    private List<PrometheusMetricConverter> converters;

    public void processMetricsRequest(final ExportMetricsServiceRequest requests) {
        requests.getResourceMetricsList().forEach(request -> {
            if (log.isDebugEnabled()) {
                log.debug("Resource attributes: {}", request.getResource().getAttributesList());
            }

            final Map<String, String> nodeLabels =
                request
                    .getResource()
                    .getAttributesList()
                    .stream()
                    .collect(toMap(
                        it -> LABEL_MAPPINGS
                            .getOrDefault(it.getKey(), it.getKey())
                            .replaceAll("\\.", "_"),
                        it -> it.getValue().getStringValue(),
                        (v1, v2) -> v1
                    ));

            converters
                .forEach(convert -> convert.toMeter(
                    request
                        .getScopeMetricsList().stream()
                        .flatMap(scopeMetrics -> scopeMetrics
                            .getMetricsList().stream()
                            .flatMap(metric -> adaptMetrics(nodeLabels, metric))
                            .map(Function1.liftTry(Function.identity()))
                            .flatMap(tryIt -> MetricConvert.log(
                                tryIt,
                                "Convert OTEL metric to prometheus metric"
                            )))));
        });

    }

    public void start() throws ModuleStartException {
        final List<String> enabledRules =
            Splitter.on(",")
                    .omitEmptyStrings()
                    .splitToList(config.getEnabledOtelRules());
        final List<Rule> rules;
        try {
            rules = Rules.loadRules("otel-rules", enabledRules);
        } catch (IOException e) {
            throw new ModuleStartException("Failed to load otel rules.", e);
        }

        if (rules.isEmpty()) {
            return;
        }
        final MeterSystem meterSystem = manager.find(CoreModule.NAME).provider().getService(MeterSystem.class);

        converters = rules
            .stream()
            .map(r -> new PrometheusMetricConverter(r, meterSystem))
            .collect(toList());
    }

    private static Map<String, String> buildLabels(List<KeyValue> kvs) {
        return kvs
            .stream()
            .collect(toMap(
                KeyValue::getKey,
                it -> it.getValue().getStringValue()
            ));
    }

    private static Map<String, String> mergeLabels(
        final Map<String, String> nodeLabels,
        final Map<String, String> pointLabels) {

        // data point labels should have higher precedence and override the one in node labels

        final Map<String, String> result = new HashMap<>(nodeLabels);
        result.putAll(pointLabels);
        return result;
    }

    private static Map<Double, Long> buildBuckets(
        final List<Long> bucketCounts,
        final List<Double> explicitBounds) {

        final Map<Double, Long> result = new HashMap<>();
        for (int i = 0; i < explicitBounds.size(); i++) {
            result.put(explicitBounds.get(i), bucketCounts.get(i));
        }
        result.put(Double.POSITIVE_INFINITY, bucketCounts.get(explicitBounds.size()));
        return result;
    }

    // Adapt the OpenTelemetry metrics to SkyWalking metrics
    private Stream<? extends Metric> adaptMetrics(
        final Map<String, String> nodeLabels,
        final io.opentelemetry.proto.metrics.v1.Metric metric) {
        if (metric.hasGauge()) {
            return metric.getGauge().getDataPointsList().stream()
                         .map(point -> new Gauge(
                             metric.getName(),
                             mergeLabels(
                                 nodeLabels,
                                 buildLabels(point.getAttributesList())
                             ),
                             point.hasAsDouble() ? point.getAsDouble()
                                 : point.getAsInt(),
                             point.getTimeUnixNano() / 1000000
                         ));
        }
        if (metric.hasSum()) {
            final Sum sum = metric.getSum();
            if (sum
                .getAggregationTemporality() != AGGREGATION_TEMPORALITY_CUMULATIVE) {
                return Stream.empty();
            }
            if (sum.getIsMonotonic()) {
                return sum.getDataPointsList().stream()
                          .map(point -> new Counter(
                              metric.getName(),
                              mergeLabels(
                                  nodeLabels,
                                  buildLabels(point.getAttributesList())
                              ),
                              point.hasAsDouble() ? point.getAsDouble()
                                  : point.getAsInt(),
                              point.getTimeUnixNano() / 1000000
                          ));
            } else {
                return sum.getDataPointsList().stream()
                          .map(point -> new Gauge(
                              metric.getName(),
                              mergeLabels(
                                  nodeLabels,
                                  buildLabels(point.getAttributesList())
                              ),
                              point.hasAsDouble() ? point.getAsDouble()
                                  : point.getAsInt(),
                              point.getTimeUnixNano() / 1000000
                          ));
            }
        }
        if (metric.hasHistogram()) {
            return metric.getHistogram().getDataPointsList().stream()
                         .map(point -> new Histogram(
                             metric.getName(),
                             mergeLabels(
                                 nodeLabels,
                                 buildLabels(point.getAttributesList())
                             ),
                             point.getCount(),
                             point.getSum(),
                             buildBuckets(
                                 point.getBucketCountsList(),
                                 point.getExplicitBoundsList()
                             ),
                             point.getTimeUnixNano() / 1000000
                         ));
        }
        if (metric.hasSummary()) {
            return metric.getSummary().getDataPointsList().stream()
                         .map(point -> new Summary(
                             metric.getName(),
                             mergeLabels(
                                 nodeLabels,
                                 buildLabels(point.getAttributesList())
                             ),
                             point.getCount(),
                             point.getSum(),
                             point.getQuantileValuesList().stream().collect(
                                 toMap(
                                     SummaryDataPoint.ValueAtQuantile::getQuantile,
                                     SummaryDataPoint.ValueAtQuantile::getValue
                                 )),
                             point.getTimeUnixNano() / 1000000
                         ));
        }
        throw new UnsupportedOperationException("Unsupported type");
    }
}
