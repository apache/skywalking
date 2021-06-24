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

import com.google.common.collect.ImmutableMap;
import io.vavr.Function2;
import io.vavr.Tuple2;
import java.time.Duration;
import java.util.function.Function;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.oap.meter.analyzer.dsl.counter.CounterWindow;

/**
 * Sample represents the metric data point in a range of time.
 */
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
@Getter
public class Sample {
    final String name;
    final ImmutableMap<String, String> labels;
    final double value;
    final long timestamp;

    Sample newValue(Function<Double, Double> transform) {
        return toBuilder().value(transform.apply(value)).build();
    }

    Sample increase(String range, Function2<Double, Long, Double> transform) {
        Tuple2<Long, Double> i = CounterWindow.INSTANCE.increase(name, labels, value, Duration.parse(range).toMillis(), timestamp);
        double nv = transform.apply(i._2, i._1);
        return newValue(ignored -> nv);
    }

    Sample increase(Function2<Double, Long, Double> transform) {
        Tuple2<Long, Double> i = CounterWindow.INSTANCE.pop(name, labels, value, timestamp);
        double nv = transform.apply(i._2, i._1);
        return newValue(ignored -> nv);
    }
}
