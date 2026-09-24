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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.apache.commons.codec.binary.Hex;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
import org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OTLPValues;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;

import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.SCOPE_RESOURCE;
import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.SERVICE_NAME;
import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.SPAN_KIND;
import static org.apache.skywalking.oap.query.traceql.handler.TraceQLApiHandler.STATUS;

/**
 * Turns the single-span {@code ResourceSpans} messages the native OTLP store keeps back into the wire shape an
 * exporter would have produced for a whole trace, and renders them as Tempo responses. Nothing is converted
 * between formats here: the stored bytes are the OTLP the SDK sent, this class only regroups them.
 */
public final class OTLPTraceAssembler {
    private static final JsonFormat.Printer PRINTER = JsonFormat.printer().omittingInsignificantWhitespace();
    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final String PARENT_SPAN_ID = "parentSpanId";

    /**
     * The tag autocomplete key and TraceQL attribute the instrumentation scope name is published under.
     */
    public static final String OTEL_SCOPE_NAME = "otel.scope.name";

    /**
     * The status a span gets when it was stored without one. Grafana reads a span's status without checking that it
     * is there ({@code int64(span.Status.Code)} in the Tempo datasource's {@code trace_transform.go}), so a span
     * without one crashes its trace view with a nil dereference. The field is optional in OTLP and senders do leave
     * it out, for instance APISIX's opentelemetry plugin, which sets a status only on an upstream 5xx. An absent
     * status and an empty one both mean {@code STATUS_CODE_UNSET}, so filling it in changes nothing a client reads.
     */
    private static final Status UNSET_STATUS = Status.getDefaultInstance();

    private OTLPTraceAssembler() {
    }

    /**
     * The attribute keys of one scope on the spans of the sampled traces that the matcher accepts, for Tempo's
     * filtered {@code /api/v2/search/tags}. Every attribute is indexed, so every key listed is filterable.
     */
    public static List<String> tagNames(final List<List<ResourceSpans>> traces,
                                        final OTLPSpanMatcher matcher,
                                        final String scope) {
        final Set<String> keys = new LinkedHashSet<>();
        for (final StoredSpan stored : matched(traces, matcher)) {
            if (SCOPE_RESOURCE.equals(scope)) {
                for (final KeyValue attribute : stored.resource.getAttributesList()) {
                    keys.add(attribute.getKey());
                }
            } else {
                if (!stored.scope.getName().isEmpty()) {
                    keys.add(OTEL_SCOPE_NAME);
                }
                for (final KeyValue attribute : stored.span.getAttributesList()) {
                    keys.add(attribute.getKey());
                }
            }
        }
        return new ArrayList<>(keys);
    }

    /**
     * The distinct values of one tag on the spans of the sampled traces that the matcher accepts, for Tempo's
     * filtered {@code /api/v2/search/tag/{tag}/values}. {@code tag} is the normalized name the handler switches on.
     */
    public static List<String> tagValues(final List<List<ResourceSpans>> traces,
                                         final OTLPSpanMatcher matcher,
                                         final String tag) {
        final List<StoredSpan> spans = matched(traces, matcher);
        final Set<String> values = new LinkedHashSet<>();
        for (final StoredSpan stored : spans) {
            final String value = tagValue(stored, tag);
            if (value != null && !value.isEmpty()) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    /**
     * TraceQL's spelling of the span status: {@code error}, {@code ok} or {@code unset}.
     */
    static String statusSpelling(final Span span) {
        return span.getStatus().getCode().name().substring("STATUS_CODE_".length()).toLowerCase(Locale.ROOT);
    }

    private static String tagValue(final StoredSpan stored, final String tag) {
        switch (tag) {
            case TraceQLApiHandler.RESOURCE_SERVICE_NAME:
            case TraceQLApiHandler.RESOURCE_SERVICE:
            case SERVICE_NAME:
                return stored.serviceName();
            case TraceQLApiHandler.RESOURCE_INSTANCE:
            case SCOPE_RESOURCE + "." + TraceQLApiHandler.SERVICE_INSTANCE_ID:
                return OTLPSpanMatcher.attribute(stored.resource.getAttributesList(), OTLPSpanRecord.SERVICE_INSTANCE_RESOURCE_KEYS);
            case TraceQLApiHandler.RESOURCE_REMOTE_SERVICE:
                return OTLPSpanMatcher.attribute(stored.span.getAttributesList(), List.of(OTLPSpanRecord.PEER_SERVICE_ATTRIBUTE));
            case TraceQLApiHandler.NAME:
                return stored.span.getName();
            case TraceQLApiHandler.STATUS:
                return statusSpelling(stored.span);
            case TraceQLApiHandler.KIND:
                return stored.span.getKind().name().substring("SPAN_KIND_".length()).toLowerCase(Locale.ROOT);
            case TraceQLApiHandler.DURATION:
                return null;
            case OTEL_SCOPE_NAME:
            case TraceQLApiHandler.SPAN_PREFIX + OTEL_SCOPE_NAME:
                return stored.scope.getName();
            default:
                if (tag.startsWith(TraceQLApiHandler.SPAN_PREFIX)) {
                    return OTLPSpanMatcher.attribute(stored.span.getAttributesList(), List.of(tag.substring(TraceQLApiHandler.SPAN_PREFIX.length())));
                }
                if (tag.startsWith(SCOPE_RESOURCE + ".")) {
                    return OTLPSpanMatcher.attribute(stored.resource.getAttributesList(), List.of(tag.substring(SCOPE_RESOURCE.length() + 1)));
                }
                return null;
        }
    }

    private static List<StoredSpan> matched(final List<List<ResourceSpans>> traces, final OTLPSpanMatcher matcher) {
        final List<StoredSpan> spans = new ArrayList<>();
        for (final List<ResourceSpans> trace : traces) {
            for (final StoredSpan stored : flatten(trace)) {
                if (matcher == null || matcher.matches(stored.resource, stored.scope, stored.span)) {
                    spans.add(stored);
                }
            }
        }
        return spans;
    }

    /**
     * Decode stored wrappers. A wrapper of another {@link Source} is skipped: the store holds OTLP only, so one
     * would be a bug elsewhere, not a reason to fail a read.
     */
    public static List<ResourceSpans> decode(final List<SpanWrapper> wrappers) throws InvalidProtocolBufferException {
        final List<ResourceSpans> spans = new ArrayList<>(wrappers.size());
        for (final SpanWrapper wrapper : wrappers) {
            if (wrapper.getSource() != Source.OTLP) {
                continue;
            }
            spans.add(ResourceSpans.parseFrom(wrapper.getSpan()));
        }
        return spans;
    }

    /**
     * Group single-span messages by resource identity, byte-equal {@code Resource} plus schema URL, then by scope
     * identity, and order the spans of each scope by start time. That is the wire shape of one export of the
     * whole trace and the only regrouping this datasource performs. Spans are returned as they were stored, except
     * that one stored without a status gets an empty one, see {@link #UNSET_STATUS}.
     */
    public static TraceByIDResponse assemble(final List<ResourceSpans> singleSpanMessages) {
        final Map<ResourceKey, Map<ScopeKey, List<Span>>> grouped = new LinkedHashMap<>();
        for (final ResourceSpans message : singleSpanMessages) {
            final ResourceKey resourceKey = new ResourceKey(message.getResource(), message.getSchemaUrl());
            final Map<ScopeKey, List<Span>> scopes = grouped.computeIfAbsent(resourceKey, k -> new LinkedHashMap<>());
            for (final ScopeSpans scopeSpans : message.getScopeSpansList()) {
                final ScopeKey scopeKey = new ScopeKey(scopeSpans.getScope(), scopeSpans.getSchemaUrl());
                scopes.computeIfAbsent(scopeKey, k -> new ArrayList<>()).addAll(scopeSpans.getSpansList());
            }
        }
        final Trace.Builder trace = Trace.newBuilder();
        grouped.forEach((resourceKey, scopes) -> {
            final ResourceSpans.Builder resourceSpans = ResourceSpans.newBuilder()
                                                                     .setResource(resourceKey.resource)
                                                                     .setSchemaUrl(resourceKey.schemaUrl);
            scopes.forEach((scopeKey, spans) -> {
                spans.sort(Comparator.comparingLong(Span::getStartTimeUnixNano));
                final ScopeSpans.Builder scopeSpans = ScopeSpans.newBuilder()
                                                                .setScope(scopeKey.scope)
                                                                .setSchemaUrl(scopeKey.schemaUrl);
                for (final Span span : spans) {
                    scopeSpans.addSpans(
                        span.hasStatus() ? span : span.toBuilder().setStatus(UNSET_STATUS).build());
                }
                resourceSpans.addScopeSpans(scopeSpans);
            });
            trace.addResourceSpans(resourceSpans);
        });
        return TraceByIDResponse.newBuilder().setTrace(trace).build();
    }

    /**
     * The OTLP/JSON encoding of the response: typed {@code AnyValue}s, enums by name, 64-bit integers as strings,
     * as protobuf's JSON mapping defines them, with one override: ids are printed as lowercase hex, not base64,
     * because that is what Tempo's API and Grafana expect.
     */
    public static String toJson(final TraceByIDResponse response) throws IOException {
        final JsonObject root = JsonParser.parseString(PRINTER.print(response)).getAsJsonObject();
        final JsonObject trace = root.getAsJsonObject("trace");
        if (trace == null) {
            return root.toString();
        }
        for (final JsonElement resourceSpans : arrayOf(trace, "resourceSpans")) {
            for (final JsonElement scopeSpans : arrayOf(resourceSpans.getAsJsonObject(), "scopeSpans")) {
                for (final JsonElement span : arrayOf(scopeSpans.getAsJsonObject(), "spans")) {
                    final JsonObject spanObject = span.getAsJsonObject();
                    base64ToHex(spanObject, TRACE_ID);
                    base64ToHex(spanObject, SPAN_ID);
                    base64ToHex(spanObject, PARENT_SPAN_ID);
                    for (final JsonElement link : arrayOf(spanObject, "links")) {
                        base64ToHex(link.getAsJsonObject(), TRACE_ID);
                        base64ToHex(link.getAsJsonObject(), SPAN_ID);
                    }
                }
            }
        }
        return root.toString();
    }

    /**
     * The Tempo search result for traces, each given as the single-span messages the store returned for it.
     *
     * @param allowedTags     the attribute keys listed per span, besides the fixed {@code service.name} and
     *                        {@code span.kind}
     * @param matcher         selects the spans the span set lists, null lists every span of the trace
     * @param spansPerSpanSet Tempo's {@code spss}, the most spans a span set lists; zero or less lists all matches
     */
    public static SearchResponse toSearchResponse(final List<List<ResourceSpans>> traces,
                                                  final Set<String> allowedTags,
                                                  final OTLPSpanMatcher matcher,
                                                  final int spansPerSpanSet) {
        final SearchResponse response = new SearchResponse();
        final List<List<StoredSpan>> all = new ArrayList<>();
        final List<List<StoredSpan>> matched = new ArrayList<>();
        for (final List<ResourceSpans> trace : traces) {
            final List<StoredSpan> spans = flatten(trace);
            if (spans.isEmpty()) {
                continue;
            }
            final List<StoredSpan> matching = matcher == null ? spans : spans.stream()
                .filter(s -> matcher.matches(s.resource, s.scope, s.span))
                .collect(Collectors.toList());
            // The storage matched this trace on the same conditions, so an empty result means the two evaluations
            // disagree; the trace is left out rather than listed with spans the query excluded (#14093).
            if (matching.isEmpty()) {
                continue;
            }
            all.add(spans);
            matched.add(matching);
        }
        final List<List<StoredSpan>> listed = new ArrayList<>(matched.size());
        for (final List<StoredSpan> matching : matched) {
            listed.add(spansPerSpanSet > 0 && matching.size() > spansPerSpanSet
                           ? matching.subList(0, spansPerSpanSet) : matching);
        }
        final Set<String> asText = keysToRenderAsText(listed, allowedTags);
        for (int i = 0; i < all.size(); i++) {
            response.getTraces().add(toSearchTrace(all.get(i), matched.get(i).size(), listed.get(i), allowedTags, asText));
        }
        return response;
    }

    /**
     * Grafana types a key's column from the first span carrying it, and fails on the next span whose value is of
     * another type ("interface {} is *string, not *int64"); its spans table is one column set over every trace of
     * the response. So a key keeps its OTLP type only when every listed span of the response carries it with the
     * same one; otherwise every span renders it as text, which the "" padding of a span without the key fits.
     */
    private static Set<String> keysToRenderAsText(final List<List<StoredSpan>> listed, final Set<String> allowedTags) {
        final Set<String> asText = new HashSet<>();
        for (final String key : allowedTags) {
            AnyValue.ValueCase seen = null;
            outer:
            for (final List<StoredSpan> spans : listed) {
                for (final StoredSpan stored : spans) {
                    final AnyValue value = stored.attributes().get(key);
                    if (value == null || (seen != null && seen != value.getValueCase())) {
                        asText.add(key);
                        break outer;
                    }
                    seen = value.getValueCase();
                }
            }
        }
        return asText;
    }

    /**
     * @param spans   every span of the trace, for the root, the bounds and {@code serviceStats}
     * @param matched how many spans the query matched, Tempo's {@code matched}
     * @param listed  the matched spans the span set lists, capped by {@code spss}
     * @param asText  the keys rendered as text on every span, see {@link #keysToRenderAsText}
     */
    private static SearchResponse.Trace toSearchTrace(final List<StoredSpan> spans,
                                                      final int matched,
                                                      final List<StoredSpan> listed,
                                                      final Set<String> allowedTags,
                                                      final Set<String> asText) {
        final SearchResponse.Trace trace = new SearchResponse.Trace();
        final StoredSpan first = spans.get(0);
        trace.setTraceID(hex(first.span.getTraceId().toByteArray()));
        final StoredSpan root = spans.stream()
                                     .filter(s -> s.span.getParentSpanId().isEmpty())
                                     .findFirst()
                                     .orElse(first);
        trace.setRootServiceName(root.serviceName());
        trace.setRootTraceName(root.span.getName());

        final long minStart = spans.stream().mapToLong(s -> s.span.getStartTimeUnixNano()).min().orElse(0L);
        final long maxEnd = spans.stream().mapToLong(s -> s.span.getEndTimeUnixNano()).max().orElse(minStart);
        trace.setStartTimeUnixNano(String.valueOf(minStart));
        trace.setDurationMs((int) ((maxEnd - minStart) / 1_000_000L));

        for (final StoredSpan stored : spans) {
            final SearchResponse.ServiceStat stat = trace.getServiceStats()
                                                         .computeIfAbsent(stored.serviceName(), k -> new SearchResponse.ServiceStat(0, 0));
            stat.setSpanCount(stat.getSpanCount() + 1);
            if (stored.span.getStatus().getCode() == Status.StatusCode.STATUS_CODE_ERROR) {
                stat.setErrorCount(stat.getErrorCount() + 1);
            }
        }

        // Every span lists the same keys, missing ones padded with an empty string: Grafana's search.go:369
        // appends "" to a []*string field when a key is absent and panics on nil.
        final Set<String> keys = new LinkedHashSet<>();
        for (final StoredSpan stored : listed) {
            keys.addAll(stored.attributes().keySet());
        }
        keys.retainAll(allowedTags);
        keys.add(SERVICE_NAME);
        keys.add(SPAN_KIND);
        keys.add(STATUS);

        final SearchResponse.SpanSet spanSet = new SearchResponse.SpanSet();
        for (final StoredSpan stored : listed) {
            final SearchResponse.Span span = new SearchResponse.Span();
            span.setSpanID(hex(stored.span.getSpanId().toByteArray()));
            span.setStartTimeUnixNano(String.valueOf(stored.span.getStartTimeUnixNano()));
            span.setDurationNanos(String.valueOf(stored.span.getEndTimeUnixNano() - stored.span.getStartTimeUnixNano()));
            final Map<String, AnyValue> attributes = stored.attributes();
            for (final String key : keys) {
                final SearchResponse.Attribute attribute = new SearchResponse.Attribute();
                attribute.setKey(key);
                final SearchResponse.Value value = new SearchResponse.Value();
                if (SPAN_KIND.equals(key)) {
                    value.setStringValue(stored.span.getKind().name());
                } else if (STATUS.equals(key)) {
                    // The span status as a fixed attribute, so a trace list can show failures without opening each
                    // trace: Tempo's search span has no status field of its own (#14093).
                    value.setStringValue(statusSpelling(stored.span));
                } else if (SERVICE_NAME.equals(key) && !attributes.containsKey(SERVICE_NAME)) {
                    value.setStringValue(stored.serviceName());
                } else if (!attributes.containsKey(key)) {
                    value.setStringValue("");
                } else if (asText.contains(key)) {
                    value.setStringValue(OTLPValues.render(attributes.get(key)));
                } else {
                    fill(value, attributes.get(key));
                }
                attribute.setValue(value);
                span.getAttributes().add(attribute);
            }
            spanSet.getSpans().add(span);
        }
        spanSet.setMatched(matched);
        trace.getSpanSets().add(spanSet);
        return trace;
    }

    private static void fill(final SearchResponse.Value value, final AnyValue anyValue) {
        if (anyValue.hasIntValue()) {
            value.setIntValue(String.valueOf(anyValue.getIntValue()));
        } else if (anyValue.hasBoolValue()) {
            value.setBoolValue(anyValue.getBoolValue());
        } else if (anyValue.hasDoubleValue()) {
            value.setDoubleValue(anyValue.getDoubleValue());
        } else {
            value.setStringValue(OTLPValues.render(anyValue));
        }
    }

    private static List<StoredSpan> flatten(final List<ResourceSpans> trace) {
        final List<StoredSpan> spans = new ArrayList<>();
        for (final ResourceSpans resourceSpans : trace) {
            for (final ScopeSpans scopeSpans : resourceSpans.getScopeSpansList()) {
                for (final Span span : scopeSpans.getSpansList()) {
                    spans.add(new StoredSpan(resourceSpans.getResource(), scopeSpans.getScope(), span));
                }
            }
        }
        return spans;
    }

    private static JsonArray arrayOf(final JsonObject object, final String member) {
        final JsonElement element = object.get(member);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static void base64ToHex(final JsonObject object, final String member) {
        final JsonElement element = object.get(member);
        if (element == null || !element.isJsonPrimitive()) {
            return;
        }
        object.addProperty(member, hex(Base64.getDecoder().decode(element.getAsString())));
    }

    private static String hex(final byte[] bytes) {
        return Hex.encodeHexString(bytes);
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    private static final class ResourceKey {
        private final Resource resource;
        private final String schemaUrl;
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    private static final class ScopeKey {
        private final InstrumentationScope scope;
        private final String schemaUrl;
    }

    /**
     * A span with the resource and instrumentation scope it was stored under.
     */
    @AllArgsConstructor
    private static final class StoredSpan {
        private final Resource resource;
        private final InstrumentationScope scope;
        private final Span span;

        /**
         * The service name the receiver stored, resolved from the same resource keys in the same order.
         */
        String serviceName() {
            return OTLPSpanMatcher.attribute(resource.getAttributesList(), OTLPSpanRecord.SERVICE_NAME_RESOURCE_KEYS);
        }

        /**
         * Resource and span attributes as one map, the span winning a duplicated key, the view the search list
         * filters and pads.
         */
        Map<String, AnyValue> attributes() {
            final Map<String, AnyValue> attributes = new LinkedHashMap<>();
            for (final KeyValue attribute : resource.getAttributesList()) {
                attributes.put(attribute.getKey(), attribute.getValue());
            }
            for (final KeyValue attribute : span.getAttributesList()) {
                attributes.put(attribute.getKey(), attribute.getValue());
            }
            return attributes;
        }
    }
}
