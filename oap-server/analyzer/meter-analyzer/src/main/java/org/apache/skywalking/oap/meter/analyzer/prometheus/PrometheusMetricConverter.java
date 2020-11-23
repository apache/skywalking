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

package org.apache.skywalking.oap.meter.analyzer.prometheus;

import com.google.common.collect.ImmutableMap;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Counter;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Gauge;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Histogram;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Metric;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Summary;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Predicates.instanceOf;
import static java.util.stream.Collectors.toList;
import static org.apache.skywalking.oap.meter.analyzer.Analyzer.NIL;

/**
 * PrometheusMetricConverter converts prometheus metrics to meter-system metrics, then store them to backend storage.
 */
@Slf4j
public class PrometheusMetricConverter {

    private final MetricConvert convert;

    public PrometheusMetricConverter(Rule rule, MeterSystem service) {
        this.convert = new MetricConvert(rule, service);
    }

    /**
     * toMeter transforms prometheus metrics to meter-system metrics.
     *
     * @param metricStream prometheus metrics stream.
     */
    public void toMeter(Stream<Metric> metricStream) {
        ImmutableMap<String, SampleFamily> data = convertPromMetricToSampleFamily(metricStream);
        convert.toMeter(data);
    }

    public ImmutableMap<String, SampleFamily> convertPromMetricToSampleFamily(Stream<Metric> metricStream) {
        return metricStream
            .peek(metric -> log.debug("Prom metric to be convert to SampleFamily: {}", metric))
            .flatMap(this::convertMetric)
            .filter(t -> t != NIL)
            .peek(t -> log.debug("SampleFamily: {}", t))
            .collect(toImmutableMap(Tuple2::_1, Tuple2::_2, (a, b) -> {
                log.debug("merge {} {}", a, b);
                Sample[] m = new Sample[a.samples.length + b.samples.length];
                System.arraycopy(a.samples, 0, m, 0, a.samples.length);
                System.arraycopy(b.samples, 0, m, a.samples.length, b.samples.length);
                return SampleFamily.build(m);
            }));
    }

    private Stream<Tuple2<String, SampleFamily>> convertMetric(Metric metric) {
        return Match(metric).of(
            Case($(instanceOf(Histogram.class)), t -> Stream.of(
                Tuple.of(metric.getName() + "_count", SampleFamily.build(Sample.builder().name(metric.getName() + "_count")
                    .timestamp(metric.getTimestamp()).labels(ImmutableMap.copyOf(metric.getLabels())).value(((Histogram) metric).getSampleCount()).build())),
                Tuple.of(metric.getName() + "_sum", SampleFamily.build(Sample.builder().name(metric.getName() + "_sum")
                    .timestamp(metric.getTimestamp()).labels(ImmutableMap.copyOf(metric.getLabels())).value(((Histogram) metric).getSampleSum()).build())),
                convertToSample(metric).orElse(NIL))),
            Case($(instanceOf(Summary.class)), t -> Stream.of(
                Tuple.of(metric.getName() + "_count", SampleFamily.build(Sample.builder().name(metric.getName() + "_count")
                    .timestamp(metric.getTimestamp()).labels(ImmutableMap.copyOf(metric.getLabels())).value(((Summary) metric).getSampleCount()).build())),
                Tuple.of(metric.getName() + "_sum", SampleFamily.build(Sample.builder().name(metric.getName() + "_sum")
                    .timestamp(metric.getTimestamp()).labels(ImmutableMap.copyOf(metric.getLabels())).value(((Summary) metric).getSampleSum()).build())),
                convertToSample(metric).orElse(NIL))),
            Case($(), t -> Stream.of(convertToSample(metric).orElse(NIL)))
        );
    }

    private Optional<Tuple2<String, SampleFamily>> convertToSample(Metric metric) {
        Sample[] ss = Match(metric).of(
            Case($(instanceOf(Counter.class)), t -> Collections.singletonList(Sample.builder()
                .name(t.getName())
                .labels(ImmutableMap.copyOf(t.getLabels()))
                .timestamp(t.getTimestamp())
                .value(t.getValue())
                .build())),
            Case($(instanceOf(Gauge.class)), t -> Collections.singletonList(Sample.builder()
                .name(t.getName())
                .labels(ImmutableMap.copyOf(t.getLabels()))
                .timestamp(t.getTimestamp())
                .value(t.getValue())
                .build())),
            Case($(instanceOf(Histogram.class)), t -> t.getBuckets()
                .entrySet().stream()
                .map(b -> Sample.builder()
                    .name(t.getName())
                    .labels(ImmutableMap.<String, String>builder()
                        .putAll(t.getLabels())
                        .put("le", b.getKey().toString())
                        .build())
                    .timestamp(t.getTimestamp())
                    .value(b.getValue())
                    .build()).collect(toList())),
            Case($(instanceOf(Summary.class)),
                t -> t.getQuantiles().entrySet().stream()
                    .map(b -> Sample.builder()
                        .name(t.getName())
                        .labels(ImmutableMap.<String, String>builder()
                            .putAll(t.getLabels())
                            .put("quantile", b.getKey().toString())
                            .build())
                        .timestamp(t.getTimestamp())
                        .value(b.getValue())
                        .build()).collect(toList()))
        ).toArray(new Sample[0]);
        if (ss.length < 1) {
            return Optional.empty();
        }
        return Optional.of(Tuple.of(metric.getName(), SampleFamily.build(ss)));
    }
}
