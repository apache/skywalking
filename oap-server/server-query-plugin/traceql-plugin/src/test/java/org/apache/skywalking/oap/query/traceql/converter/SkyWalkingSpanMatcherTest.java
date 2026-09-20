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

import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParams;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyWalkingSpanMatcherTest {
    private static Span span() {
        final Span span = new Span();
        span.setServiceCode("frontend");
        span.setServiceInstanceName("frontend-1");
        span.setEndpointName("GET:/api");
        span.setStartTime(1_000L);
        span.setEndTime(1_250L);
        span.setError(true);
        span.getTags().add(new KeyValue("http.method", "GET"));
        span.getTags().add(new KeyValue("http.status_code", "500"));
        return span;
    }

    @Test
    void shouldMatchAnEmptyQuery() {
        assertTrue(new SkyWalkingSpanMatcher(new TraceQLQueryParams()).matches(span()));
    }

    @Test
    void shouldMatchTheConditionTheHandlerBuilds() {
        final TraceQLQueryParams params = new TraceQLQueryParams();
        params.setServiceName("frontend");
        params.setServiceInstance("frontend-1");
        params.setSpanName("GET:/api");
        params.setMinDuration(250_000L);   // TraceQL durations reach the handler in microseconds
        params.setMaxDuration(250_000L);
        params.setStatus("error");
        params.getTags().put("http.method", "GET");
        params.setHttpStatusCode("500");
        assertTrue(new SkyWalkingSpanMatcher(params).matches(span()));

        params.setStatus("ok");
        assertFalse(new SkyWalkingSpanMatcher(params).matches(span()));
        params.setStatus("error");
        params.setHttpStatusCode("200");
        assertFalse(new SkyWalkingSpanMatcher(params).matches(span()));
        params.setHttpStatusCode(null);
        // the handler truncates microseconds to whole milliseconds, so 250_001 still means 250 ms
        params.setMinDuration(250_999L);
        assertTrue(new SkyWalkingSpanMatcher(params).matches(span()));
        params.setMinDuration(251_000L);
        assertFalse(new SkyWalkingSpanMatcher(params).matches(span()));
    }

    @Test
    void shouldTreatStarAsAnyValue() {
        final TraceQLQueryParams params = new TraceQLQueryParams();
        params.setServiceName("*");
        params.setSpanName("*");
        assertTrue(new SkyWalkingSpanMatcher(params).matches(span()));
    }
}
