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

package org.apache.skywalking.oap.server.fetcher.prometheus.provider.counter;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.vavr.Function2;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.skywalking.oap.server.fetcher.prometheus.provider.operation.MetricSource;

/**
 * Window stores a series of counter samples in order to calculate the increase
 * or instant rate of increase.
 */
@RequiredArgsConstructor
@ToString
@EqualsAndHashCode
public class Window {

    private final Map<ID, Queue<Tuple2<Long, Double>>> windows = Maps.newHashMap();

    public Function2<MetricSource, Double, Double> get(String name) {
        return get(name, Collections.emptyMap());
    }

    public Function2<MetricSource, Double, Double> get(String name, Map<String, String> labels) {
        ID id = new ID(name, ImmutableMap.copyOf(labels));
        return (source, sum) -> operateCounter(id, source, sum);
    }

    private Double operateCounter(ID id, MetricSource source, Double sum) {
        if (source.getCounterFunction() == null) {
            return sum;
        }
        long now = System.currentTimeMillis();
        switch (source.getCounterFunction()) {
            case INCREASE:
                Tuple2<Long, Double> i = increase(sum, id, Duration.parse(source.getRange()).toMillis());
                return sum - i._2;
            case RATE:
                i = increase(sum, id, Duration.parse(source.getRange()).toMillis());
                return (sum - i._2) / ((now - i._1) / 1000);
            case IRATE:
                i = increase(sum, id, 0);
                return (sum - i._2) / ((now - i._1) / 1000);
            default:
                return sum;
        }
    }

    private Tuple2<Long, Double> increase(Double value, ID id, long windowSize) {
        if (!windows.containsKey(id)) {
            windows.put(id, new LinkedList<>());
        }
        Queue<Tuple2<Long, Double>> window = windows.get(id);
        long now = System.currentTimeMillis();
        window.offer(Tuple.of(now, value));
        Tuple2<Long, Double> ps = window.element();
        if ((now - ps._1) >= windowSize) {
            window.remove();
        }
        return ps;
    }
}
