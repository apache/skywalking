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

package org.apache.skywalking.oap.meter.analyzer;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;
import org.apache.skywalking.oap.meter.analyzer.dsl.DSL;
import org.apache.skywalking.oap.meter.analyzer.dsl.DownsamplingType;
import org.apache.skywalking.oap.meter.analyzer.dsl.Expression;
import org.apache.skywalking.oap.meter.analyzer.dsl.ExpressionParsingContext;
import org.apache.skywalking.oap.meter.analyzer.dsl.FilterExpression;
import org.apache.skywalking.oap.meter.analyzer.dsl.Result;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.meter.function.PercentileArgument;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * Analyzer analyses DSL expression with input samples, then to generate meter-system metrics.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(of = {
    "metricName",
    "expression"
})
public class Analyzer {

    public static final Tuple2<String, SampleFamily> NIL = Tuple.of("", null);

    public static Analyzer build(final String metricName,
                                 final String filterExpression,
                                 final String expression,
                                 final MeterSystem meterSystem) {
        Expression e = DSL.parse(expression);
        FilterExpression filter = null;
        if (!Strings.isNullOrEmpty(filterExpression)) {
            filter = new FilterExpression(filterExpression);
        }
        ExpressionParsingContext ctx = e.parse();
        Analyzer analyzer = new Analyzer(metricName, filter, e, meterSystem, ctx);
        analyzer.init();
        return analyzer;
    }

    private List<String> samples;

    private final String metricName;

    private final FilterExpression filterExpression;

    private final Expression expression;

    private final MeterSystem meterSystem;

    private final ExpressionParsingContext ctx;

    private MetricType metricType;

    private int[] percentiles;

    /**
     * analyse intends to parse expression with input samples to meter-system metrics.
     *
     * @param sampleFamilies input samples.
     */
    public void analyse(final ImmutableMap<String, SampleFamily> sampleFamilies) {
        Map<String, SampleFamily> input = samples.stream()
                                                 .map(s -> Tuple.of(s, sampleFamilies.get(s)))
                                                 .filter(t -> t._2 != null)
                                                 .collect(toImmutableMap(t -> t._1, t -> t._2));
        if (input.size() < 1) {
            if (log.isDebugEnabled()) {
                log.debug("{} is ignored due to the lack of {}", expression, samples);
            }
            return;
        }
        if (filterExpression != null) {
            input = filterExpression.filter(input);
        }
        Result r = expression.run(input);
        if (!r.isSuccess()) {
            return;
        }
        SampleFamily.RunningContext ctx = r.getData().context;
        Map<MeterEntity, Sample[]> meterSamples = ctx.getMeterSamples();
        meterSamples.forEach((meterEntity, ss) -> {
            generateTraffic(meterEntity);
            switch (metricType) {
                case single:
                    AcceptableValue<Long> sv = meterSystem.buildMetrics(metricName, Long.class);
                    sv.accept(meterEntity, getValue(ss[0]));
                    send(sv, ss[0].getTimestamp());
                    break;
                case labeled:
                    AcceptableValue<DataTable> lv = meterSystem.buildMetrics(metricName, DataTable.class);
                    DataTable dt = new DataTable();
                    for (Sample each : ss) {
                        dt.put(composeGroup(each.getLabels()), getValue(each));
                    }
                    lv.accept(meterEntity, dt);
                    send(lv, ss[0].getTimestamp());
                    break;
                case histogram:
                case histogramPercentile:
                    Stream.of(ss).map(s -> Tuple.of(composeGroup(s.getLabels(), k -> !Objects.equals("le", k)), s))
                          .collect(groupingBy(Tuple2::_1, mapping(Tuple2::_2, toList())))
                          .forEach((group, subSs) -> {
                              if (subSs.size() < 1) {
                                  return;
                              }
                              long[] bb = new long[subSs.size()];
                              long[] vv = new long[bb.length];
                              for (int i = 0; i < subSs.size(); i++) {
                                  Sample s = subSs.get(i);
                                  final double leVal = Double.parseDouble(s.getLabels().get("le"));
                                  if (leVal == Double.NEGATIVE_INFINITY) {
                                      bb[i] = Long.MIN_VALUE;
                                  } else {
                                      bb[i] = (long) leVal;
                                  }
                                  vv[i] = getValue(s);
                              }
                              BucketedValues bv = new BucketedValues(bb, vv);
                              bv.setGroup(group);
                              long time = subSs.get(0).getTimestamp();
                              if (metricType == MetricType.histogram) {
                                  AcceptableValue<BucketedValues> v = meterSystem.buildMetrics(
                                      metricName, BucketedValues.class);
                                  v.accept(meterEntity, bv);
                                  send(v, time);
                                  return;
                              }
                              AcceptableValue<PercentileArgument> v = meterSystem.buildMetrics(
                                  metricName, PercentileArgument.class);
                              v.accept(meterEntity, new PercentileArgument(bv, percentiles));
                              send(v, time);
                          });
                    break;
            }
        });
    }

    private long getValue(Sample sample) {
        if (sample.getValue() <= 0.0) {
            return 0L;
        }
        if (sample.getValue() < 1.0) {
            return 1L;
        }
        return Math.round(sample.getValue());
    }

    private String composeGroup(ImmutableMap<String, String> labels) {
        return composeGroup(labels, k -> true);
    }

    private String composeGroup(ImmutableMap<String, String> labels, Predicate<String> filter) {
        return labels.keySet().stream().filter(filter).sorted().map(labels::get)
                     .collect(Collectors.joining("-"));
    }

    @RequiredArgsConstructor
    private enum MetricType {
        // metrics is aggregated by histogram function.
        histogram("histogram"),
        // metrics is aggregated by histogram based percentile function.
        histogramPercentile("histogramPercentile"),
        // metrics is aggregated by labeled function.
        labeled("labeled"),
        // metrics is aggregated by single value function.
        single("");

        private final String literal;
    }

    private void init() {
        this.samples = ctx.getSamples();
        if (ctx.isHistogram()) {
            if (ctx.getPercentiles() != null && ctx.getPercentiles().length > 0) {
                metricType = MetricType.histogramPercentile;
                this.percentiles = ctx.getPercentiles();
            } else {
                metricType = MetricType.histogram;
            }
        } else {
            if (ctx.getLabels().isEmpty()) {
                metricType = MetricType.single;
            } else {
                metricType = MetricType.labeled;
            }
        }
        createMetric(ctx.getScopeType(), metricType.literal, ctx.getDownsampling());
    }

    private void createMetric(final ScopeType scopeType,
                              final String dataType,
                              final DownsamplingType downsamplingType) {
        String downSamplingStr = CaseUtils.toCamelCase(downsamplingType.toString().toLowerCase(), false, '_');
        String functionName = String.format("%s%s", downSamplingStr, StringUtils.capitalize(dataType));
        meterSystem.create(metricName, functionName, scopeType);
    }

    private void send(final AcceptableValue<?> v, final long time) {
        v.setTimeBucket(TimeBucket.getMinuteTimeBucket(time));
        meterSystem.doStreamingCalculation(v);
    }

    private void generateTraffic(MeterEntity entity) {
        if (entity.getDetectPoint() != null) {
            switch (entity.getScopeType()) {
                case SERVICE_RELATION:
                    serviceRelationTraffic(entity);
                    break;
                case PROCESS_RELATION:
                    processRelationTraffic(entity);
                    break;
                default:
            }
        } else {
            toService(requireNonNull(entity.getServiceName()), entity.getLayer());
        }

        if (!com.google.common.base.Strings.isNullOrEmpty(entity.getInstanceName())) {
            InstanceTraffic instanceTraffic = new InstanceTraffic();
            instanceTraffic.setName(entity.getInstanceName());
            instanceTraffic.setServiceId(entity.serviceId());
            instanceTraffic.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            instanceTraffic.setLastPingTimestamp(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            if (entity.getInstanceProperties() != null && !entity.getInstanceProperties().isEmpty()) {
                final JsonObject properties = new JsonObject();
                entity.getInstanceProperties().forEach((k, v) -> properties.addProperty(k, v));
                instanceTraffic.setProperties(properties);
            }
            MetricsStreamProcessor.getInstance().in(instanceTraffic);
        }
        if (!com.google.common.base.Strings.isNullOrEmpty(entity.getEndpointName())) {
            EndpointTraffic endpointTraffic = new EndpointTraffic();
            endpointTraffic.setName(entity.getEndpointName());
            endpointTraffic.setServiceId(entity.serviceId());
            endpointTraffic.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            MetricsStreamProcessor.getInstance().in(endpointTraffic);
        }
    }

    private void toService(String serviceName, Layer layer) {
        ServiceTraffic s = new ServiceTraffic();
        s.setName(requireNonNull(serviceName));
        s.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        s.setLayer(layer);
        MetricsStreamProcessor.getInstance().in(s);
    }

    private void serviceRelationTraffic(MeterEntity entity) {
        switch (entity.getDetectPoint()) {
            case SERVER:
                entity.setServiceName(entity.getDestServiceName());
                toService(requireNonNull(entity.getDestServiceName()), entity.getLayer());
                serviceRelationServerSide(entity);
                break;
            case CLIENT:
                entity.setServiceName(entity.getSourceServiceName());
                toService(requireNonNull(entity.getSourceServiceName()), entity.getLayer());
                serviceRelationClientSide(entity);
                break;
            default:
        }
    }

    private void serviceRelationServerSide(MeterEntity entity) {
        ServiceRelationServerSideMetrics metrics = new ServiceRelationServerSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.getComponentIds().add(entity.getComponentId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private void serviceRelationClientSide(MeterEntity entity) {
        ServiceRelationClientSideMetrics metrics = new ServiceRelationClientSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.getComponentIds().add(entity.getComponentId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private void processRelationTraffic(MeterEntity entity) {
        switch (entity.getDetectPoint()) {
            case SERVER:
                processRelationServerSide(entity);
                break;
            case CLIENT:
                processRelationClientSide(entity);
                break;
            default:
        }
    }

    private void processRelationServerSide(MeterEntity entity) {
        ProcessRelationServerSideMetrics metrics = new ProcessRelationServerSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setServiceInstanceId(entity.serviceInstanceId());
        metrics.setSourceProcessId(entity.getSourceProcessId());
        metrics.setDestProcessId(entity.getDestProcessId());
        metrics.setEntityId(entity.id());
        metrics.setComponentId(entity.getComponentId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private void processRelationClientSide(MeterEntity entity) {
        ProcessRelationClientSideMetrics metrics = new ProcessRelationClientSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setServiceInstanceId(entity.serviceInstanceId());
        metrics.setSourceProcessId(entity.getSourceProcessId());
        metrics.setDestProcessId(entity.getDestProcessId());
        metrics.setEntityId(entity.id());
        metrics.setComponentId(entity.getComponentId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

}
