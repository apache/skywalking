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

package org.apache.skywalking.oal.v2.registry;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Registry for metrics aggregation functions.
 *
 * This interface abstracts the discovery and lookup of metrics functions
 * annotated with @MetricsFunction. Implementations can use different strategies:
 * - Classpath scanning (default)
 * - Manual registration
 * - Configuration-based registration
 *
 * Example usage:
 * <pre>
 * MetricsFunctionRegistry registry = DefaultMetricsFunctionRegistry.create();
 *
 * Optional&lt;MetricsFunctionDescriptor&gt; longAvg = registry.findFunction("longAvg");
 * if (longAvg.isPresent()) {
 *     Class&lt;? extends Metrics&gt; metricsClass = longAvg.get().getMetricsClass();
 *     Method entranceMethod = longAvg.get().getEntranceMethod();
 *     // ... use for code generation
 * }
 * </pre>
 */
public interface MetricsFunctionRegistry {

    /**
     * Find a metrics function by name.
     *
     * @param functionName the function name (e.g., "longAvg", "count", "percentile2")
     * @return descriptor if found, empty otherwise
     */
    Optional<MetricsFunctionDescriptor> findFunction(String functionName);

    /**
     * Get all registered metrics functions.
     *
     * @return immutable list of all function descriptors
     */
    List<MetricsFunctionDescriptor> getAllFunctions();

    /**
     * Register a new metrics function.
     *
     * @param descriptor the function descriptor to register
     * @throws IllegalArgumentException if function name already exists
     */
    void registerFunction(MetricsFunctionDescriptor descriptor);

    /**
     * Check if a function exists.
     *
     * @param functionName the function name
     * @return true if registered, false otherwise
     */
    default boolean hasFunction(String functionName) {
        return findFunction(functionName).isPresent();
    }

    /**
     * Get all function names.
     *
     * @return list of function names
     */
    default List<String> getFunctionNames() {
        return getAllFunctions().stream()
            .map(MetricsFunctionDescriptor::getName)
            .collect(Collectors.toList());
    }
}
