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

/**
 * A gauge is a metrics that represents a single numerical value that can arbitrarily go up and down.
 */
public interface GaugeMetrics {
    /**
     * Increase 1 to gauge
     */
    void inc();

    /**
     * Increase the given value to the gauge
     */
    void inc(double value);

    /**
     * Decrease 1 to gauge
     */
    void dec();

    /**
     * Decrease the given value to the gauge
     */
    void dec(double value);

    /**
     * Set the given value to the gauge
     */
    void setValue(double value);

    /**
     * Get the current value of the gauge
     */
    double getValue();
}
