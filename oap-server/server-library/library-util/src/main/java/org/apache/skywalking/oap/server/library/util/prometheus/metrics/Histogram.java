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

package org.apache.skywalking.oap.server.library.util.prometheus.metrics;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Getter
public class Histogram extends Metric {

    private final long sampleCount;
    private final double sampleSum;
    private final Map<Double, Long> buckets;

    @lombok.Builder
    public Histogram(String name, @Singular Map<String, String> labels, long sampleCount, double sampleSum,
        @Singular Map<Double, Long> buckets) {
        super(name, labels);
        getLabels().remove("le");
        this.sampleCount = sampleCount;
        this.sampleSum = sampleSum;
        this.buckets = buckets;
    }

    public static Histogram newInstance(String name) {
        return new Histogram(name, Collections.emptyMap(), 0L, 0L, Collections.emptyMap());
    }

    public static Histogram sum(Histogram a, Histogram b) {
        Map<String, String> l = Stream.concat(a.getLabels().entrySet().stream(), b.getLabels().entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (l1, l2) -> l1));
        Map<Double, Long> buckets = Stream.concat(a.getBuckets().entrySet().stream(), b.getBuckets().entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum, TreeMap::new));
        return new Histogram(a.getName(), l, Long.sum(a.sampleCount, b.sampleCount), Double.sum(a.sampleSum, b.sampleSum)
            , buckets);
    }
}
