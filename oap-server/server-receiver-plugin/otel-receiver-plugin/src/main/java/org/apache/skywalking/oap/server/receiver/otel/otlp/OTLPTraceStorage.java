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

import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.Map;
import org.apache.skywalking.oap.server.core.trace.TraceSpansDropped;

/**
 * Where {@link OpenTelemetryTraceHandler} stores OTLP spans, chosen once from {@code otlpTraceStorage}:
 * {@link OTLPNativeTraceStorage} or {@link OTLPZipkinTraceStorage}. The handler resolves the service, runs the
 * phase-one span listeners and the TAL rules for every span whatever the storage; an implementation holds only
 * what differs between the two.
 */
interface OTLPTraceStorage {
    /**
     * @return how the spans are stored, {@code otlp} or {@code zipkin}, which a TAL rule reads as
     * {@code meta.traceStorage}.
     */
    String traceStorage();

    /**
     * @return whether the spans of a resource without a service name are reported as rejected through the export
     * response's {@code partial_success}.
     */
    boolean reportsRejections();

    /**
     * @param droppedSpans the {@code spans_dropped_count} counters of the protocol the request came in on
     * @return the storage of one export request.
     */
    Export newExport(TraceSpansDropped droppedSpans);

    /**
     * The spans of one export request, given resource by resource and scope by scope, on one thread.
     */
    interface Export {
        /**
         * Open a resource whose spans have a service.
         *
         * @param resourceSpans the resource and its spans as received
         * @param resourceTags  the resource attributes as strings, as sent; not modified
         * @return the resource tags the span listeners see and the spans are stored with
         */
        Map<String, String> openResource(ResourceSpans resourceSpans, Map<String, String> resourceTags);

        /**
         * Open a scope of the resource last opened.
         *
         * @param storedTags the tags {@link #openResource} returned
         */
        void openScope(ScopeSpans scopeSpans, Map<String, String> storedTags);

        /**
         * Store one span of the scope last opened. A failure the implementation does not count itself propagates,
         * and the handler counts the span as {@code process_failed}.
         *
         * @param serviceName  the service, after the service grouping
         * @param storedTags   the tags {@link #openResource} returned
         * @param injectedTags the tags the phase-one listeners and the rules injected
         * @param enforced     whether a rule's {@code enforcer {}} kept the span past head sampling
         */
        void store(Span span, String serviceName, Map<String, String> storedTags,
                   Map<String, String> injectedTags, boolean enforced);

        /**
         * Finish the request, after its last span.
         */
        void finish();
    }
}
