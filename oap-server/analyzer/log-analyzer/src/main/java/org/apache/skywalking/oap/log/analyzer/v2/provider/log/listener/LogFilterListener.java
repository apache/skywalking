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

package org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.DSL;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfigs;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Runtime listener that executes compiled LAL rules against incoming log data.
 *
 * <p>Each instance wraps a collection of {@link DSL} objects — one per LAL rule
 * defined for a specific {@link Layer}. Created per-log by {@link Factory#create(Layer)}.
 *
 * <p>Two-phase execution (called by {@link org.apache.skywalking.oap.log.analyzer.v2.provider.log.LogAnalyzer}):
 * <ol>
 *   <li>{@link #parse} — creates a fresh {@link ExecutionContext} with the current log data.</li>
 *   <li>{@link #build} — calls {@link DSL#evaluate(ExecutionContext)} on every DSL instance,
 *       which invokes the compiled {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression}
 *       to run the filter/extractor/sink pipeline. The context is passed explicitly — no ThreadLocal.</li>
 * </ol>
 */
@Slf4j
public class LogFilterListener implements LogAnalysisListener {
    private final List<DSL> dsls;
    private final boolean autoMode;
    private List<ExecutionContext> contexts;

    LogFilterListener(final Collection<DSL> dsls, final boolean autoMode) {
        this.dsls = new ArrayList<>(dsls);
        this.autoMode = autoMode;
    }

    @Override
    public void build() {
        for (int i = 0; i < dsls.size(); i++) {
            try {
                dsls.get(i).evaluate(contexts.get(i));
            } catch (final Exception e) {
                log.warn("Failed to evaluate dsl: {}", dsls.get(i), e);
            }
        }
    }

    @Override
    public boolean claimed() {
        if (!autoMode || contexts == null) {
            return false;
        }
        // At least one auto rule did not abort — it claimed the log
        for (final ExecutionContext ctx : contexts) {
            if (!ctx.shouldAbort()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public LogAnalysisListener parse(final LogMetadata metadata,
                                     final Object input) {
        contexts = new ArrayList<>(dsls.size());
        for (int i = 0; i < dsls.size(); i++) {
            final ExecutionContext ctx = new ExecutionContext().init(metadata, input);
            if (autoMode) {
                ctx.autoLayerMode(true);
            }
            contexts.add(ctx);
        }
        return this;
    }

    /**
     * Eagerly compiles all LAL rules at startup and groups the resulting
     * {@link DSL} instances by telemetry layer and rule name.
     *
     * <p>{@code dsls} structure: {@code Layer -> (ruleName -> DSL)}.
     * <ul>
     *   <li><b>Outer key</b> ({@link Layer}): the telemetry layer declared in
     *       the YAML rule (e.g., {@code GENERAL}, {@code MESH}).</li>
     *   <li><b>Inner key</b> ({@code String}): the rule {@code name} field
     *       from the YAML config (e.g., {@code "default"}, {@code "envoy-als"}).
     *       Must be unique within a layer.</li>
     *   <li><b>Value</b> ({@link DSL}): a compiled LAL expression plus its
     *       runtime {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec},
     *       ready to evaluate incoming logs.</li>
     * </ul>
     *
     * <p>At runtime, {@link #create(Layer)} returns a {@link LogFilterListener}
     * containing all DSL instances for the requested layer.
     */
    public static class Factory implements LogAnalysisListenerFactory, Service {
        /**
         * Volatile + copy-on-write so readers in {@link #create(Layer)} are lock-free and the
         * runtime-rule hot-update path can mutate without blocking sample evaluation. Inner maps
         * are wholesale-replaced via the writeLock below rather than mutated in place, so the
         * reference observed by a reader is always fully-populated for its generation.
         */
        private volatile Map<Layer, Map<String, DSL>> dsls;
        private volatile Map<String, DSL> autoDsls;

        /**
         * Suspended rule keys — encoded as {@code layer.name() + "|" + ruleName} for layer-keyed
         * rules and {@code "<auto>|" + ruleName} for auto-layer rules. When {@link #create} is
         * asked for a layer, it filters out entries whose key is in this set so samples arriving
         * during a hot-update Suspend window never hit the prior DSL. Volatile + CoW replace so
         * readers stay lock-free — matches the {@link #dsls} / {@link #autoDsls} contract.
         */
        private volatile Set<String> suspendedKeys = Collections.emptySet();

        private static final String AUTO_LAYER_PREFIX = "<auto>|";

        /** Serializes runtime mutations (addOrReplace / remove / suspend / resume). Startup
         *  writes are single-threaded. */
        private final Object writeLock = new Object();

        private final ModuleManager moduleManager;
        private final LogAnalyzerModuleConfig analyzerConfig;
        private final Map<Layer, LALSourceTypeProvider> spiProviders;

        /**
         * Two-phase init — the constructor wires fields and runs the SPI scan, leaving the
         * {@code dsls} / {@code autoDsls} registry empty so this instance is safe to register
         * as a module service from {@code prepare()}. The rule-compile pass — which calls
         * {@code RecordSinkListener.Factory.<init>} → {@code moduleManager.find()} and is
         * therefore illegal during the prepare stage — is deferred to
         * {@link #loadStaticRules()}, which the provider invokes from {@code start()} once
         * the manager is past prepare.
         *
         * <p>This shape mirrors {@code OpenTelemetryMetricRequestProcessor}: the receiver
         * registers a config-only object in {@code prepare()} and lights it up in
         * {@code start()}, so cross-module {@code requiredCheck} resolves the service name
         * cleanly.
         */
        public Factory(final ModuleManager moduleManager, final LogAnalyzerModuleConfig config) throws Exception {
            this.moduleManager = moduleManager;
            this.analyzerConfig = config;

            // Scan SPI providers for default inputType/outputType per layer. SPI lookup uses
            // the JDK's {@code ServiceLoader} — no moduleManager.find required, safe in prepare.
            this.spiProviders = new HashMap<>();
            for (final LALSourceTypeProvider p : ServiceLoader.load(LALSourceTypeProvider.class)) {
                spiProviders.put(p.layer(), p);
                log.info("LALSourceTypeProvider: layer={}, inputType={}, outputType={}",
                    p.layer().name(), p.inputType().getName(),
                    p.outputType() != null ? p.outputType().getName() : "default(Log)");
            }

            // Empty registry — populated by {@link #loadStaticRules}.
            this.dsls = new HashMap<>();
            this.autoDsls = new HashMap<>();
        }

        /**
         * Compile every static LAL rule the {@link LogAnalyzerModuleConfig} configures and
         * publish the resulting registry. Provider must call this from {@code start()} —
         * never from {@code prepare()} — because {@code compile} reaches into
         * {@code RecordSinkListener.Factory.<init>}, which calls {@code moduleManager.find()}
         * and asserts the manager is past prepare.
         *
         * <p>Idempotent against re-entry within the same boot (the Factory only stays in
         * its empty post-construct state until this fires once); calling it twice on the
         * same instance is a programming error.
         */
        public void loadStaticRules() throws Exception {
            final Map<Layer, Map<String, DSL>> initDsls = new HashMap<>();
            final Map<String, DSL> initAutoDsls = new HashMap<>();
            final List<LALConfig> configList = LALConfigs.load(analyzerConfig.getLalPath(), analyzerConfig.lalFiles())
                                                         .stream()
                                                         .flatMap(it -> it.getRules().stream())
                                                         .collect(Collectors.toList());
            for (final LALConfig c : configList) {
                final CompiledLAL compiled = compile(c);
                if (compiled.layer == null) {
                    if (initAutoDsls.put(c.getName(), compiled.dsl) != null) {
                        throw new ModuleStartException(
                            "Auto-layer rules have duplicate name: " + c.getName());
                    }
                } else {
                    final Map<String, DSL> layerDsls = initDsls.computeIfAbsent(compiled.layer, k -> new HashMap<>());
                    if (layerDsls.put(c.getName(), compiled.dsl) != null) {
                        throw new ModuleStartException(
                            "Layer " + compiled.layer.name() + " has already set " + c.getName() + " rule.");
                    }
                }
            }
            // Publish: readers from now on see the startup-complete registry.
            this.dsls = initDsls;
            this.autoDsls = initAutoDsls;
        }

        /**
         * Compile a single LALConfig into a runnable {@link DSL}. Used by both the startup
         * constructor and the runtime-rule hot-update path (LalFileApplier).
         */
        public CompiledLAL compile(final LALConfig c) throws ModuleStartException {
            return compile(c, null, null);
        }

        /**
         * Runtime-rule overload: compile with a per-file {@link javassist.ClassPool} and target
         * {@link ClassLoader} so the generated {@code LalExpression} class is defined in the
         * caller's per-file loader. When both args are null this delegates to the legacy
         * startup path. Called by {@code LalFileApplier.apply} with the per-file
         * {@code RuleClassLoader} it creates on every compile.
         */
        public CompiledLAL compile(final LALConfig c,
                                   final javassist.ClassPool pool,
                                   final ClassLoader targetClassLoader) throws ModuleStartException {
            final boolean isAuto = LALConfig.LAYER_AUTO.equalsIgnoreCase(c.getLayer());
            final Layer layer = isAuto ? null : Layer.nameOf(c.getLayer());
            final LALSourceTypeProvider spiProvider = isAuto ? null : spiProviders.get(layer);
            final Class<?> resolvedInputType = resolveInputType(c, spiProvider);
            final Class<?> resolvedOutputType = resolveOutputType(c, spiProvider);
            final DSL dsl = DSL.of(
                moduleManager, analyzerConfig, c.getDsl(),
                resolvedInputType, resolvedOutputType,
                c.getName(), c.getSourceName(),
                pool, targetClassLoader);
            return new CompiledLAL(layer, c.getName(), dsl);
        }

        /**
         * Install a compiled rule under {@code (layer, ruleName)}, replacing any prior binding
         * for the same key. Runtime hot-update use only — startup path goes through the
         * constructor. Throws {@link ModuleStartException} if the key is already owned by a
         * DIFFERENT sourceName (cross-file collision inside a layer) — callers that are
         * legitimately re-registering the same file should have removed the old binding first.
         */
        public void addOrReplace(final CompiledLAL compiled) {
            synchronized (writeLock) {
                if (compiled.layer == null) {
                    final Map<String, DSL> next = new HashMap<>(autoDsls);
                    next.put(compiled.ruleName, compiled.dsl);
                    autoDsls = next;
                } else {
                    final Map<Layer, Map<String, DSL>> next = new HashMap<>(dsls);
                    final Map<String, DSL> layerMap =
                        new HashMap<>(next.getOrDefault(compiled.layer, new HashMap<>()));
                    layerMap.put(compiled.ruleName, compiled.dsl);
                    next.put(compiled.layer, layerMap);
                    dsls = next;
                }
            }
        }

        /** Runtime remove. No-op when the key isn't present. */
        public void remove(final Layer layer, final String ruleName) {
            synchronized (writeLock) {
                if (layer == null) {
                    if (!autoDsls.containsKey(ruleName)) {
                        return;
                    }
                    final Map<String, DSL> next = new HashMap<>(autoDsls);
                    next.remove(ruleName);
                    autoDsls = next;
                } else {
                    final Map<String, DSL> layerMap = dsls.get(layer);
                    if (layerMap == null || !layerMap.containsKey(ruleName)) {
                        return;
                    }
                    final Map<Layer, Map<String, DSL>> next = new HashMap<>(dsls);
                    final Map<String, DSL> newLayerMap = new HashMap<>(layerMap);
                    newLayerMap.remove(ruleName);
                    if (newLayerMap.isEmpty()) {
                        next.remove(layer);
                    } else {
                        next.put(layer, newLayerMap);
                    }
                    dsls = next;
                }
            }
        }

        /** Check whether {@code (layer, ruleName)} is already owned — used by hot-update to
         *  detect cross-file collisions before registering. */
        public boolean contains(final Layer layer, final String ruleName) {
            if (layer == null) {
                return autoDsls.containsKey(ruleName);
            }
            final Map<String, DSL> layerMap = dsls.get(layer);
            return layerMap != null && layerMap.containsKey(ruleName);
        }

        /**
         * Mark the given rules as suspended so {@link #create} excludes them until
         * {@link #resume} is called. Runtime hot-update path: the reconciler invokes this before
         * it broadcasts cluster Suspend so local LAL dispatch for the bundle is paused at the
         * same moment peer dispatch goes away. Idempotent — repeating the same keys is a no-op.
         */
        public void suspend(final Collection<String> keys) {
            if (keys == null || keys.isEmpty()) {
                return;
            }
            synchronized (writeLock) {
                final Set<String> next = new HashSet<>(suspendedKeys);
                if (!next.addAll(keys)) {
                    return;
                }
                suspendedKeys = Collections.unmodifiableSet(next);
            }
        }

        /**
         * Reverse of {@link #suspend}. Removes the given keys from the suspended set so
         * subsequent {@link #create} calls see the rule again. Idempotent — keys not currently
         * suspended are silently skipped.
         */
        public void resume(final Collection<String> keys) {
            if (keys == null || keys.isEmpty()) {
                return;
            }
            synchronized (writeLock) {
                if (suspendedKeys.isEmpty()) {
                    return;
                }
                final Set<String> next = new HashSet<>(suspendedKeys);
                if (!next.removeAll(keys)) {
                    return;
                }
                suspendedKeys = next.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(next);
            }
        }

        /**
         * Encode a {@code (layer, ruleName)} pair the way {@link #suspendedKeys} stores it. Null
         * layer → auto-layer prefix. Callers outside this class use this helper so the encoding
         * stays private to the factory.
         */
        public static String ruleKey(final Layer layer, final String ruleName) {
            return (layer == null ? AUTO_LAYER_PREFIX : layer.name() + "|") + ruleName;
        }

        /** Compact result of {@link #compile(LALConfig)} so callers don't handle Layer / DSL separately. */
        public static final class CompiledLAL {
            public final Layer layer;
            public final String ruleName;
            public final DSL dsl;

            public CompiledLAL(final Layer layer, final String ruleName, final DSL dsl) {
                this.layer = layer;
                this.ruleName = ruleName;
                this.dsl = dsl;
            }
        }

        private static Class<?> resolveInputType(final LALConfig config,
                                                  final LALSourceTypeProvider spiProvider) throws ModuleStartException {
            final String yamlType = config.getInputType();
            if (yamlType != null && !yamlType.isEmpty()) {
                try {
                    return Class.forName(yamlType);
                } catch (ClassNotFoundException e) {
                    throw new ModuleStartException(
                        "LAL rule '" + config.getName() + "' declares inputType '"
                            + yamlType + "' but the class was not found.", e);
                }
            }
            return spiProvider != null ? spiProvider.inputType() : null;
        }

        /**
         * Short name → implementation class map built from {@link ServiceLoader}{@code <LALOutputBuilder>}.
         * Populated once on first call to {@link #resolveOutputType}.
         */
        private static volatile Map<String, Class<?>> OUTPUT_BUILDER_NAMES;

        private static Map<String, Class<?>> loadOutputBuilderNames() {
            if (OUTPUT_BUILDER_NAMES != null) {
                return OUTPUT_BUILDER_NAMES;
            }
            synchronized (Factory.class) {
                if (OUTPUT_BUILDER_NAMES != null) {
                    return OUTPUT_BUILDER_NAMES;
                }
                final Map<String, Class<?>> map = new HashMap<>();
                for (final LALOutputBuilder builder : ServiceLoader.load(LALOutputBuilder.class)) {
                    final String name = builder.name();
                    final Class<?> prev = map.put(name, builder.getClass());
                    if (prev != null) {
                        log.warn("Duplicate LALOutputBuilder name '{}': {} vs {}",
                            name, prev.getName(), builder.getClass().getName());
                    }
                    log.info("LALOutputBuilder registered: name={}, class={}",
                        name, builder.getClass().getName());
                }
                OUTPUT_BUILDER_NAMES = map;
                return map;
            }
        }

        private static Class<?> resolveOutputType(
                final LALConfig config,
                final LALSourceTypeProvider spiProvider) throws ModuleStartException {
            // Per-rule YAML config takes priority
            final String yamlType = config.getOutputType();
            if (yamlType != null && !yamlType.isEmpty()) {
                // Try short name first (no '.' means it's not a FQCN)
                if (!yamlType.contains(".")) {
                    final Class<?> byName = loadOutputBuilderNames().get(yamlType);
                    if (byName != null) {
                        return byName;
                    }
                }
                // Fall back to FQCN
                try {
                    return Class.forName(yamlType);
                } catch (ClassNotFoundException e) {
                    throw new ModuleStartException(
                        "LAL rule '" + config.getName() + "' declares outputType '"
                            + yamlType + "' but neither a registered LALOutputBuilder name"
                            + " nor a class was found.", e);
                }
            }
            // Fall back to SPI default for the layer
            if (spiProvider != null) {
                final Class<?> spiOutput = spiProvider.outputType();
                if (spiOutput != null) {
                    return spiOutput;
                }
            }
            return null; // DSL.of() will default to Log.class
        }

        @Override
        public LogAnalysisListener create(Layer layer) {
            // Snapshot the suspended set once so a concurrent suspend/resume can't flip behaviour
            // mid-iteration. The reference is volatile + copy-on-write so this is lock-free.
            final Set<String> susp = suspendedKeys;
            if (layer == null) {
                // null layer → route to auto-layer rules
                if (autoDsls.isEmpty()) {
                    return null;
                }
                final Collection<DSL> eligible = susp.isEmpty()
                    ? autoDsls.values()
                    : filterSuspended(autoDsls, susp, null);
                if (eligible.isEmpty()) {
                    return null;
                }
                return new LogFilterListener(eligible, true);
            }
            final Map<String, DSL> dsl = dsls.get(layer);
            if (dsl == null) {
                return null;
            }
            final Collection<DSL> eligible = susp.isEmpty()
                ? dsl.values()
                : filterSuspended(dsl, susp, layer);
            if (eligible.isEmpty()) {
                return null;
            }
            return new LogFilterListener(eligible, false);
        }

        private static Collection<DSL> filterSuspended(final Map<String, DSL> source,
                                                       final Set<String> suspended,
                                                       final Layer layer) {
            final List<DSL> out = new ArrayList<>(source.size());
            for (final Map.Entry<String, DSL> e : source.entrySet()) {
                if (!suspended.contains(ruleKey(layer, e.getKey()))) {
                    out.add(e.getValue());
                }
            }
            return out;
        }
    }
}
