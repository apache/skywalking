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

import io.prometheus.client.Gauge;
import org.apache.skywalking.oap.server.telemetry.api.*;

/**
 * Gauge metric in Prometheus implementor.
 *
 * @author wusheng
 */
public class PrometheusGaugeMetric extends BaseMetric<Gauge, Gauge.Child> implements GaugeMetric {
    public PrometheusGaugeMetric(String name, String tips,
        MetricTag.Keys labels,
        MetricTag.Values values) {
        super(name, tips, labels, values);
    }

    @Override public void inc() {
        Gauge.Child metric = this.getMetric();
        if (metric != null) {
            metric.inc();
        }
    }

    @Override public void inc(double value) {
        Gauge.Child metric = this.getMetric();
        if (metric != null) {
            metric.inc(value);
        }
    }

    @Override public void dec() {
        Gauge.Child metric = this.getMetric();
        if (metric != null) {
            metric.dec();
        }
    }

    @Override public void dec(double value) {
        Gauge.Child metric = this.getMetric();
        if (metric != null) {
            metric.dec(value);
        }
    }

    @Override public void setValue(double value) {
        Gauge.Child metric = this.getMetric();
        if (metric != null) {
            metric.set(value);
        }
    }

    @Override protected Gauge create(String[] labelNames) {
        return Gauge.build()
            .name(name).help(tips).labelNames(labelNames).register();
    }
}
