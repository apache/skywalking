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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZipkinOTLPConverterTest {
    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

    private static Span span(final String id, final String parentId, final String service, final String name,
                             final long timestamp, final long duration, final String... tags) {
        final Span.Builder builder = Span.newBuilder()
                                         .traceId(TRACE_ID)
                                         .id(id)
                                         .parentId(parentId)
                                         .name(name)
                                         .localEndpoint(Endpoint.newBuilder().serviceName(service).build())
                                         .timestamp(timestamp)
                                         .duration(duration);
        for (int i = 0; i < tags.length; i += 2) {
            builder.putTag(tags[i], tags[i + 1]);
        }
        return builder.build();
    }

    @Test
    void shouldListOnlyMatchedSpansCappedBySpssAndCountServices() {
        final Span root = span("0000000000000001", null, "frontend", "get /", 1_000L, 4_000L, "http.method", "GET");
        final Span checkout = span("0000000000000002", "0000000000000001", "checkout", "charge", 2_000L, 1_000L, "http.method", "POST");
        final Span failed = span("0000000000000003", "0000000000000001", "checkout", "reserve", 2_100L, 800L, "http.method", "POST", "error", "500");
        final Span other = span("0000000000000004", "0000000000000001", "checkout", "notify", 2_200L, 600L, "http.method", "GET");
        final QueryRequest request = QueryRequest.newBuilder()
                                                 .endTs(10_000L).lookback(10_000L).limit(20)
                                                 .serviceName("checkout")
                                                 .annotationQuery(new HashMap<>(Map.of("http.method", "POST")))
                                                 .build();

        final SearchResponse response = ZipkinOTLPConverter.convertToSearchResponse(
            Collections.singletonList(Arrays.asList(root, checkout, failed, other)),
            Set.of("http.method"), new ZipkinSpanMatcher(request), 1);

        final SearchResponse.Trace trace = response.getTraces().get(0);
        assertEquals(TRACE_ID, trace.getTraceID());
        assertEquals("frontend", trace.getRootServiceName());
        assertEquals("get /", trace.getRootTraceName());
        final SearchResponse.SpanSet spanSet = trace.getSpanSets().get(0);
        assertEquals(2, spanSet.getMatched());
        assertEquals(1, spanSet.getSpans().size());
        assertEquals("0000000000000002", spanSet.getSpans().get(0).getSpanID());
        assertEquals(Arrays.asList("http.method", "service.name", "span.kind", "status"),
                     spanSet.getSpans().get(0).getAttributes().stream().map(SearchResponse.Attribute::getKey)
                            .collect(Collectors.toList()));
        // the listed checkout span has no error tag; the failed one would read "error"
        assertEquals("unset", spanSet.getSpans().get(0).getAttributes().get(3).getValue().getStringValue());
        assertEquals(1, trace.getServiceStats().get("frontend").getSpanCount());
        assertEquals(3, trace.getServiceStats().get("checkout").getSpanCount());
        assertEquals(1, trace.getServiceStats().get("checkout").getErrorCount());
    }

    @Test
    void shouldListTagNamesAndValuesOfMatchedSpansOnly() {
        final Span root = span("0000000000000001", null, "frontend", "get /", 1_000L, 4_000L, "http.method", "GET", "http.path", "/");
        final Span failed = span("0000000000000002", "0000000000000001", "checkout", "charge", 2_000L, 1_000L, "http.method", "POST", "error", "500");
        final List<List<Span>> traces = Collections.singletonList(Arrays.asList(root, failed));
        final ZipkinSpanMatcher matcher = new ZipkinSpanMatcher(
            QueryRequest.newBuilder().endTs(10_000L).lookback(10_000L).limit(20).serviceName("checkout").build());

        assertEquals(Arrays.asList("service", "remote.service"), ZipkinOTLPConverter.tagNames(traces, matcher, "resource"));
        assertEquals(Arrays.asList("error", "http.method"), ZipkinOTLPConverter.tagNames(traces, matcher, "span"));
        assertEquals(Collections.singletonList("checkout"), ZipkinOTLPConverter.tagValues(traces, matcher, "resource.service.name"));
        assertEquals(Collections.singletonList("charge"), ZipkinOTLPConverter.tagValues(traces, matcher, "name"));
        assertEquals(Collections.singletonList("error"), ZipkinOTLPConverter.tagValues(traces, matcher, "status"));
        assertEquals(Collections.singletonList("POST"), ZipkinOTLPConverter.tagValues(traces, matcher, "span.http.method"));
        assertEquals(Arrays.asList("GET", "POST"), ZipkinOTLPConverter.tagValues(traces, null, "span.http.method"));
        assertEquals(Collections.emptyList(), ZipkinOTLPConverter.tagValues(traces, null, "duration"));
    }

    @Test
    void shouldListEverySpanWithoutAMatcher() {
        final Span root = span("0000000000000001", null, "frontend", "get /", 1_000L, 4_000L);
        final Span child = span("0000000000000002", "0000000000000001", "checkout", "charge", 2_000L, 1_000L);

        final SearchResponse response = ZipkinOTLPConverter.convertToSearchResponse(
            Collections.singletonList(Arrays.asList(child, root)), Set.of(), null, 0);

        final SearchResponse.SpanSet spanSet = response.getTraces().get(0).getSpanSets().get(0);
        assertEquals(2, spanSet.getMatched());
        assertEquals(2, spanSet.getSpans().size());
        assertEquals("frontend", response.getTraces().get(0).getRootServiceName());
    }
}
