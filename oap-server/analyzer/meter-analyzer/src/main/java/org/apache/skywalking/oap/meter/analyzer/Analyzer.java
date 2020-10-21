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
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.dsl.DSL;
import org.apache.skywalking.oap.meter.analyzer.dsl.Expression;
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
        return new Analyzer(metricName, e, meterSystem);
    }

    private static final String FUNCTION_NAME_TEMP = "%s%s";

    private final String metricName;

    private final Expression expression;

    private final MeterSystem meterSystem;

    private boolean createdMetric;

    /**
     * analyse intends to parse expression with input samples to meter-system metrics.
     *
     * @param sampleFamilies input samples.
     */
    public void analyse(final ImmutableMap<String, SampleFamily> sampleFamilies) {
        Result r = expression.run(sampleFamilies);
        if (!r.isSuccess()) {
            return;
        }
        SampleFamily.Context ctx = r.getData().context;
        Sample[] ss = r.getData().samples;
        generateTraffic(ctx.getMeterEntity());
        if (ctx.isHistogram()) {
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
                        vv[i] = (long) s.getValue();
                    }
                    BucketedValues bv = new BucketedValues(bb, vv);
                    long time = subSs.get(0).getTimestamp();
                    if (ctx.getPercentiles() == null || ctx.getPercentiles().length < 1) {
                        Preconditions.checkState(createMetric(ctx.getMeterEntity().getScopeType(), "histogram", ctx));
                        AcceptableValue<BucketedValues> v = meterSystem.buildMetrics(metricName, BucketedValues.class);
                        v.accept(ctx.getMeterEntity(), bv);
                        send(v, time);
                        return;
                    }
                    Preconditions.checkState(createMetric(ctx.getMeterEntity().getScopeType(), "histogramPercentile", ctx));
                    AcceptableValue<PercentileArgument> v = meterSystem.buildMetrics(metricName, PercentileArgument.class);
                    v.accept(ctx.getMeterEntity(), new PercentileArgument(bv, ctx.getPercentiles()));
                    send(v, time);
                });
            return;
        }
        if (ss.length == 1) {
            Preconditions.checkState(createMetric(ctx.getMeterEntity().getScopeType(), "", ctx));
            AcceptableValue<Long> v = meterSystem.buildMetrics(metricName, Long.class);
            v.accept(ctx.getMeterEntity(), (long) ss[0].getValue());
            send(v, ss[0].getTimestamp());
            return;
        }
        Preconditions.checkState(createMetric(ctx.getMeterEntity().getScopeType(), "labeled", ctx));
        AcceptableValue<DataTable> v = meterSystem.buildMetrics(metricName, DataTable.class);
        DataTable dt = new DataTable();
        for (Sample each : ss) {
            dt.put(composeGroup(each.getLabels()), (long) each.getValue());
        }
        v.accept(ctx.getMeterEntity(), dt);
        send(v, ss[0].getTimestamp());
    }

    private String composeGroup(ImmutableMap<String, String> labels) {
        return composeGroup(labels, k -> true);
    }

    private String composeGroup(ImmutableMap<String, String> labels, Predicate<String> filter) {
        return labels.keySet().stream().filter(filter).sorted().map(labels::get)
            .collect(Collectors.joining("-"));
    }

    private boolean createMetric(final ScopeType scopeType, final String dataType, final SampleFamily.Context ctx) {
        if (createdMetric) {
            return true;
        }
        String functionName = String.format(FUNCTION_NAME_TEMP, ctx.getDownsampling().toString().toLowerCase(), Strings.capitalize(dataType));
        return meterSystem.create(metricName, functionName, scopeType) && (createdMetric = true);
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
