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

import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALDefStatement;

import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFieldSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFunctionArg;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALIndexSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALMethodSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessSegment;

import java.util.List;
import java.lang.reflect.Method;

/**
 * Code generation for TAL {@code def} local variable declarations and
 * typed method-chain resolution on def variables.
 *
 * <p>The {@code def} keyword declares a local variable in the extractor
 * block. The type is inferred from the initializer expression, and
 * subsequent method chains are resolved via compile-time reflection.
 *
 * <p>TAL example:
 * <pre>{@code
 * extractor {
 *   // def holding an entity, used as the scope of several metrics blocks
 *   def svc = service(meta.service, meta.layer)
 *
 *   // def over attributes, reassigned in a branch: the declaration is hoisted, so this
 *   // assigns to the same variable rather than shadowing it
 *   def peer = attr("peer.service")
 *   if (!peer) {
 *     def peer = attr("server.address", "net.peer.name")
 *   }
 *
 *   // def with an explicit type cast
 *   def status = attr("http.response.status_code") as Integer
 * }
 * }</pre>
 *
 * <p>Generated code declares Java locals with the {@code _def_} prefix
 * (e.g. {@code _def_svc}, {@code _def_peer}) at the top of the method and emits
 * assignments at the point where {@code def} appears in the DSL.
 *
 * <p>Sibling implementation: {@code LALDefCodegen} in {@code log-analyzer}. Separate copies
 * by design; see {@link TALValueCodegen}.
 */
public final class TALDefCodegen {

    private TALDefCodegen() {
        // utility class
    }

    // ==================== Def statement codegen ====================

    /**
     * Generates a {@code def} variable declaration and assignment.
     *
     * <p>TAL: {@code def payload = toJson(attr("http.request.body"))}
     *
     * <p>Generated (declaration at method top):
     * <pre>{@code
     * com.google.gson.JsonObject _def_jwt;
     * }</pre>
     * <p>Generated (assignment at def site):
     * <pre>{@code
     * _def_jwt = h.toJsonObject(h.mapVal("metadata", "filterMetadataMap", "key"));
     * }</pre>
     *
     * <p>Type inference: built-in functions ({@code toJson} &rarr;
     * {@code JsonObject}, {@code toJsonArray} &rarr; {@code JsonArray}) have
     * known return types. General expressions infer the type from
     * {@code genCtx.lastResolvedType} after codegen. An explicit
     * {@code as Type} cast overrides the inferred type.
     */
    public static void generateDefStatement(final StringBuilder sb,
                                      final TALDefStatement def,
                                      final TALGenCtx genCtx) {
        final TALValueAccess init = def.getInitializer();
        final String varName = def.getVarName();
        final String javaVar = "_def_" + varName;
        final TALLocalVar previous = genCtx.localVars.get(varName);

        // Determine type and generate initializer expression
        Class<?> resolvedType;
        final StringBuilder initExpr = new StringBuilder();

        if (init.getFunctionCallName() != null
                && TALSpanRootResolver.ENTITY_CONSTRUCTORS.contains(init.getFunctionCallName())) {
            // An entity held in a def has a compile-time scope type, so `scope svc` always has one shape and
            // reassigning the variable with a different constructor is caught here rather than at runtime.
            final String scopeType = TALBlockCodegen.appendEntityConstructor(initExpr, init, genCtx);
            final String previousScope = genCtx.entityScopeTypes.put(varName, scopeType);
            if (previousScope != null && !previousScope.equals(scopeType)) {
                throw new IllegalArgumentException(
                    "def " + varName + " was an entity of scope " + previousScope
                        + " and is reassigned as " + scopeType);
            }
            resolvedType = MeterEntity.class;
        } else if (init.getFunctionCallName() != null
                && TALCodegenHelper.BUILTIN_FUNCTIONS.containsKey(init.getFunctionCallName())) {
            // Built-in function: toJson(...), toJsonArray(...)
            final String funcName = init.getFunctionCallName();
            final int argCount = init.getFunctionCallArgs().size();
            if (argCount != 1) {
                throw new IllegalArgumentException(
                    funcName + "() requires exactly 1 argument, got " + argCount);
            }
            final Object[] info = TALCodegenHelper.BUILTIN_FUNCTIONS.get(funcName);
            final String helperMethod = (String) info[0];
            resolvedType = (Class<?>) info[1];

            initExpr.append(helperMethod).append("(");
            TALValueCodegen.generateValueAccess(initExpr,
                init.getFunctionCallArgs().get(0).getValue(), genCtx);
            initExpr.append(")");
        } else if (init.getFunctionCallName() != null
                && primitiveWrapper(genCtx.resolver.functionReturnType(init.getFunctionCallName())) != null) {
            // A built-in returning a primitive has to be boxed here: the local is declared as a wrapper, and
            // Javassist's compiler does not autobox on assignment, which the verifier rejects.
            final Class<?> wrapper = primitiveWrapper(
                genCtx.resolver.functionReturnType(init.getFunctionCallName()));
            initExpr.append(wrapper.getSimpleName()).append(".valueOf(");
            TALValueCodegen.generateValueAccess(initExpr, init, genCtx);
            initExpr.append(")");
            resolvedType = wrapper;
        } else {
            // General value access — type inferred from lastResolvedType
            TALValueCodegen.generateValueAccess(initExpr, init, genCtx);
            resolvedType = genCtx.lastResolvedType != null
                ? genCtx.lastResolvedType : Object.class;
            // Box primitive types for local variable declarations
            if (resolvedType.isPrimitive()) {
                final String boxName = TALCodegenHelper.boxTypeName(resolvedType);
                if (boxName != null) {
                    try {
                        resolvedType = Class.forName("java.lang." + boxName);
                    } catch (ClassNotFoundException ignored) {
                        // keep primitive
                    }
                }
            }
        }

        // Apply explicit type cast if specified (e.g., "as com.example.MyType")
        final String castType = def.getCastType();
        if (castType != null && !castType.isEmpty()) {
            // Resolve the cast type — primitive wrapper names are handled,
            // anything else is treated as a FQCN
            final Class<?> castClass = resolveDefCastType(castType);
            if (castClass != null) {
                resolvedType = castClass;
            }
        }

        // The first def of a name declares the variable, hoisted to the top of the method; a later def of the
        // same name assigns to it, so its value has to fit the declared type.
        if (previous == null) {
            genCtx.localVars.put(varName, new TALLocalVar(javaVar, resolvedType));
            // Initialized, so a def first assigned inside one branch can still be read after it: the variable is
            // null on the paths that did not assign it, where Javassist would otherwise reject the read.
            genCtx.localVarDecls.append("  ").append(resolvedType.getName())
                .append(" ").append(javaVar).append(" = null;\n");
            genCtx.localVarLvtVars.add(new String[]{
                javaVar, "L" + resolvedType.getName().replace('.', '/') + ";"
            });
        } else if (!previous.resolvedType.isAssignableFrom(resolvedType)) {
            throw new IllegalArgumentException(
                "def " + varName + " holds a " + previous.resolvedType.getSimpleName()
                    + " and is reassigned a " + resolvedType.getSimpleName());
        }

        // Emit assignment in body (at the point where def appears).
        // For built-in scalar casts the initialiser usually returns String
        // or Object (e.g. tag() returns String), so a plain Java cast would
        // throw ClassCastException at runtime. Route through the runtime
        // helper which performs the proper conversion. FQCN casts still use
        // a direct Java cast — those name a concrete type the caller knows
        // the initialiser already produces.
        sb.append("  ").append(javaVar).append(" = ");
        if (castType != null && !castType.isEmpty()) {
            final String conversion = scalarCastConversion(castType, initExpr);
            if (conversion != null) {
                sb.append(conversion);
            } else {
                sb.append("(").append(resolvedType.getName()).append(") ").append(initExpr);
            }
        } else {
            sb.append(initExpr);
        }
        sb.append(";\n");
    }

    /**
     * Render the boxed runtime-helper conversion for a scalar cast type
     * (e.g. {@code Long.valueOf(h.toLong(<init>))}). Returns {@code null}
     * for FQCN / unknown cast types so the caller can fall back to a
     * direct Java cast.
     */
    private static String scalarCastConversion(final String castType,
                                                 final CharSequence init) {
        switch (castType) {
            case "String":
                return "h.toStr(" + init + ")";
            case "Long":
                return "Long.valueOf(h.toLong(" + init + "))";
            case "Integer":
                return "Integer.valueOf(h.toInt(" + init + "))";
            case "Double":
                return "Double.valueOf(h.toDouble(" + init + "))";
            case "Float":
                return "Float.valueOf(h.toFloat(" + init + "))";
            case "Boolean":
                return "Boolean.valueOf(h.toBool(" + init + "))";
            default:
                return null;
        }
    }

    /**
     * Resolves a cast type string to a {@link Class}.
     * Handles the four built-in type names ({@code String}, {@code Long},
     * {@code Integer}, {@code Boolean}) and fully qualified class names.
     */
    private static Class<?> resolveDefCastType(final String castType) {
        switch (castType) {
            case "String":
                return String.class;
            case "Long":
                return Long.class;
            case "Integer":
                return Integer.class;
            case "Double":
                return Double.class;
            case "Float":
                return Float.class;
            case "Boolean":
                return Boolean.class;
            default:
                try {
                    return Class.forName(castType);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(
                        "def cast type not found on classpath: " + castType, e);
                }
        }
    }

    // ==================== Def variable chain codegen ====================

    /**
     * Generates typed method-chain access on a def variable.
     * Uses reflection to resolve each method/field call and track types.
     *
     * @param sb output buffer
     * @param localVar the def variable info (java var name + resolved type)
     * @param chain the chain segments after the variable name
     * @param genCtx codegen context
     */
    public static void generateDefVarChain(
            final StringBuilder sb,
            final TALLocalVar localVar,
            final List<? extends TALValueAccessSegment> chain,
            final TALGenCtx genCtx) {
        if (chain.isEmpty()) {
            sb.append(localVar.javaVarName);
            genCtx.lastResolvedType = localVar.resolvedType;
            return;
        }

        String prevExpr = localVar.javaVarName;
        Class<?> currentType = localVar.resolvedType;
        boolean canBeNull = true;
        // Set when a safe-navigated primitive was already boxed to carry its null.
        boolean boxed = false;

        for (int i = 0; i < chain.size(); i++) {
            final TALValueAccessSegment seg = chain.get(i);
            final boolean isLast = i == chain.size() - 1;

            if (seg instanceof TALMethodSegment) {
                final TALMethodSegment ms =
                    (TALMethodSegment) seg;
                final String methodName = ms.getName();

                // Resolve method on currentType via reflection
                final Method method =
                    resolveMethod(currentType, methodName, ms.getArguments());
                if (method == null) {
                    throw new IllegalArgumentException(
                        "Cannot resolve method " + currentType.getSimpleName()
                            + "." + methodName + "() in def variable chain");
                }
                final Class<?> returnType = method.getReturnType();
                final String args =
                    TALValueCodegen.generateMethodArgs(ms.getArguments(), genCtx);

                if (ms.isSafeNav() && canBeNull) {
                    if (isLast && returnType.isPrimitive()) {
                        // Primitive return with null guard
                        final String boxName =
                            TALCodegenHelper.boxTypeName(returnType);
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + boxName + ".valueOf(" + prevExpr + "."
                            + methodName + "(" + args + ")))";
                        currentType = returnType;
                        boxed = true;
                    } else {
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + prevExpr + "." + methodName + "(" + args + "))";
                        currentType = returnType;
                        canBeNull = true;
                    }
                } else {
                    prevExpr = prevExpr + "." + methodName + "(" + args + ")";
                    currentType = returnType;
                    canBeNull = !returnType.isPrimitive();
                }
            } else if (seg instanceof TALFieldSegment) {
                final TALFieldSegment fs =
                    (TALFieldSegment) seg;
                final String fieldName = fs.getName();
                // Try getter first
                final String getterName = "get"
                    + Character.toUpperCase(fieldName.charAt(0))
                    + fieldName.substring(1);
                Method getter = null;
                try {
                    getter = currentType.getMethod(getterName);
                } catch (NoSuchMethodException e) {
                    // Try direct field access name
                    try {
                        getter = currentType.getMethod(fieldName);
                    } catch (NoSuchMethodException e2) {
                        throw new IllegalArgumentException(
                            "Cannot resolve field/getter "
                                + currentType.getSimpleName()
                                + "." + fieldName + " in def variable chain");
                    }
                }
                final Class<?> returnType = getter.getReturnType();

                if (fs.isSafeNav() && canBeNull) {
                    if (isLast && returnType.isPrimitive()) {
                        final String boxName =
                            TALCodegenHelper.boxTypeName(returnType);
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + boxName + ".valueOf(" + prevExpr + "."
                            + getter.getName() + "()))";
                        currentType = returnType;
                        boxed = true;
                    } else {
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + prevExpr + "." + getter.getName() + "())";
                        currentType = returnType;
                        canBeNull = true;
                    }
                } else {
                    prevExpr = prevExpr + "." + getter.getName() + "()";
                    currentType = returnType;
                    canBeNull = !returnType.isPrimitive();
                }
            } else if (seg instanceof TALIndexSegment) {
                final int index = ((TALIndexSegment) seg).getIndex();
                // Try get(int) method (e.g., JsonArray.get(int))
                Method getMethod = null;
                try {
                    getMethod = currentType.getMethod("get", int.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(
                        "Cannot resolve index access on "
                            + currentType.getSimpleName()
                            + " in def variable chain");
                }
                final Class<?> returnType = getMethod.getReturnType();
                if (canBeNull) {
                    prevExpr = "(" + prevExpr + " == null ? null : "
                        + prevExpr + ".get(" + index + "))";
                } else {
                    prevExpr = prevExpr + ".get(" + index + ")";
                }
                currentType = returnType;
                canBeNull = true;
            }
        }

        genCtx.lastResolvedType = currentType;
        if (currentType.isPrimitive() && !boxed && TALCodegenHelper.boxTypeName(currentType) != null) {
            // Javassist does not autobox, so a primitive leaf such as `.length()` is boxed for the Object contexts
            // it lands in; the raw form stays available to numeric comparisons and arithmetic.
            genCtx.lastRawChain = prevExpr;
            sb.append(TALCodegenHelper.boxTypeName(currentType)).append(".valueOf(").append(prevExpr).append(")");
            return;
        }
        sb.append(prevExpr);
    }

    /**
     * Resolves a method on the given type by name, matching argument count.
     * For methods with String arguments (like JsonObject.get(String)),
     * prioritizes exact match by parameter types.
     */
    private static Method resolveMethod(
            final Class<?> type, final String name,
            final List<? extends TALFunctionArg> args) {
        final int argCount = args != null ? args.size() : 0;
        // Try exact match with common parameter types
        if (argCount == 1) {
            try {
                return type.getMethod(name, String.class);
            } catch (NoSuchMethodException ignored) {
                // fall through
            }
            try {
                return type.getMethod(name, int.class);
            } catch (NoSuchMethodException ignored) {
                // fall through
            }
        }
        if (argCount == 0) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // fall through
            }
        }
        // Fallback: find by name and arg count
        for (final Method m : type.getMethods()) {
            if (m.getName().equals(name)
                    && m.getParameterCount() == argCount) {
                return m;
            }
        }
        return null;
    }

    /**
     * @param type the static type a built-in declares
     * @return the wrapper class to box it into, or null when the type is already a reference
     */
    private static Class<?> primitiveWrapper(final ExprType type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case INT:
                return Integer.class;
            case LONG:
                return Long.class;
            case FLOAT:
                return Float.class;
            case DOUBLE:
                return Double.class;
            case BOOLEAN:
                return Boolean.class;
            default:
                return null;
        }
    }
}
