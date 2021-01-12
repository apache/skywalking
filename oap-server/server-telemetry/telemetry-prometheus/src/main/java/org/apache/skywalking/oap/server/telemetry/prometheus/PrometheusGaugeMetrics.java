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
import java.util.Optional;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * Gauge metrics in Prometheus implementor.
 */
public class PrometheusGaugeMetrics extends BaseMetrics<Gauge, Gauge.Child> implements GaugeMetrics {
    public PrometheusGaugeMetrics(String name, String tips, MetricsTag.Keys labels, MetricsTag.Values values) {
        super(name, tips, labels, values);
    }

    @Override
    public void inc() {
        Gauge.Child metrics = this.getMetric();
        if (metrics != null) {
            metrics.inc();
        }
    }

    @Override
    public void inc(double value) {
        Gauge.Child metrics = this.getMetric();
        if (metrics != null) {
            metrics.inc(value);
        }
    }

    @Override
    public void dec() {
        Gauge.Child metrics = this.getMetric();
        if (metrics != null) {
            metrics.dec();
        }
    }

    @Override
    public void dec(double value) {
        Gauge.Child metrics = this.getMetric();
        if (metrics != null) {
            metrics.dec(value);
        }
    }

    @Override
    public void setValue(double value) {
        Gauge.Child metrics = this.getMetric();
        if (metrics != null) {
            metrics.set(value);
        }
    }

    @Override
    public double getValue() {
        return Optional.ofNullable(this.getMetric()).orElse(new Gauge.Child()).get();
    }

    @Override
    protected Gauge create(String[] labelNames) {
        return Gauge.build().name(name).help(tips).labelNames(labelNames).register();
    }
}
