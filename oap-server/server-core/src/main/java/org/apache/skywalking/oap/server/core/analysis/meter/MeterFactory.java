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

package org.apache.skywalking.oap.server.core.analysis.meter;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * MeterFactory provides the API way to create {@link MetricsStreamProcessor} rather than manual analysis metrics or OAL
 * script.
 *
 * @since 8.0.0
 */
@Slf4j
public class MeterFactory {
    private static final String METER_CLASS_PACKAGE = "org.apache.skywalking.oap.server.core.analysis.meter.dynamic.";
    private static ModuleManager MANAGER;
    private static ClassPool CLASS_POOL;
    private static Map<String, Class<? extends MeterFunction>> FUNCTION_REGISTER = new HashMap<>();
    /**
     * Host the dynamic meter prototype classes. These classes could be create dynamically through {@link
     * Object#clone()} in the runtime;
     */
    private static Map<String, MeterDefinition> METER_PROTOTYPES = new HashMap<>();

    public static void init(final ModuleManager manager) throws IOException {
        MANAGER = manager;
        CLASS_POOL = ClassPool.getDefault();

        ClassPath classpath = ClassPath.from(MeterFactory.class.getClassLoader());
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        for (ClassPath.ClassInfo classInfo : classes) {
            Class<?> aClass = classInfo.load();

            if (aClass.isAnnotationPresent(MeterFunction.class)) {
                MeterFunction metricsFunction = aClass.getAnnotation(MeterFunction.class);
                FUNCTION_REGISTER.put(
                    metricsFunction.functionName(),
                    (Class<? extends MeterFunction>) aClass
                );
            }
        }
    }

    /**
     * Create streaming calculable {@link AcceptableValue}. This methods is synchronized due to heavy implementation
     * including creating dynamic class. Don't use this in concurrency runtime.
     *
     * @param metricsName  The name used as the storage eneity and in the query stage.
     * @param functionName The function provided through {@link MeterFunction}.
     * @return {@link AcceptableValue} to accept the value for further distributed calculation.
     */
    public synchronized AcceptableValue create(String metricsName, String functionName, ScopeType type) {
        MeterDefinition meterDefinition = METER_PROTOTYPES.get(metricsName);
        if (meterDefinition != null) {
            return meterDefinition.getMeterPrototype().createNew();
        } else {
            /**
             * Create a new meter class dynamically.
             */
            final Class<? extends MeterFunction> meterFunction = FUNCTION_REGISTER.get(functionName);
            if (meterFunction == null) {
                throw new IllegalArgumentException("Function " + functionName + "can't be found.");
            }
            final CtClass parentClass;
            try {
                parentClass = CLASS_POOL.get(meterFunction.getCanonicalName());
            } catch (NotFoundException e) {
                throw new IllegalArgumentException("Function " + functionName + "can't be found by javaassist.");
            }
            final String className = formatName(metricsName);
            CtClass metricsClass = CLASS_POOL.makeClass(className, parentClass);

            /**
             * Create empty construct
             */
            try {
                CtConstructor defaultConstructor = CtNewConstructor.make("public " + className + "() {}", metricsClass);
                metricsClass.addConstructor(defaultConstructor);
            } catch (CannotCompileException e) {
                log.error("Can't add empty constructor in " + className + ".", e);
                throw new UnexpectedException(e.getMessage(), e);
            }

            /**
             * Generate `AcceptableValue<T> createNew()` method.
             */
            try {
                metricsClass.addMethod(CtNewMethod.make(
                    ""
                        + "public AcceptableValue<T> createNew() {"
                        + "    return new " + className + "();"
                        + " }"
                    , metricsClass));
            } catch (CannotCompileException e) {
                log.error("Can't generate createNew method for " + className + ".", e);
                throw new UnexpectedException(e.getMessage(), e);
            }

            Class targetClass;
            try {
                targetClass = metricsClass.toClass(MeterFactory.class.getClassLoader(), null);
                AcceptableValue prototype = (AcceptableValue) targetClass.newInstance();
                METER_PROTOTYPES.put(metricsName, new MeterDefinition(type, prototype));

                log.debug("Generate metrics class, " + metricsClass.getName());

                MetricsStreamProcessor.getInstance().create(
                    MANAGER,
                    new StreamDefinition(metricsName, type.scopeId, prototype.builder(), MetricsStreamProcessor.class),
                    targetClass
                );
                return prototype;
            } catch (CannotCompileException | IllegalAccessException | InstantiationException e) {
                log.error("Can't compile/load/init " + className + ".", e);
                throw new UnexpectedException(e.getMessage(), e);
            }
        }
    }

    private String formatName(String metricsName) {
        return METER_CLASS_PACKAGE + metricsName.toLowerCase();
    }

    public enum ScopeType {
        SERVICE(DefaultScopeDefine.SERVICE),
        SERVICE_INSTANCE(DefaultScopeDefine.SERVICE_INSTANCE),
        ENDPOINT(DefaultScopeDefine.ENDPOINT);

        @Getter
        private final int scopeId;

        ScopeType(final int scopeId) {
            this.scopeId = scopeId;
        }
    }

    @RequiredArgsConstructor
    @Getter
    private class MeterDefinition {
        private final ScopeType scopeType;
        private final AcceptableValue meterPrototype;
    }
}
