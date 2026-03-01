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

package org.apache.skywalking.oap.log.analyzer.provider.log.listener;

import com.google.protobuf.Message;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.dsl.Binding;
import org.apache.skywalking.oap.log.analyzer.dsl.DSL;
import org.apache.skywalking.oap.log.analyzer.provider.LALConfig;
import org.apache.skywalking.oap.log.analyzer.provider.LALConfigs;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

/**
 * Runtime listener that executes compiled LAL rules against incoming log data.
 *
 * <p>Each instance wraps a collection of {@link DSL} objects — one per LAL rule
 * defined for a specific {@link Layer}. Created per-log by {@link Factory#create(Layer)}.
 *
 * <p>Two-phase execution (called by {@link org.apache.skywalking.oap.log.analyzer.provider.log.LogAnalyzer}):
 * <ol>
 *   <li>{@link #parse} — creates a fresh {@link Binding} with the current log data
 *       and binds it to every DSL instance (sets the ThreadLocal in each Spec).</li>
 *   <li>{@link #build} — calls {@link DSL#evaluate()} on every DSL instance,
 *       which invokes the compiled {@link org.apache.skywalking.oap.log.analyzer.dsl.LalExpression}
 *       to run the filter/extractor/sink pipeline.</li>
 * </ol>
 *
 * <p>The inner {@link Factory} is created once at startup by
 * {@link org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleProvider#start()}.
 * It loads all {@code .yaml} LAL config files, compiles each rule's DSL string
 * into a {@link DSL} instance via
 * {@link DSL#of(org.apache.skywalking.oap.server.library.module.ModuleManager,
 *   org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig, String)},
 * and organizes them by {@link Layer}.
 */
@Slf4j
@RequiredArgsConstructor
public class LogFilterListener implements LogAnalysisListener {
    private final Collection<DSL> dsls;

    @Override
    public void build() {
        dsls.forEach(dsl -> {
            try {
                dsl.evaluate();
            } catch (final Exception e) {
                log.warn("Failed to evaluate dsl: {}", dsl, e);
            }
        });
    }

    @Override
    public LogAnalysisListener parse(final LogData.Builder logData,
                                     final Message extraLog) {
        dsls.forEach(dsl -> dsl.bind(new Binding().log(logData.build())
                                                  .extraLog(extraLog)));
        return this;
    }

    public static class Factory implements LogAnalysisListenerFactory {
        private final Map<Layer, Map<String, DSL>> dsls;

        public Factory(final ModuleManager moduleManager, final LogAnalyzerModuleConfig config) throws Exception {
            dsls = new HashMap<>();

            final List<LALConfig> configList = LALConfigs.load(config.getLalPath(), config.lalFiles())
                                                         .stream()
                                                         .flatMap(it -> it.getRules().stream())
                                                         .collect(Collectors.toList());
            for (final LALConfig c : configList) {
                Layer layer = Layer.nameOf(c.getLayer());
                Map<String, DSL> dsls = this.dsls.computeIfAbsent(layer, k -> new HashMap<>());
                if (dsls.put(c.getName(), DSL.of(moduleManager, config, c.getDsl())) != null) {
                    throw new ModuleStartException("Layer " + layer.name() + " has already set " + c.getName() + " rule.");
                }
            }
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
