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

import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Open API to telemetry module, allow to create metric instance with different type. Types inherits from prometheus
 * project, and plan to move to openmetrics APIs after it is ready.
 *
 * @author wusheng
 */
public interface MetricCreator extends Service {
    /**
     * Create a counter type metric instance.
     *
     * @param name
     * @param tips
     * @param tagKeys
     * @return
     */
    CounterMetric createCounter(String name, String tips, MetricTag.Keys tagKeys, MetricTag.Values tagValues);

    /**
     * Create a gauge type metric instance.
     *
     * @param name
     * @param tips
     * @param tagKeys
     * @return
     */
    GaugeMetric createGauge(String name, String tips, MetricTag.Keys tagKeys, MetricTag.Values tagValues);

    /**
     * Create a Histogram type metric instance.
     *
     * @param name
     * @param tips
     * @param tagKeys
     * @param buckets Time bucket for duration.
     * @return
     */
    HistogramMetric createHistogramMetric(String name, String tips, MetricTag.Keys tagKeys, MetricTag.Values tagValues, double... buckets);
}
