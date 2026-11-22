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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;
import org.apache.skywalking.oap.server.core.dsl.DslMethods;
import org.apache.skywalking.oap.server.core.dsl.DslStrings;

/**
 * Method, property and subscript chains on a value of a known type ({@code x.a?.b(1)[0]}): each segment resolves to a
 * {@link Step} by reflection on its receiver's compile-time type, among what {@link LALCallables} allows, so a
 * misspelled one fails the compile. {@link #generateTypedChain} renders the steps and {@link #chainType} folds their
 * types without rendering anything, so the two cannot disagree.
 */
final class LALChainCodegen {

    private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private LALChainCodegen() {
        // utility class
    }

    private enum StepKind {
        /** A method of the receiver's compile-time type. */
        CALL,
        /** A bean property's getter, {@code getX()} or a boolean {@code isX()}, or a no-argument method of its name. */
        GETTER,
        /** A known collection's own no-argument method a property names ({@code xs.size}), before its elements'. */
        OWN_METHOD,
        /** An array's {@code length}. */
        ARRAY_LENGTH,
        /** A property read by the value's type when the rule runs, as Groovy reads it ({@code h.prop}). */
        RUNTIME_PROP,
        /** A subscript read by the value's type when the rule runs, as Groovy reads it ({@code h.at}). */
        RUNTIME_INDEX,
        /** An array's element. */
        ARRAY_INDEX,
        /** A subscript calling {@code get(int)}. */
        GET_INDEX,
        /**
         * The value's text through a helper: {@code ?.toString()} and {@code ?.trim()}, null-safe and reading their
         * receiver once, or an explicit {@code toString()} of a value that can hold items, which writes them.
         */
        TO_TEXT
    }

    /**
     * One segment of a chain, resolved on its receiver's compile-time type.
     */
    private static final class Step {
        final StepKind kind;
        /** The segment as it resolved: a property naming a no-argument method is that call. */
        final LALScriptModel.ValueAccessSegment segment;
        /** The method a CALL, GETTER, OWN_METHOD or GET_INDEX step calls. */
        final Method method;
        /** A CALL's arguments. */
        final List<Arg> args;
        /** The runtime helper a TO_TEXT or RUNTIME_PROP step calls. */
        final String helper;
        final Class<?> type;
        /** Whether the result is what a typed method or getter returned, not a value read at run time. */
        final boolean typedResult;
        /** Why the segment does not resolve: the emitter reports it, the type walker leaves the type unknown. */
        final IllegalArgumentException failure;

        private Step(final StepKind kind, final LALScriptModel.ValueAccessSegment segment, final Method method,
                     final List<Arg> args, final String helper, final Class<?> type, final boolean typedResult,
                     final IllegalArgumentException failure) {
            this.kind = kind;
            this.segment = segment;
            this.method = method;
            this.args = args;
            this.helper = helper;
            this.type = type;
            this.typedResult = typedResult;
            this.failure = failure;
        }

        static Step of(final StepKind kind, final LALScriptModel.ValueAccessSegment segment, final Method method,
                       final Class<?> type, final boolean typedResult) {
            return new Step(kind, segment, method, null, null, type, typedResult, null);
        }

        static Step call(final LALScriptModel.MethodSegment segment, final Method method, final List<Arg> args) {
            return new Step(StepKind.CALL, segment, method, args, null, method.getReturnType(), true, null);
        }

        static Step helper(final StepKind kind, final LALScriptModel.ValueAccessSegment segment, final String helper,
                           final Class<?> type) {
            return new Step(kind, segment, null, null, helper, type, false, null);
        }

        static Step unresolved(final IllegalArgumentException failure) {
            return new Step(null, null, null, null, null, null, false, failure);
        }

        /**
         * @return whether the step is a runtime helper taking its receiver, which reads it once and handles a null
         */
        boolean takesItsReceiver() {
            return kind == StepKind.TO_TEXT || kind == StepKind.RUNTIME_PROP || kind == StepKind.RUNTIME_INDEX;
        }

        /**
         * @return the helper call of a step that {@link #takesItsReceiver}
         */
        String applyTo(final String receiver) {
            switch (kind) {
                case TO_TEXT:
                    return helper + "(" + receiver + ")";
                case RUNTIME_PROP:
                    return helper + "(" + receiver + ", \""
                        + DslJavaSourceText.toLiteral(((LALScriptModel.FieldSegment) segment).getName()) + "\")";
                case RUNTIME_INDEX:
                    final String at = "h.at(" + receiver + ", " + index() + ")";
                    return type == Object.class ? at : "((" + type.getName() + ") " + at + ")";
                default:
                    throw new IllegalStateException("A " + kind + " step accesses its receiver");
            }
        }

        /**
         * @return the step accessing a non-null receiver
         */
        String access(final String receiver) {
            switch (kind) {
                case CALL:
                    return receiver + "." + method.getName() + "(" + argumentList(method, args) + ")";
                case GETTER:
                case OWN_METHOD:
                    return receiver + "." + method.getName() + "()";
                case ARRAY_LENGTH:
                    return receiver + ".length";
                case ARRAY_INDEX:
                    return receiver + "[" + index() + "]";
                case GET_INDEX:
                    return receiver + ".get(" + index() + ")";
                default:
                    throw new IllegalStateException("A " + kind + " step takes its receiver as an argument");
            }
        }

        /**
         * @return whether the segment is written {@code ?.}, so a null receiver reads null
         */
        boolean safeNav() {
            if (segment instanceof LALScriptModel.MethodSegment) {
                return ((LALScriptModel.MethodSegment) segment).isSafeNav();
            }
            return segment instanceof LALScriptModel.FieldSegment && ((LALScriptModel.FieldSegment) segment).isSafeNav();
        }

        /**
         * @return whether a helper step's result may be null: any but an explicit {@code toString()}'s
         */
        boolean nullableResult() {
            return kind != StepKind.TO_TEXT || !"h.text".equals(helper);
        }

        private int index() {
            return ((LALScriptModel.IndexSegment) segment).getIndex();
        }
    }

    // ==================== Rendering ====================

    static void generateTypedChain(
            final StringBuilder sb,
            final String rootExpr,
            final Class<?> rootType,
            final boolean rootCanBeNull,
            final List<LALScriptModel.ValueAccessSegment> chain,
            final LALClassGenerator.GenCtx genCtx) {
        generateTypedChain(sb, rootExpr, rootType, rootCanBeNull, chain, genCtx, false);
    }

    /**
     * Renders a chain on an expression of a known type. A primitive result is boxed, with its primitive form left in
     * {@code genCtx.lastRawChain} for numeric callers, as {@link LALValueCodegen#generateExtraLogAccess} does.
     *
     * @param rootExpr        Java expression the chain starts from; a {@code ?.} null guard reads it once
     * @param rootType        its static type
     * @param rootCanBeNull   whether a {@code ?.} right after the root needs a null guard
     * @param rootTypedResult whether the root is what a typed method returned, such as a def holding a map's
     *                        {@code get}, rather than a value read at run time
     */
    static void generateTypedChain(
            final StringBuilder sb,
            final String rootExpr,
            final Class<?> rootType,
            final boolean rootCanBeNull,
            final List<LALScriptModel.ValueAccessSegment> chain,
            final LALClassGenerator.GenCtx genCtx,
            final boolean rootTypedResult) {
        genCtx.lastRawChain = null;
        genCtx.lastNullChecks = null;
        genCtx.lastNonNull = false;
        genCtx.lastNullCheckOnObject = false;
        if (chain.isEmpty()) {
            sb.append(rootExpr);
            genCtx.lastResolvedType = rootType;
            genCtx.lastPure = SIMPLE_NAME.matcher(rootExpr).matches();
            genCtx.lastTypedResult = rootTypedResult;
            return;
        }

        String prevExpr = rootExpr;
        Class<?> currentType = rootType;
        boolean canBeNull = rootCanBeNull;
        boolean boxed = false;
        boolean typedResult = rootTypedResult;

        for (int i = 0; i < chain.size(); i++) {
            final boolean isLast = i == chain.size() - 1;
            if (currentType.isPrimitive()) {
                // Groovy calls a method of a primitive result on its box.
                prevExpr = LALCodegenHelper.box(currentType, prevExpr);
                currentType = DslMethods.boxed(currentType);
            }
            final Step step = step(chain.get(i), currentType, typedResult, args -> renderArgs(args, genCtx));
            if (step.failure != null) {
                throw step.failure;
            }
            final Class<?> receiverType = currentType;
            currentType = step.type;
            typedResult = step.typedResult;
            if (step.takesItsReceiver()) {
                prevExpr = step.applyTo(prevExpr);
                canBeNull = step.nullableResult();
                continue;
            }
            if (step.safeNav() && canBeNull) {
                // The receiver is read once: a call in it, such as a Map#remove, must not run twice.
                final String receiver = SIMPLE_NAME.matcher(prevExpr).matches()
                    ? prevExpr : genCtx.newTempVar(receiverType);
                final String guard = receiver.equals(prevExpr)
                    ? prevExpr + " == null" : "(" + receiver + " = " + prevExpr + ") == null";
                if (currentType.isPrimitive()) {
                    prevExpr = "(" + guard + " ? null : " + LALCodegenHelper.box(currentType, step.access(receiver)) + ")";
                    boxed = isLast;
                    if (!isLast) {
                        currentType = DslMethods.boxed(currentType);
                    }
                } else {
                    prevExpr = "(" + guard + " ? null : " + step.access(receiver) + ")";
                }
                canBeNull = true;
            } else {
                prevExpr = step.access(prevExpr);
                canBeNull = !currentType.isPrimitive();
            }
        }

        genCtx.lastResolvedType = currentType;
        genCtx.lastRawChain = null;
        genCtx.lastNullChecks = null;
        genCtx.lastNullCheckOnObject = false;
        genCtx.lastPure = false;
        genCtx.lastTypedResult = typedResult;
        if (currentType.isPrimitive() && !boxed) {
            genCtx.lastRawChain = prevExpr;
            genCtx.lastNonNull = true;
            prevExpr = LALCodegenHelper.box(currentType, prevExpr);
        }
        sb.append(prevExpr);
    }

    // ==================== Typing ====================

    static Class<?> chainType(final Class<?> rootType,
                              final List<LALScriptModel.ValueAccessSegment> chain,
                              final LALClassGenerator.GenCtx genCtx) {
        return chainType(rootType, chain, genCtx, false);
    }

    /**
     * @return the type {@link #generateTypedChain} leaves the chain at, or null when a segment does not resolve (the
     *     emitter then reports it); argument types are guessed where rendering would find them ({@link #guessArgs})
     */
    static Class<?> chainType(final Class<?> rootType,
                              final List<LALScriptModel.ValueAccessSegment> chain,
                              final LALClassGenerator.GenCtx genCtx,
                              final boolean rootTypedResult) {
        Class<?> type = rootType;
        boolean typedResult = rootTypedResult;
        for (final LALScriptModel.ValueAccessSegment segment : chain) {
            final Step step = step(segment, DslMethods.boxed(type), typedResult, args -> guessArgs(args, genCtx));
            if (step.failure != null) {
                return null;
            }
            type = step.type;
            typedResult = step.typedResult;
        }
        return type;
    }

    // ==================== Steps ====================

    /**
     * Resolves one segment on a receiver of a reference type. A refused getter or subscript fails here, for both the
     * emitter and the type walker; a segment that names nothing, or a call that does not resolve, is a step carrying
     * its failure.
     *
     * @param argsOf the arguments of a call: rendered, or their types guessed
     */
    private static Step step(final LALScriptModel.ValueAccessSegment written, final Class<?> receiver,
                             final boolean typedResult,
                             final Function<List<LALScriptModel.FunctionArg>, List<Arg>> argsOf) {
        final LALScriptModel.ValueAccessSegment segment = asCall(written, receiver);
        if (segment instanceof LALScriptModel.MethodSegment) {
            return callStep((LALScriptModel.MethodSegment) segment, receiver, typedResult, argsOf);
        }
        if (segment instanceof LALScriptModel.FieldSegment) {
            return propertyStep((LALScriptModel.FieldSegment) segment, receiver);
        }
        return indexStep((LALScriptModel.IndexSegment) segment, receiver, typedResult);
    }

    private static Step callStep(final LALScriptModel.MethodSegment segment, final Class<?> receiver,
                                 final boolean typedResult,
                                 final Function<List<LALScriptModel.FunctionArg>, List<Arg>> argsOf) {
        final String name = segment.getName();
        if (segment.getArguments().isEmpty()) {
            if (segment.isSafeNav()
                    && ("toString".equals(name) || "trim".equals(name) && trimsAsAString(receiver, typedResult))) {
                return Step.helper(StepKind.TO_TEXT, segment, "toString".equals(name) ? "h.toStr" : "h.trim",
                    String.class);
            }
            if (!segment.isSafeNav() && "toString".equals(name) && DslStrings.mayHoldItems(receiver)) {
                // An array writes its elements ([low, high]), inside a list or a value of unknown type too.
                return Step.helper(StepKind.TO_TEXT, segment, "h.text", String.class);
            }
        }
        final List<Arg> args;
        final Method method;
        try {
            args = argsOf.apply(segment.getArguments());
            method = resolveMethod(receiver, name, args);
        } catch (final IllegalArgumentException e) {
            return Step.unresolved(e);
        }
        if (method == null) {
            return Step.unresolved(new IllegalArgumentException(
                "Cannot resolve method " + receiver.getSimpleName() + "." + name + "(" + describeArgs(args)
                    + ") in a chain. " + LALCallables.get().describe(receiver)));
        }
        if (method.getReturnType() == void.class) {
            return Step.unresolved(new IllegalArgumentException(
                receiver.getSimpleName() + "." + name + "() returns no value"));
        }
        return Step.call(segment, method, args);
    }

    private static Step propertyStep(final LALScriptModel.FieldSegment segment, final Class<?> receiver) {
        final String name = segment.getName();
        final Method own = collectionMethod(receiver, name);
        if (own == null && readsPropertyAtRunTime(receiver)) {
            return Step.helper(StepKind.RUNTIME_PROP, segment, segment.isSafeNav() ? "h.safeProp" : "h.prop",
                Object.class);
        }
        if (own != null) {
            return Step.of(StepKind.OWN_METHOD, segment, own, own.getReturnType(), true);
        }
        if (receiver.isArray() && "length".equals(name)) {
            return Step.of(StepKind.ARRAY_LENGTH, segment, null, int.class, true);
        }
        final Method getter = findGetter(receiver, name);
        if (getter == null) {
            return Step.unresolved(new IllegalArgumentException(
                "Cannot resolve field/getter " + receiver.getSimpleName() + "." + name + " in a chain. "
                    + LALCallables.get().describe(receiver)));
        }
        return Step.of(StepKind.GETTER, segment, getter, getter.getReturnType(), true);
    }

    private static Step indexStep(final LALScriptModel.IndexSegment segment, final Class<?> receiver,
                                  final boolean typedResult) {
        if (receiver.isArray()) {
            return Step.of(StepKind.ARRAY_INDEX, segment, null, receiver.getComponentType(), typedResult);
        }
        if (subscriptsAtRunTime(receiver)) {
            return Step.helper(StepKind.RUNTIME_INDEX, segment, null, elementType(receiver));
        }
        final Method get = findGetByIndex(receiver);
        if (get == null) {
            return Step.unresolved(new IllegalArgumentException(
                "Cannot resolve index access on " + receiver.getSimpleName() + " in a chain"));
        }
        return Step.of(StepKind.GET_INDEX, segment, get, get.getReturnType(), typedResult);
    }

    /**
     * A property Groovy reads by the value's type at run time: a map's key, a collection's elements', or any
     * property of a value whose type the compiler does not know.
     */
    private static boolean readsPropertyAtRunTime(final Class<?> type) {
        return type == Object.class || Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type);
    }

    private static boolean subscriptsAtRunTime(final Class<?> type) {
        return type == Object.class || Iterable.class.isAssignableFrom(type)
            || CharSequence.class.isAssignableFrom(type);
    }

    private static Class<?> elementType(final Class<?> type) {
        if (CharSequence.class.isAssignableFrom(type)) {
            return String.class;
        }
        final Method get = findGetByIndex(type);
        return get == null ? Object.class : DslMethods.boxed(get.getReturnType());
    }

    /**
     * A bean property's getter, {@code getX()} or a boolean {@code isX()}, or else a no-argument method of that
     * name, when the list allows it.
     */
    private static Method findGetter(final Class<?> type, final String name) {
        final String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        final Method getter = findNoArgMethod(type, "get" + capitalized);
        if (getter != null) {
            return LALCallables.get().require(type, getter);
        }
        final Method is = findNoArgMethod(type, "is" + capitalized);
        if (is != null && (is.getReturnType() == boolean.class || is.getReturnType() == Boolean.class)) {
            return LALCallables.get().require(type, is);
        }
        final Method own = findNoArgMethod(type, name);
        return own == null ? null : LALCallables.get().require(type, own);
    }

    /**
     * A property naming a no-argument method of the value's own ({@code xs.size}, {@code a.toString}), not a getter
     * nor a map's key, is that call, so it runs as the call written with {@code ()} does; on a value of unknown type
     * only a call every value takes is one.
     */
    private static LALScriptModel.ValueAccessSegment asCall(final LALScriptModel.ValueAccessSegment segment,
                                                            final Class<?> type) {
        if (!(segment instanceof LALScriptModel.FieldSegment)) {
            return segment;
        }
        final LALScriptModel.FieldSegment field = (LALScriptModel.FieldSegment) segment;
        final String name = field.getName();
        if (readsPropertyAtRunTime(type) && collectionMethod(type, name) == null
            && (type != Object.class || !everyValueTakes(name, 0))) {
            return segment;
        }
        final String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        if (findNoArgMethod(type, "get" + capitalized) != null || findNoArgMethod(type, "is" + capitalized) != null
            || findNoArgMethod(type, name) == null) {
            return segment;
        }
        return new LALScriptModel.MethodSegment(name, List.of(), field.isSafeNav());
    }

    /**
     * Whether a chain after a text group starts with a method only a string has: a call every value takes
     * ({@code toString()}) takes the value as it is.
     */
    static boolean callsAStringMethod(final List<LALScriptModel.ValueAccessSegment> rest) {
        if (rest.isEmpty() || !(rest.get(0) instanceof LALScriptModel.MethodSegment)) {
            return false;
        }
        final LALScriptModel.MethodSegment first = (LALScriptModel.MethodSegment) rest.get(0);
        return !everyValueTakes(first.getName(), first.getArguments().size());
    }

    /**
     * @return whether a value of any type takes the call: the {@code Object} methods LAL lists
     */
    private static boolean everyValueTakes(final String name, final int arity) {
        return LALCallables.get().allows(Object.class, name, arity);
    }

    /**
     * A known collection's own no-argument method a property names ({@code xs.size}, {@code xs.isEmpty}), or null.
     */
    private static Method collectionMethod(final Class<?> type, final String field) {
        if (!Collection.class.isAssignableFrom(type)) {
            return null;
        }
        final Method method = findNoArgMethod(type, field);
        return method != null && LALCallables.get().allows(type, method) ? method : null;
    }

    /**
     * Whether {@code ?.trim()} takes its receiver as a string: a string, or a value read at run time (a parsed
     * field); not what a typed method returned as an {@code Object}.
     */
    private static boolean trimsAsAString(final Class<?> type, final boolean typedResult) {
        return type == String.class || type == Object.class && !typedResult;
    }

    private static Method findNoArgMethod(final Class<?> type, final String name) {
        try {
            return DslMethods.find(type, name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /**
     * The {@code get(int)} a subscript calls, when the list allows it.
     */
    private static Method findGetByIndex(final Class<?> type) {
        try {
            return LALCallables.get().require(type, type.getMethod("get", int.class));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // ==================== Calls ====================

    /**
     * A method argument, rendered or typed before its method is chosen.
     */
    static final class Arg {
        /** The argument as an object, a null kept; {@code "null"} for the null literal. */
        final String boxed;
        /** The argument as a primitive, for a primitive parameter, or null when it has none. */
        final String primitive;
        /** Its static type: a primitive type when {@link #primitive} is set, Object when unknown, null for null. */
        final Class<?> type;
        /** A text parser's group in one branch, so a string there: a string parameter takes it first. */
        final boolean textGroup;

        Arg(final String boxed, final String primitive, final Class<?> type) {
            this(boxed, primitive, type, false);
        }

        Arg(final String boxed, final String primitive, final Class<?> type, final boolean textGroup) {
            this.boxed = boxed;
            this.primitive = primitive;
            this.type = type;
            this.textGroup = textGroup;
        }
    }

    /**
     * The method of that name the arguments fit best ({@link DslMethods#choose}), among those the list allows.
     *
     * @return the method, or null when none takes these arguments
     * @throws IllegalArgumentException when two methods fit equally well, or only a refused one fits
     */
    private static Method resolveMethod(final Class<?> type, final String name, final List<Arg> args) {
        final List<Method> candidates = new ArrayList<>();
        Method refused = null;
        for (final Method m : DslMethods.of(type)) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.size() || m.isBridge()) {
                continue;
            }
            if (LALCallables.get().allows(type, m)) {
                candidates.add(m);
            } else {
                refused = m;
            }
        }
        final Class<?>[] types = new Class<?>[args.size()];
        final boolean[] texts = new boolean[args.size()];
        for (int i = 0; i < args.size(); i++) {
            types[i] = args.get(i).type;
            texts[i] = args.get(i).textGroup;
        }
        final DslMethods.Choice choice = DslMethods.choose(candidates, types, texts);
        if (choice == null) {
            return refused == null ? null : LALCallables.get().require(type, refused);
        }
        if (choice.rival != null) {
            throw new IllegalArgumentException(
                type.getSimpleName() + "." + name + "(" + describeArgs(args) + ") matches more than one method: "
                    + choice.method.toGenericString() + " and " + choice.rival.toGenericString()
                    + "; cast the arguments");
        }
        return choice.method;
    }

    /**
     * The arguments rendered for the chosen method's parameters.
     */
    private static String argumentList(final Method method, final List<Arg> args) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(argumentFor(args.get(i), method.getParameterTypes()[i]));
        }
        return sb.toString();
    }

    private static String argumentFor(final Arg arg, final Class<?> param) {
        if (param.isPrimitive()) {
            if (arg.primitive != null) {
                return arg.type == param ? arg.primitive : "(" + param.getName() + ") (" + arg.primitive + ")";
            }
            final Class<?> unboxed = arg.type == Boolean.class ? boolean.class : DslMethods.unboxed(arg.type);
            if (unboxed != null) {
                // A box, such as a Character def's, unboxed and widened.
                final String value = "((" + arg.type.getName() + ") " + arg.boxed + ")." + unboxed.getName() + "Value()";
                return unboxed == param ? value : "(" + param.getName() + ") " + value;
            }
            // A null fails the conversion, as Groovy fails to pass a null for a primitive.
            return LALValueCodegen.conversionHelper(param) + "(" + arg.boxed + ")";
        }
        if (arg.type == null || param.isAssignableFrom(DslMethods.boxed(arg.type))) {
            return arg.boxed;
        }
        return "((" + param.getCanonicalName() + ") " + arg.boxed + ")";
    }

    private static String describeArgs(final List<Arg> args) {
        final StringBuilder sb = new StringBuilder();
        for (final Arg arg : args) {
            sb.append(sb.length() == 0 ? "" : ", ").append(arg.type == null ? "null" : arg.type.getSimpleName());
        }
        return sb.toString();
    }

    /**
     * Renders the arguments of a method call before the method is chosen: each in its object form, a null kept,
     * and its primitive form when it has one, so the chosen overload takes the form its parameter needs. The
     * caller's {@code last*} type state is kept.
     */
    static List<Arg> renderArgs(final List<LALScriptModel.FunctionArg> args,
                                final LALClassGenerator.GenCtx genCtx) {
        final Class<?> savedType = genCtx.lastResolvedType;
        final String savedRaw = genCtx.lastRawChain;
        final String savedNullChecks = genCtx.lastNullChecks;
        final boolean savedNonNull = genCtx.lastNonNull;
        final boolean savedOnObject = genCtx.lastNullCheckOnObject;
        final boolean savedPure = genCtx.lastPure;
        final List<Arg> rendered = new ArrayList<>();
        for (final LALScriptModel.FunctionArg arg : args) {
            rendered.add(renderArg(arg, genCtx));
        }
        genCtx.lastResolvedType = savedType;
        genCtx.lastRawChain = savedRaw;
        genCtx.lastNullChecks = savedNullChecks;
        genCtx.lastNonNull = savedNonNull;
        genCtx.lastNullCheckOnObject = savedOnObject;
        genCtx.lastPure = savedPure;
        return rendered;
    }

    private static Arg renderArg(final LALScriptModel.FunctionArg arg, final LALClassGenerator.GenCtx genCtx) {
        final LALScriptModel.ValueAccess va = arg.getValue();
        final String cast = arg.getCastType();
        if (cast != null) {
            final LALValueCodegen.ExprType castET = LALValueCodegen.castToType(cast);
            final StringBuilder boxed = new StringBuilder();
            LALValueCodegen.generateBoxedValue(boxed, va, cast, genCtx);
            if (castET.isNumeric() || castET == LALValueCodegen.ExprType.BOOLEAN) {
                // The primitive form fails on a null, as Groovy fails to pass one for a primitive parameter.
                final StringBuilder primitive = new StringBuilder();
                LALValueCodegen.generateCastedValueAccess(primitive, va, cast, genCtx);
                return new Arg(boxed.toString(), primitive.toString(), castET.primitiveClass);
            }
            return new Arg(boxed.toString(), null, String.class);
        }
        if (va.isBareLiteral()) {
            final String literal = va.getSegments().get(0);
            if (va.isStringLiteral()) {
                return new Arg("\"" + DslJavaSourceText.toLiteral(literal) + "\"", null, String.class);
            }
            if (va.isNumberLiteral()) {
                final LALLiterals.NumericLiteral lit = LALLiterals.NumericLiteral.parse(literal);
                return new Arg(lit.boxed(), lit.javaText, lit.type.primitiveClass);
            }
            return "null".equals(literal)
                ? new Arg("null", null, null)
                : new Arg("Boolean." + literal.toUpperCase(), literal, boolean.class);
        }
        final StringBuilder buf = new StringBuilder();
        LALValueCodegen.generateValueAccess(buf, va, genCtx);
        final Class<?> type = genCtx.lastResolvedType;
        if ((type == null || type == Object.class) && LALValueCodegen.isTextGroupOfABranch(va, genCtx)) {
            return new Arg(buf.toString(), null, Object.class, true);
        }
        if (type != null && type.isPrimitive()) {
            final String helper = LALValueCodegen.conversionHelper(type);
            final String primitive = genCtx.lastRawChain != null && genCtx.lastNullChecks == null
                ? genCtx.lastRawChain : helper != null ? helper + "(" + buf + ")" : null;
            return new Arg(buf.toString(), primitive, primitive != null ? type : DslMethods.boxed(type));
        }
        return new Arg(buf.toString(), null, type != null ? type : Object.class);
    }

    /**
     * The static types of a call's arguments, as {@link #renderArgs} would find them where they show without
     * generating code (literals and casts); any other argument is of unknown type.
     */
    static List<Arg> guessArgs(final List<LALScriptModel.FunctionArg> args, final LALClassGenerator.GenCtx genCtx) {
        final List<Arg> guessed = new ArrayList<>();
        for (final LALScriptModel.FunctionArg arg : args) {
            final LALScriptModel.ValueAccess va = arg.getValue();
            final LALValueCodegen.ExprType castET = LALValueCodegen.castToType(arg.getCastType());
            final Class<?> type;
            if (castET.isNumeric() || castET == LALValueCodegen.ExprType.BOOLEAN) {
                type = castET.primitiveClass;
            } else if (castET == LALValueCodegen.ExprType.STRING || va.isBareLiteral() && va.isStringLiteral()) {
                type = String.class;
            } else if (va.isBareLiteral() && va.isNumberLiteral()) {
                type = LALLiterals.NumericLiteral.parse(va.getSegments().get(0)).type.primitiveClass;
            } else if (arg.getCastType() == null && va.isBareIdentifier()
                    && genCtx.localVars.containsKey(va.getSegments().get(0))) {
                // A def, whose type the codegen knows.
                final Class<?> defType = chainType(
                    genCtx.localVars.get(va.getSegments().get(0)).resolvedType, va.getChain(), genCtx);
                type = defType != null ? defType : Object.class;
            } else {
                final LALValueCodegen.ExprType inferred = arg.getCastType() == null
                    ? LALValueCodegen.inferType(va, genCtx) : LALValueCodegen.ExprType.UNKNOWN;
                type = inferred.isNumeric() || inferred == LALValueCodegen.ExprType.BOOLEAN ? inferred.primitiveClass
                    : inferred == LALValueCodegen.ExprType.STRING ? String.class : Object.class;
            }
            guessed.add(new Arg("", type.isPrimitive() ? "" : null, type));
        }
        return guessed;
    }
}
