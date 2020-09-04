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

package org.apache.skywalking.oap.server.core.metric.promethues;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.vavr.Function1;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.control.Try;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AvgHistogramPercentileFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.metric.promethues.counter.Window;
import org.apache.skywalking.oap.server.core.metric.promethues.operation.MetricSource;
import org.apache.skywalking.oap.server.core.metric.promethues.operation.Operation;
import org.apache.skywalking.oap.server.core.metric.promethues.rule.MetricsRule;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Counter;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Histogram;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Metric;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Summary;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Slf4j
public class PrometheusMetricConverter {

    private final static BigDecimal SECOND_TO_MILLISECOND  = BigDecimal.TEN.pow(3);

    private final static String AVG_HISTOGRAM = "avgHistogram";

    private final static String AVG_PERCENTILE = "avgHistogramPercentile";

    private final static String AVG = "avg";

    private final static String AVG_LABELED = "avgLabeled";

    private final static String LATEST = "latest";

    private final static String DEFAULT_GROUP = "default";

    private final static List<String> DEFAULT_GROUP_LIST = Collections.singletonList(DEFAULT_GROUP);

    private final Window window = new Window();

    private final List<MetricsRule> rules;

    private final MeterSystem service;

    public PrometheusMetricConverter(List<MetricsRule> rules, MeterSystem service) {
        this.rules = rules;
        this.service = service;
        final AtomicReference<String> lastRuleName = new AtomicReference<>();
        rules.stream().sorted(Comparator.comparing(MetricsRule::getName)).forEach(rule -> {
            if (rule.getName().equals(lastRuleName.get())) {
                lastRuleName.set(rule.getName());
                return;
            }
            if (rule.getBucketUnit().toMillis(1L) < 1L) {
                throw new IllegalArgumentException("Bucket unit should be equals or more than MILLISECOND");
            }
            service.create(formatMetricName(rule.getName()), rule.getOperation(), rule.getScope());
            lastRuleName.set(rule.getName());
        });
    }

    public void toMeter(Stream<Metric> metricStream) {
        metricStream
            .flatMap(metric -> {
                if (metric instanceof Histogram) {
                    Histogram h = (Histogram) metric;
                    return Stream.of(metric,
                        new Counter(h.getName() + "_count", h.getLabels(), h.getSampleCount(), h.getTimestamp()),
                        new Counter(h.getName() + "_sum", h.getLabels(), h.getSampleSum(), h.getTimestamp()));
                }
                if (metric instanceof Summary) {
                    Summary s = (Summary) metric;
                    return Stream.of(metric,
                        new Counter(s.getName() + "_count", s.getLabels(), s.getSampleCount(), s.getTimestamp()),
                        new Counter(s.getName() + "_sum", s.getLabels(), s.getSampleSum(), s.getTimestamp()));
                }
                return Stream.of(metric);
            })
            .flatMap(metric ->
                rules.stream()
                    .flatMap(rule -> rule.getSources().entrySet().stream().map(source -> Tuple.of(rule, source.getKey(), source.getValue())))
                    .filter(rule -> rule._2.equals(metric.getName()))
                    .filter(rule -> metric.getLabels().keySet().containsAll(rule._3.getRelabel().labelKeys()))
                    .filter(rule -> {
                        if (Objects.isNull(rule._3.getLabelFilter())) {
                            return true;
                        }
                        return rule._3.getLabelFilter().stream()
                            .allMatch(matchRule -> matchRule.getOptions().stream()
                                .anyMatch(option -> matchLabel(option, metric.getLabels().get(matchRule.getKey()))));
                    })
                    .map(rule -> Tuple.of(rule._1, rule._2, rule._3, metric))
            )
            .peek(tuple -> log.debug("Mapped rules to metrics: {}", tuple))
            .map(Function1.liftTry(tuple -> {
                String serviceName = composeEntity(tuple._3.getRelabel().getService().stream(), tuple._4.getLabels());
                Operation o = new Operation(tuple._1.getOperation(), tuple._1.getName(), tuple._1.getScope(), tuple._1.getPercentiles(), tuple._1.getBucketUnit());
                MetricSource.MetricSourceBuilder sb = MetricSource.builder();
                sb.promMetricName(tuple._2)
                    .timestamp(tuple._4.getTimestamp())
                    .scale(tuple._3.getScale())
                    .counterFunction(tuple._3.getCounterFunction())
                    .groupBy(tuple._3.getGroupBy())
                    .range(tuple._3.getRange());
                switch (tuple._1.getScope()) {
                    case SERVICE:
                        return Tuple.of(o, sb.entity(MeterEntity.newService(serviceName)).build(), tuple._4);
                    case SERVICE_INSTANCE:
                        String instanceName = composeEntity(tuple._3.getRelabel().getInstance().stream(), tuple._4.getLabels());
                        return Tuple.of(o, sb.entity(MeterEntity.newServiceInstance(serviceName, instanceName)).build(), tuple._4);
                    case ENDPOINT:
                        String endpointName = composeEntity(tuple._3.getRelabel().getEndpoint().stream(), tuple._4.getLabels());
                        return Tuple.of(o, sb.entity(MeterEntity.newEndpoint(serviceName, endpointName)).build(), tuple._4);
                    default:
                        throw new IllegalArgumentException("Unsupported scope" + tuple._1.getScope());
                }
            }))
            .flatMap(tryIt -> PrometheusMetricConverter.log(tryIt, "Generated entity from labels"))
            .collect(groupingBy(Tuple3::_1, groupingBy(Tuple3::_2, mapping(Tuple3::_3, toList()))))
            .forEach((operation, sources) -> {
                log.debug("Building metrics {} -> {}", operation, sources);
                Try.run(() -> {
                    switch (operation.getName()) {
                        case LATEST:
                        case AVG:
                            sources.forEach((source, metrics) -> {
                                AcceptableValue<Long> value = service.buildMetrics(formatMetricName(operation.getMetricName()), Long.class);
                                value.accept(source.getEntity(), sum(metrics, source));
                                value.setTimeBucket(TimeBucket.getMinuteTimeBucket(source.getTimestamp()));
                                log.debug("Input metric {}", value.getTimeBucket());
                                service.doStreamingCalculation(value);

                                generateTraffic(source.getEntity());
                            });
                            break;
                        case AVG_LABELED:
                            sources.forEach((source, metrics) -> {
                                Preconditions.checkArgument(Objects.nonNull(source.getGroupBy()));
                                DataTable dt = new DataTable();
                                metrics.stream()
                                    .collect(groupingBy(m -> source.getGroupBy().stream().map(m.getLabels()::get).collect(Collectors.joining("-"))))
                                    .forEach((group, mm) -> dt.put(group, sum(mm, source, ImmutableMap.of("group", group))));
                                AcceptableValue<DataTable> value = service.buildMetrics(formatMetricName(operation.getMetricName()), DataTable.class);
                                value.accept(source.getEntity(), dt);
                                value.setTimeBucket(TimeBucket.getMinuteTimeBucket(source.getTimestamp()));
                                log.debug("Input metric {}", value.getTimeBucket());
                                service.doStreamingCalculation(value);

                                generateTraffic(source.getEntity());
                            });
                            break;
                        case AVG_HISTOGRAM:
                        case AVG_PERCENTILE:
                            Validate.isTrue(sources.size() == 1, "Can't get source for histogram");
                            Map.Entry<MetricSource, List<Metric>> smm = sources.entrySet().iterator().next();

                            smm.getValue().stream()
                                .collect(groupingBy(m -> Optional.ofNullable(smm.getKey().getGroupBy()).orElse(DEFAULT_GROUP_LIST).stream()
                                    .map(m.getLabels()::get)
                                    .map(group -> Optional.ofNullable(group).orElse(DEFAULT_GROUP))
                                    .collect(Collectors.joining("-"))))
                                .forEach((group, mm) -> {
                                    Histogram h = (Histogram) sum(mm);

                                    long[] vv = new long[h.getBuckets().size()];
                                    long[] bb = new long[h.getBuckets().size()];
                                    long v = 0L;
                                    int i = 0;
                                    for (Map.Entry<Double, Long> entry : h.getBuckets().entrySet()) {
                                        long increase = entry.getValue() - v;
                                        vv[i] = window.get(operation.getMetricName(), ImmutableMap.of("group", group, "le", entry.getKey().toString()))
                                            .apply(smm.getKey(), (double) increase).longValue();
                                        v = entry.getValue();

                                        if (i + 1 < h.getBuckets().size()) {
                                            bb[i + 1] = BigDecimal.valueOf(entry.getKey())
                                                .multiply(BigDecimal.valueOf(operation.getBucketUnit().toMillis(1L)))
                                                .longValue();
                                        }
                                        i++;
                                    }
                                    BucketedValues bv = new BucketedValues(bb, vv);
                                    if (!group.equals(DEFAULT_GROUP)) {
                                        bv.setGroup(group);
                                    }
                                    if (operation.getName().equals(AVG_HISTOGRAM)) {
                                        AcceptableValue<BucketedValues> heatmapMetrics = service.buildMetrics(
                                            formatMetricName(operation.getMetricName()), BucketedValues.class);
                                        heatmapMetrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(smm.getKey().getTimestamp()));
                                        heatmapMetrics.accept(smm.getKey().getEntity(), bv);
                                        service.doStreamingCalculation(heatmapMetrics);
                                    } else {
                                        AcceptableValue<AvgHistogramPercentileFunction.AvgPercentileArgument> percentileMetrics =
                                            service.buildMetrics(formatMetricName(operation.getMetricName()), AvgHistogramPercentileFunction.AvgPercentileArgument.class);
                                        percentileMetrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(smm.getKey().getTimestamp()));
                                        percentileMetrics.accept(smm.getKey().getEntity(),
                                            new AvgHistogramPercentileFunction.AvgPercentileArgument(bv, operation.getPercentiles().stream().mapToInt(Integer::intValue).toArray()));
                                        service.doStreamingCalculation(percentileMetrics);
                                    }

                                    generateTraffic(smm.getKey().getEntity());
                                });

                            break;
                        default:
                            throw new IllegalArgumentException(String.format("Unsupported downSampling %s", operation.getName()));
                    }
                }).onFailure(e -> log.debug("Building metric failed", e));
            });
    }

    private String formatMetricName(String meterRuleName) {
        StringJoiner metricName = new StringJoiner("_");
        metricName.add("meter").add(meterRuleName);
        return metricName.toString();
    }

    private String composeEntity(Stream<String> stream, Map<String, String> labels) {
        return stream.map(key -> requireNonNull(labels.get(key), String.format("Getting %s from %s failed", key, labels)))
            .collect(Collectors.joining("."));
    }

    private Metric sum(List<Metric> metrics) {
        return metrics.stream().reduce(Metric::sum).orElseThrow(IllegalArgumentException::new);
    }

    private long sum(List<Metric> metrics, MetricSource source) {
        return sum(metrics, source, Collections.emptyMap());
    }

    private long sum(List<Metric> metrics, MetricSource source, Map<String, String> labels) {
        Double sumDouble = sum(metrics).value();
        sumDouble = window.get(source.getPromMetricName(), labels).apply(source, sumDouble);
        return BigDecimal.valueOf(Double.isNaN(sumDouble) ? 0D : sumDouble)
            .multiply(BigDecimal.TEN.pow(source.getScale())).longValue();
    }

    private void generateTraffic(MeterEntity entity) {
        ServiceTraffic s = new ServiceTraffic();
        s.setName(requireNonNull(entity.getServiceName()));
        s.setNodeType(NodeType.Normal);
        s.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        MetricsStreamProcessor.getInstance().in(s);
        if (!Strings.isNullOrEmpty(entity.getInstanceName())) {
            InstanceTraffic instanceTraffic = new InstanceTraffic();
            instanceTraffic.setName(entity.getInstanceName());
            instanceTraffic.setServiceId(entity.serviceId());
            instanceTraffic.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            instanceTraffic.setLastPingTimestamp(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            MetricsStreamProcessor.getInstance().in(instanceTraffic);
        }
        if (!Strings.isNullOrEmpty(entity.getEndpointName())) {
            EndpointTraffic endpointTraffic = new EndpointTraffic();
            endpointTraffic.setName(entity.getEndpointName());
            endpointTraffic.setServiceId(entity.serviceId());
            endpointTraffic.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            MetricsStreamProcessor.getInstance().in(endpointTraffic);
        }
    }

    public static <T> Stream<T> log(Try<T> t, String debugMessage) {
        return t
            .onSuccess(i -> log.debug(debugMessage + " :{}", i))
            .onFailure(e -> log.debug(debugMessage + " failed", e))
            .toJavaStream();
    }

    private boolean matchLabel(String option, String labelValue) {
        if (option.startsWith("!")) {
            return !matchLabelRule(option.substring(1), labelValue);
        }
        log.debug("{} {}", option, labelValue);
        return matchLabelRule(option, labelValue);
    }

    private boolean matchLabelRule(String rule, String labelValue) {
        if (Strings.isNullOrEmpty(rule)) {
            return Strings.isNullOrEmpty(labelValue);
        }
        return labelValue.matches(rule);
    }
}
