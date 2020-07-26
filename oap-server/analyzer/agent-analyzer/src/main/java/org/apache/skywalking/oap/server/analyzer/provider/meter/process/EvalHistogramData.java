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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import lombok.Data;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Support histogram
 */
@Data
public class EvalHistogramData extends EvalData<EvalData> {

    private Map<Double, Long> buckets;

    public static EvalHistogramData build(MeterHistogram histogram, MeterProcessor processor) {
        final EvalHistogramData evalHistogramData = new EvalHistogramData();
        evalHistogramData.name = histogram.getName();
        evalHistogramData.labels = histogram.getLabelsList().stream()
            .collect(Collectors.toMap(Label::getName, Label::getValue));
        evalHistogramData.processor = processor;
        evalHistogramData.buckets = histogram.getValuesList().stream()
            .collect(Collectors.toMap(MeterBucketValue::getBucket, MeterBucketValue::getCount, Long::sum, TreeMap::new));
        return evalHistogramData;
    }

    @Override
    public EvalHistogramData irate(String range) {
        return windowCalculate(Window.CalculateType.IRATE, range);
    }

    @Override
    public EvalHistogramData rate(String range) {
        return windowCalculate(Window.CalculateType.RATE, range);
    }

    @Override
    public EvalHistogramData increase(String range) {
        return windowCalculate(Window.CalculateType.INCREASE, range);
    }

    @Override
    EvalData combine(EvalData data) {
        final EvalHistogramData value = (EvalHistogramData) data;
        return copyTo(EvalHistogramData.class, instance ->
            instance.buckets = Stream.concat(this.buckets.entrySet().stream(), value.buckets.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum, TreeMap::new)));
    }

    private EvalHistogramData windowCalculate(Window.CalculateType calculateType, String range) {
        return copyTo(EvalHistogramData.class, instance -> instance.buckets = this.buckets.entrySet().stream()
            .map(e -> Tuple.of(e.getKey(), processor.window().get(this, e.getKey()).apply(calculateType, range).longValue()))
            .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2)));
    }
}
