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

import com.google.protobuf.ByteString;
import io.grafana.tempo.tempopb.TraceByIDResponse;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.query.type.LogEntity;
import org.apache.skywalking.oap.server.core.query.type.Ref;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceList;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.SERVICE_NAME;
import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.SPAN_KIND;

/**
 * Converter for transforming SkyWalking trace data to OpenTelemetry Protocol (OTLP) format.
 * Handles conversion of SkyWalking spans to both Protobuf and JSON representations.
 * <p>
 * Note: This class uses fully qualified names for some classes to avoid naming conflicts:
 * - org.apache.skywalking.oap.server.core.query.type.Trace (SkyWalking Trace)
 * - org.apache.skywalking.oap.server.core.query.type.Span (SkyWalking Span)
 * - org.apache.skywalking.oap.server.core.query.type.KeyValue (SkyWalking KeyValue)
 * - io.grafana.tempo.tempopb.Trace (Tempo/OTLP Trace - used via fully qualified name)
 * - io.opentelemetry.proto.trace.v1.Span (OTLP Span - used via fully qualified name)
 * - io.opentelemetry.proto.common.v1.KeyValue (OTLP KeyValue - imported as KeyValue)
 */
public class SkyWalkingOTLPConverter {

    /**
     * Convert SkyWalking trace to OTLP Protobuf format.
     *
     * @param traceId Trace ID of the trace to convert
     * @param swTrace SkyWalking Trace object
     * @return TraceByIDResponse in Protobuf format
     */
    public static TraceByIDResponse convertToProtobuf(String traceId, org.apache.skywalking.oap.server.core.query.type.Trace swTrace) throws DecoderException {
        if (swTrace == null || swTrace.getSpans().isEmpty()) {
            return TraceByIDResponse.newBuilder().build();
        }

        io.grafana.tempo.tempopb.Trace.Builder traceBuilder = io.grafana.tempo.tempopb.Trace.newBuilder();

        // Group spans by service
        Map<String, List<org.apache.skywalking.oap.server.core.query.type.Span>> spansByService = new HashMap<>();
        for (org.apache.skywalking.oap.server.core.query.type.Span swSpan : swTrace.getSpans()) {
            String serviceName = swSpan.getServiceCode();
            if (StringUtil.isEmpty(serviceName)) {
                serviceName = "unknown";
            }
            spansByService.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(swSpan);
        }

        // Convert each service group to ResourceSpans
        for (Map.Entry<String, List<org.apache.skywalking.oap.server.core.query.type.Span>> entry : spansByService.entrySet()) {
            String serviceName = entry.getKey();
            List<org.apache.skywalking.oap.server.core.query.type.Span> serviceSpans = entry.getValue();

            ResourceSpans.Builder rsBuilder = ResourceSpans.newBuilder();

            // Set resource (service)
            rsBuilder.setResource(Resource.newBuilder()
                                          .addAttributes(KeyValue.newBuilder()
                                                                 .setKey("service.name")
                                                                 .setValue(AnyValue.newBuilder()
                                                                                   .setStringValue(serviceName)
                                                                                   .build())
                                                                 .build())
                                          .build());

            ScopeSpans.Builder ssBuilder = ScopeSpans.newBuilder();
            ssBuilder.setScope(InstrumentationScope.newBuilder()
                                                   .setName("skywalking-tracer")
                                                   .setVersion("0.1.0")
                                                   .build());

            // Convert each SkyWalking span to OTLP Span
            for (org.apache.skywalking.oap.server.core.query.type.Span swSpan : serviceSpans) {
                io.opentelemetry.proto.trace.v1.Span.Builder spanBuilder = io.opentelemetry.proto.trace.v1.Span.newBuilder();
                spanBuilder.setTraceId(ByteString.copyFrom(OTLPConverter.hexToBytes(traceId)));

                String spanId = swSpan.getSegmentSpanId();
                if (StringUtil.isEmpty(spanId)) {
                    // Fallback: use segmentId + spanId
                    spanId = swSpan.getSegmentId() + Const.SEGMENT_SPAN_SPLIT + swSpan.getSpanId();
                }
                spanBuilder.setSpanId(ByteString.copyFromUtf8(spanId));

                // Set parent span ID
                if (swSpan.getParentSpanId() >= 0 && !swSpan.isRoot()) {
                    String parentSpanId = swSpan.getSegmentParentSpanId();
                    if (StringUtil.isEmpty(parentSpanId)) {
                        parentSpanId = swSpan.getSegmentId() + Const.SEGMENT_SPAN_SPLIT + swSpan.getParentSpanId();
                    }
                    spanBuilder.setParentSpanId(ByteString.copyFromUtf8(parentSpanId));
                } else if (!swSpan.getRefs().isEmpty()) {
                    // Handle cross-segment reference
                    Ref ref = swSpan.getRefs().get(0);
                    String refParentSpanId = ref.getParentSegmentId() + Const.SEGMENT_SPAN_SPLIT + ref.getParentSpanId();
                    spanBuilder.setParentSpanId(ByteString.copyFromUtf8(refParentSpanId));
                }

                // Set span name
                String spanName = swSpan.getEndpointName();
                if (StringUtil.isEmpty(spanName)) {
                    spanName = swSpan.getType();
                }
                spanBuilder.setName(StringUtil.isNotEmpty(spanName) ? spanName : "unknown");

                // Set span kind based on type
                spanBuilder.setKind(convertSpanKind(swSpan.getType()));

                // Set timestamps (convert milliseconds to nanoseconds)
                spanBuilder.setStartTimeUnixNano(swSpan.getStartTime() * 1_000_000);
                spanBuilder.setEndTimeUnixNano(swSpan.getEndTime() * 1_000_000);

                // Add tags as attributes
                if (!swSpan.getTags().isEmpty()) {
                    for (org.apache.skywalking.oap.server.core.query.type.KeyValue tag : swSpan.getTags()) {
                        spanBuilder.addAttributes(KeyValue.newBuilder()
                                                          .setKey(tag.getKey())
                                                          .setValue(AnyValue.newBuilder()
                                                                            .setStringValue(tag.getValue())
                                                                            .build())
                                                          .build());
                    }
                }

                // Add standard attributes
                if (StringUtil.isNotEmpty(swSpan.getPeer())) {
                    spanBuilder.addAttributes(KeyValue.newBuilder()
                                                      .setKey("peer.address")
                                                      .setValue(AnyValue.newBuilder()
                                                                        .setStringValue(swSpan.getPeer())
                                                                        .build())
                                                      .build());
                }

                if (StringUtil.isNotEmpty(swSpan.getComponent())) {
                    spanBuilder.addAttributes(KeyValue.newBuilder()
                                                      .setKey("component")
                                                      .setValue(AnyValue.newBuilder()
                                                                        .setStringValue(swSpan.getComponent())
                                                                        .build())
                                                      .build());
                }

                if (StringUtil.isNotEmpty(swSpan.getLayer())) {
                    spanBuilder.addAttributes(KeyValue.newBuilder()
                                                      .setKey("layer")
                                                      .setValue(AnyValue.newBuilder()
                                                                        .setStringValue(swSpan.getLayer())
                                                                        .build())
                                                      .build());
                }

                // Set status
                Status.Builder statusBuilder = Status.newBuilder();
                if (swSpan.isError()) {
                    statusBuilder.setCode(Status.StatusCode.STATUS_CODE_ERROR);
                    statusBuilder.setMessage("Error occurred");
                } else {
                    statusBuilder.setCode(Status.StatusCode.STATUS_CODE_OK);
                }
                spanBuilder.setStatus(statusBuilder.build());

                // Convert logs to events
                if (!swSpan.getLogs().isEmpty()) {
                    for (LogEntity log : swSpan.getLogs()) {
                        io.opentelemetry.proto.trace.v1.Span.Event.Builder eventBuilder =
                            io.opentelemetry.proto.trace.v1.Span.Event.newBuilder()
                                .setTimeUnixNano(log.getTime() * 1_000_000)
                                .setName("log");

                        // Add log data as event attributes
                        if (!log.getData().isEmpty()) {
                            for (org.apache.skywalking.oap.server.core.query.type.KeyValue data : log.getData()) {
                                eventBuilder.addAttributes(KeyValue.newBuilder()
                                                                  .setKey(data.getKey())
                                                                  .setValue(AnyValue.newBuilder()
                                                                                    .setStringValue(data.getValue())
                                                                                    .build())
                                                                  .build());
                            }
                        }

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
     * Convert TraceList to SearchResponse format.
     *
     * @param traceList   SkyWalking trace list
     * @param allowedTags Only span attributes whose key is in this set are included; null means all
     * @return SearchResponse containing the converted traces
     */
    public static SearchResponse convertTraceListToSearchResponse(TraceList traceList, Set<String> allowedTags) {
        SearchResponse response = new SearchResponse();
        List<SearchResponse.Trace> traces = new ArrayList<>();

        if (traceList != null && traceList.getTraces() != null) {
            for (org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceV2 trace : traceList.getTraces()) {
                if (trace.getSpans().isEmpty()) {
                    continue;
                }
                traces.add(convertSWTraceToSearchTrace(trace, allowedTags));
            }
        }

        response.setTraces(traces);
        return response;
    }

    /**
     * Convert a single SkyWalking TraceV2 to SearchResponse.Trace.
     *
     * @param swTrace       SkyWalking TraceV2
     * @param allowedTags Only span attributes whose key is in this set are included; null means all
     * @return SearchResponse.Trace
     */
    private static SearchResponse.Trace convertSWTraceToSearchTrace(
        org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceV2 swTrace, Set<String> allowedTags) {

        SearchResponse.Trace trace = new SearchResponse.Trace();

        // Find root span to get trace metadata
        org.apache.skywalking.oap.server.core.query.type.Span rootSpan =
            swTrace.getSpans().stream()
                 .filter(org.apache.skywalking.oap.server.core.query.type.Span::isRoot)
                 .findFirst()
                 .orElse(swTrace.getSpans().get(0));

        trace.setTraceID(encodeTraceId(rootSpan.getTraceId()));
        trace.setRootServiceName(rootSpan.getServiceCode());
        trace.setRootTraceName(rootSpan.getEndpointName());

        // Calculate duration and start time
        long minStartTime = Long.MAX_VALUE;
        long maxEndTime = Long.MIN_VALUE;
        for (org.apache.skywalking.oap.server.core.query.type.Span span : swTrace.getSpans()) {
            minStartTime = Math.min(minStartTime, span.getStartTime());
            maxEndTime = Math.max(maxEndTime, span.getEndTime());
        }
        trace.setStartTimeUnixNano(String.valueOf(minStartTime * 1_000_000));
        trace.setDurationMs((int) (maxEndTime - minStartTime));

        // First pass: collect all attribute keys across all spans.
        // Grafana has a bug (search.go:369) where spans missing an attribute key that other
        // spans have cause a type panic: Append("") on a []*string field instead of Append(nil).
        // By ensuring all spans have the same keys (padding missing ones with ""), we avoid
        // the else branch in Grafana entirely.
        Set<String> allSpanAttrKeys = new LinkedHashSet<>();
        for (org.apache.skywalking.oap.server.core.query.type.Span span : swTrace.getSpans()) {
            for (org.apache.skywalking.oap.server.core.query.type.KeyValue tag : span.getTags()) {
                allSpanAttrKeys.add(tag.getKey());
            }
        }
        // Restrict to allowed tags when configured
        if (allowedTags != null) {
            allSpanAttrKeys.retainAll(allowedTags);
        }
        // Add fixed tags
        allSpanAttrKeys.add(SPAN_KIND);
        allSpanAttrKeys.add(SERVICE_NAME);
        SearchResponse.SpanSet spanSet = new SearchResponse.SpanSet();
        for (org.apache.skywalking.oap.server.core.query.type.Span span : swTrace.getSpans()) {
            spanSet.getSpans().add(convertSWSpanToSearchSpan(span, allSpanAttrKeys));
        }
        spanSet.setMatched(spanSet.getSpans().size());
        trace.getSpanSets().add(spanSet);

        return trace;
    }

    /**
     * Convert a single SkyWalking Span to SearchResponse.Span.
     * All keys in {@code allSpanAttrKeys} are written (missing ones padded with "") so that
     * every span in the same SpanSet has identical attribute keys — required to avoid a
     * Grafana search.go:369 type panic on []*string DataFrame fields.
     *
     * @param swSpan            SkyWalking Span
     * @param allSpanAttrKeys Ordered, already-filtered set of attribute keys to output
     * @return SearchResponse.Span
     */
    private static SearchResponse.Span convertSWSpanToSearchSpan(
        org.apache.skywalking.oap.server.core.query.type.Span swSpan, Set<String> allSpanAttrKeys) {

        SearchResponse.Span span = new SearchResponse.Span();
        span.setSpanID(swSpan.getSegmentSpanId());
        span.setStartTimeUnixNano(String.valueOf(swSpan.getStartTime() * 1_000_000));
        span.setDurationNanos(String.valueOf((swSpan.getEndTime() - swSpan.getStartTime()) * 1_000_000));

        // Build attribute map for this span
        Map<String, String> spanAttrMap = new java.util.LinkedHashMap<>();
        if (swSpan.getServiceCode() != null) {
            spanAttrMap.put(SERVICE_NAME, swSpan.getServiceCode());
        }
        if (swSpan.getType() != null) {
            spanAttrMap.put(SPAN_KIND, convertSpanType(swSpan.getType()));
        }
        for (org.apache.skywalking.oap.server.core.query.type.KeyValue tag : swSpan.getTags()) {
            spanAttrMap.put(tag.getKey(), tag.getValue());
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
     * Convert SkyWalking span type to OTLP span kind string.
     */
    private static String convertSpanType(String type) {
        if (StringUtil.isEmpty(type)) {
            return "INTERNAL";
        }

        switch (type.toUpperCase()) {
            case "ENTRY":
                return "SERVER";
            case "EXIT":
                return "CLIENT";
            case "LOCAL":
                return "INTERNAL";
            default:
                return "UNSPECIFIED";
        }
    }

    /**
     * Convert SkyWalking span type to OTLP span kind.
     */
    private static io.opentelemetry.proto.trace.v1.Span.SpanKind convertSpanKind(String type) {
        if (StringUtil.isEmpty(type)) {
            return io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_INTERNAL;
        }

        switch (type.toUpperCase()) {
            case "ENTRY":
                return io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_SERVER;
            case "EXIT":
                return io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_CLIENT;
            case "LOCAL":
                return io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_INTERNAL;
            default:
                return io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_UNSPECIFIED;
        }
    }

    /**
     * Encode a SkyWalking trace ID to a lowercase hex string.
     * SkyWalking trace IDs may contain letters, digits, '.' and '=',
     * which are not valid in OTLP/Tempo trace IDs.
     * Each UTF-8 byte of the trace ID is encoded as two hex characters,
     * producing a hex-only string that Grafana accepts and that can be
     * decoded back to the original trace ID without any loss.
     *
     * Examples:
     *   "2a2e04e8d1114b14925c04a6321ca26c.38.17739924187687539"
     *   → "326132653034653864313131346231343932356330346136333231636132366332653333382e31373733393932343138373638373533"
     *   "abc123=.test"
     *   → "61626331323333643265746573"  (encode)  → "abc123=.test"  (decode)
     *
     * @param swTraceId original SkyWalking trace ID
     * @return lowercase hex string
     */
    public static String encodeTraceId(String swTraceId) {
        byte[] bytes = swTraceId.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Decode a hex-encoded SkyWalking trace ID back to the original string.
     *
     * @param hexTraceId hex string produced by {@link #encodeTraceId}
     * @return original SkyWalking trace ID
     */
    public static String decodeTraceId(String hexTraceId) throws DecoderException {
        byte[] bytes = Hex.decodeHex(hexTraceId.toCharArray());
        return new String(bytes, StandardCharsets.UTF_8);
    }
}














