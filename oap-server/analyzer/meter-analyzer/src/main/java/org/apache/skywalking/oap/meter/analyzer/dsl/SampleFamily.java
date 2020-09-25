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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import io.vavr.Function2;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public class SampleFamily {
    public static final SampleFamily EMPTY = new SampleFamily(new Sample[0]);

    public static SampleFamily build(Sample... samples) {
        Preconditions.checkNotNull(samples);
        Preconditions.checkArgument(samples.length > 0);
        return new SampleFamily(samples);
    }

    private final Sample[] samples;

    /**
     * Following operations are used in DSL
     */

    /* tag filter operations*/
    public SampleFamily tagEqual(String... labels) {
        return match(labels, String::equals);
    }

    public SampleFamily tagNotEqual(String[] labels) {
        return match(labels, (sv, lv) -> !sv.equals(lv));
    }

    public SampleFamily tagMatch(String[] labels) {
        return match(labels, (sv, lv) -> lv.matches(sv));
    }

    public SampleFamily tagNotMatch(String[] labels) {
        return match(labels, (sv, lv) -> !lv.matches(sv));
    }

    /* Operator overloading*/
    public SampleFamily plus(Number number) {
        return newValue(v -> v + number.doubleValue());
    }

    public SampleFamily minus(Number number) {
        return newValue(v -> v - number.doubleValue());
    }

    public SampleFamily multiply(Number number) {
        return newValue(v -> v * number.doubleValue());
    }

    public SampleFamily div(Number number) {
        return newValue(v -> v / number.doubleValue());
    }

    public SampleFamily negative() {
        return newValue(v -> -v);
    }

    public SampleFamily plus(SampleFamily another) {
        if (this == EMPTY && another == EMPTY) {
            return SampleFamily.EMPTY;
        }
        if (this == EMPTY) {
            return another;
        }
        if (another == EMPTY) {
            return this;
        }
        return newValue(another, Double::sum);
    }

    public SampleFamily minus(SampleFamily another) {
        if (this == EMPTY && another == EMPTY) {
            return SampleFamily.EMPTY;
        }
        if (this == EMPTY) {
            return another.negative();
        }
        if (another == EMPTY) {
            return this;
        }
        return newValue(another, (a, b) -> a - b);
    }

    public SampleFamily multiply(SampleFamily another) {
        if (this == EMPTY || another == EMPTY) {
            return SampleFamily.EMPTY;
        }
        return newValue(another, (a, b) -> a * b);
    }

    public SampleFamily div(SampleFamily another) {
        if (this == EMPTY) {
            return SampleFamily.EMPTY;
        }
        if (another == EMPTY) {
            return div(0.0);
        }
        return newValue(another, (a, b) -> a / b);
    }

    /* Aggregation operators */
    public SampleFamily sum(List<String> by) {
        if (this == EMPTY) {
            return EMPTY;
        }
        if (by == null) {
            double result = Arrays.stream(samples).mapToDouble(s -> s.value).reduce(Double::sum).orElse(0.0D);
            return SampleFamily.build(newSample(ImmutableMap.of(), samples[0].timestamp, result));
        }
        return SampleFamily.build(Arrays.stream(samples)
            .map(sample -> Tuple.of(by.stream()
                .collect(ImmutableMap
                    .toImmutableMap(labelKey -> labelKey, labelKey -> sample.labels.getOrDefault(labelKey, ""))), sample))
            .collect(groupingBy(Tuple2::_1, mapping(Tuple2::_2, toList())))
            .entrySet().stream()
            .map(entry -> newSample(entry.getKey(), entry.getValue().get(0).timestamp, entry.getValue().stream()
                .mapToDouble(s -> s.value).reduce(Double::sum).orElse(0.0D)))
            .toArray(Sample[]::new));
    }

    /* Function */
    public SampleFamily increase(String range) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(range));
        return SampleFamily.build(Arrays.stream(samples).map(sample -> sample
            .increase(range, (lowerBoundValue, lowerBoundTime) ->
                sample.value - lowerBoundValue))
            .toArray(Sample[]::new));
    }

    public SampleFamily rate(String range) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(range));
        return SampleFamily.build(Arrays.stream(samples).map(sample -> sample
            .increase(range, (lowerBoundValue, lowerBoundTime) ->
                sample.timestamp - lowerBoundTime < 1L ? 0.0D
                    : (sample.value - lowerBoundValue) / ((sample.timestamp - lowerBoundTime) / 1000)))
            .toArray(Sample[]::new));
    }

    public SampleFamily irate() {
        return SampleFamily.build(Arrays.stream(samples).map(sample -> sample
            .increase("PT1S", (lowerBoundValue, lowerBoundTime) ->
                sample.timestamp - lowerBoundTime < 1L ? 0.0D
                    : (sample.value - lowerBoundValue) / ((sample.timestamp - lowerBoundTime) / 1000)))
            .toArray(Sample[]::new));
    }

    @SuppressWarnings(value = "unchecked")
    public SampleFamily tag(Closure<?> cl) {
        return SampleFamily.build(Arrays.stream(samples).map(sample -> {
            Object delegate = new Object();
            Closure<?> c = cl.rehydrate(delegate, sample, delegate);
            Map<String, String> arg = Maps.newHashMap(sample.labels);
            Object r = c.call(arg);
            return newSample(ImmutableMap.copyOf(Optional.ofNullable((r instanceof Map) ? (Map<String, String>) r : null)
                .orElse(arg)), sample.timestamp, sample.value);
        }).toArray(Sample[]::new));
    }

    public SampleFamily histogram(String le, TimeUnit unit) {
        return this;
    }

    public SampleFamily histogram_percentile(int[] range) {
        return this;
    }

    private SampleFamily match(String[] labels, Function2<String, String, Boolean> op) {
        Preconditions.checkArgument(labels.length % 2 == 0);
        Map<String, String> ll = new HashMap<>(labels.length / 2);
        for (int i = 0; i < labels.length; i += 2) {
            ll.put(labels[i], labels[i + 1]);
        }
        Stream<Sample> ss = Arrays.stream(samples).filter(sample ->
            ll.entrySet().stream().allMatch(entry ->
                sample.labels.containsKey(entry.getKey()) && op.apply(sample.labels.get(entry.getKey()), entry.getValue())));
        Sample[] sArr = ss.toArray(Sample[]::new);
        if (sArr.length < 1) {
            return SampleFamily.EMPTY;
        }
        return SampleFamily.build(sArr);
    }

    private SampleFamily newValue(Function<Double, Double> transform) {
        Sample[] ss = new Sample[samples.length];
        for (int i = 0; i < ss.length; i++) {
            ss[i] = samples[i].newValue(transform);
        }
        return SampleFamily.build(ss);
    }

    private SampleFamily newValue(SampleFamily another, Function2<Double, Double, Double> transform) {
        Sample[] ss = Arrays.stream(samples)
            .flatMap(cs -> io.vavr.collection.Stream.of(another.samples)
                .find(as -> cs.labels.equals(as.labels))
                .map(as -> newSample(cs.labels, cs.timestamp, transform.apply(cs.value, as.value)))
                .toJavaStream())
            .toArray(Sample[]::new);
        return ss.length > 0 ? SampleFamily.build(ss) : EMPTY;
    }

    private Sample newSample(ImmutableMap<String, String> labels, long timestamp, double newValue) {
        return Sample.builder()
            .value(newValue)
            .labels(labels)
            .timestamp(timestamp)
            .build();
    }
}
