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

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;

/**
 * Default implementation of MetricsFunctionRegistry.
 *
 * Scans classpath for classes annotated with @MetricsFunction and builds a registry
 * of available aggregation functions.
 */
@Slf4j
@SuppressWarnings("UnstableApiUsage")
public class DefaultMetricsFunctionRegistry implements MetricsFunctionRegistry {

    private final Map<String, MetricsFunctionDescriptor> registry = new HashMap<>();
    private volatile boolean initialized = false;

    /**
     * Create a new registry instance.
     *
     * @return new registry instance
     */
    public static DefaultMetricsFunctionRegistry create() {
        return new DefaultMetricsFunctionRegistry();
    }

    @Override
    public Optional<MetricsFunctionDescriptor> findFunction(String functionName) {
        ensureInitialized();
        return Optional.ofNullable(registry.get(functionName));
    }

    @Override
    public List<MetricsFunctionDescriptor> getAllFunctions() {
        ensureInitialized();
        return new ArrayList<>(registry.values());
    }

    @Override
    public void registerFunction(MetricsFunctionDescriptor descriptor) {
        if (registry.containsKey(descriptor.getName())) {
            throw new IllegalArgumentException(
                "Function already registered: " + descriptor.getName()
            );
        }
        registry.put(descriptor.getName(), descriptor);
    }

    /**
     * Ensure the registry is initialized by scanning classpath.
     */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    try {
                        scanAndRegister();
                        initialized = true;
                        log.info("Metrics function registry initialized with {} functions", registry.size());
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to initialize metrics function registry", e);
                    }
                }
            }
        }
    }

    /**
     * Scan classpath for @MetricsFunction annotated classes and register them.
     */
    private void scanAndRegister() throws IOException {
        ClassPath classpath = ClassPath.from(DefaultMetricsFunctionRegistry.class.getClassLoader());
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");

        for (ClassPath.ClassInfo classInfo : classes) {
            Class<?> aClass = classInfo.load();

            if (aClass.isAnnotationPresent(MetricsFunction.class)) {
                MetricsFunction metricsFunction = aClass.getAnnotation(MetricsFunction.class);
                String functionName = metricsFunction.functionName();

                // Find entrance method
                Method entranceMethod = findEntranceMethod(aClass);
                if (entranceMethod == null) {
                    log.warn("Metrics function {} has no @Entrance method, skipping", functionName);
                    continue;
                }

                // Create descriptor
                MetricsFunctionDescriptor descriptor = MetricsFunctionDescriptor.builder()
                    .name(functionName)
                    .metricsClass((Class<? extends Metrics>) aClass)
                    .entranceMethod(entranceMethod)
                    .description("Metrics function: " + functionName)
                    .build();

                registry.put(functionName, descriptor);
                log.debug("Registered metrics function: {} -> {}", functionName, aClass.getName());
            }
        }
    }

    /**
     * Find the method annotated with @Entrance in the metrics class.
     *
     * @param metricsClass the metrics class
     * @return entrance method, or null if not found
     */
    private Method findEntranceMethod(Class<?> metricsClass) {
        for (Method method : metricsClass.getMethods()) {
            if (method.isAnnotationPresent(Entrance.class)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Get all registered function names sorted alphabetically.
     *
     * @return sorted list of function names
     */
    public List<String> getFunctionNamesSorted() {
        return getFunctionNames().stream()
            .sorted()
            .collect(Collectors.toList());
    }
}
