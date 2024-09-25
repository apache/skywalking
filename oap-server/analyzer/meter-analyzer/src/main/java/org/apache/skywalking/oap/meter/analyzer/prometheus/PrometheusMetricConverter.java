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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder;
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
    private static final Pattern metricsNameEscapePattern = Pattern.compile("[/.]");

    private static final LoadingCache<String, String> escapedMetricsNameCache =
        CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .build(new CacheLoader<String, String>() {
                        @Override
                        public String load(final String name) {
                            return metricsNameEscapePattern.matcher(name).replaceAll("_");
                        }
                    });

    public static ImmutableMap<String, SampleFamily> convertPromMetricToSampleFamily(Stream<Metric> metricStream) {
        return metricStream
            .peek(metric -> log.debug("Prom metric to be convert to SampleFamily: {}", metric))
            .flatMap(PrometheusMetricConverter::convertMetric)
            .filter(t -> t != NIL && t._2.samples.length > 0)
            .peek(t -> log.debug("SampleFamily: {}", t))
            .collect(toImmutableMap(Tuple2::_1, Tuple2::_2, (a, b) -> {
                log.debug("merge {} {}", a, b);
                Sample[] m = new Sample[a.samples.length + b.samples.length];
                System.arraycopy(a.samples, 0, m, 0, a.samples.length);
                System.arraycopy(b.samples, 0, m, a.samples.length, b.samples.length);
                return SampleFamilyBuilder.newBuilder(m).build();
            }));
    }

    private static Stream<Tuple2<String, SampleFamily>> convertMetric(Metric metric) {
        return Match(metric).of(
            Case($(instanceOf(Histogram.class)), t -> Stream.of(
                Tuple.of(escapedName(metric.getName() + "_count"), SampleFamilyBuilder.newBuilder(Sample.builder().name(escapedName(metric.getName() + "_count"))
                    .timestamp(metric.getTimestamp()).labels(ImmutableMap.copyOf(metric.getLabels())).value(((Histogram) metric).getSampleCount()).build()).build()),
                Tuple.of(escapedName(metric.getName() + "_sum"), SampleFamilyBuilder.newBuilder(Sample.builder().name(escapedName(metric.getName() + "_sum"))
                    .timestamp(metric.getTimestamp()).labels(ImmutableMap.copyOf(metric.getLabels())).value(((Histogram) metric).getSampleSum()).build()).build()),
                convertToSample(metric).orElse(NIL))),
            Case($(instanceOf(Summary.class)), t -> Stream.of(
                Tuple.of(escapedName(metric.getName() + "_count"), SampleFamilyBuilder.newBuilder(Sample.builder().name(escapedName(metric.getName() + "_count"))
                    .timestamp(metric.getTimestamp()).labels(ImmutableMap.copyOf(metric.getLabels())).value(((Summary) metric).getSampleCount()).build()).build()),
                Tuple.of(escapedName(metric.getName() + "_sum"), SampleFamilyBuilder.newBuilder(Sample.builder().name(escapedName(metric.getName() + "_sum"))
                    .timestamp(metric.getTimestamp()).labels(ImmutableMap.copyOf(metric.getLabels())).value(((Summary) metric).getSampleSum()).build()).build()),
                convertToSample(metric).orElse(NIL))),
            Case($(), t -> Stream.of(convertToSample(metric).orElse(NIL)))
        );
    }

    private static Optional<Tuple2<String, SampleFamily>> convertToSample(Metric metric) {
        Sample[] ss = Match(metric).of(
            Case($(instanceOf(Counter.class)), t -> Collections.singletonList(Sample.builder()
                .name(escapedName(t.getName()))
                .labels(ImmutableMap.copyOf(t.getLabels()))
                .timestamp(t.getTimestamp())
                .value(t.getValue())
                .build())),
            Case($(instanceOf(Gauge.class)), t -> Collections.singletonList(Sample.builder()
                .name(escapedName(t.getName()))
                .labels(ImmutableMap.copyOf(t.getLabels()))
                .timestamp(t.getTimestamp())
                .value(t.getValue())
                .build())),
            Case($(instanceOf(Histogram.class)), t -> t.getBuckets()
                .entrySet().stream()
                .map(b -> Sample.builder()
                    .name(escapedName(t.getName()))
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
                        .name(escapedName(t.getName()))
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
        return Optional.of(Tuple.of(escapedName(metric.getName()), SampleFamilyBuilder.newBuilder(ss).build()));
    }

    // Returns the escaped name of the given one, with "." and "/" replaced by "_"
    protected static String escapedName(final String name) {
        try {
            return escapedMetricsNameCache.get(name);
        } catch (ExecutionException e) {
            log.error("Failed to get escaped metrics name from cache", e);
            return metricsNameEscapePattern.matcher(name).replaceAll("_");
        }
    }
}
