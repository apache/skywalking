/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.log.analyzer.v2.provider.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.AutoListeners;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogAnalysisListener;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogAnalysisListenerFactory;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Entry point for log analysis. Created per-request by the log receiver.
 *
 * <p>Runtime execution ({@link #doAnalysis}):
 * <ol>
 *   <li>Validates the incoming log (service name must be non-empty, layer must be valid).</li>
 *   <li>Asks all registered {@link LogAnalysisListenerFactory} instances to create listeners for the log's
 *       layer; a log without a layer gets the auto rules', then, when none claimed it, the fallback auto
 *       rules', and is dropped when those don't claim it either. For LAL, the factory is
 *       {@link org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogFilterListener.Factory},
 *       which returns a listener wrapping all compiled {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.DSL}
 *       instances for that layer.</li>
 *   <li>{@code notifyAnalysisListener(metadata, input)} — calls
 *       {@link org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogAnalysisListener#parse}
 *       on each listener, which binds the metadata and input to the compiled LAL scripts.</li>
 *   <li>{@code notifyAnalysisListenerToBuild()} — calls
 *       {@link org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogAnalysisListener#build}
 *       on each listener, which evaluates the compiled LAL scripts (extractors, sinks).</li>
 * </ol>
 */
@Slf4j
public class LogAnalyzer {
    private final ILogAnalysisListenerManager factoryManager;

    public LogAnalyzer(final ILogAnalysisListenerManager factoryManager) {
        this.factoryManager = factoryManager;
    }

    private final List<LogAnalysisListener> listeners = new ArrayList<>();

    public void doAnalysis(LogMetadata metadata, final Object input) {
        if (StringUtil.isEmpty(metadata.getService())) {
            log.debug("The log is ignored because the Service name is empty");
            return;
        }
        if (metadata.getTimestamp() == 0) {
            metadata.setTimestamp(System.currentTimeMillis());
        }
        if (metadata.getLayer() == null || metadata.getLayer().isEmpty()) {
            // A log without a layer: the auto rules, then the fallback auto rules, until one claims it. Both
            // passes come from one view of the rules, taken before either runs.
            final List<AutoListeners> passes = factoryManager.getLogAnalysisListenerFactories()
                                                             .stream()
                                                             .map(LogAnalysisListenerFactory::createAuto)
                                                             .collect(Collectors.toList());
            // Each rule runs on its own copy of the metadata (LogFilterListener#parse), so the fallback rules start
            // from the layer the log arrived with, whatever layer an auto rule set before it aborted.
            if (analyzeUntilClaimed(metadata, input, passes, AutoListeners::getAuto)) {
                return;
            }
            if (analyzeUntilClaimed(metadata, input, passes, AutoListeners::getFallback)) {
                return;
            }
            log.debug("No LAL rule claimed the log without a layer of service {}, dropping it", metadata.getService());
            return;
        }
        final Layer layer = Layer.nameOf(metadata.getLayer());
        if (layer == Layer.UNDEFINED) {
            log.warn("The Layer {} is not recognized, abandon the log.", metadata.getLayer());
            return;
        }
        factoryManager.getLogAnalysisListenerFactories()
                      .stream()
                      .map(factory -> factory.create(layer))
                      .filter(Objects::nonNull)
                      .forEach(listeners::add);
        notifyAnalysisListener(metadata, input);
        notifyAnalysisListenerToBuild();
    }

    private boolean analyzeUntilClaimed(final LogMetadata metadata,
                                        final Object input,
                                        final List<AutoListeners> passes,
                                        final Function<AutoListeners, LogAnalysisListener> pass) {
        listeners.clear();
        passes.stream().map(pass).filter(Objects::nonNull).forEach(listeners::add);
        if (listeners.isEmpty()) {
            return false;
        }
        notifyAnalysisListener(metadata, input);
        notifyAnalysisListenerToBuild();
        return listeners.stream().anyMatch(LogAnalysisListener::claimed);
    }

    private void notifyAnalysisListener(final LogMetadata metadata, final Object input) {
        listeners.forEach(listener -> listener.parse(metadata, input));
    }

    private void notifyAnalysisListenerToBuild() {
        listeners.forEach(LogAnalysisListener::build);
    }
}
