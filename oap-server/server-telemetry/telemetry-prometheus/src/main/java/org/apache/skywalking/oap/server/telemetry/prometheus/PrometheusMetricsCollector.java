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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.skywalking.oap.server.telemetry.api.MetricFamily;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;

public class PrometheusMetricsCollector implements MetricsCollector {
    @Override
    public Iterable<MetricFamily> collect() {
        Enumeration<Collector.MetricFamilySamples> mfs = CollectorRegistry.defaultRegistry.metricFamilySamples();
        List<MetricFamily> result = new ArrayList<>();
        while (mfs.hasMoreElements()) {
            Collector.MetricFamilySamples metricFamilySamples = mfs.nextElement();
            List<MetricFamily.Sample> samples = new ArrayList<>(metricFamilySamples.samples.size());
            MetricFamily m = new MetricFamily(
                metricFamilySamples.name, MetricFamily.Type.valueOf(metricFamilySamples.type
                                                                        .name()), metricFamilySamples.help, samples
            );
            result.add(m);
            for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
                samples.add(new MetricFamily.Sample(
                    sample.name, sample.labelNames, sample.labelValues, sample.value,
                    sample.timestampMs
                ));
            }
        }
        return result;
    }

    @Override
    public long heapMemoryUsage() {
        final AtomicLong heapMemoryUsed = new AtomicLong();
        this.find("jvm_memory_bytes_used")
            .ifPresent(metricFamily -> {
                metricFamily.samples.forEach(sample -> {
                    for (int i = 0; i < sample.labelNames.size(); i++) {
                        if (sample.labelNames.get(i).equals("area")
                            && sample.labelValues.get(i).equals("heap")) {
                            heapMemoryUsed.addAndGet((long) sample.value);
                        }
                    }
                });
            });
        return heapMemoryUsed.longValue();
    }

    @Override
    public long heapMemoryMax() {
        final AtomicLong heapMemoryMax = new AtomicLong();
        this.find("jvm_memory_bytes_max")
            .ifPresent(metricFamily -> {
                metricFamily.samples.forEach(sample -> {
                    for (int i = 0; i < sample.labelNames.size(); i++) {
                        if (sample.labelNames.get(i).equals("area")
                            && sample.labelValues.get(i).equals("heap")) {
                            heapMemoryMax.addAndGet((long) sample.value);
                        }
                    }
                });
            });
        return heapMemoryMax.longValue();
    }

    @Override
    public long directMemoryUsage() {
        final AtomicLong directMemoryUsed = new AtomicLong();
        this.find("jvm_buffer_pool_used_bytes")
            .ifPresent(metricFamily -> {
                metricFamily.samples.forEach(sample -> {
                    for (int i = 0; i < sample.labelNames.size(); i++) {
                        if (sample.labelNames.get(i).equals("pool")
                            && sample.labelValues.get(i).equals("direct")) {
                            directMemoryUsed.addAndGet((long) sample.value);
                        }
                    }
                });
            });
        return directMemoryUsed.longValue();
    }

    private Optional<MetricFamily> find(final String name) {
        for (final MetricFamily metricFamily : this.collect()) {
            if (metricFamily.name.equals(name)) {
                return Optional.of(metricFamily);
            }
        }
        return Optional.empty();
    }
}
