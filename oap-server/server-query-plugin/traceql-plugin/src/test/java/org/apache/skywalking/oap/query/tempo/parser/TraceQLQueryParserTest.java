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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void testScopedSpanIntrinsics() {
        TraceQLParseResult result = TraceQLQueryParser.extractParams("{span:kind=server && span:duration>100ms && span:name=\"GET /\"}");
        assertFalse(result.hasError(), "Parse should succeed: " + result.getErrorInfo());
        TraceQLQueryParams params = result.getParams();
        assertEquals("server", params.getKind());
        assertEquals(100_000L, params.getMinDuration());
        assertEquals("GET /", params.getSpanName());
    }

    @Test
    public void testOtherScopedIntrinsicsAreRejected() {
        TraceQLParseResult result = TraceQLQueryParser.extractParams("{trace:duration>1s}");
        assertTrue(result.hasError(), "trace: intrinsics have no column to filter on");
        assertTrue(result.getErrorInfo().contains("trace:duration"), result.getErrorInfo());
    }

    @Test
    public void testKeywordLiteralsForKindAndStatus() {
        TraceQLParseResult result = TraceQLQueryParser.extractParams("{kind=server && status=error}");
        assertFalse(result.hasError(), result.getErrorInfo());
        assertEquals("server", result.getParams().getKind());
        assertEquals("error", result.getParams().getStatus());
        result = TraceQLQueryParser.extractParams("{status=\"OK\"}");
        assertFalse(result.hasError(), result.getErrorInfo());
        assertEquals("ok", result.getParams().getStatus());
    }

    @Test
    public void testSyntaxErrorsAreRefusedInsteadOfRecovered() {
        // every one of these used to parse "successfully" with the offending part dropped (#14093)
        String[] queries = {
            "{(resource.service.name=\"a\" || resource.service.name=\"b\")}",
            "{resource.service.name=\"a\" || resource.service.name=\"b\"}",
            "{span.http.method=~\"G.*\"}",
            "{http.method=\"NOPE\"}",
            "{service.name=\"skywalking\"}",
            "{event.name=\"nope\"}",
            "{ this is not traceql"
        };
        for (String query : queries) {
            TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
            assertTrue(result.hasError(), query);
            assertTrue(result.getErrorInfo().startsWith("Invalid TraceQL: line 1:"), result.getErrorInfo());
        }
    }

    @Test
    public void testOrInsideASpansetNamesTheConstruct() {
        TraceQLParseResult result = TraceQLQueryParser.extractParams(
            "{(resource.service.name=\"songs\" || resource.service.name=\"rating\")}");
        assertTrue(result.hasError());
        assertTrue(result.getErrorInfo().contains("OR (||) is not supported"), result.getErrorInfo());
    }

    @Test
    public void testUnsupportedConstructsAreRefused() {
        assertRefused("{span.http.method!=\"GET\"}", "Unsupported operator !=");
        assertRefused("{status!=error}", "Unsupported operator !=");
        assertRefused("{name>\"a\"}", "Unsupported operator >");
        assertRefused("{duration=99999ms}", "Unsupported operator = on duration");
        assertRefused("{status=\"STATUS_CODE_ERROR\"}", "Unsupported status value");
        assertRefused("{kind=SERVERISH}", "Unsupported kind value");
        assertRefused("{rootName=\"x\"}", "Unsupported intrinsic rootName");
        assertRefused("{!(span.http.method=\"GET\")}", "Negation");
        assertRefused("{span.nonexistent.tag}", "existence");
        assertRefused("{resource.service.name=\"a\"} && {span.http.method=\"NOPE\"}", "Multiple spansets");
        assertRefused("{resource.service.name=\"a\"} || {resource.service.name=\"b\"}", "Multiple spansets");
    }

    private static void assertRefused(String query, String reason) {
        TraceQLParseResult result = TraceQLQueryParser.extractParams(query);
        assertTrue(result.hasError(), query + " should be refused");
        assertTrue(result.getErrorInfo().contains(reason), query + " -> " + result.getErrorInfo());
    }
}

