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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * MeterSystem provides the API way to create {@link MetricsStreamProcessor} rather than manual analysis metrics or OAL
 * script.
 *
 * @since 8.0.0
 */
@Slf4j
public class MeterSystem implements Service {
    private static final String METER_CLASS_PACKAGE = "org.apache.skywalking.oap.server.core.analysis.meter.dynamic.";
    private ModuleManager manager;
    private ClassPool classPool;
    private Map<String, Class<? extends AcceptableValue>> functionRegister = new HashMap<>();
    /**
     * Host the dynamic meter prototype classes. These classes could be create dynamically through {@link
     * Object#clone()} in the runtime;
     */
    private Map<String, MeterDefinition> meterPrototypes = new HashMap<>();

    public MeterSystem(final ModuleManager manager) {
        this.manager = manager;
        classPool = ClassPool.getDefault();

        ClassPath classpath = null;
        try {
            classpath = ClassPath.from(MeterSystem.class.getClassLoader());
        } catch (IOException e) {
            throw new UnexpectedException("Load class path failure.");
        }
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        for (ClassPath.ClassInfo classInfo : classes) {
            Class<?> functionClass = classInfo.load();

            if (functionClass.isAnnotationPresent(MeterFunction.class)) {
                MeterFunction metricsFunction = functionClass.getAnnotation(MeterFunction.class);
                if (!AcceptableValue.class.isAssignableFrom(functionClass)) {
                    throw new IllegalArgumentException(
                        "Function " + functionClass.getCanonicalName() + " doesn't implement AcceptableValue.");
                }
                functionRegister.put(
                    metricsFunction.functionName(),
                    (Class<? extends AcceptableValue>) functionClass
                );
            }
        }
    }

    /**
     * Create streaming calculation of the given metrics name. This methods is synchronized due to heavy implementation
     * including creating dynamic class. Don't use this in concurrency runtime.
     *
     * @param metricsName  The name used as the storage eneity and in the query stage.
     * @param functionName The function provided through {@link MeterFunction}.
     * @throws IllegalArgumentException if the parameter can't match the expectation.
     * @throws UnexpectedException      if binary code manipulation fails or stream core failure.
     */
    public synchronized <T> void create(String metricsName,
        String functionName,
        ScopeType type) throws IllegalArgumentException {
        final Class<? extends AcceptableValue> meterFunction = functionRegister.get(functionName);

        if (meterFunction == null) {
            throw new IllegalArgumentException("Function " + functionName + " can't be found.");
        }
        Type acceptance = null;
        for (final Type genericInterface : meterFunction.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                if (parameterizedType.getRawType().getTypeName().equals(AcceptableValue.class.getName())) {
                    Type[] arguments = parameterizedType.getActualTypeArguments();
                    acceptance = arguments[0];
                    break;
                }
            }
        }
        try {
            create(metricsName, functionName, type, Class.forName(Objects.requireNonNull(acceptance).getTypeName()));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Create streaming calculation of the given metrics name. This methods is synchronized due to heavy implementation
     * including creating dynamic class. Don't use this in concurrency runtime.
     *
     * @param metricsName  The name used as the storage eneity and in the query stage.
     * @param functionName The function provided through {@link MeterFunction}.
     * @throws IllegalArgumentException if the parameter can't match the expectation.
     * @throws UnexpectedException      if binary code manipulation fails or stream core failure.
     */
    public synchronized <T> void create(String metricsName,
                                           String functionName,
                                           ScopeType type,
                                           Class<T> dataType) throws IllegalArgumentException {
        /**
         * Create a new meter class dynamically.
         */
        final Class<? extends AcceptableValue> meterFunction = functionRegister.get(functionName);

        if (meterFunction == null) {
            throw new IllegalArgumentException("Function " + functionName + " can't be found.");
        }

        boolean foundDataType = false;
        String acceptance = null;
        for (final Type genericInterface : meterFunction.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                if (parameterizedType.getRawType().getTypeName().equals(AcceptableValue.class.getName())) {
                    Type[] arguments = parameterizedType.getActualTypeArguments();
                    if (arguments[0].equals(dataType)) {
                        foundDataType = true;
                    } else {
                        acceptance = arguments[0].getTypeName();
                    }
                }
                if (foundDataType) {
                    break;
                }
            }
        }
        if (!foundDataType) {
            throw new IllegalArgumentException("Function " + functionName
                                                   + " requires <" + acceptance + "> in AcceptableValue"
                                                   + " but using " + dataType.getName() + " in the creation");
        }

        final CtClass parentClass;
        try {
            parentClass = classPool.get(meterFunction.getCanonicalName());
            if (!Metrics.class.isAssignableFrom(meterFunction)) {
                throw new IllegalArgumentException(
                    "Function " + functionName + " doesn't inherit from Metrics.");
            }
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("Function " + functionName + " can't be found by javaassist.");
        }
        final String className = formatName(metricsName);

        /**
         * Check whether the metrics class is already defined or not
         */
        try {
            CtClass existingMetric = classPool.get(METER_CLASS_PACKAGE + className);
            if (existingMetric.getSuperclass() != parentClass || type != meterPrototypes.get(metricsName).getScopeType()) {
                throw new IllegalArgumentException(metricsName + " has been defined, but calculate function or/are scope type is/are different.");
            }
            log.info("Metric {} is already defined, so skip the metric creation.", metricsName);
            return ;
        } catch (NotFoundException e) {
        }

        CtClass metricsClass = classPool.makeClass(METER_CLASS_PACKAGE + className, parentClass);

        /**
         * Create empty construct
         */
        try {
            CtConstructor defaultConstructor = CtNewConstructor.make(
                "public " + className + "() {}", metricsClass);
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
                    + "public org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue createNew() {"
                    + "    return new " + METER_CLASS_PACKAGE + className + "();"
                    + " }"
                , metricsClass));
        } catch (CannotCompileException e) {
            log.error("Can't generate createNew method for " + className + ".", e);
            throw new UnexpectedException(e.getMessage(), e);
        }

        Class targetClass;
        try {
            targetClass = metricsClass.toClass(MeterSystem.class.getClassLoader(), null);
            AcceptableValue prototype = (AcceptableValue) targetClass.newInstance();
            meterPrototypes.put(metricsName, new MeterDefinition(type, prototype, dataType));

            log.debug("Generate metrics class, " + metricsClass.getName());

            MetricsStreamProcessor.getInstance().create(
                manager,
                new StreamDefinition(
                    metricsName, type.getScopeId(), prototype.builder(), MetricsStreamProcessor.class),
                targetClass
            );
        } catch (CannotCompileException | IllegalAccessException | InstantiationException | StorageException e) {
            log.error("Can't compile/load/init " + className + ".", e);
            throw new UnexpectedException(e.getMessage(), e);
        }
    }

    /**
     * Create an {@link AcceptableValue} instance for streaming calculation. AcceptableValue instance is stateful,
     * shouldn't do {@link AcceptableValue#accept(MeterEntity, Object)} once it is pushed into {@link
     * #doStreamingCalculation(AcceptableValue)}.
     *
     * @param metricsName A defined metrics name. Use {@link #create(String, String, ScopeType, Class)} to define a new
     *                    one.
     * @param dataType    class type of the input of {@link AcceptableValue}
     * @return usable an {@link AcceptableValue} instance.
     */
    public <T> AcceptableValue<T> buildMetrics(String metricsName,
                                               Class<T> dataType) {
        MeterDefinition meterDefinition = meterPrototypes.get(metricsName);
        if (meterDefinition == null) {
            throw new IllegalArgumentException("Uncreated metrics " + metricsName);
        }
        if (!meterDefinition.getDataType().equals(dataType)) {
            throw new IllegalArgumentException(
                "Unmatched metrics data type, request for " + dataType.getName()
                    + ", but defined as " + meterDefinition.getDataType());
        }

        return meterDefinition.getMeterPrototype().createNew();
    }

    /**
     * Active the {@link MetricsStreamProcessor#in(Metrics)} for streaming calculation.
     *
     * @param acceptableValue should only be created through {@link #create(String, String, ScopeType, Class)}
     */
    public void doStreamingCalculation(AcceptableValue acceptableValue) {
        final long timeBucket = acceptableValue.getTimeBucket();
        if (timeBucket == 0L) {
            // Avoid no timestamp data, which could be harmful for the storage.
            acceptableValue.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        }
        MetricsStreamProcessor.getInstance().in((Metrics) acceptableValue);
    }

    private static String formatName(String metricsName) {
        return metricsName.toLowerCase();
    }

    @RequiredArgsConstructor
    @Getter
    private static class MeterDefinition {
        private final ScopeType scopeType;
        private final AcceptableValue meterPrototype;
        private final Class<?> dataType;
    }
}
