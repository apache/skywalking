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

package org.apache.skywalking.oap.server.receiver.envoy.metrics.adapters;

import io.prometheus.client.Metrics;
import java.util.Map;

public class ListenerMetricsAdapter {

    public String adaptMetricsName(final Metrics.MetricFamily metricFamily) {
        return "envoy_listener_metrics";
    }

    public Map<String, String> adaptLabels(final Metrics.MetricFamily metricFamily, final Map<String, String> labels) {
        String metricsName = metricFamily.getName();
        labels.putIfAbsent("metrics_name", metricsName);

        return labels;
    }
}
