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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.TALFunctionContext;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalRuntimeHelper;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider;

/**
 * Every function a rule may call, of every input: each input's own, the methods of its {@link TALSourceTypeProvider}
 * that take the run first, and the helper functions the provider hands over, {@link
 * TALSourceTypeProvider#helperFunctions()}; and those modules contribute through {@link TALFunctionProvider}, scoped
 * by {@link TALFunctionProvider#supportsInputType}. Knowing every input's, it
 * tells a rule that calls a function of another input which inputs have it. A call is resolved here when the rule
 * compiles, by name, the rule's input, argument count and argument types; the generated code then calls the bound
 * object, which {@link #instance} hands it.
 */
public class TALFunctionRegistry {
    /**
     * An input that is not registered, as a unit test declares, which has the OTLP input's functions, as it has the
     * OTLP input's constructs in {@link TALInputs}.
     */
    private static final String UNREGISTERED_INPUT = "\0unregistered";

    /**
     * The registered OTLP input, whose functions an unregistered input has.
     */
    private static final String OTLP_ALIAS = "otlp";

    /**
     * One function a rule calls: the bound object that serves it and the key the generated code looks it up by, the
     * method, whether the method takes the run's context first, and whether its last parameter takes the rule's
     * remaining arguments as an array.
     */
    @Getter
    public static final class Resolved {
        private final Object owner;
        private final String ownerKey;
        private final Method method;
        private final boolean takesContext;
        private final boolean varArgs;

        private Resolved(final Object owner, final String ownerKey, final Method method) {
            this.owner = owner;
            this.ownerKey = ownerKey;
            this.method = method;
            final Class<?>[] params = method.getParameterTypes();
            // The context is TALFunctionContext, or for the trace analyzer's own functions the helper that
            // implements it, which is what the generated code passes.
            this.takesContext = params.length > 0 && TALFunctionContext.class.isAssignableFrom(params[0])
                && params[0].isAssignableFrom(TalRuntimeHelper.class);
            this.varArgs = method.isVarArgs();
        }

        /**
         * @return the number of arguments a rule passes before a variable-length tail, the context excluded
         */
        public int ruleArgumentCount() {
            return method.getParameterCount() - (takesContext ? 1 : 0) - (varArgs ? 1 : 0);
        }

        /**
         * @param argument the index of a rule's argument
         * @return the type that argument is converted to
         */
        public Class<?> parameterFor(final int argument) {
            final Class<?>[] params = method.getParameterTypes();
            final int index = argument + (takesContext ? 1 : 0);
            if (varArgs && index >= params.length - 1) {
                return params[params.length - 1].getComponentType();
            }
            return params[index];
        }
    }

    /**
     * A function and the inputs it serves: an input's own functions serve that input, the default ones every input
     * that is not registered, and a module's those its provider supports.
     */
    private static final class Entry {
        private final Resolved resolved;
        private final String input;
        private final TALFunctionProvider module;

        private Entry(final Resolved resolved, final String input, final TALFunctionProvider module) {
            this.resolved = resolved;
            this.input = input;
            this.module = module;
        }

        private boolean serves(final String alias, final Set<String> registered) {
            if (module != null) {
                return module.supportsInputType(alias);
            }
            return UNREGISTERED_INPUT.equals(input) ? !registered.contains(alias) : input.equals(alias);
        }
    }

    private final List<TALFunctionProvider> discovered;
    /**
     * function name to every bound overload of it, of whichever input.
     */
    private final Map<String, List<Entry>> functions = new HashMap<>();
    /**
     * Each bound object by its key: its class name, or the class name and the input it was first bound for when
     * another object of the class holds that name, as two inputs may each hold one.
     */
    private final Map<String, Object> instances = new HashMap<>();
    private final Map<Object, String> keys = new IdentityHashMap<>();
    /**
     * The registered inputs, which overlapping functions and misused functions are reported against.
     */
    private Set<String> inputAliases = Set.of();

    public TALFunctionRegistry(final List<TALFunctionProvider> discovered) {
        this.discovered = List.copyOf(discovered);
    }

    /**
     * @return a registry over the module providers {@link ServiceLoader} finds
     */
    public static TALFunctionRegistry discover() {
        final List<TALFunctionProvider> found = new ArrayList<>();
        for (final TALFunctionProvider provider : ServiceLoader.load(TALFunctionProvider.class)) {
            found.add(provider);
        }
        return new TALFunctionRegistry(found);
    }

    /**
     * The registry a rule compiles and runs against when its caller names none, as a unit test does: every module
     * provider and input on the class path, bound without a module manager.
     */
    public static TALFunctionRegistry defaults() {
        return Defaults.REGISTRY;
    }

    private static final class Defaults {
        private static final TALFunctionRegistry REGISTRY = discover();

        static {
            REGISTRY.bind(null, TALInputs.providers());
        }
    }

    /**
     * @return the modules the discovered module providers name, which the trace analyzer declares as required when
     * loaded
     */
    public Set<String> modules() {
        final Set<String> modules = new LinkedHashSet<>();
        for (final TALFunctionProvider provider : discovered) {
            modules.add(provider.module());
        }
        return modules;
    }

    /**
     * Index every input's own functions and the helper functions its provider hands over, and bind the module
     * providers whose module is loaded and index theirs. A function name belongs to one function class for each input,
     * so two that both serve a name for one input fail here, whatever the arities, and so does an entity constructor's
     * name. Two inputs may each have a function of one name.
     *
     * @param moduleManager the module manager, or null to bind every provider without one, as a unit test does
     * @param inputs        the registered inputs
     * @throws IllegalStateException when two function classes serve one name for one input, a helper does not serve
     *                               the input that hands it over, or a function takes an entity constructor's name
     */
    public void bind(final ModuleManager moduleManager, final Collection<? extends TALSourceTypeProvider> inputs) {
        final Set<String> aliases = new LinkedHashSet<>();
        TALSourceTypeProvider otlp = null;
        List<TALFunctionProvider> otlpHelpers = null;
        final Set<TALFunctionProvider> boundHelpers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final TALSourceTypeProvider input : inputs) {
            aliases.add(input.alias());
            input.bind(moduleManager);
            final List<TALFunctionProvider> helpers = indexInput(input, input.alias(), null);
            bindOnce(helpers, moduleManager, boundHelpers);
            if (OTLP_ALIAS.equals(input.alias()) && input instanceof OtlpTALSourceTypeProvider) {
                otlp = input;
                otlpHelpers = helpers;
            }
        }
        // An unregistered input has the OTLP input's functions, the same bound objects.
        bindOnce(indexInput(otlp != null ? otlp : new OtlpTALSourceTypeProvider(), UNREGISTERED_INPUT, otlpHelpers),
            moduleManager, boundHelpers);
        this.inputAliases = Collections.unmodifiableSet(aliases);
        for (final TALFunctionProvider provider : discovered) {
            if (moduleManager != null && !moduleManager.has(provider.module())) {
                continue;
            }
            provider.bind(moduleManager);
            index(provider, null, provider, functionMethods(provider.getClass()));
        }
        requireOneOwnerPerInput();
    }

    /**
     * Bind each helper object once, as a module's provider is bound once, however many inputs hand it over.
     */
    private static void bindOnce(final List<TALFunctionProvider> helpers, final ModuleManager moduleManager,
                                 final Set<TALFunctionProvider> bound) {
        for (final TALFunctionProvider helper : helpers) {
            if (bound.add(helper)) {
                helper.bind(moduleManager);
            }
        }
    }

    /**
     * An input's own functions and the helper functions its provider hands over, served to that input.
     *
     * @param helpers the helpers already taken from the provider, or null to take them now
     * @return the helpers indexed
     */
    private List<TALFunctionProvider> indexInput(final TALSourceTypeProvider input, final String alias,
                                                 final List<TALFunctionProvider> helpers) {
        index(input, alias, null, inputMethods(input.getClass()));
        final List<TALFunctionProvider> handed = helpers != null ? helpers : input.helperFunctions();
        for (final TALFunctionProvider helper : handed) {
            if (!helper.supportsInputType(input.alias())) {
                throw new IllegalStateException(
                    "TAL input " + input.alias() + " hands over the helper functions of " + helper.getClass().getName()
                        + ", which do not serve that input");
            }
            index(helper, alias, null, functionMethods(helper.getClass()));
        }
        return handed;
    }

    private void index(final Object owner, final String input, final TALFunctionProvider module,
                       final List<Method> methods) {
        final String key = keys.computeIfAbsent(owner, o -> {
            final String byClass = o.getClass().getName();
            final String chosen = instances.containsKey(byClass) ? byClass + "/" + input : byClass;
            instances.put(chosen, o);
            return chosen;
        });
        for (final Method method : methods) {
            final String name = method.getName();
            if (TALSpanRootResolver.ENTITY_CONSTRUCTORS.contains(name)) {
                throw new IllegalStateException(
                    "TAL function " + name + " of " + owner.getClass().getName()
                        + " shadows the entity constructor the language defines under that name");
            }
            functions.computeIfAbsent(name, k -> new ArrayList<>()).add(new Entry(new Resolved(owner, key, method), input,
                module));
        }
    }

    private void requireOneOwnerPerInput() {
        final List<String> checked = new ArrayList<>(inputAliases);
        checked.add(UNREGISTERED_INPUT);
        for (final Map.Entry<String, List<Entry>> function : functions.entrySet()) {
            for (final String alias : checked) {
                Object owner = null;
                for (final Entry entry : function.getValue()) {
                    if (!entry.serves(alias, inputAliases)) {
                        continue;
                    }
                    if (owner != null && owner != entry.resolved.owner) {
                        throw new IllegalStateException(
                            "TAL function " + function.getKey() + " is served by both " + owner.getClass().getName()
                                + " and " + entry.resolved.owner.getClass().getName() + " for "
                                + (UNREGISTERED_INPUT.equals(alias) ? "an unregistered input" : "input " + alias));
                    }
                    owner = entry.resolved.owner;
                }
            }
        }
    }

    /**
     * @param name       the function a rule calls
     * @param argCount   the number of arguments the rule passes
     * @param inputAlias the alias of the rule's input
     * @return the overloads of the rule's input that take that many arguments, those of a fixed count before those
     * with a variable-length tail; null when no input has the name
     * @throws IllegalArgumentException when only other inputs have the function, or no overload takes that many
     *                                  arguments
     */
    public List<Resolved> candidates(final String name, final int argCount, final String inputAlias) {
        final List<Entry> all = functions.get(name);
        if (all == null) {
            return null;
        }
        final List<Resolved> forInput = new ArrayList<>();
        for (final Entry entry : all) {
            if (entry.serves(inputAlias, inputAliases)) {
                forInput.add(entry.resolved);
            }
        }
        if (forInput.isEmpty()) {
            final Set<String> owners = new TreeSet<>();
            for (final String input : inputAliases) {
                for (final Entry entry : all) {
                    if (entry.serves(input, inputAliases)) {
                        owners.add(input);
                    }
                }
            }
            throw new IllegalArgumentException(
                name + "() is a function of " + (owners.isEmpty() ? "another input" : "the inputs " + owners)
                    + ", not of this rule's inputType " + inputAlias);
        }
        final List<Resolved> fixed = new ArrayList<>();
        final List<Resolved> variable = new ArrayList<>();
        for (final Resolved candidate : forInput) {
            if (!candidate.varArgs && candidate.ruleArgumentCount() == argCount) {
                fixed.add(candidate);
            } else if (candidate.varArgs && argCount >= candidate.ruleArgumentCount()) {
                variable.add(candidate);
            }
        }
        if (!fixed.isEmpty()) {
            return fixed;
        }
        if (!variable.isEmpty()) {
            return variable;
        }
        throw new IllegalArgumentException("TAL function " + name + "() does not take " + argCount + " argument(s)");
    }

    /**
     * Choose among the overloads {@link #candidates} returned by the arguments' compile-time types, as Java ranks
     * them: a parameter of the argument's own type beats a supertype of it, which beats one that widens a number,
     * which beats one that converts it; a value of unknown type converts to any scalar, and an object that no
     * conversion reaches rules an overload out.
     *
     * @param name       the function, for diagnostics
     * @param candidates the overloads {@link #candidates} returned
     * @param argTypes   each argument's compile-time type, null when unknown
     * @return the one overload that fits best
     * @throws IllegalArgumentException when none fits, or two fit equally well
     */
    public static Resolved select(final String name, final List<Resolved> candidates, final Class<?>[] argTypes) {
        Resolved best = null;
        int bestScore = -1;
        boolean tie = false;
        for (final Resolved candidate : candidates) {
            int score = 0;
            for (int i = 0; i < argTypes.length && score >= 0; i++) {
                final int fit = fit(candidate.parameterFor(i), argTypes[i]);
                score = fit < 0 ? -1 : score + fit;
            }
            if (score < 0) {
                continue;
            }
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
                tie = false;
            } else if (score == bestScore) {
                tie = true;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException(
                "no overload of TAL function " + name + "() takes " + describe(argTypes));
        }
        if (tie) {
            throw new IllegalArgumentException(
                "TAL function " + name + "() has overloads that take " + describe(argTypes)
                    + " equally well; convert the arguments with as to choose one");
        }
        return best;
    }

    /**
     * The same ranking for a Java method a chain calls on an object.
     *
     * @param overloads the methods of one name and arity
     * @param argTypes  each argument's compile-time type, null when unknown
     * @return the one method that fits best, or null when none fits or two fit equally well
     */
    static Method bestMethod(final List<Method> overloads, final Class<?>[] argTypes) {
        Method best = null;
        int bestScore = -1;
        boolean tie = false;
        for (final Method method : overloads) {
            final Class<?>[] params = method.getParameterTypes();
            int score = 0;
            for (int i = 0; i < argTypes.length && score >= 0; i++) {
                final int fit = fit(params[i], argTypes[i]);
                score = fit < 0 ? -1 : score + fit;
            }
            if (score > bestScore) {
                best = method;
                bestScore = score;
                tie = false;
            } else if (score == bestScore && score >= 0) {
                tie = true;
            }
        }
        return best == null || tie || bestScore < 0 ? null : best;
    }

    /**
     * The numbers in the order Java widens them.
     */
    private static final List<Class<?>> WIDENING = List.of(Integer.class, Long.class, Float.class, Double.class);

    /**
     * @return 4 when the parameter is the argument's type, 3 when it is a supertype of it, 2 when it widens the
     * number, 1 when a conversion reaches it or the argument's type is unknown, -1 when nothing reaches it
     */
    private static int fit(final Class<?> param, final Class<?> arg) {
        if (arg == null) {
            return 1;
        }
        final Class<?> boxedParam = TALCodegenHelper.boxedClass(param);
        final Class<?> boxedArg = TALCodegenHelper.boxedClass(arg);
        if (boxedParam == boxedArg) {
            return 4;
        }
        if (arg == Object.class) {
            return 1;
        }
        if (boxedParam.isAssignableFrom(boxedArg)) {
            return 3;
        }
        final int from = WIDENING.indexOf(boxedArg);
        if (from >= 0 && WIDENING.indexOf(boxedParam) > from) {
            return 2;
        }
        if (isScalar(boxedParam) && isScalar(boxedArg)) {
            return 1;
        }
        return -1;
    }

    private static boolean isScalar(final Class<?> type) {
        return type == String.class || Number.class.isAssignableFrom(type) || type == Boolean.class
            || type.isEnum() || type == Layer.class;
    }

    private static String describe(final Class<?>[] argTypes) {
        final List<String> names = new ArrayList<>();
        for (final Class<?> type : argTypes) {
            names.add(type == null ? "?" : type.getSimpleName());
        }
        return "(" + String.join(", ", names) + ")";
    }

    /**
     * @param key a bound object's key, {@link Resolved#getOwnerKey()}
     * @return the bound object, which generated code calls
     */
    public Object instance(final String key) {
        return instances.get(key);
    }

    /**
     * The public methods a function class declares itself, the extension points' own methods and every {@code bind}
     * overload excluded.
     */
    /**
     * An input provider's functions: its public methods, inherited ones included, whose first parameter is the run,
     * so the provider's own methods and its span readers are not functions.
     */
    private static List<Method> inputMethods(final Class<?> type) {
        final List<Method> methods = new ArrayList<>();
        for (final Method method : type.getMethods()) {
            final Class<?>[] params = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
                || params.length == 0 || params[0] != TalRuntimeHelper.class) {
                continue;
            }
            methods.add(method);
        }
        return methods;
    }

    private static List<Method> functionMethods(final Class<?> type) {
        final List<Method> methods = new ArrayList<>();
        for (final Method method : type.getDeclaredMethods()) {
            final int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || method.isSynthetic()
                || method.isBridge()) {
                continue;
            }
            final String name = method.getName();
            if ("module".equals(name) && method.getParameterCount() == 0
                || "supportsInputType".equals(name) && method.getParameterCount() == 1
                || "bind".equals(name)) {
                continue;
            }
            methods.add(method);
        }
        return methods;
    }
}
