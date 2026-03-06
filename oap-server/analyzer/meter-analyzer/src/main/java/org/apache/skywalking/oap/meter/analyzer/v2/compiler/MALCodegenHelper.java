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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static utility methods and constants shared across MAL code generation classes.
 */
final class MALCodegenHelper {

    private MALCodegenHelper() {
    }

    // ---- Well-known enum types used in MAL expressions ----

    static final Map<String, String> ENUM_FQCN;

    // ---- Well-known helper classes used inside MAL closures ----

    static final Map<String, String> CLOSURE_CLASS_FQCN;

    static {
        ENUM_FQCN = new HashMap<>();
        ENUM_FQCN.put("Layer", "org.apache.skywalking.oap.server.core.analysis.Layer");
        ENUM_FQCN.put("DetectPoint",
            "org.apache.skywalking.oap.server.core.source.DetectPoint");
        ENUM_FQCN.put("K8sRetagType",
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.tagOpt.K8sRetagType");
        ENUM_FQCN.put("DownsamplingType",
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.DownsamplingType");

        CLOSURE_CLASS_FQCN = new HashMap<>();
        CLOSURE_CLASS_FQCN.put("ProcessRegistry",
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry");
    }

    // ---- Closure interface FQCNs ----

    static final String FOR_EACH_FUNCTION_TYPE =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
            + ".SampleFamilyFunctions$ForEachFunction";

    static final String PROPERTIES_EXTRACTOR_TYPE =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
            + ".SampleFamilyFunctions$PropertiesExtractor";

    static final String DECORATE_FUNCTION_TYPE =
        "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
            + ".SampleFamilyFunctions$DecorateFunction";

    static final String METER_ENTITY_FQCN =
        "org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity";

    static final String RUNTIME_HELPER_FQCN =
        "org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalRuntimeHelper";

    // ---- Method classification sets ----

    /**
     * Methods on SampleFamily that take String[] (varargs).
     * Javassist doesn't support varargs syntax, so multiple string args
     * must be wrapped in {@code new String[]{}}.
     */
    static final Set<String> VARARGS_STRING_METHODS = Set.of(
        "tagEqual", "tagNotEqual", "tagMatch", "tagNotMatch"
    );

    /**
     * Methods on SampleFamily whose first argument is a primitive {@code double}.
     * Javassist cannot auto-unbox {@code Double} to {@code double}, so numeric
     * arguments to these methods must be emitted as raw double literals.
     */
    static final Set<String> PRIMITIVE_DOUBLE_METHODS = Set.of(
        "valueEqual", "valueNotEqual", "valueGreater",
        "valueGreaterEqual", "valueLess", "valueLessEqual"
    );

    // ---- Static utility methods ----

    static String sanitizeName(final String name) {
        final StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            sb.append(i == 0
                ? (Character.isJavaIdentifierStart(c) ? c : '_')
                : (Character.isJavaIdentifierPart(c) ? c : '_'));
        }
        return sb.length() == 0 ? "Generated" : sb.toString();
    }

    static String escapeJava(final String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static String opMethodName(final MALExpressionModel.ArithmeticOp op) {
        switch (op) {
            case ADD:
                return "plus";
            case SUB:
                return "minus";
            case MUL:
                return "multiply";
            case DIV:
                return "div";
            default:
                throw new IllegalArgumentException("Unknown op: " + op);
        }
    }

    static String comparisonOperator(final MALExpressionModel.CompareOp op) {
        switch (op) {
            case GT:
                return " > ";
            case LT:
                return " < ";
            case GTE:
                return " >= ";
            case LTE:
                return " <= ";
            default:
                return " == ";
        }
    }

    static boolean isDownsamplingType(final String name) {
        return "AVG".equals(name) || "SUM".equals(name) || "LATEST".equals(name)
            || "SUM_PER_MIN".equals(name) || "MAX".equals(name) || "MIN".equals(name);
    }

    /**
     * Extracts a constant string key from a closure expression
     * (used for bean setter naming).
     * Returns the key string if the expression is a string literal,
     * or null otherwise.
     */
    static String extractConstantKey(final MALExpressionModel.ClosureExpr expr) {
        if (expr instanceof MALExpressionModel.ClosureStringLiteral) {
            return ((MALExpressionModel.ClosureStringLiteral) expr).getValue();
        }
        return null;
    }

    // ---- LambdaMetafactory wiring for closure methods ----

    /**
     * Closure type metadata for each functional interface used in MAL closures.
     * Used to create LambdaMetafactory-based wrappers from methods on the main class.
     */
    static final class ClosureTypeInfo {
        final Class<?> interfaceClass;
        final String samName;
        final MethodType samType;
        final MethodType instantiatedType;
        final MethodType methodType;

        ClosureTypeInfo(final Class<?> interfaceClass,
                        final String samName,
                        final MethodType samType,
                        final MethodType instantiatedType,
                        final MethodType methodType) {
            this.interfaceClass = interfaceClass;
            this.samName = samName;
            this.samType = samType;
            this.instantiatedType = instantiatedType;
            this.methodType = methodType;
        }
    }

    private static final Map<String, ClosureTypeInfo> CLOSURE_TYPE_INFO;

    static {
        CLOSURE_TYPE_INFO = new HashMap<>();

        // TagFunction extends Function<Map, Map>
        // SAM: apply(Object) → Object (erased), instantiated: apply(Map) → Map
        CLOSURE_TYPE_INFO.put(
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl"
                + ".SampleFamilyFunctions$TagFunction",
            new ClosureTypeInfo(
                org.apache.skywalking.oap.meter.analyzer.v2.dsl
                    .SampleFamilyFunctions.TagFunction.class,
                "apply",
                MethodType.methodType(Object.class, Object.class),
                MethodType.methodType(Map.class, Map.class),
                MethodType.methodType(Map.class, Map.class)));

        // ForEachFunction — not generic, SAM = instantiated
        CLOSURE_TYPE_INFO.put(FOR_EACH_FUNCTION_TYPE,
            new ClosureTypeInfo(
                org.apache.skywalking.oap.meter.analyzer.v2.dsl
                    .SampleFamilyFunctions.ForEachFunction.class,
                "accept",
                MethodType.methodType(void.class, String.class, Map.class),
                MethodType.methodType(void.class, String.class, Map.class),
                MethodType.methodType(void.class, String.class, Map.class)));

        // PropertiesExtractor extends Function<Map, Map>
        CLOSURE_TYPE_INFO.put(PROPERTIES_EXTRACTOR_TYPE,
            new ClosureTypeInfo(
                org.apache.skywalking.oap.meter.analyzer.v2.dsl
                    .SampleFamilyFunctions.PropertiesExtractor.class,
                "apply",
                MethodType.methodType(Object.class, Object.class),
                MethodType.methodType(Map.class, Map.class),
                MethodType.methodType(Map.class, Map.class)));

        // DecorateFunction extends Consumer<MeterEntity>
        // SAM: accept(Object) → void (erased), instantiated: accept(Object) → void
        CLOSURE_TYPE_INFO.put(DECORATE_FUNCTION_TYPE,
            new ClosureTypeInfo(
                org.apache.skywalking.oap.meter.analyzer.v2.dsl
                    .SampleFamilyFunctions.DecorateFunction.class,
                "accept",
                MethodType.methodType(void.class, Object.class),
                MethodType.methodType(void.class, Object.class),
                MethodType.methodType(void.class, Object.class)));
    }

    static ClosureTypeInfo getClosureTypeInfo(final String interfaceType) {
        return CLOSURE_TYPE_INFO.get(interfaceType);
    }

    /**
     * Creates a functional interface instance from a method handle using
     * {@link LambdaMetafactory}. This is the same mechanism {@code javac}
     * uses to compile lambda expressions — the JIT can fully inline through
     * the callsite. No separate {@code .class} file is produced on disk.
     */
    /**
     * Creates a functional interface instance from a direct (unbound) method handle
     * using {@link LambdaMetafactory}, capturing the target instance.
     *
     * @param target a direct method handle (not bound via bindTo)
     * @param receiverClass the class of the instance to capture
     * @param receiver the instance to capture as the lambda's receiver
     */
    static Object createLambda(final MethodHandles.Lookup lookup,
                               final ClosureTypeInfo typeInfo,
                               final MethodHandle target,
                               final Class<?> receiverClass,
                               final Object receiver) throws Exception {
        try {
            // The factory type captures the receiver: (ReceiverClass) → InterfaceType
            final CallSite site = LambdaMetafactory.metafactory(
                lookup,
                typeInfo.samName,
                MethodType.methodType(typeInfo.interfaceClass, receiverClass),
                typeInfo.samType,
                target,
                typeInfo.instantiatedType);
            return site.getTarget().invoke(receiver);
        } catch (final Exception e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException("Failed to create lambda for " + typeInfo.samName, t);
        }
    }

    /**
     * Checks whether a closure expression returns {@code boolean} by inspecting
     * the last method call in the chain against {@link String} method signatures.
     * MAL closure params are always {@code Map<String, String>}, so chained
     * methods operate on {@code String}.
     */
    static boolean isBooleanExpression(final MALExpressionModel.ClosureExpr expr) {
        String lastMethodName = null;
        if (expr instanceof MALExpressionModel.ClosureMethodChain) {
            final List<MALExpressionModel.ClosureChainSegment> segs =
                ((MALExpressionModel.ClosureMethodChain) expr).getSegments();
            for (int i = segs.size() - 1; i >= 0; i--) {
                if (segs.get(i) instanceof MALExpressionModel.ClosureMethodCallSeg) {
                    lastMethodName =
                        ((MALExpressionModel.ClosureMethodCallSeg) segs.get(i))
                            .getName();
                    break;
                }
            }
        }
        if (lastMethodName == null) {
            return false;
        }
        for (final java.lang.reflect.Method m : String.class.getMethods()) {
            if (m.getName().equals(lastMethodName)
                    && m.getReturnType() == boolean.class) {
                return true;
            }
        }
        return false;
    }
}
