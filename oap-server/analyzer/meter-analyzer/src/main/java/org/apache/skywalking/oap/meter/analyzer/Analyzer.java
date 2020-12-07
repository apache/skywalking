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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.dsl.DSL;
import org.apache.skywalking.oap.meter.analyzer.dsl.DownsamplingType;
import org.apache.skywalking.oap.meter.analyzer.dsl.Expression;
import org.apache.skywalking.oap.meter.analyzer.dsl.ExpressionParsingContext;
import org.apache.skywalking.oap.meter.analyzer.dsl.Result;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.meter.function.PercentileArgument;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.elasticsearch.common.Strings;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * Analyzer analyses DSL expression with input samples, then to generate meter-system metrics.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(of = {"metricName", "expression"})
public class Analyzer {

    public static final Tuple2<String, SampleFamily> NIL = Tuple.of("", null);

    public static Analyzer build(final String metricName, final String expression,
        final MeterSystem meterSystem) {
        Expression e = DSL.parse(expression);
        ExpressionParsingContext ctx = e.parse();
        Analyzer analyzer = new Analyzer(metricName, e, meterSystem);
        analyzer.init(ctx);
        return analyzer;
    }

    private static final String FUNCTION_NAME_TEMP = "%s%s";

    private List<String> samples;

    private final String metricName;

    private final Expression expression;

    private final MeterSystem meterSystem;

    private MetricType metricType;

    private int[] percentiles;

    /**
     * analyse intends to parse expression with input samples to meter-system metrics.
     *
     * @param sampleFamilies input samples.
     */
    public void analyse(final ImmutableMap<String, SampleFamily> sampleFamilies) {
        ImmutableMap<String, SampleFamily> input = samples.stream().map(s -> Tuple.of(s, sampleFamilies.get(s)))
            .filter(t -> t._2 != null).collect(ImmutableMap.toImmutableMap(t -> t._1, t -> t._2));
        if (input.size() < 1) {
            if (log.isDebugEnabled()) {
                log.debug("{} is ignored due to the lack of {}", expression, samples);
            }
            return;
        }
        Result r = expression.run(input);
        if (!r.isSuccess()) {
            return;
        }
        SampleFamily.RunningContext ctx = r.getData().context;
        Sample[] ss = r.getData().samples;
        generateTraffic(ctx.getMeterEntity());
        switch (metricType) {
            case single:
                AcceptableValue<Long> sv = meterSystem.buildMetrics(metricName, Long.class);
                sv.accept(ctx.getMeterEntity(), getValue(ss[0]));
                send(sv, ss[0].getTimestamp());
                break;
            case labeled:
                AcceptableValue<DataTable> lv = meterSystem.buildMetrics(metricName, DataTable.class);
                DataTable dt = new DataTable();
                for (Sample each : ss) {
                    dt.put(composeGroup(each.getLabels()), getValue(each));
                }
                lv.accept(ctx.getMeterEntity(), dt);
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
                              bb[i] = Long.parseLong(s.getLabels().get("le"));
                              vv[i] = getValue(s);
                          }
                          BucketedValues bv = new BucketedValues(bb, vv);
                          long time = subSs.get(0).getTimestamp();
                          if (metricType == MetricType.histogram) {
                              AcceptableValue<BucketedValues> v = meterSystem.buildMetrics(metricName, BucketedValues.class);
                              v.accept(ctx.getMeterEntity(), bv);
                              send(v, time);
                              return;
                          }
                          AcceptableValue<PercentileArgument> v = meterSystem.buildMetrics(metricName, PercentileArgument.class);
                          v.accept(ctx.getMeterEntity(), new PercentileArgument(bv, percentiles));
                          send(v, time);
                      });
                break;
        }
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

    private void init(final ExpressionParsingContext ctx) {
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
        Preconditions.checkState(createMetric(ctx.getScopeType(), metricType.literal, ctx.getDownsampling()));
    }

    private boolean createMetric(final ScopeType scopeType, final String dataType, final DownsamplingType downsamplingType) {
        String functionName = String.format(FUNCTION_NAME_TEMP, downsamplingType.toString().toLowerCase(), Strings.capitalize(dataType));
        return meterSystem.create(metricName, functionName, scopeType);
    }

    private void send(final AcceptableValue<?> v, final long time) {
        v.setTimeBucket(TimeBucket.getMinuteTimeBucket(time));
        meterSystem.doStreamingCalculation(v);
    }

    private void generateTraffic(MeterEntity entity) {
        ServiceTraffic s = new ServiceTraffic();
        s.setName(requireNonNull(entity.getServiceName()));
        s.setNodeType(NodeType.Normal);
        s.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        MetricsStreamProcessor.getInstance().in(s);
        if (!com.google.common.base.Strings.isNullOrEmpty(entity.getInstanceName())) {
            InstanceTraffic instanceTraffic = new InstanceTraffic();
            instanceTraffic.setName(entity.getInstanceName());
            instanceTraffic.setServiceId(entity.serviceId());
            instanceTraffic.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            instanceTraffic.setLastPingTimestamp(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
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
}
