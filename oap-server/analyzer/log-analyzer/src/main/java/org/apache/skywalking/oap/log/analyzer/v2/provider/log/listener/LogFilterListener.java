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

import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
 *   <li>{@link #parse} — creates a fresh {@link ExecutionContext} with the current log data
 *       and binds it to every DSL instance (sets the ThreadLocal in each Spec).</li>
 *   <li>{@link #build} — calls {@link DSL#evaluate(ExecutionContext)} on every DSL instance,
 *       which invokes the compiled {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression}
 *       to run the filter/extractor/sink pipeline.</li>
 * </ol>
 *
 * <p>The inner {@link Factory} is created once at startup by
 * {@link org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider#start()}.
 * It loads all {@code .yaml} LAL config files, compiles each rule's DSL string
 * into a {@link DSL} instance via
 * {@link DSL#of(org.apache.skywalking.oap.server.library.module.ModuleManager,
 *   org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig, String)},
 * and organizes them by {@link Layer}.
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
                                     final Message extraLog) {
        final LogData log = logData.build();
        contexts = new ArrayList<>(dsls.size());
        for (int i = 0; i < dsls.size(); i++) {
            contexts.add(new ExecutionContext().log(log).extraLog(extraLog));
        }
        return this;
    }

    public static class Factory implements LogAnalysisListenerFactory {
        private final Map<Layer, Map<String, DSL>> dsls;

        public Factory(final ModuleManager moduleManager, final LogAnalyzerModuleConfig config) throws Exception {
            dsls = new HashMap<>();

            // Scan SPI providers for default extraLogType per layer
            final Map<Layer, Class<?>> spiTypes = new HashMap<>();
            for (final LALSourceTypeProvider p : ServiceLoader.load(LALSourceTypeProvider.class)) {
                spiTypes.put(p.layer(), p.extraLogType());
                log.info("LALSourceTypeProvider: layer={} -> {}",
                    p.layer().name(), p.extraLogType().getName());
            }

            final List<LALConfig> configList = LALConfigs.load(config.getLalPath(), config.lalFiles())
                                                         .stream()
                                                         .flatMap(it -> it.getRules().stream())
                                                         .collect(Collectors.toList());
            for (final LALConfig c : configList) {
                final Layer layer = Layer.nameOf(c.getLayer());

                // Per-rule resolution: explicit YAML > SPI > null
                Class<?> resolvedType = resolveExtraLogType(c, spiTypes.get(layer));

                final Map<String, DSL> layerDsls = this.dsls.computeIfAbsent(layer, k -> new HashMap<>());
                if (layerDsls.put(c.getName(), DSL.of(moduleManager, config, c.getDsl(), resolvedType, c.getName())) != null) {
                    throw new ModuleStartException("Layer " + layer.name() + " has already set " + c.getName() + " rule.");
                }
            }
        }

        private static Class<?> resolveExtraLogType(final LALConfig config,
                                                     final Class<?> spiType) throws ModuleStartException {
            final String yamlType = config.getExtraLogType();
            if (yamlType != null && !yamlType.isEmpty()) {
                try {
                    return Class.forName(yamlType);
                } catch (ClassNotFoundException e) {
                    throw new ModuleStartException(
                        "LAL rule '" + config.getName() + "' declares extraLogType '"
                            + yamlType + "' but the class was not found.", e);
                }
            }
            return spiType;
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
