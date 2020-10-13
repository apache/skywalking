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
import io.vavr.control.Try;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.Analyzer;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.MetricsRule;
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
import static org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily.build;

@Slf4j
public class MetricConverter {

    private final List<Analyzer> analyzers;

    public MetricConverter(List<MetricsRule> rules, MeterSystem service) {
        this.analyzers = rules.stream().map(r -> Analyzer.build(formatMetricName(r.getName()), r.getExp(), service)).collect(toList());
    }

    public void toMeter(Stream<Metric> metricStream) {
        ImmutableMap<String, SampleFamily> data = metricStream
            .flatMap(metric -> Match(metric).of(
                    Case($(instanceOf(Histogram.class)), h -> Stream.of(metric,
                        new Counter(h.getName() + "_count", h.getLabels(), h.getSampleCount(), h.getTimestamp()),
                        new Counter(h.getName() + "_sum", h.getLabels(), h.getSampleSum(), h.getTimestamp()))),
                    Case($(instanceOf(Summary.class)), s -> Stream.of(metric,
                        new Counter(s.getName() + "_count", s.getLabels(), s.getSampleCount(), s.getTimestamp()),
                        new Counter(s.getName() + "_sum", s.getLabels(), s.getSampleSum(), s.getTimestamp()))),
                    Case($(), (Function<Metric, Stream<Metric>>) Stream::of)
                ))
            .map(metric -> Tuple.of(metric.getName(), Match(metric).of(
                    Case($(instanceOf(Counter.class)), t -> build(Sample.builder()
                        .name(t.getName())
                        .labels(ImmutableMap.copyOf(t.getLabels()))
                        .timestamp(t.getTimestamp())
                        .value(t.getValue())
                        .build())),
                    Case($(instanceOf(Gauge.class)), t -> build(Sample.builder()
                        .name(t.getName())
                        .labels(ImmutableMap.copyOf(t.getLabels()))
                        .timestamp(t.getTimestamp())
                        .value(t.getValue())
                        .build())),
                    Case($(instanceOf(Histogram.class)), t -> build(t.getBuckets()
                        .entrySet().stream()
                        .map(b -> Sample.builder()
                            .name(t.getName())
                            .labels(ImmutableMap.<String, String>builder()
                                .putAll(t.getLabels())
                                .put("le", b.getKey().toString())
                                .build())
                            .timestamp(t.getTimestamp())
                            .value(b.getValue())
                            .build())
                        .toArray(Sample[]::new))),
                    Case($(instanceOf(Summary.class)), t -> build(t.getQuantiles()
                        .entrySet().stream()
                        .map(b -> Sample.builder()
                            .name(t.getName())
                            .labels(ImmutableMap.<String, String>builder()
                                .putAll(t.getLabels())
                                .put("quantile", b.getKey().toString())
                                .build())
                            .timestamp(t.getTimestamp())
                            .value(b.getValue())
                            .build())
                        .toArray(Sample[]::new)))
                )))
            .collect(toImmutableMap(Tuple2::_1, Tuple2::_2));

        for (Analyzer each : analyzers) {
            try {
                each.analyse(data);
            } catch (Throwable t) {
                log.error("Analyze {} error", each);
            }
        }
    }

    private String formatMetricName(String meterRuleName) {
        StringJoiner metricName = new StringJoiner("_");
        metricName.add("meter").add(meterRuleName);
        return metricName.toString();
    }

    public static <T> Stream<T> log(Try<T> t, String debugMessage) {
        return t
            .onSuccess(i -> log.debug(debugMessage + " :{}", i))
            .onFailure(e -> log.debug(debugMessage + " failed", e))
            .toJavaStream();
    }
}
