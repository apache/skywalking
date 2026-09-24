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

package org.apache.skywalking.oap.query.traceql.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceQLApiHandlerTest {
    /**
     * The shared helpers are what is under test; the datasource hooks are not reached.
     */
    private static final class StubHandler extends TraceQLApiHandler {
        @Override
        protected HttpResponse queryTraceImpl(final String traceId, final Optional<Long> start, final Optional<Long> end,
                                              final Optional<Boolean> coldStage, final Optional<String> accept) {
            return HttpResponse.of(HttpStatus.NOT_IMPLEMENTED);
        }

        @Override
        protected HttpResponse searchImpl(final Optional<String> query, final Optional<String> tags,
                                          final Optional<String> minDuration, final Optional<String> maxDuration,
                                          final Optional<Integer> limit, final Optional<Long> start, final Optional<Long> end,
                                          final Optional<Integer> spss, final Optional<Boolean> coldStage) {
            return HttpResponse.of(HttpStatus.NOT_IMPLEMENTED);
        }

        @Override
        protected HttpResponse searchTagsImpl(final Optional<String> scope, final Optional<Integer> limit,
                                              final Optional<Long> start, final Optional<Long> end) {
            return HttpResponse.of(HttpStatus.NOT_IMPLEMENTED);
        }

        @Override
        protected HttpResponse searchTagsV2Impl(final Optional<String> q, final Optional<String> scope,
                                                final Optional<Integer> limit, final Optional<Long> start,
                                                final Optional<Long> end) {
            return HttpResponse.of(HttpStatus.NOT_IMPLEMENTED);
        }

        @Override
        protected HttpResponse searchTagValuesImpl(final String tagName, final Optional<String> query,
                                                   final Optional<Integer> limit, final Optional<Long> start,
                                                   final Optional<Long> end) {
            return HttpResponse.of(HttpStatus.NOT_IMPLEMENTED);
        }
    }

    private static final TraceQLApiHandler.TagNamesSource THREE_PER_SCOPE =
        scope -> Arrays.asList(scope + ".a", scope + ".b", scope + ".c");

    @Test
    void shouldFoldTempoSpellingsOntoTheHandlerNames() {
        assertEquals("status", TraceQLApiHandler.normalizeTagName("span:status"));
        assertEquals("resource.service.name", TraceQLApiHandler.normalizeTagName(".service.name"));
        assertEquals("resource.service.instance.id", TraceQLApiHandler.normalizeTagName(".service.instance.id"));
        assertEquals("span.http.method", TraceQLApiHandler.normalizeTagName(".http.method"));
        assertEquals("resource.service.name", TraceQLApiHandler.normalizeTagName("resource.service.name"));
        assertEquals("span.http.method", TraceQLApiHandler.normalizeTagName("span.http.method"));
        assertEquals(".", TraceQLApiHandler.normalizeTagName("."));
    }

    @Test
    void shouldTellAnEmptyFilterFromAServiceOnlyFilterFromANarrowerOne() {
        assertNull(TraceQLApiHandler.parseFilter(Optional.empty()));
        assertNull(TraceQLApiHandler.parseFilter(Optional.of(" ")));
        assertTrue(TraceQLApiHandler.parseFilter(Optional.of("{ this is not traceql")).hasError());

        final TraceQLQueryParams empty = TraceQLApiHandler.parseFilter(Optional.of("{}")).getParams();
        assertFalse(TraceQLApiHandler.hasFilter(empty));

        final TraceQLQueryParams serviceOnly = TraceQLApiHandler.parseFilter(Optional.of("{resource.service.name=\"frontend\"}")).getParams();
        assertTrue(TraceQLApiHandler.hasFilter(serviceOnly));
        assertTrue(TraceQLApiHandler.onlyServiceName(serviceOnly));

        final TraceQLQueryParams narrower = TraceQLApiHandler.parseFilter(Optional.of("{resource.service.name=\"frontend\" && span.http.method=\"GET\"}")).getParams();
        assertTrue(TraceQLApiHandler.hasFilter(narrower));
        assertFalse(TraceQLApiHandler.onlyServiceName(narrower));

        final TraceQLQueryParams star = new TraceQLQueryParams();
        star.setServiceName("*");
        assertFalse(TraceQLApiHandler.hasFilter(star));
    }

    @Test
    void shouldKeepTheEnumIntrinsicsOutOfTheSampledLookup() {
        assertTrue(TraceQLApiHandler.isEnumIntrinsic("status"));
        assertTrue(TraceQLApiHandler.isEnumIntrinsic("kind"));
        assertFalse(TraceQLApiHandler.isEnumIntrinsic("name"));
        assertFalse(TraceQLApiHandler.isEnumIntrinsic("span.http.method"));
    }

    @Test
    void shouldParseTheDeprecatedTagsParameterAsLogfmt() {
        final TraceQLQueryParams params = TraceQLApiHandler.parseTagsParameter(
            "service.name=frontend  span.http.method=GET name=\"GET /\" resource.host.name=web-1 error=true");
        assertEquals("frontend", params.getServiceName());
        assertEquals("GET /", params.getSpanName());
        assertEquals("GET", params.getTags().get("span.http.method"));
        assertEquals("web-1", params.getTags().get("resource.host.name"));
        assertEquals("true", params.getTags().get("error"));
        assertEquals(3, params.getTags().size());
        assertEquals("GET", params.flatTags().get("http.method"));

        assertThrows(IllegalArgumentException.class, () -> TraceQLApiHandler.parseTagsParameter("http.method"));
        assertThrows(IllegalArgumentException.class, () -> TraceQLApiHandler.parseTagsParameter("=GET"));
    }

    @Test
    void shouldResolveRequestedScopes() {
        assertEquals(TraceQLApiHandler.ALL_SCOPES, TraceQLApiHandler.requestedScopes(Optional.empty()));
        assertEquals(Collections.singletonList("span"), TraceQLApiHandler.requestedScopes(Optional.of("SPAN")));
        assertTrue(TraceQLApiHandler.requestedScopes(Optional.of("event")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TraceQLApiHandler.requestedScopes(Optional.of("bogus")));
    }

    @Test
    void shouldTruncateOnlyForAPositiveLimit() {
        final List<String> values = Arrays.asList("a", "b", "c");
        assertEquals(values, TraceQLApiHandler.truncate(values, Optional.empty()));
        assertEquals(values, TraceQLApiHandler.truncate(values, Optional.of(0)));
        assertEquals(Arrays.asList("a", "b"), TraceQLApiHandler.truncate(values, Optional.of(2)));
        assertEquals(3, TraceQLApiHandler.spansPerSpanSet(Optional.empty()));
        assertEquals(3, TraceQLApiHandler.spansPerSpanSet(Optional.of(0)));
        assertEquals(5, TraceQLApiHandler.spansPerSpanSet(Optional.of(5)));
    }

    @Test
    void shouldApplyTheV2LimitPerScope() throws IOException {
        final JsonNode body = new ObjectMapper().readTree(
            new StubHandler().tagNamesV2(Optional.empty(), Optional.of(2), THREE_PER_SCOPE).aggregate().join().contentUtf8());
        assertEquals(3, body.get("scopes").size());
        for (final JsonNode scope : body.get("scopes")) {
            assertEquals(2, scope.get("tags").size(), scope.get("name").asText());
        }
    }

    @Test
    void shouldFlattenTheV1ListAndCapItOnce() throws IOException {
        final JsonNode body = new ObjectMapper().readTree(
            new StubHandler().tagNames(Optional.empty(), Optional.of(4), THREE_PER_SCOPE).aggregate().join().contentUtf8());
        assertEquals(4, body.get("tagNames").size());
        assertEquals("resource.a", body.get("tagNames").get(0).asText());
        assertEquals("span.a", body.get("tagNames").get(3).asText());
    }

    @Test
    void shouldRejectAnUnknownScope() throws IOException {
        final var response = new StubHandler().tagNamesV2(Optional.of("bogus"), Optional.empty(), THREE_PER_SCOPE).aggregate().join();
        assertEquals(400, response.status().code());
        assertTrue(response.contentUtf8().contains("Unsupported scope: bogus"));
    }
}
