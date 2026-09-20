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

package org.apache.skywalking.oap.query.traceql.converter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import io.grafana.tempo.tempopb.TraceByIDResponse;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OTLPTraceAssemblerTest {
    private static final ByteString TRACE_ID = ByteString.copyFrom(new byte[] {
        0x0a, (byte) 0xf7, 0x65, 0x19, 0x16, (byte) 0xcd, 0x43, (byte) 0xdd,
        (byte) 0x84, 0x48, (byte) 0xeb, 0x21, 0x1c, (byte) 0x80, 0x31, (byte) 0x9c
    });
    private static final ByteString ROOT_SPAN_ID = ByteString.copyFrom(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
    private static final ByteString CHILD_SPAN_ID = ByteString.copyFrom(new byte[] {0x00, (byte) 0xb7, (byte) 0xad, 0x6b, 0x71, 0x69, 0x20, 0x33});
    private static final Resource FRONTEND = resource("frontend");
    private static final Resource CHECKOUT = resource("checkout");
    private static final InstrumentationScope HTTP_SCOPE = InstrumentationScope.newBuilder().setName("io.opentelemetry.http").build();
    private static final InstrumentationScope GRPC_SCOPE = InstrumentationScope.newBuilder().setName("io.opentelemetry.grpc").build();

    @Test
    void shouldRegroupByResourceThenScopeAndOrderSpansByStart() {
        final Span later = span(CHILD_SPAN_ID, ROOT_SPAN_ID, "GET /cart", 300L, 350L).build();
        final Span earlier = span(ROOT_SPAN_ID, ByteString.EMPTY, "GET /", 100L, 400L).build();
        final Span other = span(ByteString.copyFrom(new byte[] {9, 9, 9, 9, 9, 9, 9, 9}), ROOT_SPAN_ID, "Checkout", 200L, 250L).build();

        final TraceByIDResponse response = OTLPTraceAssembler.assemble(Arrays.asList(
            stored(FRONTEND, HTTP_SCOPE, later),
            stored(CHECKOUT, GRPC_SCOPE, other),
            stored(FRONTEND, HTTP_SCOPE, earlier)
        ));

        final List<ResourceSpans> resourceSpans = response.getTrace().getResourceSpansList();
        assertEquals(2, resourceSpans.size());
        assertEquals(FRONTEND, resourceSpans.get(0).getResource());
        assertEquals(1, resourceSpans.get(0).getScopeSpansCount());
        final ScopeSpans frontendScope = resourceSpans.get(0).getScopeSpans(0);
        assertEquals(HTTP_SCOPE, frontendScope.getScope());
        assertEquals(Arrays.asList("GET /", "GET /cart"),
                     frontendScope.getSpansList().stream().map(Span::getName).collect(Collectors.toList()));
        assertEquals(CHECKOUT, resourceSpans.get(1).getResource());
        assertEquals(GRPC_SCOPE, resourceSpans.get(1).getScopeSpans(0).getScope());
        assertEquals(other, resourceSpans.get(1).getScopeSpans(0).getSpans(0));
    }

    @Test
    void shouldDecodeOnlyOTLPWrappers() throws Exception {
        final ResourceSpans stored = stored(FRONTEND, HTTP_SCOPE, span(ROOT_SPAN_ID, ByteString.EMPTY, "GET /", 1L, 2L).build());
        final SpanWrapper otlp = SpanWrapper.newBuilder().setSource(Source.OTLP).setSpan(stored.toByteString()).build();
        final SpanWrapper zipkin = SpanWrapper.newBuilder().setSource(Source.ZIPKIN).setSpan(ByteString.copyFromUtf8("{}")).build();

        assertEquals(Collections.singletonList(stored), OTLPTraceAssembler.decode(Arrays.asList(zipkin, otlp)));
    }

    @Test
    void shouldPrintIdsAsHexInJson() throws Exception {
        final Span span = span(CHILD_SPAN_ID, ROOT_SPAN_ID, "GET /", 1L, 2L)
            .addLinks(Span.Link.newBuilder().setTraceId(TRACE_ID).setSpanId(ROOT_SPAN_ID))
            .build();
        final TraceByIDResponse response = OTLPTraceAssembler.assemble(
            Collections.singletonList(stored(FRONTEND, HTTP_SCOPE, span)));

        final JsonObject json = JsonParser.parseString(OTLPTraceAssembler.toJson(response)).getAsJsonObject();
        final JsonObject printed = json.getAsJsonObject("trace")
                                       .getAsJsonArray("resourceSpans").get(0).getAsJsonObject()
                                       .getAsJsonArray("scopeSpans").get(0).getAsJsonObject()
                                       .getAsJsonArray("spans").get(0).getAsJsonObject();
        assertEquals("0af7651916cd43dd8448eb211c80319c", printed.get("traceId").getAsString());
        assertEquals("00b7ad6b71692033", printed.get("spanId").getAsString());
        assertEquals("0102030405060708", printed.get("parentSpanId").getAsString());
        assertEquals("SPAN_KIND_SERVER", printed.get("kind").getAsString());
        assertEquals("1", printed.get("startTimeUnixNano").getAsString());
        final JsonObject link = printed.getAsJsonArray("links").get(0).getAsJsonObject();
        assertEquals("0af7651916cd43dd8448eb211c80319c", link.get("traceId").getAsString());
        assertEquals("0102030405060708", link.get("spanId").getAsString());
    }

    @Test
    void shouldListTheSameKeysOnEverySpanWithTypedValues() {
        final Span root = span(ROOT_SPAN_ID, ByteString.EMPTY, "GET /", 1_000_000L, 4_000_000L)
            .addAttributes(KeyValue.newBuilder().setKey("http.response.status_code").setValue(AnyValue.newBuilder().setIntValue(200)))
            .addAttributes(KeyValue.newBuilder().setKey("http.route").setValue(AnyValue.newBuilder().setStringValue("/")))
            .build();
        final Span child = span(CHILD_SPAN_ID, ROOT_SPAN_ID, "Checkout", 2_000_000L, 3_000_000L)
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .build();
        final Set<String> allowed = Set.of("http.response.status_code");

        final SearchResponse response = OTLPTraceAssembler.toSearchResponse(Collections.singletonList(Arrays.asList(
            stored(CHECKOUT, GRPC_SCOPE, child),
            stored(FRONTEND, HTTP_SCOPE, root)
        )), allowed, null, 0);

        assertEquals(1, response.getTraces().size());
        final SearchResponse.Trace trace = response.getTraces().get(0);
        assertEquals("0af7651916cd43dd8448eb211c80319c", trace.getTraceID());
        assertEquals("frontend", trace.getRootServiceName());
        assertEquals("GET /", trace.getRootTraceName());
        assertEquals("1000000", trace.getStartTimeUnixNano());
        assertEquals(3, trace.getDurationMs());
        final List<SearchResponse.Span> spans = trace.getSpanSets().get(0).getSpans();
        assertEquals(2, spans.size());
        assertEquals(2, trace.getSpanSets().get(0).getMatched());

        final SearchResponse.Span checkout = spans.get(0);
        assertEquals("00b7ad6b71692033", checkout.getSpanID());
        assertEquals("1000000", checkout.getDurationNanos());
        assertEquals(Arrays.asList("http.response.status_code", "service.name", "span.kind", "status"), keys(checkout));
        assertEquals("", checkout.getAttributes().get(0).getValue().getStringValue());
        assertEquals("checkout", checkout.getAttributes().get(1).getValue().getStringValue());
        assertEquals("SPAN_KIND_CLIENT", checkout.getAttributes().get(2).getValue().getStringValue());
        assertEquals("unset", checkout.getAttributes().get(3).getValue().getStringValue());

        final SearchResponse.Span frontend = spans.get(1);
        assertEquals(keys(checkout), keys(frontend));
        assertEquals("200", frontend.getAttributes().get(0).getValue().getIntValue());
        assertNull(frontend.getAttributes().get(0).getValue().getStringValue());
        assertEquals("frontend", frontend.getAttributes().get(1).getValue().getStringValue());
        assertTrue(keys(frontend).stream().noneMatch("http.route"::equals));
    }

    @Test
    void shouldListOnlyMatchedSpansCappedBySpssAndCountServices() {
        final Span root = span(ROOT_SPAN_ID, ByteString.EMPTY, "GET /", 1_000_000L, 4_000_000L).build();
        final Span client = span(CHILD_SPAN_ID, ROOT_SPAN_ID, "Checkout", 2_000_000L, 3_000_000L)
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .build();
        final Span failed = span(ByteString.copyFrom(new byte[] {7, 7, 7, 7, 7, 7, 7, 7}), ROOT_SPAN_ID, "Charge", 2_100_000L, 2_900_000L)
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR))
            .build();
        final Span other = span(ByteString.copyFrom(new byte[] {8, 8, 8, 8, 8, 8, 8, 8}), ROOT_SPAN_ID, "Reserve", 2_200_000L, 2_800_000L).build();
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setServiceName("checkout");
        condition.setKind(Span.SpanKind.SPAN_KIND_SERVER.getNumber());

        final SearchResponse response = OTLPTraceAssembler.toSearchResponse(Collections.singletonList(Arrays.asList(
            stored(FRONTEND, HTTP_SCOPE, root),
            stored(CHECKOUT, GRPC_SCOPE, client),
            stored(CHECKOUT, GRPC_SCOPE, failed),
            stored(CHECKOUT, GRPC_SCOPE, other)
        )), Set.of(), new OTLPSpanMatcher(condition), 1);

        final SearchResponse.Trace trace = response.getTraces().get(0);
        final SearchResponse.SpanSet spanSet = trace.getSpanSets().get(0);
        // The frontend span and the checkout client span do not match; the two checkout server spans do, one listed.
        assertEquals(2, spanSet.getMatched());
        assertEquals(1, spanSet.getSpans().size());
        assertEquals("0707070707070707", spanSet.getSpans().get(0).getSpanID());
        assertEquals("frontend", trace.getRootServiceName());
        assertEquals(1, trace.getServiceStats().get("frontend").getSpanCount());
        assertEquals(0, trace.getServiceStats().get("frontend").getErrorCount());
        assertEquals(3, trace.getServiceStats().get("checkout").getSpanCount());
        assertEquals(1, trace.getServiceStats().get("checkout").getErrorCount());
    }

    @Test
    void shouldFallBackToEverySpanWhenNoSpanMatchesInMemory() {
        final Span root = span(ROOT_SPAN_ID, ByteString.EMPTY, "GET /", 1L, 2L).build();
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setSpanName("a name NamingControl reformatted");

        final SearchResponse response = OTLPTraceAssembler.toSearchResponse(Collections.singletonList(
            Collections.singletonList(stored(FRONTEND, HTTP_SCOPE, root))), Set.of(), new OTLPSpanMatcher(condition), 3);

        assertEquals(1, response.getTraces().get(0).getSpanSets().get(0).getSpans().size());
        assertEquals(1, response.getTraces().get(0).getSpanSets().get(0).getMatched());
    }

    @Test
    void shouldListTagNamesAndValuesOfMatchedSpansOnly() {
        final Span root = span(ROOT_SPAN_ID, ByteString.EMPTY, "GET /", 1L, 4L)
            .addAttributes(KeyValue.newBuilder().setKey("http.route").setValue(AnyValue.newBuilder().setStringValue("/")))
            .build();
        final Span client = span(CHILD_SPAN_ID, ROOT_SPAN_ID, "Charge", 2L, 3L)
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .addAttributes(KeyValue.newBuilder().setKey("peer.service").setValue(AnyValue.newBuilder().setStringValue("payment")))
            .addAttributes(KeyValue.newBuilder().setKey("rpc.grpc.status_code").setValue(AnyValue.newBuilder().setIntValue(0)))
            .build();
        final List<List<ResourceSpans>> traces = Collections.singletonList(Arrays.asList(
            stored(FRONTEND, HTTP_SCOPE, root), stored(CHECKOUT, GRPC_SCOPE, client)));
        final OTLPTraceQueryCondition condition = new OTLPTraceQueryCondition();
        condition.setKind(Span.SpanKind.SPAN_KIND_CLIENT.getNumber());
        final OTLPSpanMatcher matcher = new OTLPSpanMatcher(condition);

        assertEquals(Arrays.asList("otel.scope.name", "peer.service", "rpc.grpc.status_code"),
                     OTLPTraceAssembler.tagNames(traces, matcher, "span"));
        assertEquals(Collections.singletonList("service.name"), OTLPTraceAssembler.tagNames(traces, matcher, "resource"));
        assertEquals(Collections.singletonList("checkout"), OTLPTraceAssembler.tagValues(traces, matcher, "resource.service.name"));
        assertEquals(Collections.singletonList("payment"), OTLPTraceAssembler.tagValues(traces, matcher, "resource.remote.service"));
        assertEquals(Collections.singletonList("Charge"), OTLPTraceAssembler.tagValues(traces, matcher, "name"));
        assertEquals(Collections.singletonList("client"), OTLPTraceAssembler.tagValues(traces, matcher, "kind"));
        assertEquals(Collections.singletonList("unset"), OTLPTraceAssembler.tagValues(traces, matcher, "status"));
        assertEquals(Collections.singletonList("0"), OTLPTraceAssembler.tagValues(traces, matcher, "span.rpc.grpc.status_code"));
        assertEquals(Collections.singletonList("io.opentelemetry.grpc"), OTLPTraceAssembler.tagValues(traces, matcher, "span.otel.scope.name"));
        assertTrue(OTLPTraceAssembler.tagValues(traces, matcher, "duration").isEmpty());
        assertTrue(OTLPTraceAssembler.tagValues(traces, matcher, "span.http.route").isEmpty());
        // without a matcher every span counts
        assertEquals(Arrays.asList("frontend", "checkout"), OTLPTraceAssembler.tagValues(traces, null, "resource.service.name"));
    }

    private static List<String> keys(final SearchResponse.Span span) {
        return span.getAttributes().stream().map(SearchResponse.Attribute::getKey).collect(Collectors.toList());
    }

    private static Resource resource(final String serviceName) {
        return Resource.newBuilder()
                       .addAttributes(KeyValue.newBuilder()
                                              .setKey("service.name")
                                              .setValue(AnyValue.newBuilder().setStringValue(serviceName)))
                       .build();
    }

    private static Span.Builder span(final ByteString spanId, final ByteString parentSpanId, final String name,
                                     final long startNanos, final long endNanos) {
        return Span.newBuilder()
                   .setTraceId(TRACE_ID)
                   .setSpanId(spanId)
                   .setParentSpanId(parentSpanId)
                   .setName(name)
                   .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                   .setStartTimeUnixNano(startNanos)
                   .setEndTimeUnixNano(endNanos);
    }

    private static ResourceSpans stored(final Resource resource, final InstrumentationScope scope, final Span span) {
        return ResourceSpans.newBuilder()
                            .setResource(resource)
                            .addScopeSpans(ScopeSpans.newBuilder().setScope(scope).addSpans(span))
                            .build();
    }
}
