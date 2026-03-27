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

package org.apache.skywalking.oap.query.traceql.converter;

import java.util.LinkedHashSet;
import org.apache.commons.codec.DecoderException;
import com.google.protobuf.ByteString;
import io.grafana.tempo.tempopb.Trace;
import io.grafana.tempo.tempopb.TraceByIDResponse;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.SERVICE_NAME;
import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.SPAN_KIND;

/**
 * Converter for transforming Zipkin trace data to OpenTelemetry Protocol (OTLP) format.
 */
public class ZipkinOTLPConverter {

    /**
     * Convert Zipkin spans to OTLP Protobuf format.
     * This is the primary conversion that happens first.
     *
     * @param zipkinTrace List of Zipkin spans
     * @return TraceByIDResponse in Protobuf format
     */
    public static TraceByIDResponse convertToProtobuf(List<zipkin2.Span> zipkinTrace) throws DecoderException {
        if (zipkinTrace == null || zipkinTrace.isEmpty()) {
            return TraceByIDResponse.newBuilder().build();
        }

        // Convert to Protobuf format - build Trace with all ResourceSpans
        Trace.Builder traceBuilder = Trace.newBuilder();

        // Group spans by service name to create ResourceSpans
        Map<String, List<zipkin2.Span>> spansByService = new HashMap<>();
        for (zipkin2.Span zipkinSpan : zipkinTrace) {
            String serviceName = zipkinSpan.localServiceName() != null
                ? zipkinSpan.localServiceName()
                : "unknown-service";
            spansByService.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(zipkinSpan);
        }

        // Create ResourceSpans for each service
        for (Map.Entry<String, List<zipkin2.Span>> entry : spansByService.entrySet()) {
            String serviceName = entry.getKey();
            List<zipkin2.Span> serviceSpans = entry.getValue();

            ResourceSpans.Builder rsBuilder = ResourceSpans.newBuilder();

            // Build Resource with service.name attribute
            Resource.Builder resourceBuilder = Resource.newBuilder();
            resourceBuilder.addAttributes(KeyValue.newBuilder()
                                                  .setKey("service.name")
                                                  .setValue(AnyValue.newBuilder()
                                                                    .setStringValue(serviceName)
                                                                    .build())
                                                  .build()
            );
            rsBuilder.setResource(resourceBuilder.build());

            // Create ScopeSpans
            ScopeSpans.Builder ssBuilder = ScopeSpans.newBuilder();
            ssBuilder.setScope(InstrumentationScope.newBuilder()
                                                   .setName("zipkin-tracer")
                                                   .setVersion("0.1.0")
                                                   .build()
            );

            // Convert each Zipkin span to OTLP Span
            for (zipkin2.Span zipkinSpan : serviceSpans) {
                Span.Builder spanBuilder = Span.newBuilder()
                                               .setTraceId(ByteString.copyFrom(OTLPConverter.hexToBytes(zipkinSpan.traceId())))
                                               .setSpanId(ByteString.copyFrom(OTLPConverter.hexToBytes(zipkinSpan.id())))
                                               .setName(zipkinSpan.name() != null ? zipkinSpan.name() : "")
                                               .setKind(convertZipkinKindToOtlp(zipkinSpan.kind()))
                                               .setStartTimeUnixNano(zipkinSpan.timestampAsLong() * 1000)
                                               .setEndTimeUnixNano(
                                                   (zipkinSpan.timestampAsLong() + (zipkinSpan.durationAsLong() != 0
                                                       ? zipkinSpan.durationAsLong()
                                                       : 0)) * 1000);

                // Set parent span ID if present
                if (zipkinSpan.parentId() != null) {
                    spanBuilder.setParentSpanId(ByteString.copyFrom(OTLPConverter.hexToBytes(zipkinSpan.parentId())));
                }

                // Set status based on tags (reference: OpenTelemetryTraceHandler.populateStatus)
                Status.StatusCode statusCode = Status.StatusCode.STATUS_CODE_UNSET;
                String statusMessage = "";
                if (zipkinSpan.tags() != null) {
                    // Check for error tag first (converts to ERROR status)
                    String errorTag = zipkinSpan.tags().get("error");
                    if ("true".equalsIgnoreCase(errorTag)) {
                        statusCode = Status.StatusCode.STATUS_CODE_ERROR;
                    }

                    // Check for OTLP status code (takes precedence if present)
                    String otelStatusCode = zipkinSpan.tags().get("otel.status_code");
                    if (otelStatusCode != null) {
                        try {
                            statusCode = Status.StatusCode.valueOf(otelStatusCode);
                        } catch (IllegalArgumentException e) {
                            // Keep the status code from error tag if parsing fails
                        }
                    }

                    // Get status description from OTLP tag
                    String otelStatusDescription = zipkinSpan.tags().get("otel.status_description");
                    if (otelStatusDescription != null) {
                        statusMessage = otelStatusDescription;
                    }
                }
                spanBuilder.setStatus(Status.newBuilder()
                                            .setCode(statusCode)
                                            .setMessage(statusMessage)
                                            .build());

                // Convert tags to attributes
                if (zipkinSpan.tags() != null) {
                    for (Map.Entry<String, String> tag : zipkinSpan.tags().entrySet()) {
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey(tag.getKey())
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(tag.getValue())
                                                                            .build())
                                                          .build()
                        );
                    }
                }

                // Add local endpoint info as attributes
                if (zipkinSpan.localEndpoint() != null) {
                    zipkin2.Endpoint localEndpoint = zipkinSpan.localEndpoint();
                    if (StringUtil.isNotBlank(localEndpoint.ipv4())) {
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey("net.host.ip")
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(localEndpoint.ipv4())
                                                                            .build())
                                                          .build()
                        );
                    }
                    if (StringUtil.isNotBlank(localEndpoint.ipv6())) {
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey("net.host.ip")
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(localEndpoint.ipv6())
                                                                            .build())
                                                          .build()
                        );
                    }
                    if (localEndpoint.portAsInt() != 0) {
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey("net.host.port")
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(String.valueOf(
                                                                                localEndpoint.portAsInt()))
                                                                            .build())
                                                          .build()
                        );
                    }
                }

                // Add remote endpoint info as attributes
                // Reference: OpenTelemetryTraceHandler.convertEndpointFromTags
                if (zipkinSpan.remoteEndpoint() != null) {
                    zipkin2.Endpoint remoteEndpoint = zipkinSpan.remoteEndpoint();
                    // Service name mapping: prefer peer.service, fallback to net.peer.name
                    if (StringUtil.isNotBlank(remoteEndpoint.serviceName())) {
                        String remoteServiceName = remoteEndpoint.serviceName();
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey("net.peer.name")
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(remoteServiceName)
                                                                            .build())
                                                          .build()
                        );
                        // If it's not an IP, store as peer.service
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey("peer.service")
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(remoteServiceName)
                                                                            .build())
                                                          .build()
                        );
                    }

                    // IP address mapping
                    if (remoteEndpoint.ipv4() != null) {
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey("net.peer.ip")
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(remoteEndpoint.ipv4())
                                                                            .build())
                                                          .build()
                        );
                    }
                    if (remoteEndpoint.ipv6() != null) {
                        // Store IPv6 as net.peer.ip (OTLP uses same attribute for both)
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey("net.peer.ip")
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(remoteEndpoint.ipv6())
                                                                            .build())
                                                          .build()
                        );
                    }

                    // Port mapping
                    if (remoteEndpoint.portAsInt() != 0) {
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey("net.peer.port")
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(String.valueOf(
                                                                                remoteEndpoint.portAsInt()))
                                                                            .build())
                                                          .build()
                        );
                    }
                }

                // Convert annotations to events
                if (zipkinSpan.annotations() != null) {
                    for (zipkin2.Annotation annotation : zipkinSpan.annotations()) {
                        Span.Event.Builder eventBuilder = Span.Event.newBuilder()
                                                                    .setTimeUnixNano(annotation.timestamp() * 1000)
                                                                    .setName(annotation.value());
                        spanBuilder.addEvents(eventBuilder.build());
                    }
                }

                ssBuilder.addSpans(spanBuilder.build());
            }

            rsBuilder.addScopeSpans(ssBuilder.build());
            traceBuilder.addResourceSpans(rsBuilder.build());
        }

        return TraceByIDResponse.newBuilder()
                                .setTrace(traceBuilder.build())
                                .build();
    }

    /**
     * Convert Zipkin traces to SearchResponse.
     * Each trace in the list becomes a Trace in the SearchResponse.
     *
     * @param traces      List of Zipkin trace (each trace is a list of spans)
     * @param allowedTags Only span attributes whose key is in this set are included in the result
     * @return SearchResponse containing the converted traces
     */
    public static SearchResponse convertToSearchResponse(List<List<zipkin2.Span>> traces, Set<String> allowedTags) {
        SearchResponse response = new SearchResponse();

        if (traces == null || traces.isEmpty()) {
            return response;
        }

        for (List<zipkin2.Span> zipkinTrace : traces) {
            if (zipkinTrace == null || zipkinTrace.isEmpty()) {
                continue;
            }

            SearchResponse.Trace trace = convertZipkinTraceToSearchTrace(zipkinTrace, allowedTags);
            response.getTraces().add(trace);
        }

        return response;
    }

    /**
     * Convert a single Zipkin trace (list of spans) to SearchResponse.Trace.
     *
     * @param zipkinTrace List of Zipkin spans representing one trace
     * @param allowedTags Only span attributes whose key is in this set are included; null means all
     * @return SearchResponse.Trace
     */
    private static SearchResponse.Trace convertZipkinTraceToSearchTrace(List<zipkin2.Span> zipkinTrace,
                                                                         Set<String> allowedTags) {
        SearchResponse.Trace trace = new SearchResponse.Trace();

        if (zipkinTrace.isEmpty()) {
            return trace;
        }

        zipkin2.Span firstSpan = zipkinTrace.get(0);
        trace.setTraceID(firstSpan.traceId());

        zipkin2.Span rootSpan = zipkinTrace.stream()
                                           .filter(s -> s.parentId() == null)
                                           .findFirst()
                                           .orElse(firstSpan);

        trace.setRootServiceName(rootSpan.localServiceName() != null
                                     ? rootSpan.localServiceName()
                                     : "unknown-service");
        trace.setRootTraceName(rootSpan.name() != null ? rootSpan.name() : "unknown");

        long minStartTime = zipkinTrace.stream()
                                       .filter(s -> s.timestampAsLong() != 0)
                                       .mapToLong(zipkin2.Span::timestampAsLong)
                                       .min()
                                       .orElse(0L);

        long maxEndTime = zipkinTrace.stream()
                                     .filter(s -> s.timestampAsLong() != 0 && s.durationAsLong() != 0)
                                     .mapToLong(s -> s.timestampAsLong() + s.durationAsLong())
                                     .max()
                                     .orElse(minStartTime);

        trace.setStartTimeUnixNano(String.valueOf(TimeUnit.MICROSECONDS.toNanos(minStartTime)));
        trace.setDurationMs((int) TimeUnit.MICROSECONDS.toMillis(maxEndTime - minStartTime));

        // First pass: collect all attribute keys across all spans.
        // Grafana has a bug (search.go:369) where spans missing an attribute key that other
        // spans have cause a type panic: Append("") on a []*string field instead of Append(nil).
        // By ensuring all spans have the same keys (padding missing ones with ""), we avoid
        // the else branch in Grafana entirely.
        Set<String> allSpanAttrKeys = new LinkedHashSet<>();
        for (zipkin2.Span zipkinSpan : zipkinTrace) {
            if (zipkinSpan.tags() != null) {
                allSpanAttrKeys.addAll(zipkinSpan.tags().keySet());
            }
        }
        // Restrict to allowed tags when configured
        if (allowedTags != null) {
            allSpanAttrKeys.retainAll(allowedTags);
        }
        // Add fixed tags last so they always appear
        allSpanAttrKeys.add(SERVICE_NAME);
        allSpanAttrKeys.add(SPAN_KIND);

        SearchResponse.SpanSet spanSet = new SearchResponse.SpanSet();
        for (zipkin2.Span zipkinSpan : zipkinTrace) {
            spanSet.getSpans().add(convertZipkinSpanToSearchSpan(zipkinSpan, allSpanAttrKeys));
        }
        spanSet.setMatched(spanSet.getSpans().size());
        trace.getSpanSets().add(spanSet);

        return trace;
    }

    /**
     * Convert a single Zipkin span to SearchResponse.Span.
     * All keys in {@code allSpanAttrKeys} are written (missing ones padded with "") so that
     * every span in the same SpanSet has identical attribute keys — required to avoid a
     * Grafana search.go:369 type panic on []*string DataFrame fields.
     *
     * @param zipkinSpan      Zipkin span
     * @param allSpanAttrKeys Ordered, already-filtered set of attribute keys to output
     * @return SearchResponse.Span
     */
    private static SearchResponse.Span convertZipkinSpanToSearchSpan(zipkin2.Span zipkinSpan,
                                                                       Set<String> allSpanAttrKeys) {
        SearchResponse.Span span = new SearchResponse.Span();
        span.setSpanID(zipkinSpan.id());

        if (zipkinSpan.timestampAsLong() != 0) {
            span.setStartTimeUnixNano(String.valueOf(
                TimeUnit.MICROSECONDS.toNanos(zipkinSpan.timestampAsLong())));
        }
        if (zipkinSpan.durationAsLong() != 0) {
            span.setDurationNanos(String.valueOf(
                TimeUnit.MICROSECONDS.toNanos(zipkinSpan.durationAsLong())));
        }

        // Build attribute map for this span
        Map<String, String> spanAttrMap = new HashMap<>();
        if (zipkinSpan.localServiceName() != null) {
            spanAttrMap.put(SERVICE_NAME, zipkinSpan.localServiceName());
        }
        if (zipkinSpan.kind() != null) {
            spanAttrMap.put(SPAN_KIND, convertZipkinKindToOtlp(zipkinSpan.kind()).name());
        }
        if (zipkinSpan.tags() != null) {
            spanAttrMap.putAll(zipkinSpan.tags());
        }

        // Output all keys in consistent order, padding missing keys with "" to avoid
        // the Grafana search.go:369 type panic on []*string fields
        List<SearchResponse.Attribute> attributes = new ArrayList<>();
        for (String key : allSpanAttrKeys) {
            SearchResponse.Attribute attr = new SearchResponse.Attribute();
            attr.setKey(key);
            SearchResponse.Value value = new SearchResponse.Value();
            value.setStringValue(spanAttrMap.getOrDefault(key, ""));
            attr.setValue(value);
            attributes.add(attr);
        }

        span.setAttributes(attributes);
        return span;
    }

    /**
     * Convert Zipkin span kind to OTLP span kind.
     */
    private static Span.SpanKind convertZipkinKindToOtlp(zipkin2.Span.Kind kind) {
        if (kind == null) {
            return Span.SpanKind.SPAN_KIND_UNSPECIFIED;
        }
        switch (kind) {
            case CLIENT:
                return Span.SpanKind.SPAN_KIND_CLIENT;
            case SERVER:
                return Span.SpanKind.SPAN_KIND_SERVER;
            case PRODUCER:
                return Span.SpanKind.SPAN_KIND_PRODUCER;
            case CONSUMER:
                return Span.SpanKind.SPAN_KIND_CONSUMER;
            default:
                return Span.SpanKind.SPAN_KIND_INTERNAL;
        }
    }
}
