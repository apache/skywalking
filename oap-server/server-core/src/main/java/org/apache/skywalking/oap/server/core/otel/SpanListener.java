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

package org.apache.skywalking.oap.server.core.otel;

import java.util.Map;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Two-phase listener for trace span processing.
 *
 * <p><b>Phase 1 ({@link #onOTLPSpan})</b>: called by the OTLP trace handler before Zipkin
 * conversion. Only OTLP spans reach this phase. Listeners can veto persistence (skip Zipkin
 * conversion entirely), inject tags, set layer override, or emit metric sources.
 *
 * <p><b>Phase 2 ({@link #onZipkinSpan})</b>: called by {@code SpanForward} after Zipkin
 * conversion. All trace sources (OTLP, Zipkin HTTP, Kafka) reach this phase.
 *
 * <p>Both methods are {@code default} no-ops. Implementations override one or both.
 * Registered via {@link java.util.ServiceLoader}.
 *
 * <p>Listeners receive the {@link ModuleManager} at {@link #init} time and can use it to
 * emit data to any pipeline: OAL via {@code SourceReceiver}, MAL via the meter pipeline,
 * or logs via {@code ILogAnalyzerService}. The {@link SpanListenerResult#getSources()} list
 * is a convenience for OAL sources that the caller dispatches; for MAL/logs, listeners
 * should emit directly via their cached module references.
 */
public interface SpanListener {
    /**
     * Initialize the listener with the module manager.
     * Called once at startup after SPI discovery.
     */
    void init(ModuleManager moduleManager);

    /**
     * Phase 1: process a raw OTLP span before Zipkin conversion.
     * Only OTLP trace sources reach this phase.
     *
     * @param span             abstracted OTLP span (no proto dependency)
     * @param resourceAttributes OTLP resource attributes as a flat map
     * @param scopeName        InstrumentationScope name (e.g., "NSURLSession", "MetricKit")
     * @param scopeVersion     InstrumentationScope version
     * @return result controlling persistence, tag injection, layer override, and metric emission
     */
    default SpanListenerResult onOTLPSpan(final OTLPSpanReader span,
                                          final Map<String, String> resourceAttributes,
                                          final String scopeName,
                                          final String scopeVersion) {
        return SpanListenerResult.CONTINUE;
    }

    /**
     * Phase 2: process a Zipkin span after conversion.
     * All trace sources (OTLP, Zipkin HTTP, Kafka) reach this phase.
     *
     * @param span the ZipkinSpan with all tags populated
     * @return result controlling tag injection and metric emission
     */
    default SpanListenerResult onZipkinSpan(final ZipkinSpan span) {
        return SpanListenerResult.CONTINUE;
    }
}
