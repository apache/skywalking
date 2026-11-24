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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.trace.SpanListenerManager;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.core.trace.TraceSpansDropped;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;

/**
 * {@code otlpTraceStorage: otlp}, the default: each span is stored as received, through the phase-two span
 * listeners and {@link OTLPSpanForward}. The resource attributes are left as sent, but for the stored
 * {@code service.layer}.
 */
@Slf4j
final class OTLPNativeTraceStorage implements OTLPTraceStorage {
    /**
     * Built once, so its rate limiter is shared by every export request.
     */
    private final OTLPSpanForward otlpSpanForward;
    private final Supplier<SpanListenerManager> spanListenerManager;

    OTLPNativeTraceStorage(final OtelMetricReceiverConfig config,
                           final ModuleManager manager,
                           final Supplier<SpanListenerManager> spanListenerManager) {
        this.otlpSpanForward = new OTLPSpanForward(config, manager);
        this.spanListenerManager = spanListenerManager;
    }

    @Override
    public String traceStorage() {
        return OtelMetricReceiverConfig.OTLP_TRACE_STORAGE_OTLP;
    }

    /**
     * The OTLP specification asks a receiver to report the data it rejects.
     */
    @Override
    public boolean reportsRejections() {
        return true;
    }

    @Override
    public Export newExport(final TraceSpansDropped droppedSpans) {
        return new NativeExport(droppedSpans);
    }

    private final class NativeExport implements Export {
        private final TraceSpansDropped droppedSpans;
        private Resource storedResource;
        private String resourceSchemaUrl;
        private ScopeSpans scopeSpans;

        private NativeExport(final TraceSpansDropped droppedSpans) {
            this.droppedSpans = droppedSpans;
        }

        /**
         * The analysis reads {@code service.layer} as sent; the stored span carries the layer the service catalog
         * files the service under, {@code UNDEFINED} for an unregistered or absent one, so a search on it agrees
         * with the catalog.
         */
        @Override
        public Map<String, String> openResource(final ResourceSpans resourceSpans,
                                                final Map<String, String> resourceTags) {
            storedResource = OTLPSpanForward.withCatalogLayer(
                resourceSpans.getResource(), OTLPSpanForward.catalogLayerOf(resourceTags));
            resourceSchemaUrl = resourceSpans.getSchemaUrl();
            return resourceTags;
        }

        @Override
        public void openScope(final ScopeSpans scopeSpans, final Map<String, String> storedTags) {
            this.scopeSpans = scopeSpans;
        }

        /**
         * Run the native-only phase-two listeners and store the span. A failure here is counted as
         * {@code store_failed}, apart from the handler's {@code process_failed}.
         */
        @Override
        public void store(final Span span, final String serviceName, final Map<String, String> storedTags,
                          final Map<String, String> injectedTags, final boolean enforced) {
            try {
                final InstrumentationScope scope = scopeSpans.getScope();
                // Phase 2 for natively stored spans: no Zipkin span will ever exist for them.
                final SpanListenerResult storedResult = spanListenerManager.get().notifyNativeOTLPPhase(
                    new OTLPSpanReaderImpl(span), serviceName, storedTags, scope.getName(), scope.getVersion());
                if (!storedResult.isShouldPersist()) {
                    return;
                }
                final Map<String, String> tags = new HashMap<>(injectedTags);
                tags.putAll(storedResult.getAdditionalTags());
                final OTLPSpanForward.Outcome outcome = otlpSpanForward.send(
                    storedResource, resourceSchemaUrl, scope, scopeSpans.getSchemaUrl(),
                    span, serviceName, storedTags, tags, enforced);
                if (outcome == OTLPSpanForward.Outcome.RATE_LIMITED) {
                    droppedSpans.count(TraceSpansDropped.Reason.RATE_LIMITED, 1);
                } else if (outcome == OTLPSpanForward.Outcome.SAMPLED_OUT) {
                    droppedSpans.count(TraceSpansDropped.Reason.SAMPLED, 1);
                }
            } catch (Exception e) {
                log.warn("Storing OTLP span {} failed, dropping it: {}",
                         OTLPValues.hexId(span.getSpanId()), e.getMessage());
                droppedSpans.count(TraceSpansDropped.Reason.STORE_FAILED, 1);
            }
        }

        @Override
        public void finish() {
        }
    }
}
