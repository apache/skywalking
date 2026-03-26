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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.spi.MALContextFunction;
import org.apache.skywalking.oap.meter.analyzer.v2.spi.MalFunctionExtension;

/**
 * Compile-time registry for MAL extension functions discovered via SPI.
 * Used by {@link org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator}
 * to validate and generate direct static method calls for {@code namespace::method()} syntax.
 *
 * <p>No runtime dispatch — the compiler generates direct calls like
 * {@code sf = com.example.MyExtension.myMethod(sf, arg1);}.
 */
@Slf4j
public final class MalExtensionRegistry {

    private static final Map<String, Map<String, ExtensionMethod>> REGISTRY =
        new ConcurrentHashMap<>();

    static {
        init();
    }

    private MalExtensionRegistry() {
    }

    public static void init() {
        REGISTRY.clear();
        for (final MalFunctionExtension ext :
                ServiceLoader.load(MalFunctionExtension.class)) {
            register(ext);
        }
    }

    private static void register(final MalFunctionExtension ext) {
        final String namespace = ext.name();
        if (namespace == null || namespace.isEmpty()) {
            log.warn("Skipping MalFunctionExtension with null/empty name: {}",
                     ext.getClass().getName());
            return;
        }
        final Map<String, ExtensionMethod> methods = new HashMap<>();
        for (final Method m : ext.getClass().getMethods()) {
            if (!m.isAnnotationPresent(MALContextFunction.class)) {
                continue;
            }
            if (!Modifier.isStatic(m.getModifiers())) {
                throw new IllegalArgumentException(
                    "@MALContextFunction " + ext.getClass().getSimpleName()
                        + "." + m.getName() + "() must be static");
            }
            final Class<?>[] paramTypes = m.getParameterTypes();
            if (paramTypes.length == 0
                    || !SampleFamily.class.isAssignableFrom(paramTypes[0])) {
                throw new IllegalArgumentException(
                    "@MALContextFunction " + ext.getClass().getSimpleName()
                        + "." + m.getName()
                        + "() first parameter must be SampleFamily");
            }
            if (!SampleFamily.class.isAssignableFrom(m.getReturnType())) {
                throw new IllegalArgumentException(
                    "@MALContextFunction " + ext.getClass().getSimpleName()
                        + "." + m.getName()
                        + "() return type must be SampleFamily");
            }
            final Class<?>[] extraParamTypes = new Class<?>[paramTypes.length - 1];
            System.arraycopy(paramTypes, 1, extraParamTypes, 0, extraParamTypes.length);
            // Validate List params use List<String> (the only List type MAL supports)
            final java.lang.reflect.Type[] genericTypes = m.getGenericParameterTypes();
            for (int i = 1; i < genericTypes.length; i++) {
                if (java.util.List.class.isAssignableFrom(extraParamTypes[i - 1])
                        && genericTypes[i] instanceof java.lang.reflect.ParameterizedType) {
                    final java.lang.reflect.Type elementType =
                        ((java.lang.reflect.ParameterizedType) genericTypes[i])
                            .getActualTypeArguments()[0];
                    if (!String.class.equals(elementType)) {
                        throw new IllegalArgumentException(
                            "@MALContextFunction " + ext.getClass().getSimpleName()
                                + "." + m.getName()
                                + "() List parameters must be List<String>, got "
                                + genericTypes[i]);
                    }
                }
            }
            final String methodName = m.getName();
            if (methods.containsKey(methodName)) {
                throw new IllegalArgumentException(
                    "Duplicate @MALContextFunction name '" + methodName
                        + "' in namespace '" + namespace
                        + "' from " + ext.getClass().getName());
            }
            final String fqcn = m.getDeclaringClass().getName();
            methods.put(methodName,
                        new ExtensionMethod(fqcn, methodName, extraParamTypes));
            log.info("Registered MAL extension function {}::{}() -> {}.{}()",
                     namespace, methodName, fqcn, methodName);
        }
        if (!methods.isEmpty()) {
            if (REGISTRY.containsKey(namespace)) {
                throw new IllegalArgumentException(
                    "Duplicate MAL extension namespace '" + namespace
                        + "' from " + ext.getClass().getName());
            }
            REGISTRY.put(namespace, methods);
        }
    }

    /**
     * Look up an extension method at compile time for validation and codegen.
     *
     * @return the extension method descriptor, or null if not found
     */
    public static ExtensionMethod lookup(final String namespace,
                                          final String methodName) {
        final Map<String, ExtensionMethod> methods = REGISTRY.get(namespace);
        if (methods == null) {
            return null;
        }
        return methods.get(methodName);
    }

    /**
     * Describes a static extension method for compile-time codegen.
     */
    @Getter
    public static final class ExtensionMethod {
        private final String declaringClass;
        private final String methodName;
        private final Class<?>[] extraParamTypes;

        ExtensionMethod(final String declaringClass, final String methodName,
                        final Class<?>[] extraParamTypes) {
            this.declaringClass = declaringClass;
            this.methodName = methodName;
            this.extraParamTypes = extraParamTypes;
        }
    }
}
