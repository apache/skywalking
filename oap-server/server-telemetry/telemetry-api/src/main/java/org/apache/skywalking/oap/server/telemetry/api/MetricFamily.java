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

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * MetricFamily define a metric and all its samples.
 */
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class MetricFamily {

    public final String name;
    public final Type type;
    public final String help;
    public final List<Sample> samples;

    public enum Type {
        COUNTER, GAUGE, SUMMARY, HISTOGRAM, UNTYPED,
    }

    /**
     * A single Sample, with a unique name and set of labels.
     */
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    public static class Sample {
        public final String name;
        public final List<String> labelNames;
        public final List<String> labelValues;  // Must have same length as labelNames.
        public final double value;
        public final Long timestampMs;  // It's an epoch format with milliseconds value included (this field is subject to change).

    }
}
