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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The Java a DSL rule may call, by class: the methods a rule calls on a value of the class, the static methods it calls
 * on the class, and whether it reads the class's getters (or calls its setters). A value's methods are looked up along
 * its own type hierarchy (its class, superclasses and interfaces), so what is listed under {@code Object} is every
 * value's, and what is listed under {@code Collection} every list's and set's.
 *
 * <p>The structure is shared; each DSL engine declares its own list, built the first time the engine compiles a rule.
 * A name the class does not have fails the build of the list, so a typo cannot leave a method silently unsupported.
 */
public final class DslCallables {

    /** The String methods every DSL supports. */
    public static final List<String> TEXT_METHODS = Collections.unmodifiableList(Arrays.asList(
        "length", "isEmpty", "trim", "toLowerCase", "toUpperCase", "startsWith", "endsWith", "contains", "indexOf",
        "lastIndexOf", "substring", "replace", "replaceAll", "replaceFirst", "split", "charAt", "equals",
        "equalsIgnoreCase", "matches", "concat", "toString"));

    private final String language;
    private final String documentation;
    private final Map<Class<?>, Set<String>> methods;
    private final Map<Class<?>, Set<String>> statics;
    private final Map<Class<?>, Set<String>> staticFields;
    private final Map<Class<?>, Predicate<String>> getters;
    private final Set<Class<?>> setters;
    /** The classes the list does not name whose getters a value reads; null when none. */
    private final Predicate<Class<?>> unlistedWithGetters;
    private final boolean refusesVarargs;

    private DslCallables(final Builder builder) {
        this.language = builder.language;
        this.documentation = builder.documentation;
        this.methods = frozen(builder.methods);
        this.statics = frozen(builder.statics);
        this.staticFields = frozen(builder.staticFields);
        this.getters = Collections.unmodifiableMap(builder.getters);
        this.setters = Collections.unmodifiableSet(builder.setters);
        this.unlistedWithGetters = builder.unlistedWithGetters;
        this.refusesVarargs = builder.refusesVarargs;
    }

    /**
     * @param language      how a message names a rule of the engine ("a MAL closure")
     * @param documentation how a message names the documentation that lists what is supported ("the MAL
     *                      documentation")
     */
    public static Builder builder(final String language, final String documentation) {
        return new Builder(language, documentation);
    }

    /**
     * @return whether a rule may call the method on a value of the type (a primitive as its box). Only an instance
     *     method matches: a static one of the same name ({@code Long.toString(long)} beside {@code toString()}) is a
     *     static call, allowed only through {@link #allowsStatic}.
     */
    public boolean allows(final Class<?> receiver, final String name, final int arity) {
        final Class<?> type = boxed(receiver);
        if (!hasInstanceMethod(type, name, arity)) {
            return false;
        }
        boolean listed = false;
        for (final Class<?> c : hierarchy(type)) {
            if (methods.getOrDefault(c, Collections.emptySet()).contains(name)) {
                return true;
            }
            final Predicate<String> getter = getters.get(c);
            if (getter != null && isGetter(name) && getter.test(name)) {
                return true;
            }
            if (setters.contains(c) && arity == 1 && prefixed(name, "set")) {
                return true;
            }
            listed |= isListed(c);
        }
        // a class the list does not name (an input or a function's object): its getters, where the engine says so
        return !listed && unlistedWithGetters != null && isGetter(name) && unlistedWithGetters.test(type)
            && declaredWhereAccepted(type, name, arity);
    }

    /**
     * @return whether a rule may call this resolved method on a value of the type: an instance method the list allows,
     *     not variable-length where the engine refuses those
     */
    public boolean allows(final Class<?> receiver, final Method method) {
        return !Modifier.isStatic(method.getModifiers()) && !(refusesVarargs && method.isVarArgs())
            && allows(receiver, method.getName(), method.getParameterCount());
    }

    /**
     * Refuses a resolved method a rule may not call on a value of the type: a static one (a value calls its own
     * methods), a variable-length one where the engine refuses those, or one the list does not allow.
     *
     * @return the method, or null for null
     */
    public Method require(final Class<?> receiver, final Method method) {
        if (method == null) {
            return null;
        }
        if (Modifier.isStatic(method.getModifiers()) || refusesVarargs && method.isVarArgs()) {
            throw notThroughAValue(receiver, method.getName(), Modifier.isStatic(method.getModifiers()));
        }
        require(receiver, method.getName(), method.getParameterCount());
        return method;
    }

    /**
     * The failure for a static or variable-length method a rule calls through a value.
     */
    public IllegalArgumentException notThroughAValue(final Class<?> receiver, final String name,
                                                     final boolean isStatic) {
        return new IllegalArgumentException(boxed(receiver).getSimpleName() + "." + name + "(...) is "
            + (isStatic ? "a static" : "a variable-length") + " method, which " + language + " does not call through "
            + "a value. " + describe(receiver));
    }

    private boolean isListed(final Class<?> type) {
        return type != Object.class && (methods.containsKey(type) || getters.containsKey(type) || setters.contains(type));
    }

    /**
     * @return whether the class declaring the getter is one the engine reads getters of too: a getter a class inherits
     *     from a type the engine refuses (a JDK list's isEmpty()) stays that type's
     */
    private boolean declaredWhereAccepted(final Class<?> type, final String name, final int arity) {
        for (final Method m : DslMethods.of(type)) {
            if (m.getName().equals(name) && m.getParameterCount() == arity && !Modifier.isStatic(m.getModifiers())
                && unlistedWithGetters.test(m.getDeclaringClass())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether a rule may call the static method on the class
     */
    public boolean allowsStatic(final Class<?> type, final String name) {
        return statics.getOrDefault(type, Collections.emptySet()).contains(name);
    }

    /**
     * Refuses a static field the list does not name.
     */
    public void requireStaticField(final Class<?> type, final String name) {
        if (!staticFields.getOrDefault(type, Collections.emptySet()).contains(name)) {
            throw new IllegalArgumentException(type.getSimpleName() + "." + name + " is not supported in " + language
                + ". The static fields are " + listed(staticFields) + " (see " + documentation + ")");
        }
    }

    /**
     * Refuses a call on a value the list does not allow.
     */
    public void require(final Class<?> receiver, final String name, final int arity) {
        if (!allows(receiver, name, arity)) {
            throw new IllegalArgumentException(boxed(receiver).getSimpleName() + "." + name
                + "(...) is not supported in " + language + ". " + describe(receiver));
        }
    }

    /**
     * Refuses a static call the list does not allow.
     */
    public void requireStatic(final Class<?> type, final String name) {
        if (!allowsStatic(type, name)) {
            throw new IllegalArgumentException(type.getSimpleName() + "." + name + "(...) is not supported in "
                + language + ". The static calls are " + describeStatics() + " (see " + documentation + ")");
        }
    }

    /**
     * The failure for a name a rule uses that is none of its own and no class of the static calls ({@code Math},
     * or a name of the generated code): it would otherwise reach the generated method's scope.
     *
     * @param own what the rule's own names are, for the message, such as {@code "its parameters [u, l]"}
     */
    public IllegalArgumentException unknownName(final String name, final String own) {
        return new IllegalArgumentException("'" + name + "' is not supported in " + language + ": it names " + own
            + " and the classes of the static calls " + describeStatics() + " (see " + documentation + ")");
    }

    /**
     * @return what a value of the type may call and where the documentation lists it, for a message
     */
    public String describe(final Class<?> receiver) {
        final Class<?> type = boxed(receiver);
        final Set<String> names = new TreeSet<>();
        boolean readsGetters = false;
        boolean callsSetters = false;
        boolean listed = false;
        for (final Class<?> c : hierarchy(type)) {
            names.addAll(methods.getOrDefault(c, Collections.emptySet()));
            readsGetters |= getters.containsKey(c);
            callsSetters |= setters.contains(c);
            listed |= isListed(c);
        }
        readsGetters |= !listed && unlistedWithGetters != null && type != Object.class && unlistedWithGetters.test(type);
        final String simple = type.getSimpleName();
        final String article = !simple.isEmpty() && "AEIOU".indexOf(simple.charAt(0)) >= 0 ? "An " : "A ";
        return article + simple + " allows " + names + (readsGetters ? " and its getters" : "")
            + (callsSetters ? " and its setters" : "") + " (see " + documentation + ")";
    }

    /**
     * @return every static call the list allows, for a message
     */
    public String describeStatics() {
        return listed(statics);
    }

    private static String listed(final Map<Class<?>, Set<String>> members) {
        final Set<String> named = new TreeSet<>();
        members.forEach((type, names) -> names.forEach(n -> named.add(type.getSimpleName() + "." + n)));
        return named.toString();
    }

    /**
     * @return every class's own instance entries, for a test comparing the list with its documentation
     */
    public Map<Class<?>, Set<String>> declaredMethods() {
        return methods;
    }

    /**
     * @return every class's static entries, for a test comparing the list with its documentation
     */
    public Map<Class<?>, Set<String>> declaredStatics() {
        return statics;
    }

    /**
     * @return every class's static fields, for a test comparing the list with its documentation
     */
    public Map<Class<?>, Set<String>> declaredStaticFields() {
        return staticFields;
    }

    /**
     * @return the class's own entries, for a test comparing the list with its documentation
     */
    public Set<String> methodsOf(final Class<?> type) {
        return methods.getOrDefault(type, Collections.emptySet());
    }

    private static Map<Class<?>, Set<String>> frozen(final Map<Class<?>, Set<String>> table) {
        final Map<Class<?>, Set<String>> copy = new LinkedHashMap<>();
        table.forEach((type, names) -> copy.put(type, Collections.unmodifiableSet(new LinkedHashSet<>(names))));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * A getter reads: {@code getX(...)}, {@code isX()}, {@code hasX(...)} and a keyed {@code containsX(key)} (a
     * protobuf map field), any arity ({@code getTags(int)}, {@code getAttributesOrDefault(k, d)}); {@code getClass()}
     * is no getter.
     */
    private static boolean isGetter(final String name) {
        return !"getClass".equals(name) && (prefixed(name, "get") || prefixed(name, "is") || prefixed(name, "has")
            || prefixed(name, "contains"));
    }

    private static boolean hasInstanceMethod(final Class<?> type, final String name, final int arity) {
        for (final Method m : DslMethods.of(type)) {
            if (m.getName().equals(name) && !Modifier.isStatic(m.getModifiers())
                && (m.getParameterCount() == arity || m.isVarArgs() && arity >= m.getParameterCount() - 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean prefixed(final String name, final String prefix) {
        return name.length() > prefix.length() && name.startsWith(prefix)
            && Character.isUpperCase(name.charAt(prefix.length()));
    }

    /**
     * The class, its superclasses and every interface it implements, nearest first; {@code Object} last.
     */
    private static Set<Class<?>> hierarchy(final Class<?> type) {
        final Set<Class<?>> out = new LinkedHashSet<>();
        final Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(type);
        while (!queue.isEmpty()) {
            final Class<?> c = queue.poll();
            if (c == Object.class || !out.add(c)) {
                continue;
            }
            if (c.getSuperclass() != null) {
                queue.add(c.getSuperclass());
            }
            queue.addAll(Arrays.asList(c.getInterfaces()));
        }
        out.add(Object.class);
        return out;
    }

    private static Class<?> boxed(final Class<?> type) {
        if (type == null) {
            return Object.class;
        }
        return type == void.class ? Void.class : DslMethods.boxed(type);
    }

    /**
     * Declares an engine's list. Every name is checked against its class when added.
     */
    public static final class Builder {
        private final String language;
        private final String documentation;
        private final Map<Class<?>, Set<String>> methods = new LinkedHashMap<>();
        private final Map<Class<?>, Set<String>> statics = new LinkedHashMap<>();
        private final Map<Class<?>, Set<String>> staticFields = new LinkedHashMap<>();
        private final Map<Class<?>, Predicate<String>> getters = new LinkedHashMap<>();
        private final Set<Class<?>> setters = new LinkedHashSet<>();
        private Predicate<Class<?>> unlistedWithGetters;
        private boolean refusesVarargs;

        private Builder(final String language, final String documentation) {
            this.language = language;
            this.documentation = documentation;
        }

        /**
         * The methods a rule calls on a value of the type (every overload of each name).
         */
        public Builder methods(final Class<?> type, final String... names) {
            return methods(type, Arrays.asList(names));
        }

        public Builder methods(final Class<?> type, final Collection<String> names) {
            for (final String name : names) {
                require(type, name, false);
            }
            methods.computeIfAbsent(type, t -> new LinkedHashSet<>()).addAll(names);
            return this;
        }

        /**
         * The static methods a rule calls on the type.
         */
        public Builder statics(final Class<?> type, final String... names) {
            for (final String name : names) {
                require(type, name, true);
            }
            statics.computeIfAbsent(type, t -> new LinkedHashSet<>()).addAll(Arrays.asList(names));
            return this;
        }

        /**
         * The public static fields a rule reads on the type, such as a constant.
         */
        public Builder staticFields(final Class<?> type, final String... names) {
            for (final String name : names) {
                try {
                    if (!Modifier.isStatic(type.getField(name).getModifiers())) {
                        throw new IllegalStateException(type.getName() + "." + name + " is no static field");
                    }
                } catch (final NoSuchFieldException e) {
                    throw new IllegalStateException(type.getName() + " has no public field " + name, e);
                }
            }
            staticFields.computeIfAbsent(type, t -> new LinkedHashSet<>()).addAll(Arrays.asList(names));
            return this;
        }

        /**
         * A value of the type reads its getters ({@code getX(...)}, {@code isX()}, {@code hasX(...)},
         * {@code containsX(key)}), {@code getClass()} aside.
         */
        public Builder getters(final Class<?> type) {
            return getters(type, name -> true);
        }

        /**
         * A value of the type reads the getters the filter accepts.
         */
        public Builder getters(final Class<?> type, final Predicate<String> accepted) {
            getters.put(type, accepted);
            return this;
        }

        /**
         * A value of the type calls its instance setters ({@code setX(value)}); a static setter stays refused.
         */
        public Builder setters(final Class<?> type) {
            setters.add(type);
            return this;
        }

        /**
         * A value of a class the list does not name reads its getters (an input's or a function's object).
         */
        public Builder gettersOfUnlistedTypes() {
            return gettersOfUnlistedTypes(type -> true);
        }

        /**
         * As {@link #gettersOfUnlistedTypes()}, for the unlisted classes the predicate accepts only.
         */
        public Builder gettersOfUnlistedTypes(final Predicate<Class<?>> which) {
            unlistedWithGetters = which;
            return this;
        }

        /**
         * A rule calls no variable-length method through a value.
         */
        public Builder refusingVarargs() {
            refusesVarargs = true;
            return this;
        }

        public DslCallables build() {
            return new DslCallables(this);
        }

        private static void require(final Class<?> type, final String name, final boolean wantStatic) {
            for (final Method m : wantStatic ? type.getMethods() : DslMethods.of(type)) {
                if (m.getName().equals(name) && Modifier.isStatic(m.getModifiers()) == wantStatic) {
                    return;
                }
            }
            throw new IllegalStateException(type.getName() + " has no public " + (wantStatic ? "static " : "")
                + "method " + name);
        }
    }
}
