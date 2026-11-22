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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.oap.server.core.dsl.DslMethods;

/**
 * The static type of a closure value where the closure's own text decides it: a literal, a label (a string), a method
 * chain resolved on its receiver's class, a typed local, arithmetic over those. The generated Java is untyped source,
 * and Javassist neither boxes nor unboxes, so the code generator boxes, unboxes or treats a value as {@code void} only
 * where this knows the type. {@code null} is "not known".
 */
final class ClosureTypes {
    private final ClosureScope scope;
    /** The closure's parameter: a chain on it reads the labels. */
    private String paramName;

    ClosureTypes(final ClosureScope scope) {
        this.scope = scope;
    }

    /**
     * Starts a closure body whose parameter, declared in the scope, is {@code paramName}.
     */
    void begin(final String paramName) {
        this.paramName = paramName;
    }

    /**
     * Statically a primitive to Groovy too: a whole literal, a local declared with a primitive type, arithmetic of
     * those. Groovy compares two such as Java does (NaN unequal to itself, -0.0 equal to 0.0); a method call or a def
     * it compares at run time.
     */
    boolean groovyStatic(final MALExpressionModel.ClosureExpr expr) {
        if (expr instanceof MALExpressionModel.ClosureNumberLiteral) {
            return ((MALExpressionModel.ClosureNumberLiteral) expr).isIntegral();
        }
        if (expr instanceof MALExpressionModel.ClosureMethodChain) {
            final MALExpressionModel.ClosureMethodChain chain = (MALExpressionModel.ClosureMethodChain) expr;
            return chain.getSegments().isEmpty() && scope.isStatic(chain.getTarget());
        }
        if (expr instanceof MALExpressionModel.ClosureBinaryExpr) {
            return groovyStatic(((MALExpressionModel.ClosureBinaryExpr) expr).getLeft())
                && groovyStatic(((MALExpressionModel.ClosureBinaryExpr) expr).getRight());
        }
        if (expr instanceof MALExpressionModel.ClosureUnaryNegExpr) {
            return groovyStatic(((MALExpressionModel.ClosureUnaryNegExpr) expr).getOperand());
        }
        return false;
    }

    /**
     * A call on the label map that reads a label: get(k), remove(k), or getOrDefault(k, d) with a string (or null)
     * fallback written out, so the value is a string.
     */
    boolean isLabelRead(final MALExpressionModel.ClosureMethodCallSeg call) {
        final List<MALExpressionModel.ClosureExpr> args = call.getArguments();
        if (args.size() == 1) {
            return "get".equals(call.getName()) || "remove".equals(call.getName());
        }
        // getOrDefault with a string fallback, and put, whose value is the label it replaces
        return args.size() == 2 && ("put".equals(call.getName())
            || "getOrDefault".equals(call.getName()) && (args.get(1) instanceof MALExpressionModel.ClosureNullLiteral
            || typeOf(args.get(1)) == String.class));
    }

    /**
     * @return whether both sides of a comparison are numbers Groovy types statically ({@link #groovyStatic}), which it
     *     compares as Java compares the primitives
     */
    boolean bothGroovyStatic(final MALExpressionModel.ClosureComparison cc) {
        return groovyStatic(cc.getLeft()) && groovyStatic(cc.getRight())
            && isNumber(typeOf(cc.getLeft())) && isNumber(typeOf(cc.getRight()));
    }

    /**
     * @return the class a declared type name denotes, null for {@code def} or a name not resolvable here
     */
    static Class<?> forName(final String typeName) {
        switch (typeName) {
            case "def":
                return null;
            case "boolean":
                return boolean.class;
            case "byte":
                return byte.class;
            case "char":
                return char.class;
            case "short":
                return short.class;
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "Map":
            case "java.util.Map":
                return Map.class;
            case "List":
            case "java.util.List":
                return List.class;
            default:
                break;
        }
        int dims = 0;
        String base = typeName;
        while (base.endsWith("[]")) {
            base = base.substring(0, base.length() - 2);
            dims++;
        }
        Class<?> type = dims > 0 ? forName(base) : null;
        if (type == null) {
            try {
                // typing only: the class is not initialized
                type = Class.forName(base.contains(".") ? base : "java.lang." + base, false,
                    ClosureTypes.class.getClassLoader());
            } catch (final ClassNotFoundException e) {
                return null;
            }
        }
        for (int i = 0; i < dims; i++) {
            type = Array.newInstance(type, 0).getClass();
        }
        return type;
    }

    /**
     * @return the Java source name for a local: the declared name, or the inferred type's for {@code def}
     */
    static String javaName(final Class<?> type, final String declared) {
        // qualified, as Javassist resolves simple names against java.lang only (Map is java.util.Map)
        return type != null ? type.getCanonicalName() : declared;
    }

    Class<?> typeOf(final MALExpressionModel.ClosureExpr expr) {
        if (expr instanceof MALExpressionModel.ClosureStringLiteral
            || expr instanceof MALExpressionModel.ClosureInterpolation) {
            return String.class;
        }
        if (expr instanceof MALExpressionModel.ClosureNumberLiteral) {
            final MALExpressionModel.ClosureNumberLiteral number = (MALExpressionModel.ClosureNumberLiteral) expr;
            if (!number.isIntegral()) {
                return double.class;
            }
            return number.getValue() >= Integer.MIN_VALUE && number.getValue() <= Integer.MAX_VALUE
                ? int.class : long.class;
        }
        if (expr instanceof MALExpressionModel.ClosureBoolLiteral) {
            return boolean.class;
        }
        if (expr instanceof MALExpressionModel.ClosureMapLiteral) {
            return Map.class;
        }
        if (expr instanceof MALExpressionModel.ClosureRegexMatchExpr) {
            return String[][].class;
        }
        if (expr instanceof MALExpressionModel.ClosureExprCondition) {
            return typeOf(((MALExpressionModel.ClosureExprCondition) expr).getExpr());
        }
        if (expr instanceof MALExpressionModel.ClosureCondition) {
            return boolean.class;
        }
        if (expr instanceof MALExpressionModel.ClosureUnaryNegExpr) {
            // -x keeps the operand's number type (byte, short and char promote to int; a quotient stays a BigDecimal),
            // or is computed at run time
            final Class<?> declared = typeOf(((MALExpressionModel.ClosureUnaryNegExpr) expr).getOperand());
            if (declared == BigDecimal.class) {
                return BigDecimal.class;
            }
            final Class<?> operand = numericOf(declared);
            if (operand == null) {
                return Object.class;
            }
            return operand == char.class ? int.class : operand;
        }
        if (expr instanceof MALExpressionModel.ClosureBinaryExpr) {
            final MALExpressionModel.ClosureBinaryExpr bin = (MALExpressionModel.ClosureBinaryExpr) expr;
            final Class<?> left = typeOf(bin.getLeft());
            final Class<?> right = typeOf(bin.getRight());
            if (bin.getOp() == MALExpressionModel.ArithmeticOp.ADD && isContainer(left)) {
                // Groovy appends to the array or collection (aliases + t.extra): not a concatenation
                return null;
            }
            if (bin.getOp() == MALExpressionModel.ArithmeticOp.ADD && (left == String.class || right == String.class)) {
                return String.class;
            }
            if (left == BigDecimal.class || right == BigDecimal.class) {
                // arithmetic with a quotient, as Groovy computes it: a BigDecimal with whole numbers or quotients
                // ((ns / 1000) * 2), a double beside a floating number
                final Class<?> other = left == BigDecimal.class ? right : left;
                if (other == BigDecimal.class || isIntegral(numericOf(other))) {
                    return BigDecimal.class;
                }
                if (isFloating(numericOf(other))) {
                    return double.class;
                }
                return other == void.class ? null : Object.class;
            }
            // a decimal literal is a double, beside a whole number too (n * 2.0 is 4.0)
            final Class<?> l = numericOf(left);
            final Class<?> r = numericOf(right);
            if (l != null && r != null) {
                if (bin.getOp() == MALExpressionModel.ArithmeticOp.DIV) {
                    // two whole numbers: the BigDecimal quotient Groovy holds (7 / 2 is 3.5, ns / 1000000 exact);
                    // with a floating operand or a decimal literal, a double
                    return isIntegral(l) && isIntegral(r) ? BigDecimal.class : double.class;
                }
                return widen(l, r);
            }
            if (left == void.class || right == void.class) {
                return null;
            }
            // An operand the closure does not type as a number (a def held as Object, a ternary of mixed numbers):
            // computed at run time as Groovy computes it (MalRuntimeHelper.plus/minus/multiply/divide).
            return Object.class;
        }
        if (expr instanceof MALExpressionModel.ClosureTernaryExpr) {
            final MALExpressionModel.ClosureTernaryExpr ternary = (MALExpressionModel.ClosureTernaryExpr) expr;
            return ternaryType(ternary.getTrueExpr(), ternary.getFalseExpr());
        }
        if (expr instanceof MALExpressionModel.ClosureElvisExpr) {
            // a primary and a fallback of one String or primitive type keep it; any other known pair is an Object
            final MALExpressionModel.ClosureElvisExpr elvis = (MALExpressionModel.ClosureElvisExpr) expr;
            final Class<?> primary = typeOf(elvis.getPrimary());
            final Class<?> fallback = typeOf(elvis.getFallback());
            final Class<?> type = same(primary, fallback);
            if (type == String.class || type == int.class || type == long.class || type == double.class
                || type == float.class || type == short.class || type == byte.class || type == boolean.class) {
                return type;
            }
            // t.job?.length() ?: 0: an Integer and an int give the Integer (the code generator casts the result)
            if (primary != null && fallback != null && DslMethods.boxed(primary) == DslMethods.boxed(fallback)) {
                return DslMethods.boxed(primary);
            }
            // known, different and not both numbers (t.host ?: fallback): an Object, as for a ternary
            return primary != null && fallback != null && primary != void.class && fallback != void.class
                && !(computesAsNumber(primary) && computesAsNumber(fallback)) ? Object.class : null;
        }
        if (expr instanceof MALExpressionModel.ClosureExprChain) {
            final MALExpressionModel.ClosureExprChain chain = (MALExpressionModel.ClosureExprChain) expr;
            return chainType(typeOf(chain.getBase()), false, false, chain.getSegments());
        }
        if (expr instanceof MALExpressionModel.ClosureMethodChain) {
            final MALExpressionModel.ClosureMethodChain chain = (MALExpressionModel.ClosureMethodChain) expr;
            final List<MALExpressionModel.ClosureChainSegment> segments = chain.getSegments();
            if (!scope.isDeclared(chain.getTarget())) {
                // a static call on a class of the list (Long.parseLong(...), java.util.Arrays.asList(...)): typed by
                // the method it calls
                final StaticRoot root = resolveStatic(chain.getTarget(), segments);
                return root == null || root.consumed == segments.size() ? null
                    : chainType(root.type, false, true, segments.subList(root.consumed, segments.size()));
            }
            final Class<?> target = scope.localType(chain.getTarget());
            return chainType(target, chain.getTarget().equals(paramName) && target == Map.class, false, segments);
        }
        return null;
    }

    /**
     * A class of the list's static calls a chain starts at.
     */
    static final class StaticRoot {
        final Class<?> type;
        /** The chain's first segments that spell the class's full name (java.util.Arrays: util, Arrays). */
        final int consumed;

        private StaticRoot(final Class<?> type, final int consumed) {
            this.type = type;
            this.consumed = consumed;
        }
    }

    /**
     * @return the class of the list's static calls a chain on an undeclared name starts at, named by its simple name
     *     (Integer) or by its full one, read through the chain's first fields (java.util.Arrays); null for none
     */
    static StaticRoot resolveStatic(final String target, final List<MALExpressionModel.ClosureChainSegment> segments) {
        final Class<?> simple = MALCallables.listedClass(target);
        if (simple != null) {
            return new StaticRoot(simple, 0);
        }
        final StringBuilder name = new StringBuilder(target);
        for (int i = 0; i < segments.size() && segments.get(i) instanceof MALExpressionModel.ClosureFieldAccess; i++) {
            name.append('.').append(((MALExpressionModel.ClosureFieldAccess) segments.get(i)).getName());
            final Class<?> qualified = MALCallables.listedClass(name.toString());
            if (qualified != null) {
                return new StaticRoot(qualified, i + 1);
            }
        }
        return null;
    }

    private Class<?> chainType(final Class<?> receiver, final boolean labels, final boolean onClass,
                               final List<MALExpressionModel.ClosureChainSegment> segments) {
        final Class<?>[] receivers = receiverTypes(receiver, labels, onClass, segments);
        return receivers[receivers.length - 1];
    }

    /**
     * @param labels  the receiver is the closure's label map: an entry, read or removed, is a string
     * @param onClass the receiver is a class named in the rule: a first field reads its static field
     * @return the type each segment of a chain is applied to, and last the chain's own (null where not known)
     */
    Class<?>[] receiverTypes(final Class<?> receiver, final boolean labels, final boolean onClass,
                             final List<MALExpressionModel.ClosureChainSegment> segments) {
        final Class<?>[] receivers = new Class<?>[segments.size() + 1];
        receivers[0] = receiver;
        for (int i = 0; i < segments.size(); i++) {
            receivers[i + 1] = onClass && i == 0 && segments.get(0) instanceof MALExpressionModel.ClosureFieldAccess
                ? publicFieldType(receiver, ((MALExpressionModel.ClosureFieldAccess) segments.get(0)).getName())
                : segmentType(receivers[i], labels && i == 0, segments.get(i));
        }
        return receivers;
    }

    /**
     * @return the getter a property of a value reads ({@code isX} for a boolean one that has no {@code getX}), or
     *     null when the type has neither
     */
    static String getterOf(final Class<?> type, final String property) {
        final String capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        if (type == null || DslMethods.has(type, "get" + capitalized, 0)) {
            return "get" + capitalized;
        }
        final Class<?> is = DslMethods.commonReturnType(type, "is" + capitalized, 0);
        return is == boolean.class || is == Boolean.class ? "is" + capitalized : null;
    }

    private Class<?> segmentType(final Class<?> type, final boolean labelMap,
                                 final MALExpressionModel.ClosureChainSegment seg) {
        if (type == null || type == void.class) {
            return null;
        }
        if (labelMap && (seg instanceof MALExpressionModel.ClosureFieldAccess
            || seg instanceof MALExpressionModel.ClosureIndexAccess)) {
            return String.class;
        }
        if (seg instanceof MALExpressionModel.ClosureMethodCallSeg) {
            final MALExpressionModel.ClosureMethodCallSeg call = (MALExpressionModel.ClosureMethodCallSeg) seg;
            if (labelMap && isLabelRead(call)) {
                return String.class;
            }
            final Class<?> result = type.isArray() && "size".equals(call.getName()) && call.getArguments().isEmpty()
                ? int.class
                : returnType(type, call.getName(), argumentPlan(type, call.getName(), call.getArguments()).types);
            // ?. yields the boxed result or null
            return call.isSafeNav() && result != null && result.isPrimitive() ? DslMethods.boxed(result) : result;
        }
        if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
            // an array's element; a string's character as the one-character String Groovy gives; a list's element
            return type.isArray() ? type.getComponentType() : CharSequence.class.isAssignableFrom(type)
                ? String.class : List.class.isAssignableFrom(type) ? Object.class : null;
        }
        final String name = ((MALExpressionModel.ClosureFieldAccess) seg).getName();
        if (type.isArray() && "length".equals(name)) {
            return int.class;
        }
        final String getter = getterOf(type, name);
        return getter == null ? null : returnType(type, getter, 0);
    }

    /**
     * @return a public field's type (Boolean.TRUE), else null
     */
    private static Class<?> publicFieldType(final Class<?> type, final String name) {
        try {
            return type.getField(name).getType();
        } catch (final NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * @return the return type every public method of that name and arity shares, else null
     */
    private static Class<?> returnType(final Class<?> type, final String name, final int arity) {
        return returnType(type, name, new Class<?>[arity]);
    }

    /**
     * How a call's arguments pass, as the code generator emits them: per argument the type its overload is chosen
     * with, the primitive the value is converted to first (or null), and whether Groovy would pick the overload only
     * at run time in a way no single overload reproduces.
     */
    static final class ArgumentPlan {
        final Class<?>[] types;
        final Class<?>[] converted;
        final boolean[] unresolved;

        private ArgumentPlan(final int size) {
            types = new Class<?>[size];
            converted = new Class<?>[size];
            unresolved = new boolean[size];
        }
    }

    /**
     * The arguments of a call as Groovy dispatches them. An argument whose kind is known only at run time (a ternary
     * or ?: of different kinds, a def held as Object) or a quotient (a BigDecimal) is dispatched by Groovy on the value:
     * every combination of the kinds the arguments can take is resolved, and the call takes the one overload they all
     * pick; with one signature, that one. Overloads alike but for the parameter, one of them taking an Object, take
     * such an argument as it is (String.valueOf(t.port ?: 443)); overloads that compute differently
     * (String.indexOf(int) or indexOf(String), String.valueOf(char[]) or valueOf(Object)) leave it unresolved.
     */
    ArgumentPlan argumentPlan(final Class<?> owner, final String name,
                              final List<MALExpressionModel.ClosureExpr> args) {
        final int size = args.size();
        final ArgumentPlan plan = new ArgumentPlan(size);
        final List<List<MALExpressionModel.ClosureExpr>> leaves = new ArrayList<>();
        boolean dispatched = false;
        for (int i = 0; i < size; i++) {
            final MALExpressionModel.ClosureExpr arg = args.get(i);
            plan.types[i] = typeOf(arg);
            final List<MALExpressionModel.ClosureExpr> kinds = new ArrayList<>();
            if (runtimeKind(plan.types[i]) && arms(arg) != null) {
                collectArms(arg, kinds);
            } else {
                kinds.add(arg);
            }
            leaves.add(kinds);
            dispatched |= kinds.size() > 1 || plan.types[i] == Object.class || plan.types[i] == BigDecimal.class;
        }
        final Class<?> boxedOwner = owner != null && owner.isPrimitive() ? DslMethods.boxed(owner) : owner;
        final Class<?> receiver = boxedOwner == null ? null
            : stringReceiver(boxedOwner, name, size) ? String.class : boxedOwner;
        if (receiver == null || !dispatched) {
            return plan;
        }
        final List<Method> candidates = new ArrayList<>();
        for (final Method m : DslMethods.of(receiver)) {
            if (m.getName().equals(name) && m.getParameterCount() == size && !m.isBridge()) {
                candidates.add(m);
            }
        }
        if (candidates.isEmpty()) {
            return plan;
        }
        final Set<Method> picked = new HashSet<>();
        boolean unknown = false;
        boolean incompatible = false;
        for (final List<MALExpressionModel.ClosureExpr> combination : combinations(leaves)) {
            final Class<?>[] kinds = new Class<?>[size];
            for (int i = 0; i < size; i++) {
                kinds[i] = leafType(combination.get(i), leaves.get(i).size() > 1 || plan.types[i] == BigDecimal.class,
                    plan.types[i]);
            }
            if (Arrays.asList(kinds).contains(null) || Arrays.asList(kinds).contains(Object.class)) {
                unknown = true;
                continue;
            }
            final Method m = groovyOverload(receiver, candidates, kinds);
            if (m == null) {
                // t.name.startsWith(t.prefix ?: 404): a kind it can take fits no overload, refused when compiled
                incompatible = true;
                continue;
            }
            picked.add(m);
        }
        if (incompatible) {
            markUnresolved(plan, leaves);
            return plan;
        }
        if (!unknown && picked.size() == 1) {
            take(plan, picked.iterator().next(), leaves);
            return plan;
        }
        if (unknown && candidates.size() == 1 && referenceAtDispatched(candidates.get(0), leaves, plan.types)) {
            // t.name.startsWith(def): the one signature, the value cast to its parameter as Groovy passes it
            take(plan, candidates.get(0), leaves);
            return plan;
        }
        if (unknown ? sameReturnWithObjectParam(candidates, plan.types) : alikeButForObject(picked, plan.types)) {
            for (int i = 0; i < size; i++) {
                if (leaves.get(i).size() > 1 || plan.types[i] == Object.class) {
                    plan.types[i] = Object.class;
                }
            }
            return plan;
        }
        markUnresolved(plan, leaves);
        return plan;
    }

    private static void markUnresolved(final ArgumentPlan plan,
                                       final List<List<MALExpressionModel.ClosureExpr>> leaves) {
        for (int i = 0; i < plan.types.length; i++) {
            if (leaves.get(i).size() > 1 || plan.types[i] == Object.class || plan.types[i] == BigDecimal.class) {
                plan.unresolved[i] = true;
                return;
            }
        }
    }

    private static boolean referenceAtDispatched(final Method method,
                                                 final List<List<MALExpressionModel.ClosureExpr>> leaves,
                                                 final Class<?>[] types) {
        for (int i = 0; i < types.length; i++) {
            final boolean dispatched = leaves.get(i).size() > 1 || types[i] == Object.class || types[i] == null;
            if (dispatched && method.getParameterTypes()[i].isPrimitive()) {
                return false;
            }
        }
        return true;
    }

    private static void take(final ArgumentPlan plan, final Method method,
                             final List<List<MALExpressionModel.ClosureExpr>> leaves) {
        for (int i = 0; i < plan.types.length; i++) {
            final Class<?> param = method.getParameterTypes()[i];
            final boolean dispatched = leaves.get(i).size() > 1 || plan.types[i] == Object.class
                || plan.types[i] == BigDecimal.class;
            if (dispatched) {
                plan.types[i] = param;
                plan.converted[i] = param.isPrimitive() && param != boolean.class ? param : null;
            }
        }
    }

    /**
     * Every leaf of a ternary or ?: (nested ones included).
     */
    private static void collectArms(final MALExpressionModel.ClosureExpr expr,
                                    final List<MALExpressionModel.ClosureExpr> into) {
        final MALExpressionModel.ClosureExpr[] arms = arms(expr);
        if (arms == null) {
            into.add(expr);
            return;
        }
        collectArms(arms[0], into);
        collectArms(arms[1], into);
    }

    private static List<List<MALExpressionModel.ClosureExpr>> combinations(
        final List<List<MALExpressionModel.ClosureExpr>> leaves) {
        List<List<MALExpressionModel.ClosureExpr>> out = new ArrayList<>();
        out.add(new ArrayList<>());
        for (final List<MALExpressionModel.ClosureExpr> options : leaves) {
            final List<List<MALExpressionModel.ClosureExpr>> next = new ArrayList<>();
            for (final List<MALExpressionModel.ClosureExpr> prefix : out) {
                for (final MALExpressionModel.ClosureExpr option : options) {
                    final List<MALExpressionModel.ClosureExpr> combination = new ArrayList<>(prefix);
                    combination.add(option);
                    next.add(combination);
                }
            }
            out = next;
            if (out.size() > 64) {
                // too many to enumerate: known only at run time
                final List<List<MALExpressionModel.ClosureExpr>> unknown = new ArrayList<>();
                final List<MALExpressionModel.ClosureExpr> none = new ArrayList<>();
                for (int i = 0; i < leaves.size(); i++) {
                    none.add(new MALExpressionModel.ClosureNullLiteral());
                }
                unknown.add(none);
                return unknown;
            }
        }
        return out;
    }

    /**
     * The kind a leaf passes as: a number as its primitive (Groovy hands it over unboxed or boxed alike), a quotient
     * as the BigDecimal it is, anything else its type; null when unknown.
     */
    private Class<?> leafType(final MALExpressionModel.ClosureExpr leaf, final boolean ownKind,
                              final Class<?> declared) {
        if (!ownKind) {
            return declared;
        }
        if (leaf instanceof MALExpressionModel.ClosureNullLiteral) {
            return null;
        }
        final Class<?> type = typeOf(leaf);
        if (type == null || type == void.class) {
            return null;
        }
        final Class<?> number = numericOf(type);
        return number != null ? number : type;
    }

    /**
     * Groovy's choice among the overloads for arguments of these kinds. Without a quotient, Java's (Groovy's distance
     * agrees on primitives), a primitive boxed for a reference parameter (map.get(c ? 1 : 'a')). With a quotient,
     * Groovy's parameter distance: a BigDecimal goes to a float or double parameter, and of two equally near, Groovy's
     * call site takes the float one (f(n / 4) with an f(float) and an f(double) is f(float)).
     */
    private static Method groovyOverload(final Class<?> receiver, final List<Method> candidates,
                                         final Class<?>[] kinds) {
        if (!Arrays.asList(kinds).contains(BigDecimal.class)) {
            final String name = candidates.get(0).getName();
            final Method m = DslMethods.overload(receiver, name, kinds);
            if (m != null) {
                return m;
            }
            final Class<?>[] boxedKinds = kinds.clone();
            for (int i = 0; i < boxedKinds.length; i++) {
                boxedKinds[i] = boxedKinds[i].isPrimitive() ? DslMethods.boxed(boxedKinds[i]) : boxedKinds[i];
            }
            return DslMethods.overload(receiver, name, boxedKinds);
        }
        Method best = null;
        long bestDistance = Long.MAX_VALUE;
        for (final Method m : candidates) {
            long distance = 0;
            for (int i = 0; i < kinds.length && distance >= 0; i++) {
                final long d = groovyDistance(kinds[i], m.getParameterTypes()[i]);
                distance = d < 0 ? -1 : distance + d;
            }
            if (distance < 0) {
                continue;
            }
            if (distance < bestDistance || distance == bestDistance && narrower(m, best)) {
                best = m;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean narrower(final Method m, final Method than) {
        for (int i = 0; i < m.getParameterCount(); i++) {
            final int a = GROOVY_NUMBERS.indexOf(m.getParameterTypes()[i]);
            final int b = GROOVY_NUMBERS.indexOf(than.getParameterTypes()[i]);
            if (a != b) {
                return a >= 0 && (b < 0 || a < b);
            }
        }
        return false;
    }

    /**
     * Groovy's MetaClassHelper order and distance table (Groovy 5): row the argument's kind, column the parameter's.
     */
    private static final List<Class<?>> GROOVY_NUMBERS = Arrays.asList(
        boolean.class, Boolean.class, byte.class, Byte.class, short.class, Short.class, char.class, Character.class,
        int.class, Integer.class, long.class, Long.class, BigInteger.class, float.class, Float.class,
        double.class, Double.class, BigDecimal.class, Number.class, Object.class);
    private static final int[][] GROOVY_DISTANCE = {
        {0, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2},
        {1, 0, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2},
        {18, 19, 0, 1, 2, 3, 16, 17, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
        {18, 19, 1, 0, 2, 3, 16, 17, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
        {18, 19, 14, 15, 0, 1, 16, 17, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13},
        {18, 19, 14, 15, 1, 0, 16, 17, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13},
        {18, 19, 16, 17, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13},
        {18, 19, 16, 17, 14, 15, 1, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13},
        {18, 19, 14, 15, 12, 13, 16, 17, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
        {18, 19, 14, 15, 12, 13, 16, 17, 1, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
        {18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 1, 0, 2, 3, 4, 5, 6, 7, 8, 9},
        {18, 19, 9, 10, 7, 8, 16, 17, 5, 6, 3, 4, 0, 14, 15, 12, 13, 11, 1, 2},
        {18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 0, 1, 2, 3, 4, 5, 6},
        {18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 1, 0, 2, 3, 4, 5, 6},
        {18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 0, 1, 2, 3, 4},
        {18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 1, 0, 2, 3, 4},
        {18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 3, 4, 0, 1, 2},
        {18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 3, 4, 2, 0, 1},
        {18, 19, 14, 15, 12, 13, 16, 17, 10, 11, 8, 9, 7, 5, 6, 3, 4, 2, 1, 0},
    };

    /**
     * @return Groovy's distance from an argument kind to a parameter, -1 when Groovy does not pass it there (a
     *     BigDecimal to an int or long, a double to a float)
     */
    private static long groovyDistance(final Class<?> kind, final Class<?> param) {
        final Class<?> argument = kind.isPrimitive() ? DslMethods.boxed(kind) : kind;
        if (param.isPrimitive()) {
            final boolean passes = argument == BigDecimal.class
                ? param == float.class || param == double.class
                : DslMethods.unboxed(argument) != null && DslMethods.widens(DslMethods.unboxed(argument), param);
            if (!passes) {
                return -1;
            }
        } else if (!param.isAssignableFrom(argument)) {
            return -1;
        }
        final int from = GROOVY_NUMBERS.indexOf(argument);
        final int to = GROOVY_NUMBERS.indexOf(param);
        return from >= 0 && to >= 0 ? GROOVY_DISTANCE[from][to] : 20;
    }

    /**
     * Overloads picked by the kinds the argument takes, alike but for that parameter: one return type, and each
     * parameter one an Object prints alike (an Object, a number, a boolean, a character or a string), so the Object
     * overload Groovy reaches for a boxed value computes the same.
     */
    private static boolean alikeButForObject(final Set<Method> picked, final Class<?>[] types) {
        if (picked.size() < 2) {
            return false;
        }
        final Method any = picked.iterator().next();
        for (final Method m : picked) {
            if (m.getReturnType() != any.getReturnType()) {
                return false;
            }
            for (int i = 0; i < m.getParameterCount(); i++) {
                if (!printsAlike(m.getParameterTypes()[i])) {
                    return false;
                }
            }
        }
        return sameReturnWithObjectParam(candidatesOf(picked), types, any.getName());
    }

    private static List<Method> candidatesOf(final Set<Method> picked) {
        final List<Method> all = new ArrayList<>();
        for (final Method m : picked) {
            for (final Method o : DslMethods.of(m.getDeclaringClass())) {
                if (o.getName().equals(m.getName()) && o.getParameterCount() == m.getParameterCount()) {
                    all.add(o);
                }
            }
        }
        return all;
    }

    private static boolean printsAlike(final Class<?> param) {
        return param == Object.class || param.isPrimitive() || DslMethods.unboxed(param) != null || param == String.class
            || param == CharSequence.class;
    }

    private static boolean sameReturnWithObjectParam(final List<Method> candidates, final Class<?>[] types) {
        return !candidates.isEmpty() && sameReturnWithObjectParam(candidates, types, candidates.get(0).getName());
    }

    /**
     * A value known only at run time: taken as it is when an overload takes an Object where it goes and the overloads
     * of that name and arity share one return type (String.valueOf(def)).
     */
    private static boolean sameReturnWithObjectParam(final List<Method> methods, final Class<?>[] types,
                                                     final String name) {
        Class<?> returns = null;
        boolean objectParam = false;
        for (final Method m : methods) {
            if (!m.getName().equals(name) || m.getParameterCount() != types.length || m.isBridge()) {
                continue;
            }
            if (returns != null && m.getReturnType() != returns) {
                return false;
            }
            returns = m.getReturnType();
            for (int i = 0; i < types.length; i++) {
                if ((types[i] == null || types[i] == Object.class) && m.getParameterTypes()[i] == Object.class) {
                    objectParam = true;
                }
            }
        }
        return objectParam;
    }

    private static boolean runtimeKind(final Class<?> type) {
        return type == null || type == Object.class;
    }

    /**
     * @return the return type the overloads of that arity share; else, with every argument's type known, that of
     *     the most specific overload the arguments fit by subtyping or primitive widening, as Java chooses; else null
     */
    private static Class<?> returnType(final Class<?> type, final String name, final Class<?>[] args) {
        if (type.isPrimitive()) {
            return type == void.class ? null : returnType(DslMethods.boxed(type), name, args);
        }
        if (type == Arrays.class && "asList".equals(name)) {
            return List.class;
        }
        if (stringReceiver(type, name, args.length)) {
            return returnType(String.class, name, args);
        }
        final Class<?> common = DslMethods.commonReturnType(type, name, args.length);
        if (common != null) {
            return common;
        }
        // the overload the call compiles to
        final Method best = DslMethods.overload(type, name, args);
        return best == null ? null : best.getReturnType();
    }

    /**
     * An Object-typed value (a label read whose fallback is an Object, a def) calling a method Object lacks and String
     * has: Groovy would call it on the string it holds, so the code generator casts it to String.
     */
    static boolean stringReceiver(final Class<?> type, final String name, final int arity) {
        return type == Object.class && !DslMethods.has(Object.class, name, arity)
            && DslMethods.has(String.class, name, arity);
    }

    /**
     * The type the generated ternary leaves: its arms' type when they agree; with a null arm, the other arm's
     * reference (a primitive boxed); otherwise unknown, as the code generator boxes arms of different types.
     */
    private Class<?> ternaryType(final MALExpressionModel.ClosureExpr whenTrue,
                                 final MALExpressionModel.ClosureExpr whenFalse) {
        final boolean nullTrue = whenTrue instanceof MALExpressionModel.ClosureNullLiteral;
        final boolean nullFalse = whenFalse instanceof MALExpressionModel.ClosureNullLiteral;
        if (nullTrue != nullFalse) {
            final Class<?> other = typeOf(nullTrue ? whenFalse : whenTrue);
            return other == null || other == void.class ? null : DslMethods.boxed(other);
        }
        final Class<?> a = typeOf(whenTrue);
        final Class<?> b = typeOf(whenFalse);
        if (a != null && b != null && a != b && a != void.class && b != void.class
            && !(computesAsNumber(a) && computesAsNumber(b))) {
            // known, different and not both numbers (c ? t.host : fallback): an Object the generated code boxes
            return Object.class;
        }
        return same(a, b);
    }

    /**
     * @return the primitive a value of the type takes part in arithmetic as (its own, or the one its wrapper holds; a
     *     character counts), or null when it is not statically a number
     */
    static Class<?> numericOf(final Class<?> type) {
        if (type == null) {
            return null;
        }
        return isPrimitiveNumber(type) ? type : DslMethods.unboxed(type);
    }

    /**
     * @return whether a value of the type is statically a number, not a character
     */
    static boolean isNumber(final Class<?> type) {
        return numericOf(type) != null && !isCharacter(type);
    }

    /**
     * @return whether Groovy computes with a value of the type as a number: one of {@link #numericOf}, or a quotient
     */
    private static boolean computesAsNumber(final Class<?> type) {
        return numericOf(type) != null || type == BigDecimal.class;
    }

    static boolean isCharacter(final Class<?> type) {
        return type == char.class || type == Character.class;
    }

    /**
     * @return the arms of a ternary, or null for another expression
     */
    static MALExpressionModel.ClosureExpr[] arms(final MALExpressionModel.ClosureExpr expr) {
        if (expr instanceof MALExpressionModel.ClosureTernaryExpr) {
            final MALExpressionModel.ClosureTernaryExpr t = (MALExpressionModel.ClosureTernaryExpr) expr;
            return new MALExpressionModel.ClosureExpr[] {t.getTrueExpr(), t.getFalseExpr()};
        }
        if (expr instanceof MALExpressionModel.ClosureElvisExpr) {
            final MALExpressionModel.ClosureElvisExpr e = (MALExpressionModel.ClosureElvisExpr) expr;
            return new MALExpressionModel.ClosureExpr[] {e.getPrimary(), e.getFallback()};
        }
        return null;
    }

    private static Class<?> same(final Class<?> a, final Class<?> b) {
        return a != null && a == b ? a : null;
    }

    /**
     * @return whether a value of the type takes a text joined after it with +: a String, a number or a character, which
     *     Groovy has that + for
     */
    static boolean joinsText(final Class<?> type) {
        return type == String.class || numericOf(type) != null || type != null && Number.class.isAssignableFrom(type);
    }

    /**
     * An array, collection or map: Groovy's + appends to or merges it, which a closure does not support.
     */
    static boolean isContainer(final Class<?> type) {
        return type != null && (type.isArray() || Collection.class.isAssignableFrom(type)
            || Map.class.isAssignableFrom(type));
    }

    static boolean isFloating(final Class<?> type) {
        return type == double.class || type == float.class || type == Double.class || type == Float.class;
    }

    static boolean isIntegral(final Class<?> type) {
        return type == int.class || type == long.class || type == short.class || type == byte.class
            || type == char.class;
    }

    /**
     * @return whether the type is a primitive number, a char included
     */
    static boolean isPrimitiveNumber(final Class<?> type) {
        return type == int.class || type == long.class || type == double.class || type == float.class
            || type == short.class || type == byte.class || type == char.class;
    }

    /**
     * Whether a local declared with this type holds the value as it is (null for a value of unknown type).
     */
    static boolean holds(final Class<?> declared, final Class<?> value, final MALExpressionModel.ClosureExpr expr) {
        if (expr instanceof MALExpressionModel.ClosureNullLiteral) {
            return !declared.isPrimitive();
        }
        if (value == null || value == void.class) {
            return false;
        }
        if (declared.isPrimitive()) {
            // Groovy's def holds the value assigned as it is: a char stays a char ('5', not 53), an int an Integer
            // (123, not 123.0), a box may be null; only the same primitive kind keeps it
            return value == declared;
        }
        return DslMethods.boxed(declared).isAssignableFrom(value.isPrimitive() ? DslMethods.boxed(value) : value);
    }

    /**
     * Refuses a ternary arm the slot cannot hold: null in a primitive, a number that does not widen, an unrelated type.
     */
    static void requireFits(final Class<?> slot, final Class<?> armType, final boolean nullArm) {
        if (nullArm) {
            if (slot.isPrimitive()) {
                throw new IllegalArgumentException("A null arm does not fit a " + slot.getSimpleName() + " local");
            }
            return;
        }
        if (armType == null) {
            throw new IllegalArgumentException("The type of an arm of this ternary is not known, so it cannot be "
                + "checked against the " + slot.getSimpleName() + " local; declare it through a typed local");
        }
        if (armType == Object.class && !slot.isPrimitive() || slot == String.class && armType != void.class) {
            // a String local converts whatever the arm holds (443 -> "443"), as Groovy does
            return;
        }
        final Class<?> primitive = armType.isPrimitive() ? armType : DslMethods.unboxed(armType);
        final boolean fits = slot.isPrimitive()
            ? primitive != null && (DslMethods.widens(primitive, slot)
                || isPrimitiveNumber(slot) && isPrimitiveNumber(primitive))
                || armType == BigDecimal.class && isPrimitiveNumber(slot)
            : slot.isAssignableFrom(armType.isPrimitive() ? DslMethods.boxed(armType) : armType);
        if (!fits) {
            throw new IllegalArgumentException("A " + armType.getSimpleName() + " arm does not fit a "
                + slot.getSimpleName() + " local; declare the local's type to match");
        }
    }

    /**
     * @return whether a label written with a value of the type takes its string: a number, a boolean, or a character
     *     (the one-character string it is)
     */
    static boolean labelConvertible(final Class<?> type) {
        final Class<?> primitive = type.isPrimitive() ? type : DslMethods.unboxed(type);
        return primitive != null || type == Boolean.class || Number.class.isAssignableFrom(type);
    }

    private static Class<?> widen(final Class<?> a, final Class<?> b) {
        if (a == double.class || b == double.class || a == float.class || b == float.class) {
            return double.class;
        }
        return a == long.class || b == long.class ? long.class : int.class;
    }
}
