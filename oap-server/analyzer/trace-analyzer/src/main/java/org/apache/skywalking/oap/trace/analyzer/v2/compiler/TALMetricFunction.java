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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.util.HashMap;
import java.util.Map;

/**
 * The functions a {@code metrics {}} block may name, each mapping to a {@code MeterSystem} function and the
 * value type that function accepts.
 *
 * <p>{@code cpm} is {@code sumPerMin} under the name the agent metrics use, because both compute
 * {@code total / getDurationInMinute()}; a TAL metric named like an agent metric therefore holds the same number
 * in the same unit.
 */
final class TALMetricFunction {
    /**
     * What the generated code has to build for the function to accept it.
     */
    enum Kind {
        LONG, BOOLEAN, APDEX, HISTOGRAM, PERCENTILE, LABELED
    }

    private static final Map<String, TALMetricFunction> REGISTRY = new HashMap<>();

    static {
        register("cpm", "sumPerMin", Kind.LONG, "java.lang.Long");
        register("sumPerMin", "sumPerMin", Kind.LONG, "java.lang.Long");
        register("sum", "sum", Kind.LONG, "java.lang.Long");
        register("avg", "avg", Kind.LONG, "java.lang.Long");
        register("max", "max", Kind.LONG, "java.lang.Long");
        register("min", "min", Kind.LONG, "java.lang.Long");
        register("latest", "latest", Kind.LONG, "java.lang.Long");
        register("percent", "percent", Kind.BOOLEAN, "java.lang.Boolean");
        register("apdex", "apdex", Kind.APDEX,
            "org.apache.skywalking.oap.server.core.analysis.meter.function.ApdexArgument");
        register("histogram", "sumHistogram", Kind.HISTOGRAM,
            "org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues");
        register("percentile", "sumHistogramPercentile", Kind.PERCENTILE,
            "org.apache.skywalking.oap.server.core.analysis.meter.function.PercentileArgument");
        register("sumLabeled", "sumLabeled", Kind.LABELED,
            "org.apache.skywalking.oap.server.core.analysis.metrics.DataTable");
        register("avgLabeled", "avgLabeled", Kind.LABELED,
            "org.apache.skywalking.oap.server.core.analysis.metrics.DataTable");
        register("maxLabeled", "maxLabeled", Kind.LABELED,
            "org.apache.skywalking.oap.server.core.analysis.metrics.DataTable");
        register("minLabeled", "minLabeled", Kind.LABELED,
            "org.apache.skywalking.oap.server.core.analysis.metrics.DataTable");
        register("latestLabeled", "latestLabeled", Kind.LABELED,
            "org.apache.skywalking.oap.server.core.analysis.metrics.DataTable");
        register("sumPerMinLabeled", "sumPerMinLabeled", Kind.LABELED,
            "org.apache.skywalking.oap.server.core.analysis.metrics.DataTable");
    }

    final String meterFunction;
    final Kind kind;
    final String valueClass;

    private TALMetricFunction(final String meterFunction, final Kind kind, final String valueClass) {
        this.meterFunction = meterFunction;
        this.kind = kind;
        this.valueClass = valueClass;
    }

    private static void register(final String name, final String meterFunction,
                                 final Kind kind, final String valueClass) {
        REGISTRY.put(name, new TALMetricFunction(meterFunction, kind, valueClass));
    }

    /**
     * @param name the function a rule named
     * @return its definition
     * @throws IllegalArgumentException when no function goes by that name
     */
    static TALMetricFunction of(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("a metrics block needs a function");
        }
        final TALMetricFunction function = REGISTRY.get(name);
        if (function == null) {
            throw new IllegalArgumentException(
                "unknown metrics function '" + name + "'; known functions are " + REGISTRY.keySet());
        }
        return function;
    }
}
