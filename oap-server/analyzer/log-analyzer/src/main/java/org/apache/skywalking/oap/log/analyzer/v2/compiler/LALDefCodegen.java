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
import org.apache.skywalking.oap.server.core.dsl.DslMethods;

/**
 * Code generation for LAL {@code def} local variable declarations and the chains on def variables.
 *
 * <p>The {@code def} keyword declares a local variable in the extractor
 * block. The type is inferred from the initializer expression, and
 * subsequent method chains resolve on it ({@link LALChainCodegen}).
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
     * _def_jwt = h.toJsonObject(h.safeProp(h.safeProp(h.mapVal("metadata"), "filterMetadataMap"), "key"));
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
        final String castType = def.getCastType();
        if (genCtx.localVars.containsKey(varName)) {
            // Groovy LAL refused it.
            throw new IllegalArgumentException(
                "def '" + varName + "' is already declared where this def is: give the new one another name");
        }

        final StringBuilder initExpr = new StringBuilder();
        LALValueCodegen.generateValueAccess(initExpr, init, genCtx);
        final boolean typedResult = genCtx.lastTypedResult && castType == null;
        final boolean textGroup = castType == null && LALValueCodegen.isTextGroupOfABranch(init, genCtx);
        if (castType != null) {
            LALValueCodegen.rejectUnconvertibleCast(init, castType, genCtx);
        }
        final boolean initNonNull = genCtx.lastNonNull;
        // A local holds a primitive boxed; an explicit cast types it instead.
        final Class<?> resolvedType = castType != null ? resolveDefCastType(castType, genCtx.outputType)
            : DslMethods.boxed(genCtx.lastResolvedType != null ? genCtx.lastResolvedType : Object.class);

        // One Java variable per name and type in the method, declared at its top: a def of a name already declared
        // with another type, such as in the other branch of an if, gets a variable of its own.
        // A suffixed name skips one another def's own name would take: `x_2` is a def name too.
        String javaVar = "_def_" + varName;
        for (int n = 2; genCtx.declaredDefVars.containsKey(javaVar)
                && genCtx.declaredDefVars.get(javaVar) != resolvedType
                || n > 2 && genCtx.allDefNames.contains(varName + "_" + (n - 1)); n++) {
            javaVar = "_def_" + varName + "_" + n;
        }
        genCtx.localVars.put(varName,
            new LALClassGenerator.LocalVarInfo(javaVar, resolvedType, typedResult, textGroup));
        if (genCtx.declaredDefVars.put(javaVar, resolvedType) == null) {
            genCtx.localVarDecls.append("  ").append(LALClassGenerator.sourceNameOf(resolvedType))
                .append(" ").append(javaVar).append(";\n");
            genCtx.localVarLvtVars.add(new String[]{
                javaVar, LALClassGenerator.descriptorOf(resolvedType)
            });
        }

        // A built-in cast converts through the runtime helper; a class cast converts as Groovy's `as` does
        // (h.asClass), so a list cast as a Set becomes one.
        sb.append("  ").append(javaVar).append(" = ");
        if (castType != null) {
            final String conversion = LALValueCodegen.castObject(castType, initExpr.toString(), initNonNull);
            if (conversion != null) {
                sb.append(conversion);
            } else {
                sb.append("(").append(LALClassGenerator.sourceNameOf(resolvedType)).append(") h.asClass(")
                  .append(initExpr).append(", ").append(LALClassGenerator.sourceNameOf(resolvedType)).append(".class)");
            }
        } else {
            sb.append(initExpr);
        }
        sb.append(";\n");
    }

    /**
     * Resolves a def's cast to its class: a built-in cast's ({@code String}, {@code Long}, {@code int}, ...), else
     * the class a fully qualified name names, which the list must allow.
     */
    private static Class<?> resolveDefCastType(final String castType, final Class<?> outputType) {
        final Class<?> builtIn = LALValueCodegen.classOf(LALValueCodegen.castToType(castType));
        if (builtIn != null) {
            return builtIn;
        }
        try {
            // typing only: the class is not initialized before the list accepts it
            return LALCallables.checkDefCast(Class.forName(castType, false,
                LALDefCodegen.class.getClassLoader()), outputType);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                "def cast type not found on classpath: " + castType, e);
        }
    }

    // ==================== Def variable chain codegen ====================

    /**
     * Generates the chain on a def variable, on the type the def holds.
     *
     * @param localVar the def variable info (java var name + resolved type)
     * @param chain    the chain segments after the variable name
     */
    static void generateDefVarChain(
            final StringBuilder sb,
            final LALClassGenerator.LocalVarInfo localVar,
            final List<LALScriptModel.ValueAccessSegment> chain,
            final LALClassGenerator.GenCtx genCtx) {
        if (localVar.textGroup && localVar.resolvedType == Object.class && LALChainCodegen.callsAStringMethod(chain)) {
            // As parsed.<group>.<method> takes it: a string, failing the cast for another branch's value.
            LALChainCodegen.generateTypedChain(sb, "((String) " + localVar.javaVarName + ")", String.class, true,
                chain, genCtx);
            return;
        }
        LALChainCodegen.generateTypedChain(sb, localVar.javaVarName, localVar.resolvedType, true, chain, genCtx,
            localVar.typedResult);
    }
}
