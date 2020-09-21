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
import groovy.lang.Closure;
import io.vavr.Function2;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public class SampleFamily {
    public static final SampleFamily EMPTY = new SampleFamily(new Sample[0]);

    public static SampleFamily build(Sample... samples) {
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

    public SampleFamily plus(SampleFamily number) {
        return this;
    }

    public SampleFamily minus(SampleFamily number) {
        return this;
    }

    public SampleFamily multiply(SampleFamily number) {
        return this;
    }

    public SampleFamily div(SampleFamily number) {
        return this;
    }

    /* Aggregation operators */
    @RequiredArgsConstructor
    public static class AggregationParameter {
        private final String[] by;
    }

    public SampleFamily sum(AggregationParameter parameter) {
        return this;
    }

    /* Function */
    public SampleFamily increase(String range) {
        return this;
    }

    public SampleFamily rate(String range) {
        return this;
    }

    public SampleFamily irate() {
        return this;
    }

    public SampleFamily tag(Closure cl) {
        return this;
    }

    @RequiredArgsConstructor
    public static class HistogramParameter {
        private final String le;
    }

    public  SampleFamily histogram(HistogramParameter parameter) {
       return this;
    }

    public SampleFamily histogram_quantile(int[] range) {
        return  this;
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
}
