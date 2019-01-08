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

import io.prometheus.client.Histogram;
import org.apache.skywalking.oap.server.telemetry.api.*;

/**
 * HistogramMetric metric in Prometheus implementor.
 *
 * @author wusheng
 */
public class PrometheusHistogramMetric extends HistogramMetric {
    private InnerMetricObject inner;
    private final double[] buckets;

    public PrometheusHistogramMetric(String name, String tips, MetricTag.Keys labels,
        MetricTag.Values values, double... buckets) {
        inner = new InnerMetricObject(name, tips, labels, values);
        this.buckets = buckets;
    }

    @Override public void observe(double value) {
        Histogram.Child metric = inner.getMetric();
        if (metric != null) {
            metric.observe(value);
        }
    }

    class InnerMetricObject extends BaseMetric<Histogram, Histogram.Child> {
        public InnerMetricObject(String name, String tips, MetricTag.Keys labels,
            MetricTag.Values values) {
            super(name, tips, labels, values);
        }

        @Override protected Histogram create(String[] labelNames) {
            Histogram.Builder builder = Histogram.build()
                .name(name).help(tips);
            if (builder != null && buckets.length > 0) {
                builder = builder.buckets(buckets);
            }
            return builder.labelNames(labelNames).register();
        }
    }
}
