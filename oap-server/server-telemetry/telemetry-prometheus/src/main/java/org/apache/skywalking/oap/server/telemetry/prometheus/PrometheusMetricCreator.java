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

package org.apache.skywalking.oap.server.telemetry.prometheus;

import org.apache.skywalking.oap.server.telemetry.api.*;

/**
 * Create metric instance for Prometheus exporter.
 *
 * @author wusheng
 */
public class PrometheusMetricCreator implements MetricCreator {
    @Override
    public CounterMetric createCounter(String name, String tips, MetricTag.Keys tagKeys, MetricTag.Values tagValues) {
        return new PrometheusCounterMetric(name, tips, tagKeys, tagValues);
    }

    @Override
    public GaugeMetric createGauge(String name, String tips, MetricTag.Keys tagKeys, MetricTag.Values tagValues) {
        return new PrometheusGaugeMetric(name, tips, tagKeys, tagValues);
    }

    @Override
    public HistogramMetric createHistogramMetric(String name, String tips, MetricTag.Keys tagKeys,
        MetricTag.Values tagValues, double... buckets) {
        return new PrometheusHistogramMetric(name, tips, tagKeys, tagValues, buckets);
    }
}
