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

package org.apache.skywalking.oap.server.core.dsl;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Java methods a DSL value answers, and the overload Java (and Javassist) picks for known argument types, shared
 * by the DSL compilers so their type inference and their allow lists see the same methods.
 */
public final class DslMethods {

    private DslMethods() {
    }

    /**
     * The public methods of the type, and for an interface also {@code Object}'s, which {@link Class#getMethods()}
     * leaves out though every value of it answers them ({@code map.toString()}).
     */
    public static Method[] of(final Class<?> type) {
        final Method[] own = type.getMethods();
        if (!type.isInterface()) {
            return own;
        }
        final Map<String, Method> all = new LinkedHashMap<>();
        for (final Method m : own) {
            all.put(signature(m), m);
        }
        for (final Method m : Object.class.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) {
                all.putIfAbsent(signature(m), m);
            }
        }
        return all.values().toArray(new Method[0]);
    }

    /**
     * {@link Class#getMethod}, finding {@code Object}'s methods on an interface too.
     */
    public static Method find(final Class<?> type, final String name, final Class<?>... params)
        throws NoSuchMethodException {
        try {
            return type.getMethod(name, params);
        } catch (final NoSuchMethodException e) {
            if (type.isInterface()) {
                final Method m = Object.class.getMethod(name, params);
                if (!Modifier.isStatic(m.getModifiers())) {
                    return m;
                }
            }
            throw e;
        }
    }

    /**
     * @return whether the type answers a method of the name taking that many arguments
     */
    public static boolean has(final Class<?> type, final String name, final int arity) {
        for (final Method m : of(type)) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the return type every method of the name and arity shares, null when they differ or there is none
     */
    public static Class<?> commonReturnType(final Class<?> type, final String name, final int arity) {
        Class<?> found = null;
        for (final Method m : of(type)) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) {
                if (found != null && found != m.getReturnType()) {
                    return null;
                }
                found = m.getReturnType();
            }
        }
        return found;
    }

    /**
     * @return the parameter types every method of the name and arity shares, null when they differ or there is none
     */
    public static Class<?>[] commonParameterTypes(final Class<?> type, final String name, final int arity) {
        Class<?>[] found = null;
        for (final Method m : of(type)) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) {
                if (found != null && !Arrays.equals(found, m.getParameterTypes())) {
                    return null;
                }
                found = m.getParameterTypes();
            }
        }
        return found;
    }

    /**
     * The overload a LAL or TAL chain calls, chosen when the rule compiles: each argument scores how well it fits its
     * parameter ({@link #fit}), the candidates with the best total win, and of them the most specific.
     *
     * @param candidates the methods of the name and arity the engine lets the call reach
     * @param args       each argument's static type: null for a literal null, {@code Object} for a value typed only at
     *                   run time
     * @param texts      for each argument, whether it is known to hold text though typed otherwise, which a string
     *                   parameter takes as its own; null when none is
     * @return the choice; null when no candidate takes the arguments
     */
    public static Choice choose(final Collection<Method> candidates, final Class<?>[] args, final boolean[] texts) {
        int bestScore = -1;
        final List<Method> best = new ArrayList<>();
        for (final Method m : candidates) {
            int score = 0;
            for (int i = 0; i < args.length && score >= 0; i++) {
                final int s = fit(args[i], texts != null && texts[i], m.getParameterTypes()[i]);
                score = s < 0 ? -1 : score + s;
            }
            if (score > bestScore) {
                bestScore = score;
                best.clear();
                best.add(m);
            } else if (score == bestScore && score >= 0) {
                best.add(m);
            }
        }
        if (best.isEmpty()) {
            return null;
        }
        Method chosen = best.get(0);
        for (final Method m : best) {
            if (moreSpecific(m, chosen)) {
                chosen = m;
            }
        }
        for (final Method m : best) {
            if (m != chosen && !moreSpecific(chosen, m)) {
                return new Choice(chosen, m);
            }
        }
        return new Choice(chosen, null);
    }

    /**
     * An overload {@link #choose} picked.
     */
    public static final class Choice {
        public final Method method;
        /**
         * Another candidate that fits as well and is no less specific, which makes the call ambiguous; null when
         * {@link #method} is the one choice.
         */
        public final Method rival;

        Choice(final Method method, final Method rival) {
            this.method = method;
            this.rival = rival;
        }
    }

    /**
     * @return how well an argument fits a parameter: 3 for the parameter's own type, 2 for one widened or boxed to it
     *     (or a text a string parameter takes), 1 for a value of unknown type ({@code Object} or {@code Number})
     *     converted or cast when the rule runs, -1 when it cannot be passed
     */
    private static int fit(final Class<?> arg, final boolean text, final Class<?> param) {
        if (arg == null) {
            return param.isPrimitive() ? -1 : 2;
        }
        if (param.isPrimitive()) {
            final Class<?> primitive = arg.isPrimitive() ? arg : arg == Boolean.class ? boolean.class : unboxed(arg);
            if (primitive != null) {
                return primitive == param ? 3 : widens(primitive, param) ? 2 : -1;
            }
            return (arg == Object.class || arg == Number.class) && convertsAtRunTime(param) ? 1 : -1;
        }
        final Class<?> boxedArg = boxed(arg);
        if (param == boxedArg) {
            return 3;
        }
        if (param.isAssignableFrom(boxedArg) || text && param.isAssignableFrom(String.class)) {
            return 2;
        }
        return arg == Object.class ? 1 : -1;
    }

    /**
     * @return whether a value typed only at run time converts to the primitive parameter (LAL's and TAL's numeric and
     *     boolean conversions)
     */
    private static boolean convertsAtRunTime(final Class<?> param) {
        return param == int.class || param == long.class || param == double.class || param == float.class
            || param == boolean.class;
    }

    private static boolean moreSpecific(final Method m, final Method other) {
        final Class<?>[] a = m.getParameterTypes();
        final Class<?>[] b = other.getParameterTypes();
        for (int i = 0; i < a.length; i++) {
            if (!(a[i] == b[i] || !b[i].isPrimitive() && boxed(b[i]).isAssignableFrom(boxed(a[i]))
                || a[i].isPrimitive() && b[i].isPrimitive() && widens(a[i], b[i]))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the overload Java chooses for arguments of these types (all known): by subtyping or primitive
     *     widening, else (Java's second phase) with boxing and unboxing; null when none or several fit equally
     */
    public static Method overload(final Class<?> type, final String name, final Class<?>[] args) {
        if (type.isPrimitive()) {
            return type == void.class ? null : overload(boxed(type), name, args);
        }
        if (!Arrays.stream(args).allMatch(Objects::nonNull)) {
            return null;
        }
        final List<Method> applicable = new ArrayList<>();
        for (final Method m : of(type)) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length
                && fits(args, m.getParameterTypes())) {
                applicable.add(m);
            }
        }
        if (applicable.isEmpty()) {
            // a primitive boxed for a reference parameter (getOrDefault('port', 443)), a wrapper unboxed for a
            // primitive one (substring(Integer.valueOf(n)))
            for (final Method m : of(type)) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length
                    && fitsBoxing(args, m.getParameterTypes())) {
                    applicable.add(m);
                }
            }
        }
        return mostSpecific(applicable);
    }

    private static Method mostSpecific(final List<Method> applicable) {
        Method best = null;
        for (final Method m : applicable) {
            if (applicable.stream().allMatch(o -> fits(m.getParameterTypes(), o.getParameterTypes()))) {
                if (best != null && !Arrays.equals(best.getParameterTypes(), m.getParameterTypes())) {
                    return null;
                }
                best = m;
            }
        }
        return best;
    }

    private static boolean fits(final Class<?>[] args, final Class<?>[] params) {
        for (int i = 0; i < args.length; i++) {
            final boolean fit = params[i].isPrimitive()
                ? args[i].isPrimitive() && widens(args[i], params[i])
                : !args[i].isPrimitive() && params[i].isAssignableFrom(args[i]);
            if (!fit) {
                return false;
            }
        }
        return true;
    }

    private static boolean fitsBoxing(final Class<?>[] args, final Class<?>[] params) {
        for (int i = 0; i < args.length; i++) {
            final Class<?> primitive = args[i].isPrimitive() ? args[i] : unboxed(args[i]);
            final boolean fit = params[i].isPrimitive()
                ? primitive != null && widens(primitive, params[i])
                : params[i].isAssignableFrom(args[i].isPrimitive() ? boxed(args[i]) : args[i]);
            if (!fit) {
                return false;
            }
        }
        return true;
    }

    public static Class<?> boxed(final Class<?> type) {
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        return type == byte.class ? Byte.class : type;
    }

    /**
     * @return the primitive a numeric or character wrapper holds, else null
     */
    public static Class<?> unboxed(final Class<?> type) {
        for (final Class<?> p : new Class<?>[] {int.class, long.class, double.class, float.class, short.class,
            byte.class, char.class}) {
            if (boxed(p) == type) {
                return p;
            }
        }
        return null;
    }

    /**
     * @return whether a {@code from} value fits a {@code to} slot by Java's widening primitive conversions
     */
    public static boolean widens(final Class<?> from, final Class<?> to) {
        if (from == to) {
            return true;
        }
        final boolean small = from == int.class || from == short.class || from == byte.class || from == char.class;
        if (to == long.class) {
            return small;
        }
        if (to == double.class) {
            return small || from == long.class || from == float.class;
        }
        if (to == float.class) {
            return small || from == long.class;
        }
        if (to == short.class) {
            return from == byte.class;
        }
        return to == int.class && (from == short.class || from == byte.class || from == char.class);
    }

    private static String signature(final Method m) {
        return m.getName() + Arrays.toString(m.getParameterTypes());
    }
}
