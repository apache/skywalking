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

import io.prometheus.client.Counter;
import org.apache.skywalking.oap.server.telemetry.api.*;

/**
 * Counter metric in Prometheus implementor.
 *
 * @author wusheng
 */
public class PrometheusCounterMetric extends BaseMetric<Counter, Counter.Child> implements CounterMetric {

    public PrometheusCounterMetric(String name, String tips,
        MetricTag.Keys labels, MetricTag.Values values) {
        super(name, tips, labels, values);
    }

    @Override public void inc() {
        Counter.Child metric = this.getMetric();
        if (metric != null) {
            metric.inc();
        }
    }

    @Override public void inc(double value) {
        Counter.Child metric = this.getMetric();
        if (metric != null) {
            metric.inc(value);
        }
    }

    @Override protected Counter create(String[] labelNames) {
        return Counter.build()
            .name(name).help(tips).labelNames(labelNames).register();
    }
}
