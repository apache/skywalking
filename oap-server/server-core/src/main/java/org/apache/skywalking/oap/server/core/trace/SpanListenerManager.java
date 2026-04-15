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

package org.apache.skywalking.oap.server.core.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Core service that manages {@link SpanListener} instances discovered via SPI.
 * Provides two-phase notification for OTLP and Zipkin span processing.
 *
 * <p>The manager only aggregates persistence/tag/layer decisions from listeners.
 * Listeners emit sources (OAL, MAL, logs) directly via their own cached module references.
 *
 * <p>Callers:
 * <ul>
 *   <li>{@code OpenTelemetryTraceHandler} calls {@link #notifyOTLPPhase} before Zipkin conversion.</li>
 *   <li>{@code SpanForward} calls {@link #notifyZipkinPhase} after Zipkin conversion.</li>
 * </ul>
 */
@Slf4j
public class SpanListenerManager implements Service {
    private List<SpanListener> listeners = Collections.emptyList();

    /**
     * Load and initialize all {@link SpanListener} implementations via SPI.
     * Listeners whose {@link SpanListener#requiredModules()} are not all loaded
     * are skipped with an info log.
     */
    public void init(final ModuleManager moduleManager) {
        final List<SpanListener> loaded = new ArrayList<>();
        for (final SpanListener listener : ServiceLoader.load(SpanListener.class)) {
            final String[] required = listener.requiredModules();
            boolean satisfied = true;
            for (final String moduleName : required) {
                if (!moduleManager.has(moduleName)) {
                    log.info("SpanListener {} skipped: required module {} is not loaded",
                        listener.getClass().getName(), moduleName);
                    satisfied = false;
                    break;
                }
            }
            if (!satisfied) {
                continue;
            }
            listener.init(moduleManager);
            loaded.add(listener);
            log.info("SpanListener registered: {}", listener.getClass().getName());
        }
        this.listeners = loaded;
    }

    /**
     * Phase 1: notify all listeners with a raw OTLP span before Zipkin conversion.
     *
     * @return merged result. If any listener vetoes persistence, {@code shouldPersist} is false.
     */
    public SpanListenerResult notifyOTLPPhase(final OTLPSpanReader span,
                                              final Map<String, String> resourceAttributes,
                                              final String scopeName,
                                              final String scopeVersion) {
        if (listeners.isEmpty()) {
            return SpanListenerResult.CONTINUE;
        }
        boolean shouldPersist = true;
        Map<String, String> mergedTags = null;

        for (final SpanListener listener : listeners) {
            final SpanListenerResult result = listener.onOTLPSpan(
                span, resourceAttributes, scopeName, scopeVersion);
            if (result == SpanListenerResult.CONTINUE) {
                continue;
            }
            if (!result.isShouldPersist()) {
                shouldPersist = false;
            }
            // Merge additional tags — last-writer-wins if multiple listeners
            // set the same key. Each listener should use distinct tag keys;
            // collisions indicate a design issue in the listener implementations.
            if (!result.getAdditionalTags().isEmpty()) {
                if (mergedTags == null) {
                    mergedTags = new HashMap<>();
                }
                mergedTags.putAll(result.getAdditionalTags());
            }
        }

        return SpanListenerResult.builder()
                                .shouldPersist(shouldPersist)
                                .additionalTags(mergedTags != null ? mergedTags : Collections.emptyMap())
                                .build();
    }

    /**
     * Phase 2: notify all listeners with a Zipkin span after conversion.
     * All trace sources (OTLP, Zipkin HTTP, Kafka) reach this phase.
     *
     * @return merged result.
     */
    public SpanListenerResult notifyZipkinPhase(final ZipkinSpan span) {
        if (listeners.isEmpty()) {
            return SpanListenerResult.CONTINUE;
        }
        Map<String, String> mergedTags = null;

        for (final SpanListener listener : listeners) {
            final SpanListenerResult result = listener.onZipkinSpan(span);
            if (result == SpanListenerResult.CONTINUE) {
                continue;
            }
            // Merge additional tags — last-writer-wins for same key (see phase 1 comment)
            if (!result.getAdditionalTags().isEmpty()) {
                if (mergedTags == null) {
                    mergedTags = new HashMap<>();
                }
                mergedTags.putAll(result.getAdditionalTags());
            }
        }

        return SpanListenerResult.builder()
                                .additionalTags(mergedTags != null ? mergedTags : Collections.emptyMap())
                                .build();
    }
}
