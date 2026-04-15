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

package org.apache.skywalking.oap.query.tempo.parser;

import org.apache.skywalking.oap.query.traceql.rt.TraceQLParseResult;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParams;
import org.apache.skywalking.oap.query.traceql.rt.TraceQLQueryParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test TraceQL parser.
 */
public class TraceQLQueryParserTest {

    @Test
    public void testUnscopedServiceName() {
        String query = "{.service.name=\"frontend\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals("frontend", params.getServiceName());
    }

    @Test
    public void testScopedServiceName() {
        String query = "{resource.service.name=\"backend\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals("backend", params.getServiceName());
    }

    @Test
    public void testScopeRemoteService() {
        String query = "{resource.remote.service=\"backend\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals("backend", params.getRemoteServiceName());
    }

    @Test
    public void testScopeServiceInstance() {
        String query = "{resource.instance=\"backend_instance\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals("backend_instance", params.getServiceInstance());
    }

    @Test
    public void testDurationFilter() {
        String query = "{duration > 100ms}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals(100000L, params.getMinDuration()); // 100ms = 100000 microseconds
    }

    @Test
    public void testComplexQuery() {
        String query = "{.service.name=\"myservice\" && duration > 1s && .http.status_code=\"200\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals("myservice", params.getServiceName());
        assertEquals(1000000L, params.getMinDuration()); // 1s = 1000000 microseconds
        assertEquals("200", params.getHttpStatusCode());
    }

    @Test
    public void testHttpAttributes() {
        String query = "{.http.method=\"GET\" && .http.url=\"/api/test\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals("GET", params.getTags().get("http.method"));
        assertEquals("/api/test", params.getTags().get("http.url"));
    }

    @Test
    public void testScopedHttpAttributes() {
        // Test that span.http.method is stored as http.method (scope prefix removed)
        String query = "{span.http.method=\"POST\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals("POST", params.getTags().get("http.method"));
    }

    @Test
    public void testNameIntrinsicField() {
        String query = "{name=\"HTTP GET\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals("HTTP GET", params.getSpanName());
    }

    @Test
    public void testSpanName() {
        String query = "{span.name=\"HTTP GET\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);
        assertEquals("HTTP GET", params.getSpanName());
    }

    @Test
    public void testComplexQueryWithAllFields() {
        // Test the exact query from user:
        // {span.http.method="GET" && resource.service.name="frontend" && duration>100ms && name="HTTP GET" && duration<10ms && status="ok"}
        String query = "{span.http.method=\"GET\" && resource.service.name=\"frontend\" && duration>100ms && name=\"HTTP GET\" && duration<10ms && status=\"ok\"}";
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertFalse(result.hasError(), "Parse should succeed");
        TraceQLQueryParams params = result.getParams();
        assertNotNull(params);

        // Check service name
        assertEquals("frontend", params.getServiceName());

        // Check span name
        assertEquals("HTTP GET", params.getSpanName());

        // Check duration (both min and max should be set)
        assertEquals(100000L, params.getMinDuration()); // 100ms in microseconds
        assertEquals(10000L, params.getMaxDuration()); // 10ms in microseconds

        // Check status
        assertEquals("ok", params.getStatus());

        // Check http.method tag
        assertEquals("GET", params.getTags().get("http.method"));
    }
}
