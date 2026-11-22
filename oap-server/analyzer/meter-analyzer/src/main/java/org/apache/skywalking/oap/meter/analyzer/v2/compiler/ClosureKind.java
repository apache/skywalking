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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyFunctions;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;

/**
 * The closures a MAL rule passes, each compiled into one method of a class implementing the kind's interface: the
 * interface, its method, the parameters the closure takes, and what the closure's value gives back.
 */
enum ClosureKind {
    /** {@code tag({ ... })}, and any other call taking a closure but those below. */
    TAG(SampleFamilyFunctions.TagFunction.class, "apply", 1, "_raw", Map.class, ReturnMode.TAG),
    /** {@code forEach([...], { element, tags -> ... })}. */
    FOR_EACH(SampleFamilyFunctions.ForEachFunction.class, "accept", 2, null, Map.class, ReturnMode.NOTHING),
    /** {@code instance(..., { tags -> [k: v] })}: the instance's properties. */
    PROPERTIES(SampleFamilyFunctions.PropertiesExtractor.class, "apply", 1, "_raw", Map.class, ReturnMode.PROPERTIES),
    /** {@code decorate({ me -> ... })}: the bean the closure reads and sets. */
    DECORATE(SampleFamilyFunctions.DecorateFunction.class, "accept", 1, "_arg", MeterEntity.class,
        ReturnMode.NOTHING),
    /** {@code filter({ tags -> ... })} in a rule's chain. */
    SAMPLE_FILTER(SampleFamilyFunctions.SampleFilter.class, "test", 1, "_raw", Map.class, ReturnMode.FILTER),
    /** A rule file's {@code filter:}, its own class, the labels its method's parameter. */
    RULE_FILTER(MalFilter.class, "test", 1, null, Map.class, ReturnMode.FILTER);

    /**
     * What a closure's value, or its {@code return}, gives back, as the kind's caller takes it.
     */
    enum ReturnMode {
        NOTHING, TAG, PROPERTIES, FILTER
    }

    final String interfaceType;
    final String samName;
    final int arity;
    /** The erased argument the method takes and binds the closure's parameter from; null when it takes it itself. */
    final String samArgument;
    /** The type of the closure's last parameter: the labels, or decorate's bean. */
    final Class<?> parameterType;
    final ReturnMode returnMode;

    ClosureKind(final Class<?> type, final String samName, final int arity, final String samArgument,
                final Class<?> parameterType, final ReturnMode returnMode) {
        this.interfaceType = type.getName();
        this.samName = samName;
        this.arity = arity;
        this.samArgument = samArgument;
        this.parameterType = parameterType;
        this.returnMode = returnMode;
    }

    /**
     * @return the kind of the closure a chain's method takes
     */
    static ClosureKind of(final String methodName) {
        switch (methodName) {
            case "forEach":
                return FOR_EACH;
            case "instance":
                return PROPERTIES;
            case "decorate":
                return DECORATE;
            case "filter":
                return SAMPLE_FILTER;
            default:
                return TAG;
        }
    }

    /**
     * @return the method's signature line, and the line binding the closure's parameter from the erased argument
     */
    String signature(final List<String> params) {
        final String last = params.get(params.size() - 1);
        final String parameter = parameterType == Map.class ? "java.util.Map" : parameterType.getName();
        final String returns = returnMode == ReturnMode.FILTER ? "boolean"
            : returnMode == ReturnMode.NOTHING ? "void" : "Object";
        if (samArgument != null) {
            return "public " + returns + " " + samName + "(Object " + samArgument + ") {\n"
                + "  " + parameter + " " + last + " = (" + parameter + ") " + samArgument + ";\n";
        }
        return "public " + returns + " " + samName + "(" + (arity == 2 ? "String " + params.get(0) + ", " : "")
            + parameter + " " + last + ") {\n";
    }

    /**
     * @return the method's LocalVariableTable entries after {@code this}: its arguments, then the bound parameter
     */
    String[][] localVariables(final List<String> params) {
        final String last = params.get(params.size() - 1);
        final String parameter = "L" + parameterType.getName().replace('.', '/') + ";";
        if (samArgument != null) {
            return new String[][] {{samArgument, "Ljava/lang/Object;"}, {last, parameter}};
        }
        return arity == 2 ? new String[][] {{params.get(0), "Ljava/lang/String;"}, {last, parameter}}
            : new String[][] {{last, parameter}};
    }
}
