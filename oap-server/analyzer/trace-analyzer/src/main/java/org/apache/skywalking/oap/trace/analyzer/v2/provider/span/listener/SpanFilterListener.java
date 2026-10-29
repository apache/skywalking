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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javassist.ClassPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.source.TALOutputBuilder;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALMetricShapes;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalDSL;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfigs;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider;

/**
 * The registry of compiled TAL rules, keyed by {@code (Layer, ruleName)}.
 *
 * <p>A span whose resource carries {@code service.layer} is evaluated by the rules declared for that layer and by
 * nothing else; a span without a registered one is not analyzed. There is no fallback to {@code GENERAL}, which is
 * the agent layer.
 */
@Slf4j
public class SpanFilterListener {
    private SpanFilterListener() {
    }

    /**
     * @param layer    the layer a rule was declared for
     * @param ruleName the rule's name
     * @return the key a rule is registered and suspended under
     */
    public static String ruleKey(final Layer layer, final String ruleName) {
        return layer.name() + "|" + ruleName;
    }

    /**
     * The rule store, registered as a module service.
     */
    public static class Factory implements Service {
        private final ModuleManager moduleManager;
        private final TraceAnalyzerModuleConfig config;

        /**
         * Compiled rules of each declared layer. Volatile with copy-on-write, so readers see a consistent
         * snapshot without a lock while a rule is installed.
         */
        private volatile Map<Layer, Map<String, CompiledTAL>> dsls = Collections.emptyMap();

        private volatile Set<String> suspendedKeys = Collections.emptySet();

        private final Object writeLock = new Object();

        /**
         * The input kinds rules may declare, by the alias each provider registers.
         */
        private final Map<String, TALSourceTypeProvider> inputProviders = new LinkedHashMap<>();

        /**
         * Output builders by the short name a rule writes in {@code outputType}.
         */
        private final Map<String, Class<?>> outputBuilders = new HashMap<>();
        /**
         * The shape of every metric the rules this factory loaded declare, across files.
         */
        private final TALMetricShapes metricShapes = new TALMetricShapes();

        public Factory(final ModuleManager moduleManager, final TraceAnalyzerModuleConfig config) {
            this.moduleManager = moduleManager;
            this.config = config;
            for (final TALSourceTypeProvider provider : ServiceLoader.load(TALSourceTypeProvider.class)) {
                inputProviders.put(provider.alias(), provider);
            }
            for (final TALOutputBuilder builder : ServiceLoader.load(TALOutputBuilder.class)) {
                outputBuilders.put(builder.name(), builder.getClass());
            }
        }

        /**
         * Compile the rule files named by the module's configuration. Called from {@code start()}, after every
         * peer module is past {@code prepare}, because compiling reaches other modules' services.
         *
         * @throws Exception when a file cannot be read or a rule does not compile
         */
        public void loadStaticRules() throws Exception {
            final List<TALConfigs> files = TALConfigs.load(config.getTalPath(), config.talFiles(), moduleManager);
            for (final TALConfigs file : files) {
                for (final TALConfig rule : file.getRules()) {
                    addOrReplace(compile(rule));
                }
            }
            log.info("TAL loaded {} rule file(s) from {}", files.size(), config.getTalPath());
        }

        public CompiledTAL compile(final TALConfig rule) throws ModuleStartException {
            return compile(rule, null, null);
        }

        /**
         * Compile one rule, resolving its input and output types first.
         *
         * @param rule              the rule as loaded from YAML
         * @param pool              a pool scoped to {@code targetClassLoader}, or null for the default pool
         * @param targetClassLoader the loader to define the class in, or null for the app loader
         * @return the compiled rule with the coordinates it is stored under
         * @throws ModuleStartException when a type does not resolve or the rule does not compile
         */
        public CompiledTAL compile(final TALConfig rule,
                                   final ClassPool pool,
                                   final ClassLoader targetClassLoader) throws ModuleStartException {
            final List<String> declaredInputs = rule.inputTypes();
            if (declaredInputs.size() != 1) {
                throw new ModuleStartException(
                    "TAL rule " + rule.getName() + " declares " + declaredInputs.size()
                        + " input types; the span root can only be typed to one");
            }
            final TALSourceTypeProvider provider = resolveInput(rule, declaredInputs.get(0));
            final Class<?> outputType = resolveOutput(rule);
            final Layer layer = resolveLayer(rule);

            final TalDSL dsl = TalDSL.of(moduleManager, config, rule.getDsl(),
                provider.inputType(), outputType, rule.getName(), rule.getMetricPrefix(),
                DslSourceRef.ofRule(rule.getSourcePath(), rule.getLineNo()), metricShapes, pool, targetClassLoader);
            return new CompiledTAL(layer, rule.getName(), dsl, provider);
        }

        /**
         * {@code Layer.nameOf} is not used here because it answers {@code UNDEFINED} for a name that is not
         * registered, and a rule filed under {@code UNDEFINED} would compile and never run.
         */
        private static Layer resolveLayer(final TALConfig rule) throws ModuleStartException {
            if (StringUtil.isEmpty(rule.getLayer())) {
                throw new ModuleStartException("TAL rule " + rule.getName() + " declares no layer");
            }
            try {
                return Layer.valueOf(rule.getLayer());
            } catch (final IllegalArgumentException e) {
                throw new ModuleStartException(
                    "TAL rule " + rule.getName() + " declares layer '" + rule.getLayer()
                        + "' which is not registered; declare it under layerDefinitions or name a registered layer", e);
            }
        }

        private TALSourceTypeProvider resolveInput(final TALConfig rule, final String declared)
            throws ModuleStartException {
            final TALSourceTypeProvider byAlias = inputProviders.get(declared);
            if (byAlias != null) {
                return byAlias;
            }
            for (final TALSourceTypeProvider provider : inputProviders.values()) {
                if (provider.inputType().getName().equals(declared)) {
                    return provider;
                }
            }
            throw new ModuleStartException(
                "TAL rule " + rule.getName() + " declares inputType '" + declared
                    + "' which no TALSourceTypeProvider claims; registered aliases are "
                    + inputProviders.keySet());
        }

        private Class<?> resolveOutput(final TALConfig rule) throws ModuleStartException {
            if (rule.getOutputType() == null || rule.getOutputType().isEmpty()) {
                return null;
            }
            final Class<?> byName = outputBuilders.get(rule.getOutputType());
            if (byName != null) {
                return byName;
            }
            try {
                final Class<?> clazz = Class.forName(rule.getOutputType());
                if (!TALOutputBuilder.class.isAssignableFrom(clazz)) {
                    throw new ModuleStartException(
                        "TAL rule " + rule.getName() + " outputType " + rule.getOutputType()
                            + " is not a TALOutputBuilder");
                }
                return clazz;
            } catch (final ClassNotFoundException e) {
                throw new ModuleStartException(
                    "TAL rule " + rule.getName() + " outputType '" + rule.getOutputType()
                        + "' is neither a registered builder name nor a class; registered names are "
                        + outputBuilders.keySet(), e);
            }
        }

        /**
         * Install a compiled rule, replacing whatever occupied its key.
         *
         * @param compiled the rule to install
         */
        public void addOrReplace(final CompiledTAL compiled) {
            synchronized (writeLock) {
                final Map<Layer, Map<String, CompiledTAL>> copy = new LinkedHashMap<>(dsls);
                final Map<String, CompiledTAL> perLayer =
                    new LinkedHashMap<>(copy.getOrDefault(compiled.layer, Collections.emptyMap()));
                perLayer.put(compiled.ruleName, compiled);
                copy.put(compiled.layer, Collections.unmodifiableMap(perLayer));
                dsls = Collections.unmodifiableMap(copy);
            }
        }

        /**
         * @return whether any rule is installed; with none, as when {@code talFiles} is empty, a span has nothing
         * to run and the analyzer skips its own bookkeeping too
         */
        public boolean hasRules() {
            return !dsls.isEmpty();
        }

        public TalDSL find(final Layer layer, final String ruleName) {
            final CompiledTAL compiled = dsls.getOrDefault(layer, Collections.emptyMap()).get(ruleName);
            return compiled == null ? null : compiled.dsl;
        }

        public boolean contains(final Layer layer, final String ruleName) {
            return find(layer, ruleName) != null;
        }

        public void remove(final Layer layer, final String ruleName) {
            synchronized (writeLock) {
                final Map<Layer, Map<String, CompiledTAL>> copy = new LinkedHashMap<>(dsls);
                final Map<String, CompiledTAL> perLayer =
                    new LinkedHashMap<>(copy.getOrDefault(layer, Collections.emptyMap()));
                perLayer.remove(ruleName);
                copy.put(layer, Collections.unmodifiableMap(perLayer));
                dsls = Collections.unmodifiableMap(copy);
            }
        }

        /**
         * Take the named rules out of dispatch without discarding them.
         *
         * @param keys the rule keys to suspend
         */
        public void suspend(final Collection<String> keys) {
            synchronized (writeLock) {
                final Set<String> copy = new HashSet<>(suspendedKeys);
                copy.addAll(keys);
                suspendedKeys = Collections.unmodifiableSet(copy);
            }
        }

        public void resume(final Collection<String> keys) {
            synchronized (writeLock) {
                final Set<String> copy = new HashSet<>(suspendedKeys);
                copy.removeAll(keys);
                suspendedKeys = Collections.unmodifiableSet(copy);
            }
        }

        /**
         * The rules that should see one span.
         *
         * @param layer the span's layer
         * @param input the wire object, used to skip rules typed to a different input
         * @return the rules to run, in file order
         */
        public List<RuleBinding> rulesFor(final Layer layer, final Object input) {
            final Map<String, CompiledTAL> candidates = dsls.getOrDefault(layer, Collections.emptyMap());
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            final List<RuleBinding> out = new ArrayList<>(candidates.size());
            for (final CompiledTAL compiled : candidates.values()) {
                if (suspendedKeys.contains(ruleKey(layer, compiled.ruleName))) {
                    continue;
                }
                if (!compiled.dsl.getInputType().isInstance(input)) {
                    continue;
                }
                // The provider the rule was compiled against indexes the span's attributes the way that rule reads
                // them; it travels with the rule, so installing a rule replaces both in one snapshot swap.
                out.add(new RuleBinding(compiled.dsl, compiled.provider));
            }
            return out;
        }
    }

    /**
     * A compiled rule with the coordinates it is stored under.
     */
    public static final class CompiledTAL {
        public final Layer layer;
        public final String ruleName;
        public final TalDSL dsl;
        public final TALSourceTypeProvider provider;

        public CompiledTAL(final Layer layer, final String ruleName,
                           final TalDSL dsl, final TALSourceTypeProvider provider) {
            this.layer = layer;
            this.ruleName = ruleName;
            this.dsl = dsl;
            this.provider = provider;
        }
    }

    /**
     * A rule paired with the provider that reads its input's attributes.
     */
    public static final class RuleBinding {
        public final TalDSL dsl;
        public final TALSourceTypeProvider provider;

        public RuleBinding(final TalDSL dsl, final TALSourceTypeProvider provider) {
            this.dsl = dsl;
            this.provider = provider;
        }
    }
}
