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

package org.apache.skywalking.apm.toolkit.meter;

import java.util.function.Supplier;

public class MeterFactory {

    /**
     * Create a counter builder by name
     */
    public static Counter.Builder counter(String name) {
        return new Counter.Builder(name);
    }

    /**
     * Create a counter builder by meter id
     */
    public static Counter.Builder counter(MeterId meterId) {
        return new Counter.Builder(meterId);
    }

    /**
     * Create a gauge builder by name and getter
     */
    public static Gauge.Builder gauge(String name, Supplier<Double> getter) {
        return new Gauge.Builder(name, getter);
    }

    /**
     * Create a gauge builder by meter id and getter
     */
    public static Gauge.Builder gauge(MeterId meterId, Supplier<Double> getter) {
        return new Gauge.Builder(meterId, getter);
    }

    /**
     * Create a histogram builder by name
     */
    public static Histogram.Builder histogram(String name) {
        return new Histogram.Builder(name);
    }

    /**
     * Create a histogram builder by meterId
     */
    public static Histogram.Builder histogram(MeterId meterId) {
        return new Histogram.Builder(meterId);
    }

}
