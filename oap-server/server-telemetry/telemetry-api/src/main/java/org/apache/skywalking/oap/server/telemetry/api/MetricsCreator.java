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

package org.apache.skywalking.oap.server.telemetry.api;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Open API to telemetry module, allow to create metrics instance with different type. Types inherits from prometheus
 * project, and plan to move to openmetrics APIs after it is ready.
 */
public interface MetricsCreator extends Service {

    String HEALTH_METRIC_PREFIX = "health_check_";
    /**
     * Create a counter type metrics instance.
     */
    CounterMetrics createCounter(String name, String tips, MetricsTag.Keys tagKeys, MetricsTag.Values tagValues);

    /**
     * Create a gauge type metrics instance.
     */
    GaugeMetrics createGauge(String name, String tips, MetricsTag.Keys tagKeys, MetricsTag.Values tagValues);

    /**
     * Create a Histogram type metrics instance.
     *
     * @param buckets Time bucket for duration.
     */
    HistogramMetrics createHistogramMetric(String name, String tips, MetricsTag.Keys tagKeys,
        MetricsTag.Values tagValues, double... buckets);

    /**
     * Create a Health Check gauge.
     */
    default HealthCheckMetrics createHealthCheckerGauge(String name, MetricsTag.Keys tagKeys, MetricsTag.Values tagValues) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "Require non-null or empty metric name");
        return new HealthCheckMetrics(createGauge(Strings.lenientFormat("%s%s", HEALTH_METRIC_PREFIX, name),
            Strings.lenientFormat("%s health check", name),
            tagKeys, tagValues));
    }

    /**
     * Find out whether it's a health check metric.
     */
    default boolean isHealthCheckerMetrics(String name) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "Require non-null or empty metric name");
        return name.startsWith(HEALTH_METRIC_PREFIX);
    }

    /**
     * Extract the raw module name
     */
    default String extractModuleName(String metricName) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(metricName), "Require non-null or empty metric name");
        return metricName.replace(HEALTH_METRIC_PREFIX, "");
    }
}
