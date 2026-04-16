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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogAnalysisListener;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Entry point for log analysis. Created per-request by the log receiver.
 *
 * <p>Runtime execution ({@link #doAnalysis}):
 * <ol>
 *   <li>Validates the incoming log (service name must be non-empty, layer must be valid).</li>
 *   <li>Calls {@code createAnalysisListeners(layer)} — asks all registered
 *       {@link org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogAnalysisListenerFactory}
 *       instances to create listeners for the log's layer. For LAL, this is
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
        if (metadata.getLayer() == null || metadata.getLayer().isEmpty()) {
            // Empty layer: try auto-layer rules first.
            createAnalysisListeners(null);
            if (!listeners.isEmpty()) {
                if (metadata.getTimestamp() == 0) {
                    metadata.setTimestamp(System.currentTimeMillis());
                }
                notifyAnalysisListener(metadata, input);
                notifyAnalysisListenerToBuild();

                // If any auto rule claimed the log (didn't abort), we're done.
                if (listeners.stream().anyMatch(LogAnalysisListener::claimed)) {
                    return;
                }
                // All auto rules aborted — fall back to GENERAL
                listeners.clear();
            }
            // No auto rules configured, or all aborted — fall back to GENERAL
            createAnalysisListeners(Layer.GENERAL);
        } else {
            final Layer layer = Layer.nameOf(metadata.getLayer());
            if (layer == Layer.UNDEFINED) {
                log.warn("The Layer {} is not recognized, abandon the log.", metadata.getLayer());
                return;
            }
            createAnalysisListeners(layer);
        }
        if (metadata.getTimestamp() == 0) {
            metadata.setTimestamp(System.currentTimeMillis());
        }

        notifyAnalysisListener(metadata, input);
        notifyAnalysisListenerToBuild();
    }

    private void notifyAnalysisListener(final LogMetadata metadata, final Object input) {
        listeners.forEach(listener -> listener.parse(metadata, input));
    }

    private void notifyAnalysisListenerToBuild() {
        listeners.forEach(LogAnalysisListener::build);
    }

    private void createAnalysisListeners(Layer layer) {
        factoryManager.getLogAnalysisListenerFactories()
                      .stream()
                      .map(factory -> factory.create(layer))
                      .filter(Objects::nonNull)
                      .forEach(listeners::add);
    }
}
