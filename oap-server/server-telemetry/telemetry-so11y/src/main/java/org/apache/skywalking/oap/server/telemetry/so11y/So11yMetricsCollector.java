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

package org.apache.skywalking.oap.server.telemetry.so11y;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import org.apache.skywalking.oap.server.telemetry.api.Metrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

/**
 * Implement MetricCollector to generate prometheus metrics.
 *
 * @author gaohongtao
 */
public class So11yMetricsCollector implements MetricsCollector {
    @Override
    public Iterable<Metrics> collect() {
        Enumeration<Collector.MetricFamilySamples> mfs = CollectorRegistry.defaultRegistry.metricFamilySamples();
        List<Metrics> result = new LinkedList<>();
        while (mfs.hasMoreElements()) {
            Collector.MetricFamilySamples metricFamilySamples = mfs.nextElement();
            List<Metrics.Sample> samples = new ArrayList<>(metricFamilySamples.samples.size());
            Metrics m = new Metrics(metricFamilySamples.name, Metrics.Type.valueOf(metricFamilySamples.type.name()),
                    metricFamilySamples.help, samples);
            result.add(m);
            for (Collector.MetricFamilySamples.Sample sample: metricFamilySamples.samples) {
                samples.add(new Metrics.Sample(sample.name, sample.labelNames, sample.labelValues, sample.value, sample.timestampMs));
            }
        }
        return result;
    }
}
