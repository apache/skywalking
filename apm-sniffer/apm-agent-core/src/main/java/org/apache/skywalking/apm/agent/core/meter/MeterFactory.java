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

package org.apache.skywalking.apm.agent.core.meter;

import org.apache.skywalking.apm.agent.core.meter.builder.BaseBuilder;
import org.apache.skywalking.apm.agent.core.meter.builder.Counter;
import org.apache.skywalking.apm.agent.core.meter.builder.Gauge;
import org.apache.skywalking.apm.agent.core.meter.builder.Histogram;
import org.apache.skywalking.apm.agent.core.meter.builder.adapter.InternalCounterAdapter;
import org.apache.skywalking.apm.agent.core.meter.builder.adapter.InternalGaugeAdapter;
import org.apache.skywalking.apm.agent.core.meter.builder.adapter.InternalHistogramAdapter;

import java.util.function.Supplier;

/**
 * Help to create meter build, and use {@link BaseBuilder#build()} to build the meter
 */
public class MeterFactory {

    /**
     * Create a counter builder by name
     */
    public static Counter.Builder counter(String name) {
        return new InternalCounterAdapter.Builder(name);
    }

    /**
     * Create a gauge builder by name and getter
     */
    public static Gauge.Builder gauge(String name, Supplier<Double> supplier) {
        return new InternalGaugeAdapter.Builder(name, supplier);
    }

    /**
     * Create a histogram builder by name
     */
    public static Histogram.Builder histogram(String name) {
        return new InternalHistogramAdapter.Builder(name);
    }

}
