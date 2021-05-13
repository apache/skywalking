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

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.oap.server.telemetry.api.MetricFamily;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;

public class PrometheusMetricsCollector implements MetricsCollector {
    @Override public Iterable<MetricFamily> collect() {
        Enumeration<Collector.MetricFamilySamples> mfs = CollectorRegistry.defaultRegistry.metricFamilySamples();
        List<MetricFamily> result = new LinkedList<>();
        while (mfs.hasMoreElements()) {
            Collector.MetricFamilySamples metricFamilySamples = mfs.nextElement();
            List<MetricFamily.Sample> samples = new ArrayList<>(metricFamilySamples.samples.size());
            MetricFamily m = new MetricFamily(metricFamilySamples.name, MetricFamily.Type.valueOf(metricFamilySamples.type
                .name()), metricFamilySamples.help, samples);
            result.add(m);
            for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
                samples.add(new MetricFamily.Sample(sample.name, sample.labelNames, sample.labelValues, sample.value, sample.timestampMs));
            }
        }
        return result;
    }
}
