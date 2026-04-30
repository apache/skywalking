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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import java.util.List;

/**
 * Code generation for LAL {@code def} local variable declarations and
 * typed method-chain resolution on def variables.
 *
 * <p>The {@code def} keyword declares a local variable in the extractor
 * block. The type is inferred from the initializer expression, and
 * subsequent method chains are resolved via compile-time reflection.
 *
 * <p>LAL example:
 * <pre>{@code
 * extractor {
 *   // def with built-in function — type inferred as JsonObject
 *   def jwt = toJson(parsed?.commonProperties?.metadata?
 *       .filterMetadataMap?.get("envoy.filters.http.jwt_authn"))
 *
 *   // def chaining on another def variable — type inferred as JsonObject
 *   def payload = jwt?.getAsJsonObject("payload")
 *
 *   // Using def variable in tag assignment
 *   tag 'email': payload?.get("email")?.getAsString()
 *
 *   // def with explicit type cast
 *   def code = parsed?.response?.responseCode?.value as Integer
 * }
 * }</pre>
 *
 * <p>Generated code declares Java locals with {@code _def_} prefix
 * (e.g. {@code _def_jwt}, {@code _def_payload}) and emits assignments
 * at the point where {@code def} appears in the DSL.
 */
final class LALDefCodegen {

    private LALDefCodegen() {
        // utility class
    }

    // ==================== Def statement codegen ====================

    /**
     * Generates a {@code def} variable declaration and assignment.
     *
     * <p>LAL: {@code def jwt = toJson(parsed?.metadata?.filterMetadataMap?.get("key"))}
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
    static void generateDefStatement(final StringBuilder sb,
                                      final LALScriptModel.DefStatement def,
                                      final LALClassGenerator.GenCtx genCtx) {
        final LALScriptModel.ValueAccess init = def.getInitializer();
        final String varName = def.getVarName();
        final String javaVar = "_def_" + varName;
        final boolean alreadyDeclared = genCtx.localVars.containsKey(varName);

        // Determine type and generate initializer expression
        Class<?> resolvedType;
        final StringBuilder initExpr = new StringBuilder();

        if (init.getFunctionCallName() != null
                && LALBlockCodegen.BUILTIN_FUNCTIONS.containsKey(init.getFunctionCallName())) {
            // Built-in function: toJson(...), toJsonArray(...)
            final String funcName = init.getFunctionCallName();
            final int argCount = init.getFunctionCallArgs().size();
            if (argCount != 1) {
                throw new IllegalArgumentException(
                    funcName + "() requires exactly 1 argument, got " + argCount);
            }
            final Object[] info = LALBlockCodegen.BUILTIN_FUNCTIONS.get(funcName);
            final String helperMethod = (String) info[0];
            resolvedType = (Class<?>) info[1];

            initExpr.append(helperMethod).append("(");
            LALValueCodegen.generateValueAccess(initExpr,
                init.getFunctionCallArgs().get(0).getValue(), genCtx);
            initExpr.append(")");
        } else {
            // General value access — type inferred from lastResolvedType
            LALValueCodegen.generateValueAccess(initExpr, init, genCtx);
            resolvedType = genCtx.lastResolvedType != null
                ? genCtx.lastResolvedType : Object.class;
            // Box primitive types for local variable declarations
            if (resolvedType.isPrimitive()) {
                final String boxName = LALCodegenHelper.boxTypeName(resolvedType);
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

        // Register in local vars for later reference
        genCtx.localVars.put(varName,
            new LALClassGenerator.LocalVarInfo(javaVar, resolvedType));

        // Emit declaration (placed at method top via localVarDecls) — skip if already declared
        if (!alreadyDeclared) {
            genCtx.localVarDecls.append("  ").append(resolvedType.getName())
                .append(" ").append(javaVar).append(";\n");
            genCtx.localVarLvtVars.add(new String[]{
                javaVar, "L" + resolvedType.getName().replace('.', '/') + ";"
            });
        }

        // Emit assignment in body (at the point where def appears)
        sb.append("  ").append(javaVar).append(" = ");
        if (castType != null && !castType.isEmpty()) {
            sb.append("(").append(resolvedType.getName()).append(") ");
        }
        sb.append(initExpr).append(";\n");
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
    static void generateDefVarChain(
            final StringBuilder sb,
            final LALClassGenerator.LocalVarInfo localVar,
            final List<LALScriptModel.ValueAccessSegment> chain,
            final LALClassGenerator.GenCtx genCtx) {
        if (chain.isEmpty()) {
            sb.append(localVar.javaVarName);
            genCtx.lastResolvedType = localVar.resolvedType;
            return;
        }

        String prevExpr = localVar.javaVarName;
        Class<?> currentType = localVar.resolvedType;
        boolean canBeNull = true;

        for (int i = 0; i < chain.size(); i++) {
            final LALScriptModel.ValueAccessSegment seg = chain.get(i);
            final boolean isLast = i == chain.size() - 1;

            if (seg instanceof LALScriptModel.MethodSegment) {
                final LALScriptModel.MethodSegment ms =
                    (LALScriptModel.MethodSegment) seg;
                final String methodName = ms.getName();

                // Resolve method on currentType via reflection
                final java.lang.reflect.Method method =
                    resolveMethod(currentType, methodName, ms.getArguments());
                if (method == null) {
                    throw new IllegalArgumentException(
                        "Cannot resolve method " + currentType.getSimpleName()
                            + "." + methodName + "() in def variable chain");
                }
                final Class<?> returnType = method.getReturnType();
                final String args =
                    LALValueCodegen.generateMethodArgs(ms.getArguments(), genCtx);

                if (ms.isSafeNav() && canBeNull) {
                    if (isLast && returnType.isPrimitive()) {
                        // Primitive return with null guard
                        final String boxName =
                            LALCodegenHelper.boxTypeName(returnType);
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + boxName + ".valueOf(" + prevExpr + "."
                            + methodName + "(" + args + ")))";
                        currentType = returnType;
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
            } else if (seg instanceof LALScriptModel.FieldSegment) {
                final LALScriptModel.FieldSegment fs =
                    (LALScriptModel.FieldSegment) seg;
                final String fieldName = fs.getName();
                // Try getter first
                final String getterName = "get"
                    + Character.toUpperCase(fieldName.charAt(0))
                    + fieldName.substring(1);
                java.lang.reflect.Method getter = null;
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
                            LALCodegenHelper.boxTypeName(returnType);
                        prevExpr = "(" + prevExpr + " == null ? null : "
                            + boxName + ".valueOf(" + prevExpr + "."
                            + getter.getName() + "()))";
                        currentType = returnType;
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
            } else if (seg instanceof LALScriptModel.IndexSegment) {
                final int index = ((LALScriptModel.IndexSegment) seg).getIndex();
                // Try get(int) method (e.g., JsonArray.get(int))
                java.lang.reflect.Method getMethod = null;
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
        sb.append(prevExpr);
    }

    /**
     * Resolves a method on the given type by name, matching argument count.
     * For methods with String arguments (like JsonObject.get(String)),
     * prioritizes exact match by parameter types.
     */
    private static java.lang.reflect.Method resolveMethod(
            final Class<?> type, final String name,
            final List<LALScriptModel.FunctionArg> args) {
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
        for (final java.lang.reflect.Method m : type.getMethods()) {
            if (m.getName().equals(name)
                    && m.getParameterCount() == argCount) {
                return m;
            }
        }
        return null;
    }
}
