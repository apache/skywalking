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
import java.util.Set;
import org.apache.skywalking.oap.query.traceql.entity.SearchResponse;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParams;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceList;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceV2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyWalkingOTLPConverterTest {
    private static Span span(final String segmentSpanId, final boolean root, final String service, final String endpoint,
                             final String type, final long start, final long end, final boolean error, final String... tags) {
        final Span span = new Span();
        span.setTraceId("trace-1");
        span.setSegmentSpanId(segmentSpanId);
        span.setRoot(root);
        span.setServiceCode(service);
        span.setEndpointName(endpoint);
        span.setType(type);
        span.setStartTime(start);
        span.setEndTime(end);
        span.setError(error);
        for (int i = 0; i < tags.length; i += 2) {
            span.getTags().add(new KeyValue(tags[i], tags[i + 1]));
        }
        return span;
    }

    @Test
    void shouldListOnlyMatchedSpansCappedBySpssAndCountServices() {
        final TraceV2 trace = new TraceV2();
        trace.getSpans().add(span("s1-1", true, "frontend", "GET:/", "Entry", 1_000L, 1_400L, false, "http.method", "GET"));
        trace.getSpans().add(span("s2-1", false, "checkout", "POST:/charge", "Entry", 1_100L, 1_300L, true, "http.method", "POST", "http.status_code", "500"));
        trace.getSpans().add(span("s2-2", false, "checkout", "POST:/reserve", "Entry", 1_150L, 1_250L, false, "http.method", "POST"));
        trace.getSpans().add(span("s2-3", false, "checkout", "notify", "Local", 1_200L, 1_210L, false));
        final TraceList traceList = new TraceList(null);
        traceList.getTraces().add(trace);
        final TraceQLQueryParams params = new TraceQLQueryParams();
        params.setServiceName("checkout");
        params.getTags().put("http.method", "POST");

        final SearchResponse response = SkyWalkingOTLPConverter.convertTraceListToSearchResponse(
            traceList, Set.of("http.method"), new SkyWalkingSpanMatcher(params), 1);

        final SearchResponse.Trace result = response.getTraces().get(0);
        assertEquals("frontend", result.getRootServiceName());
        assertEquals("GET:/", result.getRootTraceName());
        final SearchResponse.SpanSet spanSet = result.getSpanSets().get(0);
        assertEquals(2, spanSet.getMatched());
        assertEquals(1, spanSet.getSpans().size());
        assertEquals("s2-1", spanSet.getSpans().get(0).getSpanID());
        assertEquals("error", spanSet.getSpans().get(0).getAttributes().stream()
                                     .filter(a -> "status".equals(a.getKey())).findFirst().get().getValue().getStringValue());
        assertEquals(1, result.getServiceStats().get("frontend").getSpanCount());
        assertEquals(3, result.getServiceStats().get("checkout").getSpanCount());
        assertEquals(1, result.getServiceStats().get("checkout").getErrorCount());
    }

    @Test
    void shouldListTagNamesAndValuesOfMatchedSpansOnly() {
        final TraceV2 trace = new TraceV2();
        trace.getSpans().add(span("s1-1", true, "frontend", "GET:/", "Entry", 1_000L, 1_400L, false, "http.method", "GET", "http.url", "/"));
        trace.getSpans().add(span("s2-1", false, "checkout", "POST:/charge", "Entry", 1_100L, 1_300L, true, "http.method", "POST", "http.status_code", "500"));
        final TraceList traceList = new TraceList(null);
        traceList.getTraces().add(trace);
        final TraceQLQueryParams params = new TraceQLQueryParams();
        params.setServiceName("checkout");
        final SkyWalkingSpanMatcher matcher = new SkyWalkingSpanMatcher(params);

        assertEquals(Arrays.asList("service", "instance"), SkyWalkingOTLPConverter.tagNames(traceList, matcher, "resource", Set.of()));
        // only searchable keys are query conditions, so only they are offered
        assertEquals(Collections.singletonList("http.method"),
                     SkyWalkingOTLPConverter.tagNames(traceList, matcher, "span", Set.of("http.method", "http.url")));
        assertEquals(Collections.singletonList("checkout"), SkyWalkingOTLPConverter.tagValues(traceList, matcher, "resource.service.name"));
        assertEquals(Collections.singletonList("POST:/charge"), SkyWalkingOTLPConverter.tagValues(traceList, matcher, "name"));
        assertEquals(Collections.singletonList("error"), SkyWalkingOTLPConverter.tagValues(traceList, matcher, "status"));
        assertEquals(Collections.singletonList("500"), SkyWalkingOTLPConverter.tagValues(traceList, matcher, "span.http.status_code"));
        assertEquals(Arrays.asList("ok", "error"), SkyWalkingOTLPConverter.tagValues(traceList, null, "status"));
    }

    @Test
    void shouldListEverySpanWithoutAMatcher() {
        final TraceV2 trace = new TraceV2();
        trace.getSpans().add(span("s1-1", true, "frontend", "GET:/", "Entry", 1_000L, 1_400L, false));
        trace.getSpans().add(span("s1-2", false, "frontend", "redis", "Exit", 1_100L, 1_200L, false));
        final TraceList traceList = new TraceList(null);
        traceList.getTraces().add(trace);

        final SearchResponse response = SkyWalkingOTLPConverter.convertTraceListToSearchResponse(traceList, Set.of(), null, 0);

        assertEquals(2, response.getTraces().get(0).getSpanSets().get(0).getSpans().size());
        assertEquals(2, response.getTraces().get(0).getSpanSets().get(0).getMatched());
    }
}
