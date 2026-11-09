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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.MQOperation;
import org.apache.skywalking.oap.server.core.source.RequestType;
import org.apache.skywalking.oap.server.core.source.VirtualCacheOperation;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALDefStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFieldSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFunctionArg;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALIndexSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALMethodSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALConstruct;

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
    /**
     * A string literal of one identifier, as the generated code writes it: a name a literal {@code as Layer} or
     * {@code as <Enum>} can be resolved for once.
     */
    private static final Pattern LITERAL_NAME = Pattern.compile("\"([A-Za-z_][A-Za-z0-9_]*)\"");

    /**
     * The simple names {@code as} accepts for {@code Layer} and the enums the OAL sources take; any other enum is
     * named by its class name.
     */
    private static final Map<String, Class<?>> TYPED_VALUE_CLASSES = Map.of(
        "Layer", Layer.class,
        "RequestType", RequestType.class,
        "DetectPoint", DetectPoint.class,
        "MQOperation", MQOperation.class,
        "VirtualCacheOperation", VirtualCacheOperation.class,
        "TagType", TagType.class,
        "GenAITraceRefType", GenAITraceRefType.class
    );

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
        generateValueStatement(sb, def, genCtx, true);
    }

    /**
     * Generates a plain assignment, {@code <name> = <expr>}, to a variable a def declared earlier in the rule and
     * still in scope, or a segmentDef. It never declares: a name nothing declares is a compile error.
     */
    public static void generateAssignStatement(final StringBuilder sb,
                                               final TALDefStatement assign,
                                               final TALGenCtx genCtx) {
        generateValueStatement(sb, assign, genCtx, false);
    }

    /**
     * {@code segmentDef <name> = <expr>}: assigns the rule's variable of the segment, which every generated method
     * reads from the segment's array when it starts, see {@link TALGenCtx#beginMethod}, and which every assignment
     * writes back to it. The value's type is what the next generation of the rule declares the variable as.
     */
    public static void generateSegmentDefStatement(final StringBuilder sb,
                                                   final TALScriptModel.SegmentDefStatement def,
                                                   final TALGenCtx genCtx) {
        TALInputs.require(TALConstruct.SEGMENT_DEF, genCtx.inputAlias);
        generateValueStatement(sb, def, genCtx, false);
    }

    private static void generateValueStatement(final StringBuilder sb,
                                               final TALDefStatement def,
                                               final TALGenCtx genCtx,
                                               final boolean mayDeclare) {
        final TALValueAccess init = def.getInitializer();
        final String varName = def.getVarName();
        final TALLocalVar previous = genCtx.localVars.get(varName);
        if (previous == null && !mayDeclare) {
            throw new IllegalArgumentException(
                "'" + varName + " = ...' assigns a variable that no def or segmentDef declares before it in scope; "
                    + "declare it with def " + varName + " = ...");
        }
        if (mayDeclare && previous != null && previous.segmentSlot >= 0) {
            throw new IllegalArgumentException(
                "def " + varName + " names the rule's segmentDef " + varName + "; a name is a def or a segmentDef, "
                    + "so assign it with " + varName + " = ...");
        }

        // Determine type and generate initializer expression
        Class<?> resolvedType;
        Class<?> elementType = null;
        String entityScope = null;
        final StringBuilder initExpr = new StringBuilder();

        if (init.getFunctionCallName() != null
                && TALSpanRootResolver.ENTITY_CONSTRUCTORS.contains(init.getFunctionCallName())) {
            // An entity held in a def has a compile-time scope type, so `scope svc` always has one shape and
            // reassigning the variable with a different constructor is caught here rather than at runtime.
            final String scopeType = TALBlockCodegen.appendEntityConstructor(initExpr, init, genCtx);
            entityScope = scopeType;
            final String previousScope = genCtx.entityScopeTypes.put(varName, scopeType);
            if (previousScope != null && !previousScope.equals(scopeType)) {
                throw new IllegalArgumentException(
                    "def " + varName + " was an entity of scope " + previousScope
                        + " and is reassigned as " + scopeType);
            }
            resolvedType = MeterEntity.class;
        } else if (init.getFunctionCallName() != null
                && primitiveWrapper(genCtx.resolver.functionReturnType(init, genCtx)) != null) {
            // A function returning a primitive has to be boxed here: the local is declared as a wrapper, and
            // Javassist's compiler does not autobox on assignment, which the verifier rejects.
            final Class<?> wrapper = primitiveWrapper(
                genCtx.resolver.functionReturnType(init, genCtx));
            initExpr.append(wrapper.getSimpleName()).append(".valueOf(");
            TALValueCodegen.generateValueAccess(initExpr, init, genCtx);
            initExpr.append(")");
            resolvedType = wrapper;
        } else {
            // General value access — type inferred from lastResolvedType, a primitive boxed for the wrapper local
            TALValueCodegen.generateBoxedValueAccess(initExpr, init, null, genCtx);
            resolvedType = genCtx.lastResolvedType != null
                ? genCtx.lastResolvedType : Object.class;
            elementType = genCtx.lastResolvedElementType;
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
        Class<?> castClass = null;
        if (castType != null && !castType.isEmpty()) {
            // Resolve the cast type — primitive wrapper names, Layer and the source enums are handled,
            // anything else is treated as a FQCN
            castClass = resolveDefCastType(castType);
            if (castClass != null) {
                resolvedType = castClass;
                elementType = null;
                checkTypedLiteral(castClass, init);
            }
        }

        if (def instanceof TALScriptModel.SegmentDefStatement) {
            genCtx.learnedSegmentDefTypes.putIfAbsent(varName, Arrays.asList(resolvedType, elementType, entityScope));
        }

        // The first def of a name declares the variable, hoisted to the top of the method; a later def of the
        // same name, or a plain assignment, assigns to it, so its value has to fit the declared type.
        final String javaVar;
        if (previous == null) {
            javaVar = freshJavaName(varName, genCtx);
            genCtx.localVars.put(varName, new TALLocalVar(javaVar, resolvedType, elementType));
            if (!genCtx.blockScopes.isEmpty()) {
                genCtx.blockScopes.peekLast().add(varName);
            }
            // Initialized, so a def first assigned inside one branch can still be read after it: the variable is
            // null on the paths that did not assign it, where Javassist would otherwise reject the read.
            genCtx.localVarDecls.append("  ").append(TALCodegenHelper.javaTypeName(resolvedType))
                .append(" ").append(javaVar).append(" = null;\n");
            genCtx.localVarLvtVars.add(new String[]{
                javaVar, TALCodegenHelper.descriptor(resolvedType)
            });
        } else {
            javaVar = previous.javaVarName;
            if (!previous.resolvedType.isAssignableFrom(resolvedType) && !isNullLiteral(init)) {
                throw new IllegalArgumentException(
                    "def " + varName + " holds a " + previous.resolvedType.getSimpleName()
                        + " and is reassigned a " + resolvedType.getSimpleName());
            }
            // A foreach over the variable is typed to the element type it was declared with, so a list of anything
            // else would be cast to that type at runtime.
            if (previous.elementType != null && !isNullLiteral(init)
                && (elementType == null || !previous.elementType.isAssignableFrom(elementType))) {
                throw new IllegalArgumentException(
                    "def " + varName + " holds a list of " + previous.elementType.getSimpleName()
                        + " and is reassigned a list of "
                        + (elementType == null ? "elements of unknown type" : elementType.getSimpleName()));
            }
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
            final String typed = typedCastConversion(castClass, initExpr, genCtx);
            final String conversion = typed != null ? typed : scalarCastConversion(castType, initExpr);
            if (conversion != null) {
                sb.append(conversion);
            } else {
                sb.append("(").append(TALCodegenHelper.javaTypeName(resolvedType)).append(") ").append(initExpr);
            }
        } else {
            sb.append(initExpr);
        }
        sb.append(";\n");
        if (previous != null && previous.segmentSlot >= 0) {
            sb.append("  ctx.getSegmentDefs()[").append(previous.segmentSlot).append("] = ").append(javaVar)
              .append(";\n");
        }
    }

    /**
     * Render the boxed runtime-helper conversion for a scalar cast type
     * (e.g. {@code Long.valueOf(TalValueConversions.toLong(<init>))}). Returns {@code null}
     * for FQCN / unknown cast types so the caller can fall back to a
     * direct Java cast.
     */
    private static String scalarCastConversion(final String castType,
                                                 final CharSequence init) {
        switch (castType) {
            case "String":
                return TALRuntimeRoles.method("toStr") + "(" + init + ")";
            case "Long":
                return "Long.valueOf(" + TALRuntimeRoles.method("toLong") + "(" + init + "))";
            case "Integer":
                return "Integer.valueOf(" + TALRuntimeRoles.method("toInt") + "(" + init + "))";
            case "Double":
                return "Double.valueOf(" + TALRuntimeRoles.method("toDouble") + "(" + init + "))";
            case "Float":
                return "Float.valueOf(" + TALRuntimeRoles.method("toFloat") + "(" + init + "))";
            case "Boolean":
                return "Boolean.valueOf(" + TALRuntimeRoles.method("toBool") + "(" + init + "))";
            default:
                return null;
        }
    }

    /**
     * @return a Java local name for a def, fresh when the rule declared the same name again after its earlier
     * declaration's scope ended
     */
    private static String freshJavaName(final String varName, final TALGenCtx genCtx) {
        String javaVar = "_def_" + varName;
        int suffix = 1;
        while (genCtx.declaredJavaNames.contains(javaVar)) {
            javaVar = "_def_" + varName + "_" + suffix++;
        }
        genCtx.declaredJavaNames.add(javaVar);
        return javaVar;
    }

    private static boolean isNullLiteral(final TALValueAccess init) {
        return !init.isStringLiteral() && !init.isNumberLiteral() && init.getFunctionCallName() == null
            && init.getParenInner() == null && init.getChain().isEmpty() && !init.isInputRef() && !init.isMetaRef()
            && init.getSegments().size() == 1 && "null".equals(init.getSegments().get(0));
    }

    /**
     * {@code as Layer} and {@code as <Enum>}: a literal name is checked against the registered layers or the enum's
     * constants now, so a misspelt one is a compile error where it is written.
     */
    static void checkTypedLiteral(final Class<?> castClass, final TALValueAccess init) {
        if (!init.isStringLiteral() || !init.getConcatParts().isEmpty()) {
            return;
        }
        final String literal = init.getSegments().get(0);
        if (castClass == Layer.class) {
            try {
                Layer.valueOf(literal);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("'" + literal + "' as Layer names no registered layer", e);
            }
        } else if (castClass.isEnum()) {
            if (enumConstant(castClass, literal) == null) {
                throw new IllegalArgumentException(
                    "'" + literal + "' as " + castClass.getSimpleName() + " is none of its constants");
            }
        }
    }

    /**
     * @return whether the name is a layer {@code Layer} declares as a constant, which {@code unregisterDynamic}
     * refuses to remove, so the field and {@code Layer.valueOf(name)} are always the same object
     */
    private static boolean isBuiltInLayer(final String name) {
        try {
            final Field field = Layer.class.getField(name);
            return field.getType() == Layer.class && Modifier.isStatic(field.getModifiers())
                && Modifier.isFinal(field.getModifiers());
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    static Object enumConstant(final Class<?> enumClass, final String name) {
        for (final Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        return null;
    }

    /**
     * The conversion for {@code as Layer} and {@code as <Enum>}: from a value of that type as it is, and from a
     * string by name. {@code null} stays {@code null}. A literal naming an enum constant is that constant, and one
     * naming a built-in layer is its {@code Layer} field, which no runtime change removes; any other literal is
     * converted at the statement, where it fails when the name is not registered then.
     */
    static String typedCastConversion(final Class<?> castClass, final CharSequence init, final TALGenCtx genCtx) {
        if (castClass == null) {
            return null;
        }
        final Matcher literal = LITERAL_NAME.matcher(init);
        if (castClass == Layer.class) {
            if (literal.matches() && isBuiltInLayer(literal.group(1))) {
                return Layer.class.getName() + "." + literal.group(1);
            }
            return TALRuntimeRoles.method("toLayer") + "(" + init + ")";
        }
        if (castClass.isEnum() && literal.matches() && enumConstant(castClass, literal.group(1)) != null) {
            return castClass.getName() + "." + literal.group(1);
        }
        if (castClass.isEnum()) {
            // Grouped, so a method that follows applies to the enum rather than to toEnum's Object.
            return "((" + castClass.getName() + ") " + TALRuntimeRoles.method("toEnum") + "(" + castClass.getName() + ".class, "
                + init + "))";
        }
        return null;
    }

    /**
     * @return {@code Layer} or the enum an {@code as} names, by simple name for Layer and the source enums and by
     * class name for any other enum; null for any other cast
     */
    static Class<?> typedCastClass(final String castType) {
        if (castType == null) {
            return null;
        }
        final Class<?> known = TYPED_VALUE_CLASSES.get(castType);
        if (known != null || castType.indexOf('.') < 0) {
            return known;
        }
        try {
            final Class<?> named = Class.forName(castType);
            return named.isEnum() ? named : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Resolves a cast type string to a {@link Class}.
     * Handles the four built-in type names ({@code String}, {@code Long},
     * {@code Integer}, {@code Boolean}) and fully qualified class names.
     */
    static Class<?> resolveDefCastType(final String castType) {
        final Class<?> typed = TYPED_VALUE_CLASSES.get(castType);
        if (typed != null) {
            return typed;
        }
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
        genCtx.lastResolvedElementType = null;
        if (chain.isEmpty()) {
            sb.append(localVar.javaVarName);
            genCtx.lastResolvedType = localVar.resolvedType;
            genCtx.lastResolvedElementType = localVar.elementType;
            return;
        }

        String prevExpr = localVar.javaVarName;
        Class<?> currentType = localVar.resolvedType;
        boolean canBeNull = true;
        // Set when a safe-navigated primitive was already boxed to carry its null.
        boolean boxed = false;
        // The element type of the last segment's generic List, kept apart from the metadata the arguments' code
        // overwrites.
        Class<?> resultElementType = null;

        for (int i = 0; i < chain.size(); i++) {
            final TALValueAccessSegment seg = chain.get(i);
            final boolean isLast = i == chain.size() - 1;

            if (seg instanceof TALMethodSegment) {
                final TALMethodSegment ms =
                    (TALMethodSegment) seg;
                final String methodName = ms.getName();

                // Resolve method on currentType via reflection
                final Method method =
                    resolveMethod(currentType, methodName, ms.getArguments(), genCtx);
                if (method == null) {
                    throw new IllegalArgumentException(
                        "Cannot resolve method " + currentType.getSimpleName()
                            + "." + methodName + "() in def variable chain");
                }
                final Class<?> returnType = method.getReturnType();
                if (isLast) {
                    resultElementType = TALCodegenHelper.listElementType(method.getGenericReturnType());
                }
                final String args = method.isVarArgs()
                    ? TALValueCodegen.generateMethodArgs(ms.getArguments(), genCtx)
                    : TALValueCodegen.generateArgumentsFor(ms.getArguments(), method, genCtx);

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
                final Method getter = TALCodegenHelper.resolveGetter(currentType, fieldName);
                if (getter == null) {
                    throw new IllegalArgumentException(
                        "Cannot resolve field/getter "
                            + currentType.getSimpleName()
                            + "." + fieldName + " in def variable chain");
                }
                final Class<?> returnType = getter.getReturnType();
                if (isLast) {
                    resultElementType = TALCodegenHelper.listElementType(getter.getGenericReturnType());
                }

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
            } else if (seg instanceof TALIndexSegment && currentType.isArray()) {
                final int index = ((TALIndexSegment) seg).getIndex();
                final Class<?> component = currentType.getComponentType();
                final String access = prevExpr + "[" + index + "]";
                if (canBeNull && component.isPrimitive() && TALCodegenHelper.boxTypeName(component) != null) {
                    prevExpr = "(" + prevExpr + " == null ? null : "
                        + TALCodegenHelper.boxTypeName(component) + ".valueOf(" + access + "))";
                    boxed = true;
                } else if (canBeNull) {
                    prevExpr = "(" + prevExpr + " == null ? null : " + access + ")";
                } else {
                    prevExpr = access;
                }
                currentType = component;
                canBeNull = !component.isPrimitive();
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

        // The arguments' code above left its own metadata; what follows describes the chain's result alone.
        genCtx.clearExtraLogResult();
        genCtx.lastResolvedElementType = resultElementType;
        genCtx.lastResolvedType = currentType;
        if (boxed) {
            // Already boxed, carrying the null of a safe-navigated receiver.
            genCtx.lastNullChecks = "safe navigation";
        }
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
            final List<? extends TALFunctionArg> args,
            final TALGenCtx genCtx) {
        final int argCount = args != null ? args.size() : 0;
        // Overloads of one arity are told apart by the arguments' types, since each argument is converted to the
        // chosen method's parameter; a tie falls back to the order below.
        final List<Method> overloads = new ArrayList<>();
        for (final Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argCount && !m.isVarArgs()) {
                overloads.add(m);
            }
        }
        if (overloads.size() > 1) {
            final Method best = TALFunctionRegistry.bestMethod(overloads, TALValueCodegen.argumentTypes(args, genCtx));
            if (best != null) {
                return best;
            }
        }
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
