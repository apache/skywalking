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
import java.util.Set;
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
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.BinaryOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALDefStatement;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFieldSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFunctionArg;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALIndexSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALMethodSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALConstruct;
import org.apache.skywalking.oap.server.core.dsl.DslMethods;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;

/**
 * Code generation for TAL {@code def} local variable declarations and assignments, and for the typed chains of
 * methods, fields and indexes on any value.
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
 * <p>Sibling implementation: {@code LALDefCodegen} in {@code log-analyzer}.
 */
public final class TALDefCodegen {
    /**
     * The numbers a quotient assigned to a {@code def} holding one converts to, as a cast to it does.
     */
    private static final List<Class<?>> SCALARS = List.of(Long.class, Integer.class, Double.class, Float.class);
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
     * <p>TAL: {@code def payload = parseJsonObject(span.name)}
     *
     * <p>Generated (declaration at method top):
     * <pre>{@code
     * com.google.gson.JsonObject _def_payload = null;
     * }</pre>
     * <p>Generated (assignment at def site):
     * <pre>{@code
     * _def_payload = _fn0.parseJsonObject(TalValueConversions.toStr(_p.getName()));
     * }</pre>
     *
     * <p>The variable is typed by its initializer, a function by the type it returns, any other expression by the
     * type its code resolves to; an explicit {@code as Type} cast overrides it.
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

        final String castType = def.getCastType();
        // `as String` and the scalar casts convert the initializer as they convert a value wherever a cast stands
        final boolean scalarCast = ExprType.ofCast(castType).boxedClass != null;
        final boolean nullValue = init.isLiteral("null");
        Class<?> resolvedType;
        Class<?> elementType = null;
        String entityScope = null;
        final StringBuilder initExpr = new StringBuilder();
        final ExprType returned = init.getFunctionCallName() != null && init.getChain().isEmpty()
            ? genCtx.resolver.functionReturnType(init, genCtx) : null;

        if (init.getFunctionCallName() != null
                && TALSpanRootResolver.ENTITY_CONSTRUCTORS.contains(init.getFunctionCallName())) {
            // An entity held in a def has a compile-time scope type, so `scope svc` always has one shape and
            // reassigning the variable with a different constructor is caught here rather than at runtime.
            TALBlockCodegen.requireBareConstructor(init, castType);
            final String scopeType = TALBlockCodegen.appendEntityConstructor(initExpr, init, genCtx);
            entityScope = scopeType;
            final String previousScope = genCtx.entityScopeTypes.put(varName, scopeType);
            if (previousScope != null && !previousScope.equals(scopeType)) {
                throw new IllegalArgumentException(
                    "def " + varName + " was an entity of scope " + previousScope
                        + " and is reassigned as " + scopeType);
            }
            resolvedType = MeterEntity.class;
        } else if (scalarCast) {
            TALValueCodegen.generateBoxedValueAccess(initExpr, init, castType, genCtx);
            resolvedType = genCtx.lastResolvedType;
        } else if (returned != null && (returned.isNumeric() || returned == ExprType.BOOLEAN)) {
            // A function returning a primitive has to be boxed here: the local is declared as a wrapper, and
            // Javassist's compiler does not autobox on assignment, which the verifier rejects.
            initExpr.append(returned.boxedClass.getSimpleName()).append(".valueOf(");
            TALValueCodegen.generateValueAccess(initExpr, init, genCtx);
            initExpr.append(")");
            resolvedType = returned.boxedClass;
        } else {
            // General value access — type inferred from lastResolvedType, a primitive boxed for the wrapper local
            TALValueCodegen.generateBoxedValueAccess(initExpr, init, null, genCtx);
            resolvedType = genCtx.lastResolvedType != null ? DslMethods.boxed(genCtx.lastResolvedType) : Object.class;
            elementType = genCtx.lastResolvedElementType;
        }

        // An explicit cast types the variable: a scalar type, Layer, a source enum or a class by its name
        final Class<?> initType = resolvedType;
        final Class<?> castClass = castType == null ? null : resolveDefCastType(castType);
        if (castClass != null) {
            resolvedType = castClass;
            elementType = null;
            checkTypedLiteral(castClass, init);
        }

        if (def instanceof TALScriptModel.SegmentDefStatement) {
            genCtx.learnedSegmentDefTypes.putIfAbsent(varName,
                new TALGenCtx.SegmentDefType(resolvedType, elementType, entityScope));
        }
        final boolean quotient = holdsQuotient(init, genCtx);

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
            if (castType == null && SCALARS.contains(previous.resolvedType) && isQuotient(resolvedType) && quotient) {
                // A quotient, or arithmetic on one, assigned to a whole or a floating number converts as a cast to it
                // does; any other number keeps the declared type's check.
                initExpr.insert(0, TALRuntimeRoles.method("as" + previous.resolvedType.getSimpleName()) + "(")
                  .append(")");
                resolvedType = previous.resolvedType;
            }
            if (!previous.resolvedType.isAssignableFrom(resolvedType) && !nullValue) {
                throw new IllegalArgumentException(
                    "def " + varName + " holds a " + previous.resolvedType.getSimpleName()
                        + " and is reassigned a " + resolvedType.getSimpleName());
            }
            // A foreach over the variable is typed to the element type it was declared with, so a list of anything
            // else would be cast to that type at runtime.
            if (previous.elementType != null && !nullValue
                && (elementType == null || !previous.elementType.isAssignableFrom(elementType))) {
                throw new IllegalArgumentException(
                    "def " + varName + " holds a list of " + previous.elementType.getSimpleName()
                        + " and is reassigned a list of "
                        + (elementType == null ? "elements of unknown type" : elementType.getSimpleName()));
            }
        }

        // What the def holds now: a map or list of its own, which put and add change, and a quotient, which narrows
        // into a def of a whole or floating number; a null is either.
        genCtx.assigned(genCtx.localVars.get(varName),
            nullValue || keepsValue(castType) && ownsCollection(init, genCtx),
            nullValue || keepsValue(castType) && isQuotient(resolvedType) && quotient);

        // A Layer or an enum cast converts by name; a cast to a class by its name is a Java cast, which the value's
        // type has to allow.
        sb.append("  ").append(javaVar).append(" = ");
        final String typed = castClass == null ? null : typedCastConversion(castClass, initExpr, genCtx);
        if (typed != null) {
            sb.append(typed);
        } else if (castClass != null && !scalarCast) {
            TALValueCodegen.requireCastable(initType == Object.class ? null : initType, resolvedType,
                "def " + varName + " = ... as " + castType);
            sb.append("(").append(TALCodegenHelper.javaTypeName(resolvedType)).append(") (").append(initExpr)
              .append(")");
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

    /**
     * {@code as Layer} and {@code as <Enum>}: a literal name is checked against the registered layers or the enum's
     * constants now, so a misspelt one is a compile error where it is written.
     */
    static void checkTypedLiteral(final Class<?> castClass, final TALValueAccess init) {
        final String literal = literalText(init, null);
        if (literal == null || castClass != Layer.class && !castClass.isEnum()) {
            return;
        }
        requireTypedName(castClass, literal, castClass == Layer.class
            ? "'" + literal + "' as Layer names no registered layer"
            : "'" + literal + "' as " + castClass.getSimpleName() + " is none of its constants");
    }

    /**
     * Refuses a name that is no registered layer, for {@code Layer}, or none of the enum's constants.
     *
     * @param message the compile error's message
     */
    static void requireTypedName(final Class<?> type, final String name, final String message) {
        if (type == Layer.class) {
            try {
                Layer.valueOf(name);
            } catch (final RuntimeException e) {
                throw new IllegalArgumentException(message, e);
            }
        } else if (!hasEnumConstant(type, name)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * @return whether a value of the class can be a quotient: a number Java has no arithmetic for
     */
    private static boolean isQuotient(final Class<?> type) {
        return type != null && Number.class.isAssignableFrom(type) && !SCALARS.contains(type);
    }

    /**
     * @return whether the expression is a quotient: it divides, reads a def holding one, or adds, subtracts or
     * multiplies one, at its top or through grouping
     */
    private static boolean holdsQuotient(final TALValueAccess value, final TALGenCtx genCtx) {
        final TALValueAccess held = heldValue(value);
        if (held == null) {
            return false;
        }
        if (!held.getConcatParts().isEmpty()) {
            return held.getConcatOps().contains(BinaryOp.SLASH)
                || held.getConcatParts().stream().anyMatch(part -> holdsQuotient(part, genCtx));
        }
        final TALLocalVar def = genCtx.localVars.get(held.bareName());
        return def != null && genCtx.holdsQuotient(def.javaVarName);
    }

    /**
     * @return whether the expression is a map or list the rule made: {@code emptyMap()}, {@code emptyList()}, a def
     * holding one, or one of them grouped
     */
    static boolean ownsCollection(final TALValueAccess value, final TALGenCtx genCtx) {
        final TALValueAccess held = heldValue(value);
        if (held == null || !held.getConcatParts().isEmpty()) {
            return false;
        }
        if (held.getFunctionCallName() != null) {
            return "emptyMap".equals(held.getFunctionCallName()) || "emptyList".equals(held.getFunctionCallName());
        }
        final TALLocalVar def = genCtx.localVars.get(held.bareName());
        return def != null && genCtx.ownsCollection(def.javaVarName);
    }

    /**
     * @return the expression whose value this one is, past grouping through an {@code as} that keeps the value; null
     * for a chain and for a group that converts
     */
    private static TALValueAccess heldValue(final TALValueAccess value) {
        TALValueAccess held = value;
        while (held.getParenInner() != null && held.getChain().isEmpty()) {
            if (!keepsValue(held.getParenCast())) {
                return null;
            }
            held = held.getParenInner();
        }
        return held.getChain().isEmpty() ? held : null;
    }

    /**
     * @return whether an {@code as} leaves the value itself, as a Java cast to a class does, rather than converting it
     */
    static boolean keepsValue(final String cast) {
        return cast == null || cast.indexOf('.') >= 0 && typedCastClass(cast) == null;
    }

    /**
     * @param value the expression
     * @param cast  the {@code as} written after it, or null
     * @return the text of a value the rule spells out in literals, as the rule computes it: a literal, in
     * parentheses, converted {@code as String}, or literals joined with {@code +}, so {@code "TEAM_" + 2} is
     * {@code TEAM_2}; null for any other expression, whose value is known only when the rule runs
     */
    static String literalText(final TALValueAccess value, final String cast) {
        final Object constant = constantOf(value, cast);
        return constant == null ? null : String.valueOf(constant);
    }

    private static Object constantOf(final TALValueAccess value, final String cast) {
        if (cast != null && !"String".equals(cast) && !"java.lang.String".equals(cast) || !value.getChain().isEmpty()) {
            return null;
        }
        final Object constant = constantValue(value);
        return constant == null || cast == null ? constant : String.valueOf(constant);
    }

    private static Object constantValue(final TALValueAccess value) {
        if (value.isStringLiteral()) {
            return value.getSegments().get(0);
        }
        if (value.getParenInner() != null) {
            return constantOf(value.getParenInner(), value.getParenCast());
        }
        if (value.getConcatParts().isEmpty()) {
            return scalarConstant(value);
        }
        Object folded = null;
        for (int i = 0; i < value.getConcatParts().size(); i++) {
            final Object part = constantOf(value.getConcatParts().get(i), null);
            if (part == null || i > 0 && value.getConcatOps().get(i - 1) != BinaryOp.PLUS) {
                return null;
            }
            if (i == 0) {
                folded = part;
            } else if (folded instanceof String || part instanceof String) {
                folded = String.valueOf(folded) + part;
            } else if (folded instanceof Number && part instanceof Number) {
                // The promotion the generated sum computes with, a float in double.
                folded = DslNumbers.add((Number) folded, (Number) part);
            } else {
                return null;
            }
        }
        return folded;
    }

    /**
     * @return a number or a {@code true} / {@code false} literal as the Java value the rule holds; null otherwise
     */
    private static Object scalarConstant(final TALValueAccess value) {
        if (value.isNumberLiteral()) {
            return TALLiterals.NumericLiteral.parse(value.getSegments().get(0)).value();
        }
        return value.isLiteral("true") || value.isLiteral("false") ? Boolean.valueOf(value.bareName()) : null;
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

    /**
     * @return whether the enum has a constant of that name, read from its fields: the class is not initialized
     */
    static boolean hasEnumConstant(final Class<?> enumClass, final String name) {
        try {
            return enumClass.getField(name).isEnumConstant();
        } catch (final NoSuchFieldException e) {
            return false;
        }
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
        final String constant = typedConstant(castClass, init);
        if (constant != null) {
            return constant;
        }
        if (castClass == Layer.class) {
            return TALRuntimeRoles.method("toLayer") + "(" + init + ")";
        }
        if (castClass.isEnum()) {
            // Grouped, so a method that follows applies to the enum rather than to toEnum's Object.
            return "((" + castClass.getName() + ") " + TALRuntimeRoles.method("toEnum") + "(" + castClass.getName() + ".class, "
                + init + "))";
        }
        return null;
    }

    /**
     * @return the simple names {@code as} accepts besides the scalar types: {@code Layer} and the source enums
     */
    static Set<String> typedValueNames() {
        return TYPED_VALUE_CLASSES.keySet();
    }

    /**
     * @return the constant a literal name converts to, a built-in layer's field or an enum constant, which no runtime
     * change removes; null for any other value
     */
    static String typedConstant(final Class<?> castClass, final CharSequence init) {
        final Matcher literal = LITERAL_NAME.matcher(init);
        if (!literal.matches()) {
            return null;
        }
        final String name = literal.group(1);
        if (castClass == Layer.class) {
            return isBuiltInLayer(name) ? Layer.class.getName() + "." + name : null;
        }
        return castClass.isEnum() && hasEnumConstant(castClass, name) ? castClass.getName() + "." + name : null;
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
            final Class<?> named = classNamed(castType);
            // Layer by its full name converts as `as Layer` does.
            return named.isEnum() || named == Layer.class ? named : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * A class by the name a rule writes, a member class with dots as Java source names it, such as
     * {@code io.opentelemetry.proto.trace.v1.Span.Event}.
     */
    private static Class<?> classNamed(final String name) throws ClassNotFoundException {
        String binary = name;
        while (true) {
            try {
                // typing only: the class is not initialized before the list accepts it
                return Class.forName(binary, false, TALDefCodegen.class.getClassLoader());
            } catch (final ClassNotFoundException e) {
                final int dot = binary.lastIndexOf('.');
                if (dot < 0) {
                    throw new ClassNotFoundException(name, e);
                }
                binary = binary.substring(0, dot) + '$' + binary.substring(dot + 1);
            }
        }
    }

    /**
     * @return the class an {@code as} names: a scalar type, {@code Layer} or a source enum by its simple name, any
     *     other class by its full name
     */
    static Class<?> resolveDefCastType(final String castType) {
        final Class<?> typed = TYPED_VALUE_CLASSES.get(castType);
        if (typed != null) {
            return typed;
        }
        final Class<?> scalar = ExprType.ofCast(castType).boxedClass;
        if (scalar != null) {
            return scalar;
        }
        try {
            return classNamed(castType);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("`as " + castType + "` names no class on the classpath", e);
        }
    }

    // ==================== Chain codegen ====================

    /**
     * A value a chain of methods, fields and indexes is applied to: a def, the input's root, a function's result or a
     * group.
     */
    public static final class ChainReceiver {
        final String code;
        final Class<?> type;
        /**
         * The element type when the receiver is a generic {@code List}, so a {@code foreach} over it types its loop
         * variable; null otherwise.
         */
        final Class<?> elementType;
        /**
         * The expression the receiver's code is the value of, {@code m} in {@code (m).put(...)}, so {@code put} and
         * {@code add} ask what it holds; null for any other receiver, a def's own variable asking by its local.
         */
        final TALValueAccess held;
        /**
         * Whether the receiver can be null, which a {@code ?.} after it checks.
         */
        final boolean canBeNull;

        public ChainReceiver(final String code, final Class<?> type, final Class<?> elementType,
                             final TALValueAccess held, final boolean canBeNull) {
            this.code = code;
            this.type = type;
            this.elementType = elementType;
            this.held = held;
            this.canBeNull = canBeNull;
        }

        /**
         * @return a def's variable as a receiver
         */
        public static ChainReceiver of(final TALLocalVar var) {
            return new ChainReceiver(var.javaVarName, var.resolvedType, var.elementType, null, true);
        }
    }

    /**
     * Where a chain's walk is: the code so far, its static type, and whether it can be null.
     */
    private static final class ChainStep {
        private String code;
        private Class<?> type;
        private boolean canBeNull;
        /**
         * Set when the last segment was a {@code ?.} that boxed a primitive to carry its null.
         */
        private boolean guardedPrimitive;
        /**
         * The element type of the last segment's generic {@code List}, kept apart from the metadata the arguments'
         * code overwrites.
         */
        private Class<?> elementType;
    }

    /**
     * Generates a chain of methods, fields and indexes on a receiver, each resolved by reflection on what the one before
     * returns, so the result is typed: a field reads its getter, {@code getX()}, {@code isX()} or {@code x()}, and a
     * primitive takes the methods of its box.
     *
     * @param sb       output buffer
     * @param receiver the value the chain starts from
     * @param chain    the segments after it
     * @param genCtx   codegen context
     */
    public static void generateDefVarChain(
            final StringBuilder sb,
            final ChainReceiver receiver,
            final List<? extends TALValueAccessSegment> chain,
            final TALGenCtx genCtx) {
        genCtx.lastResolvedElementType = null;
        if (chain.isEmpty()) {
            sb.append(receiver.code);
            genCtx.lastResolvedType = receiver.type;
            genCtx.lastResolvedElementType = receiver.elementType;
            return;
        }

        final ChainStep walk = new ChainStep();
        walk.code = receiver.code;
        walk.type = receiver.type;
        walk.canBeNull = receiver.canBeNull;

        for (int i = 0; i < chain.size(); i++) {
            final TALValueAccessSegment seg = chain.get(i);
            final boolean isLast = i == chain.size() - 1;
            if (walk.type.isPrimitive() && !(seg instanceof TALIndexSegment)) {
                // `getAsInt().toString()`: a primitive takes its methods as its box.
                walk.code = TALCodegenHelper.boxTypeName(walk.type) + ".valueOf(" + walk.code + ")";
                walk.type = DslMethods.boxed(walk.type);
            }

            if (seg instanceof TALMethodSegment) {
                final TALMethodSegment ms = (TALMethodSegment) seg;
                final String methodName = ms.getName();
                final Method method = resolveMethod(walk.type, methodName, ms.getArguments(), genCtx);
                if (method == null) {
                    throw new IllegalArgumentException(
                        "Cannot resolve method " + walk.type.getSimpleName()
                            + "." + methodName + "() in def variable chain");
                }
                TALCallables.get().require(walk.type, method);
                if (("put".equals(methodName) || "add".equals(methodName))
                    && (i > 0 || !(receiver.held != null ? ownsCollection(receiver.held, genCtx)
                    : genCtx.ownsCollection(receiver.code)))) {
                    // An input's maps and lists are its own; a rule changes only the ones it made.
                    throw new IllegalArgumentException(
                        methodName + "() changes only a map or list the rule made with emptyMap() or emptyList()"
                            + " and holds in a def, not " + walk.type.getSimpleName() + " values it reads");
                }
                callStep(walk, method, TALValueCodegen.generateArgumentsFor(ms.getArguments(), method, genCtx),
                    ms.isSafeNav(), isLast);
            } else if (seg instanceof TALFieldSegment) {
                final TALFieldSegment fs = (TALFieldSegment) seg;
                final Method getter = TALCallables.get().require(walk.type,
                    TALCodegenHelper.resolveGetter(walk.type, fs.getName()));
                if (getter == null) {
                    throw new IllegalArgumentException(
                        walk.type.getSimpleName() + "." + fs.getName() + " reads no property: a "
                            + walk.type.getSimpleName() + " has no get or is method for it. "
                            + TALCallables.get().describe(walk.type));
                }
                callStep(walk, getter, "", fs.isSafeNav(), isLast);
            } else if (seg instanceof TALIndexSegment && walk.type.isArray()) {
                final int index = ((TALIndexSegment) seg).getIndex();
                final Class<?> component = walk.type.getComponentType();
                final String access = walk.code + "[" + index + "]";
                if (walk.canBeNull && component.isPrimitive() && TALCodegenHelper.boxTypeName(component) != null) {
                    walk.code = "(" + walk.code + " == null ? null : "
                        + TALCodegenHelper.boxTypeName(component) + ".valueOf(" + access + "))";
                    walk.type = isLast ? component : DslMethods.boxed(component);
                    walk.guardedPrimitive = isLast;
                    walk.canBeNull = true;
                } else {
                    walk.code = walk.canBeNull ? "(" + walk.code + " == null ? null : " + access + ")" : access;
                    walk.type = component;
                    walk.canBeNull = !component.isPrimitive();
                }
            } else if (seg instanceof TALIndexSegment) {
                final int index = ((TALIndexSegment) seg).getIndex();
                final Method getMethod;
                try {
                    // a subscript calls get(int): the list decides, as for a call written out
                    getMethod = TALCallables.get().require(walk.type, walk.type.getMethod("get", int.class));
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(
                        "Cannot resolve index access on " + walk.type.getSimpleName() + " in def variable chain");
                }
                walk.code = walk.canBeNull ? "(" + walk.code + " == null ? null : " + walk.code + ".get(" + index + "))"
                    : walk.code + ".get(" + index + ")";
                walk.type = getMethod.getReturnType();
                walk.canBeNull = true;
            }
        }

        // The arguments' code above left its own metadata; what follows describes the chain's result alone.
        genCtx.clearExtraLogResult();
        genCtx.lastResolvedElementType = walk.elementType;
        genCtx.lastResolvedType = walk.type;
        // Already boxed, carrying the null of a safe-navigated receiver.
        genCtx.lastNullChecks = walk.guardedPrimitive;
        if (walk.type.isPrimitive() && !walk.guardedPrimitive && TALCodegenHelper.boxTypeName(walk.type) != null) {
            // Javassist does not autobox, so a primitive leaf such as `.length()` is boxed for the Object contexts
            // it lands in; the raw form stays available to numeric comparisons and arithmetic.
            genCtx.lastRawChain = walk.code;
            sb.append(TALCodegenHelper.boxTypeName(walk.type)).append(".valueOf(").append(walk.code).append(")");
            return;
        }
        sb.append(walk.code);
    }

    /**
     * One getter or method a chain calls: {@code toString()} on a value that can hold items prints them, else the call,
     * behind a null check when it is a {@code ?.} on a receiver that can be null, which boxes a primitive result to
     * carry the null.
     */
    private static void callStep(final ChainStep walk, final Method method, final String args, final boolean safeNav,
                                 final boolean last) {
        final boolean guarded = safeNav && walk.canBeNull;
        final String printed = TALCodegenHelper.printedByContent(walk.type, method, walk.code, guarded);
        if (printed != null) {
            // ?. reads a null as null, a plain call fails on it
            walk.code = printed;
            walk.type = String.class;
            walk.canBeNull = guarded;
            return;
        }
        final Class<?> returnType = method.getReturnType();
        if (last) {
            walk.elementType = TALCodegenHelper.listElementType(method.getGenericReturnType());
        }
        final String call = walk.code + "." + method.getName() + "(" + args + ")";
        if (guarded) {
            walk.code = "(" + walk.code + " == null ? null : " + (returnType.isPrimitive()
                ? TALCodegenHelper.boxTypeName(returnType) + ".valueOf(" + call + ")" : call) + ")";
            walk.type = returnType.isPrimitive() && !last ? DslMethods.boxed(returnType) : returnType;
            walk.guardedPrimitive = returnType.isPrimitive() && last;
            walk.canBeNull = true;
        } else {
            walk.code = call;
            walk.type = returnType;
            walk.canBeNull = !returnType.isPrimitive();
        }
    }

    /**
     * Resolves the method a chain calls as LAL does, ranked by {@link DslMethods#choose}: a string is never read as a
     * number, nor a number as a string. Two methods with neither more specific are a compile error asking for a
     * conversion, as is a call no method takes.
     *
     * @return the method, or null when no method has the name and the arguments' count
     */
    private static Method resolveMethod(
            final Class<?> type, final String name,
            final List<? extends TALFunctionArg> args,
            final TALGenCtx genCtx) {
        final int argCount = args != null ? args.size() : 0;
        final List<Method> candidates = new ArrayList<>();
        for (final Method m : DslMethods.of(type)) {
            if (m.getName().equals(name) && m.getParameterCount() == argCount) {
                candidates.add(m);
            }
        }
        // A visibility bridge, such as StringBuilder's substring, is the method when nothing else declares it.
        candidates.removeIf(m -> m.isBridge() && candidates.stream().anyMatch(
            other -> !other.isBridge() && Arrays.equals(other.getParameterTypes(), m.getParameterTypes())));
        if (candidates.isEmpty()) {
            TALCallables.get().require(type, name, argCount);
            return null;
        }
        final boolean anyStatic = candidates.stream().anyMatch(m -> Modifier.isStatic(m.getModifiers()));
        // A static method reached through a value, and a variable-length one, are not called.
        candidates.removeIf(m -> Modifier.isStatic(m.getModifiers()) || m.isVarArgs());
        if (candidates.isEmpty()) {
            throw TALCallables.get().notThroughAValue(type, name, anyStatic);
        }
        TALCallables.get().require(type, name, argCount);
        final Class<?>[] argTypes = TALValueCodegen.chainArgumentTypes(args, genCtx);
        final DslMethods.Choice choice = DslMethods.choose(candidates, argTypes, null);
        if (choice == null) {
            throw new IllegalArgumentException(
                type.getSimpleName() + "." + name + describe(argTypes) + " matches no method; convert the arguments "
                    + "with as, such as `as String` or `as Integer`");
        }
        if (choice.rival != null) {
            throw new IllegalArgumentException(
                type.getSimpleName() + "." + name + describe(argTypes) + " matches more than one method, "
                    + choice.method.toGenericString() + " and " + choice.rival.toGenericString()
                    + "; convert the arguments with as, such as `as String` or `as Integer`");
        }
        return choice.method;
    }

    private static String describe(final Class<?>[] argTypes) {
        final List<String> names = new ArrayList<>();
        for (final Class<?> argType : argTypes) {
            names.add(argType == null ? "null" : argType == Object.class ? "a value of unknown type"
                : argType.getSimpleName());
        }
        return "(" + String.join(", ", names) + ")";
    }
}
