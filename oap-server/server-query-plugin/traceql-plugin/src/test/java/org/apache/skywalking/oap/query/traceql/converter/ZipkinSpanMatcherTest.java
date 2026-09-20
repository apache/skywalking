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

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipkinSpanMatcherTest {
    private static final Span SPAN = Span.newBuilder()
                                         .traceId("0af7651916cd43dd8448eb211c80319c")
                                         .id("00b7ad6b71692033")
                                         .name("get /api")
                                         .localEndpoint(Endpoint.newBuilder().serviceName("frontend").build())
                                         .remoteEndpoint(Endpoint.newBuilder().serviceName("backend").build())
                                         .timestamp(1_000_000L)
                                         .duration(2_000L)
                                         .putTag("http.method", "GET")
                                         .putTag("http.status_code", "500")
                                         .putTag("error", "500")
                                         .addAnnotation(1_000_500L, "ws")
                                         .build();

    private static QueryRequest.Builder request() {
        return QueryRequest.newBuilder().endTs(2_000L).lookback(1_000L).limit(10);
    }

    @Test
    void shouldMatchAnEmptyRequest() {
        assertTrue(new ZipkinSpanMatcher(request().build()).matches(SPAN));
    }

    @Test
    void shouldMatchServicesNameAndDurationLikeTheQueryDao() {
        assertTrue(new ZipkinSpanMatcher(request().serviceName("frontend").remoteServiceName("backend")
                                                  .spanName("get /api").minDuration(2_000L).maxDuration(2_000L).build())
                       .matches(SPAN));
        assertFalse(new ZipkinSpanMatcher(request().serviceName("backend").build()).matches(SPAN));
        assertFalse(new ZipkinSpanMatcher(request().minDuration(2_001L).build()).matches(SPAN));
        assertFalse(new ZipkinSpanMatcher(request().spanName("post /api").build()).matches(SPAN));
    }

    @Test
    void shouldMatchTheAnnotationQueryOnTagsAndAnnotations() {
        assertTrue(new ZipkinSpanMatcher(request().annotationQuery(new HashMap<>(Map.of("http.method", "GET", "http.status_code", "500"))).build())
                       .matches(SPAN));
        // an empty value asks for the key, on a tag or on an annotation value, Zipkin's {status = error} form
        assertTrue(new ZipkinSpanMatcher(request().annotationQuery(new HashMap<>(Map.of("error", ""))).build()).matches(SPAN));
        assertTrue(new ZipkinSpanMatcher(request().annotationQuery(new HashMap<>(Map.of("ws", ""))).build()).matches(SPAN));
        assertFalse(new ZipkinSpanMatcher(request().annotationQuery(new HashMap<>(Map.of("http.status_code", "200"))).build()).matches(SPAN));
        assertFalse(new ZipkinSpanMatcher(request().annotationQuery(new HashMap<>(Map.of("missing", ""))).build()).matches(SPAN));
    }
}
