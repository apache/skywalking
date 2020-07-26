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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.vavr.Function2;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent base window. Using on counter function, such as rate, irate, increase.
 */
public class Window {

    private static Map<String, Window> INSTANCE_WINDOW = new ConcurrentHashMap<>();
    private final Map<ID, Queue<Tuple2<Long, Double>>> windows = Maps.newHashMap();

    private Window() {
    }

    public static Window getWindow(String service, String serviceInstance) {
        return INSTANCE_WINDOW.computeIfAbsent(service + "_" + serviceInstance, k -> new Window());
    }

    public Function2<CalculateType, String, Double> get(EvalSingleData data) {
        ID id = new ID(data.getName(), ImmutableMap.copyOf(data.getLabels()));
        return (calculateType, range) -> operateCounter(id, data.getValue(), calculateType, range);
    }

    public Function2<CalculateType, String, Double> get(EvalHistogramData data, double bucket) {
        ID id = new ID(data.getName(), ImmutableMap.<String, String>builder()
            .putAll(data.getLabels()).put("_bucket", String.valueOf(bucket)).build());
        return (calculateType, range) -> operateCounter(id, (double) data.getBuckets().get(bucket), calculateType, range);
    }

    private Double operateCounter(ID id, Double sum, CalculateType calculateType, String range) {
        long now = System.currentTimeMillis();
        switch (calculateType) {
            case INCREASE:
                Tuple2<Long, Double> i = increase(sum, id, Duration.parse(range).toMillis());
                return sum - i._2;
            case RATE:
                i = increase(sum, id, Duration.parse(range).toMillis());
                double rateVal = (sum - i._2) / ((now - i._1) / 1000.0);
                return Objects.equals(rateVal, Double.NaN) ? 0d : rateVal;
            case IRATE:
                i = increase(sum, id, 0);
                double iRateVal = (sum - i._2) / ((now - i._1) / 1000.0);
                return Objects.equals(iRateVal, Double.NaN) ? 0d : iRateVal;
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

    @RequiredArgsConstructor
    @EqualsAndHashCode
    @ToString
    private static class ID {
        private final String name;
        private final ImmutableMap<String, String> labels;
    }

    public static enum CalculateType {
        INCREASE,
        RATE,
        IRATE
    }
}
