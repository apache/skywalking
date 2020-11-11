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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Getter
public class Summary extends Metric {

    private long sampleCount;
    private double sampleSum;
    private final Map<Double, Double> quantiles;

    @lombok.Builder
    public Summary(String name, @Singular Map<String, String> labels, long sampleCount, double sampleSum,
        @Singular Map<Double, Double> quantiles, long timestamp) {
        super(name, labels, timestamp);
        getLabels().remove("quantile");
        this.sampleCount = sampleCount;
        this.sampleSum = sampleSum;
        this.quantiles = quantiles;
    }

    @Override public Metric sum(Metric m) {
        Summary s = (Summary) m;
        this.sampleCount =  this.sampleCount + s.getSampleCount();
        this.sampleSum = this.sampleSum + s.getSampleSum();
        return this;
    }

    @Override public Double value() {
        return this.getSampleSum() * 1000 / this.getSampleCount();
    }
}