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

package org.apache.skywalking.oap.server.telemetry.none;

import org.apache.skywalking.oap.server.telemetry.api.*;

/**
 * A no-op metric create, just create nut shell metric instance.
 *
 * @author wusheng
 */
public class MetricCreatorNoop implements MetricCreator {
    @Override
    public CounterMetric createCounter(String name, String tips, MetricTag.Keys tagKeys, MetricTag.Values tagValues) {
        return new CounterMetric() {
            @Override public void inc() {

            }

            @Override public void inc(double value) {

            }
        };
    }

    @Override
    public GaugeMetric createGauge(String name, String tips, MetricTag.Keys tagKeys, MetricTag.Values tagValues) {
        return new GaugeMetric() {
            @Override public void inc() {

            }

            @Override public void inc(double value) {

            }

            @Override public void dec() {

            }

            @Override public void dec(double value) {

            }

            @Override public void setValue(double value) {

            }
        };
    }

    @Override
    public HistogramMetric createHistogramMetric(String name, String tips, MetricTag.Keys tagKeys,
        MetricTag.Values tagValues, double... buckets) {
        return new HistogramMetric() {
            @Override public void observe(double value) {

            }
        };
    }
}
