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

    private long sampleCount;
    private double sampleSum;
    private Map<Double, Long> buckets;

    @lombok.Builder
    public Histogram(String name, @Singular Map<String, String> labels, long sampleCount, double sampleSum,
        @Singular Map<Double, Long> buckets, long timestamp) {
        super(name, labels, timestamp);
        getLabels().remove("le");
        this.sampleCount = sampleCount;
        this.sampleSum = sampleSum;
        this.buckets = buckets;
    }

    @Override public Metric sum(Metric m) {
        Histogram h = (Histogram) m;
        this.buckets = Stream.concat(getBuckets().entrySet().stream(), h.getBuckets().entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum, TreeMap::new));
        this.sampleSum = this.sampleSum + h.sampleSum;
        this.sampleCount = this.sampleCount + h.sampleCount;
        return this;
    }

    @Override public Double value() {
        return this.getSampleSum() * 1000 / this.getSampleCount();
    }
}
