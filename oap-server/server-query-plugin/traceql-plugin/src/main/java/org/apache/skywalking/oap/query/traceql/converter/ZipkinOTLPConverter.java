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

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
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
import org.apache.skywalking.oap.query.traceql.entity.OtlpTraceResponse;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Converter for transforming Zipkin trace data to OpenTelemetry Protocol (OTLP) format.
 * Handles conversion of Zipkin spans to both Protobuf and JSON representations.
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
                                               .setTraceId(ByteString.copyFrom(hexToBytes(zipkinSpan.traceId())))
                                               .setSpanId(ByteString.copyFrom(hexToBytes(zipkinSpan.id())))
                                               .setName(zipkinSpan.name() != null ? zipkinSpan.name() : "")
                                               .setKind(convertZipkinKindToOtlp(zipkinSpan.kind()))
                                               .setStartTimeUnixNano(zipkinSpan.timestampAsLong() * 1000)
                                               .setEndTimeUnixNano(
                                                   (zipkinSpan.timestampAsLong() + (zipkinSpan.durationAsLong() != 0
                                                       ? zipkinSpan.durationAsLong()
                                                       : 0)) * 1000);

                // Set parent span ID if present
                if (zipkinSpan.parentId() != null) {
                    spanBuilder.setParentSpanId(ByteString.copyFrom(hexToBytes(zipkinSpan.parentId())));
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
     * Convert Protobuf TraceByIDResponse to OtlpTraceResponse JSON model.
     *
     * @param protoResponse TraceByIDResponse in Protobuf format
     * @return OtlpTraceResponse JSON model
     */
    public static OtlpTraceResponse convertProtobufToJson(TraceByIDResponse protoResponse) {
        OtlpTraceResponse response = new OtlpTraceResponse();
        OtlpTraceResponse.TraceData traceData = new OtlpTraceResponse.TraceData();

        Trace trace = protoResponse.getTrace();

        // Convert each ResourceSpans
        for (ResourceSpans rs : trace.getResourceSpansList()) {
            OtlpTraceResponse.ResourceSpans jsonResourceSpans = new OtlpTraceResponse.ResourceSpans();

            // Convert Resource
            OtlpTraceResponse.Resource jsonResource = new OtlpTraceResponse.Resource();
            for (KeyValue attr : rs.getResource().getAttributesList()) {
                OtlpTraceResponse.KeyValue jsonKv = new OtlpTraceResponse.KeyValue();
                jsonKv.setKey(attr.getKey());
                OtlpTraceResponse.AnyValue jsonValue = new OtlpTraceResponse.AnyValue();
                jsonValue.setStringValue(attr.getValue().getStringValue());
                jsonKv.setValue(jsonValue);
                jsonResource.getAttributes().add(jsonKv);
            }
            jsonResourceSpans.setResource(jsonResource);

            // Convert ScopeSpans
            for (ScopeSpans ss : rs.getScopeSpansList()) {
                OtlpTraceResponse.ScopeSpans jsonScopeSpans = new OtlpTraceResponse.ScopeSpans();

                // Convert Scope
                OtlpTraceResponse.Scope jsonScope = new OtlpTraceResponse.Scope();
                jsonScope.setName(ss.getScope().getName());
                jsonScope.setVersion(ss.getScope().getVersion());
                jsonScopeSpans.setScope(jsonScope);

                // Convert Spans
                for (Span span : ss.getSpansList()) {
                    OtlpTraceResponse.Span jsonSpan = new OtlpTraceResponse.Span();
                    jsonSpan.setTraceId(bytesToHex(span.getTraceId().toByteArray()));
                    jsonSpan.setSpanId(bytesToHex(span.getSpanId().toByteArray()));
                    if (!span.getParentSpanId().isEmpty()) {
                        jsonSpan.setParentSpanId(bytesToHex(span.getParentSpanId().toByteArray()));
                    }
                    jsonSpan.setName(span.getName());
                    jsonSpan.setKind(span.getKind().name());
                    jsonSpan.setStartTimeUnixNano(String.valueOf(span.getStartTimeUnixNano()));
                    jsonSpan.setEndTimeUnixNano(String.valueOf(span.getEndTimeUnixNano()));

                    // Convert Status
                    if (span.hasStatus()) {
                        OtlpTraceResponse.Status jsonStatus = new OtlpTraceResponse.Status();
                        jsonStatus.setCode(span.getStatus().getCode().name());
                        if (!span.getStatus().getMessage().isEmpty()) {
                            jsonStatus.setMessage(span.getStatus().getMessage());
                        }
                        jsonSpan.setStatus(jsonStatus);
                    }

                    // Convert Attributes
                    for (KeyValue attr : span.getAttributesList()) {
                        OtlpTraceResponse.KeyValue jsonKv = new OtlpTraceResponse.KeyValue();
                        jsonKv.setKey(attr.getKey());
                        OtlpTraceResponse.AnyValue jsonValue = new OtlpTraceResponse.AnyValue();
                        jsonValue.setStringValue(attr.getValue().getStringValue());
                        jsonKv.setValue(jsonValue);
                        jsonSpan.getAttributes().add(jsonKv);
                    }

                    // Convert Events
                    for (Span.Event event : span.getEventsList()) {
                        OtlpTraceResponse.Event jsonEvent = new OtlpTraceResponse.Event();
                        jsonEvent.setTimeUnixNano(String.valueOf(event.getTimeUnixNano()));
                        jsonEvent.setName(event.getName());

                        // Convert Event Attributes
                        for (KeyValue attr : event.getAttributesList()) {
                            OtlpTraceResponse.KeyValue jsonKv = new OtlpTraceResponse.KeyValue();
                            jsonKv.setKey(attr.getKey());
                            OtlpTraceResponse.AnyValue jsonValue = new OtlpTraceResponse.AnyValue();
                            jsonValue.setStringValue(attr.getValue().getStringValue());
                            jsonKv.setValue(jsonValue);
                            jsonEvent.getAttributes().add(jsonKv);
                        }

                        jsonSpan.getEvents().add(jsonEvent);
                    }

                    jsonScopeSpans.getSpans().add(jsonSpan);
                }

                jsonResourceSpans.getScopeSpans().add(jsonScopeSpans);
            }

            traceData.getResourceSpans().add(jsonResourceSpans);
        }

        response.setTrace(traceData);
        return response;
    }

    /**
     * Convert Zipkin traces to SearchResponse.
     * Each trace in the list becomes a Trace in the SearchResponse.
     *
     * @param traces List of Zipkin trace (each trace is a list of spans)
     * @return SearchResponse containing the converted traces
     */
    public static SearchResponse convertToSearchResponse(List<List<zipkin2.Span>> traces) {
        SearchResponse response = new SearchResponse();

        if (traces == null || traces.isEmpty()) {
            return response;
        }

        for (List<zipkin2.Span> zipkinTrace : traces) {
            if (zipkinTrace == null || zipkinTrace.isEmpty()) {
                continue;
            }

            SearchResponse.Trace trace = convertZipkinTraceToSearchTrace(zipkinTrace);
            response.getTraces().add(trace);
        }

        return response;
    }

    /**
     * Convert a single Zipkin trace (list of spans) to SearchResponse.Trace.
     *
     * @param zipkinTrace List of Zipkin spans representing one trace
     * @return SearchResponse.Trace
     */
    private static SearchResponse.Trace convertZipkinTraceToSearchTrace(List<zipkin2.Span> zipkinTrace) {
        SearchResponse.Trace trace = new SearchResponse.Trace();

        if (zipkinTrace.isEmpty()) {
            return trace;
        }

        // Get the first span to extract trace-level information
        zipkin2.Span firstSpan = zipkinTrace.get(0);
        trace.setTraceID(firstSpan.traceId());

        // Find the root span (span without parent)
        zipkin2.Span rootSpan = zipkinTrace.stream()
                                           .filter(s -> s.parentId() == null)
                                           .findFirst()
                                           .orElse(firstSpan);

        // Set root service name and trace name
        trace.setRootServiceName(rootSpan.localServiceName() != null
                                     ? rootSpan.localServiceName()
                                     : "unknown-service");
        trace.setRootTraceName(rootSpan.name() != null
                                   ? rootSpan.name()
                                   : "unknown");

        // Calculate trace start time and duration
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

        // Convert to nanoseconds for startTimeUnixNano
        trace.setStartTimeUnixNano(String.valueOf(TimeUnit.MICROSECONDS.toNanos(minStartTime)));

        // Duration in milliseconds
        long durationMicros = maxEndTime - minStartTime;
        trace.setDurationMs((int) TimeUnit.MICROSECONDS.toMillis(durationMicros));

        // Create SpanSet with all spans
        SearchResponse.SpanSet spanSet = new SearchResponse.SpanSet();
        spanSet.setMatched(zipkinTrace.size());

        for (zipkin2.Span zipkinSpan : zipkinTrace) {
            SearchResponse.Span span = convertZipkinSpanToSearchSpan(zipkinSpan);
            spanSet.getSpans().add(span);
        }

        trace.getSpanSets().add(spanSet);

        return trace;
    }

    /**
     * Convert a single Zipkin span to SearchResponse.Span.
     *
     * @param zipkinSpan Zipkin span
     * @return SearchResponse.Span
     */
    private static SearchResponse.Span convertZipkinSpanToSearchSpan(zipkin2.Span zipkinSpan) {
        SearchResponse.Span span = new SearchResponse.Span();

        span.setSpanID(zipkinSpan.id());

        // Convert timestamp to nanoseconds
        if (zipkinSpan.timestampAsLong() != 0) {
            span.setStartTimeUnixNano(String.valueOf(
                TimeUnit.MICROSECONDS.toNanos(zipkinSpan.timestampAsLong())));
        }

        // Convert duration to nanoseconds
        if (zipkinSpan.durationAsLong() != 0) {
            span.setDurationNanos(String.valueOf(
                TimeUnit.MICROSECONDS.toNanos(zipkinSpan.durationAsLong())));
        }

        // Convert tags to attributes
        if (zipkinSpan.tags() != null && !zipkinSpan.tags().isEmpty()) {
            for (Map.Entry<String, String> tag : zipkinSpan.tags().entrySet()) {
                SearchResponse.Attribute attribute = new SearchResponse.Attribute();
                attribute.setKey(tag.getKey());

                SearchResponse.Value value = new SearchResponse.Value();
                value.setStringValue(tag.getValue());
                attribute.setValue(value);

                span.getAttributes().add(attribute);
            }
        }

        // Add service name as attribute if available
        if (zipkinSpan.localServiceName() != null) {
            SearchResponse.Attribute serviceAttr = new SearchResponse.Attribute();
            serviceAttr.setKey("service.name");

            SearchResponse.Value value = new SearchResponse.Value();
            value.setStringValue(zipkinSpan.localServiceName());
            serviceAttr.setValue(value);

            span.getAttributes().add(serviceAttr);
        }

        // Add span kind as attribute
        if (zipkinSpan.kind() != null) {
            SearchResponse.Attribute kindAttr = new SearchResponse.Attribute();
            kindAttr.setKey("span.kind");

            SearchResponse.Value value = new SearchResponse.Value();
            value.setStringValue(zipkinSpan.kind().name());
            kindAttr.setValue(value);

            span.getAttributes().add(kindAttr);
        }

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

    /**
     * Convert byte array to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        return Hex.encodeHexString(bytes);
    }

    /**
     * Convert hex string to byte array.
     */
    private static byte[] hexToBytes(String hex) throws DecoderException {
        return Hex.decodeHex(hex);
    }
}
