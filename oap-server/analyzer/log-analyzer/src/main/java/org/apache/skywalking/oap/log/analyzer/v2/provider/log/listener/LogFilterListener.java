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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.DSL;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LALConfigs;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

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
    private List<ExecutionContext> contexts;

    LogFilterListener(final Collection<DSL> dsls) {
        this.dsls = new ArrayList<>(dsls);
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
    public LogAnalysisListener parse(final LogData.Builder logData,
                                     final Optional<Object> extraLog) {
        final LogData logDataSnapshot = logData.build();
        contexts = new ArrayList<>(dsls.size());
        for (int i = 0; i < dsls.size(); i++) {
            contexts.add(new ExecutionContext().log(logDataSnapshot).extraLog(extraLog.orElse(null)));
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
    public static class Factory implements LogAnalysisListenerFactory {
        private final Map<Layer, Map<String, DSL>> dsls;

        public Factory(final ModuleManager moduleManager, final LogAnalyzerModuleConfig config) throws Exception {
            dsls = new HashMap<>();

            // Scan SPI providers for default inputType/outputType per layer
            final Map<Layer, LALSourceTypeProvider> spiProviders = new HashMap<>();
            for (final LALSourceTypeProvider p : ServiceLoader.load(LALSourceTypeProvider.class)) {
                spiProviders.put(p.layer(), p);
                log.info("LALSourceTypeProvider: layer={}, inputType={}, outputType={}",
                    p.layer().name(), p.inputType().getName(),
                    p.outputType() != null ? p.outputType().getName() : "default(Log)");
            }

            final List<LALConfig> configList = LALConfigs.load(config.getLalPath(), config.lalFiles())
                                                         .stream()
                                                         .flatMap(it -> it.getRules().stream())
                                                         .collect(Collectors.toList());
            for (final LALConfig c : configList) {
                final Layer layer = Layer.nameOf(c.getLayer());
                final LALSourceTypeProvider spiProvider = spiProviders.get(layer);

                // Per-rule resolution: explicit YAML > SPI > null
                final Class<?> resolvedInputType = resolveInputType(c, spiProvider);
                final Class<?> resolvedOutputType = resolveOutputType(c, spiProvider);

                final Map<String, DSL> layerDsls = this.dsls.computeIfAbsent(layer, k -> new HashMap<>());
                if (layerDsls.put(c.getName(), DSL.of(
                        moduleManager, config, c.getDsl(),
                        resolvedInputType, resolvedOutputType,
                        c.getName(), c.getSourceName())) != null) {
                    throw new ModuleStartException("Layer " + layer.name() + " has already set " + c.getName() + " rule.");
                }
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
            if (layer == null) {
                return null;
            }
            final Map<String, DSL> dsl = dsls.get(layer);
            if (dsl == null) {
                return null;
            }
            return new LogFilterListener(dsl.values());
        }
    }
}
