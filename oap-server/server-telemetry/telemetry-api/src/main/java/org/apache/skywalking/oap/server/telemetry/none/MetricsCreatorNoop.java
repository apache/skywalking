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

package org.apache.skywalking.oap.server.telemetry.none;

import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * A no-op metrics create, just create nut shell metrics instance.
 */
public class MetricsCreatorNoop implements MetricsCreator {
    @Override
    public CounterMetrics createCounter(String name, String tips, MetricsTag.Keys tagKeys,
        MetricsTag.Values tagValues) {
        return new CounterMetrics() {
            @Override
            public void inc() {

            }

            @Override
            public void inc(double value) {

            }
        };
    }

    @Override
    public GaugeMetrics createGauge(String name, String tips, MetricsTag.Keys tagKeys, MetricsTag.Values tagValues) {
        return new GaugeMetrics() {
            @Override
            public void inc() {

            }

            @Override
            public void inc(double value) {

            }

            @Override
            public void dec() {

            }

            @Override
            public void dec(double value) {

            }

            @Override
            public void setValue(double value) {

            }

            @Override
            public double getValue() {
                return 0;
            }
        };
    }

    @Override
    public HistogramMetrics createHistogramMetric(String name, String tips, MetricsTag.Keys tagKeys,
        MetricsTag.Values tagValues, double... buckets) {
        return new HistogramMetrics() {
            @Override
            public void observe(double value) {

            }
        };
    }
}
