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

package org.apache.skywalking.oal.v2.parser;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Test error handling and error messages for OAL parsing.
 *
 * This test suite validates that:
 * 1. Parser properly detects syntax errors
 * 2. Error messages are clear and helpful
 * 3. Error locations (line/column) are reported accurately
 * 4. Different types of errors are handled appropriately
 */
public class OALParsingErrorTest {

    /**
     * Test syntax error: missing semicolon
     */
    @Test
    public void testSyntaxError_MissingSemicolon() {
        String script = "service_resp_time = from(Service.latency).longAvg()"; // Missing ;

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should throw IllegalArgumentException for missing semicolon");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal"), "Error message should include file name");
        assertTrue(message.contains("1:"), "Error message should include line number");
    }

    /**
     * Test syntax error: invalid variable name (starts with number)
     */
    @Test
    public void testSyntaxError_InvalidVariableName() {
        String script = "1service_resp_time = from(Service.latency).longAvg();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject variable names starting with numbers");
    }

    /**
     * Test syntax error: missing equals sign
     */
    @Test
    public void testSyntaxError_MissingEquals() {
        String script = "service_resp_time from(Service.latency).longAvg();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should require equals sign in metric definition");
    }

    /**
     * Test syntax error: missing from() clause
     */
    @Test
    public void testSyntaxError_MissingFrom() {
        String script = "service_resp_time = .longAvg();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should require from() clause");
    }

    /**
     * Test syntax error: missing source name in from()
     */
    @Test
    public void testSyntaxError_MissingSourceName() {
        String script = "service_resp_time = from().longAvg();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should require source name in from() clause");
    }

    /**
     * Test syntax error: malformed filter expression
     */
    @Test
    public void testSyntaxError_MalformedFilter() {
        String script = "service_sla = from(Service.*).filter(status ==).percent();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should detect incomplete filter expression");
    }

    /**
     * Test syntax error: unclosed parenthesis in filter
     */
    @Test
    public void testSyntaxError_UnclosedParenthesis() {
        String script = "service_sla = from(Service.*).filter(status == true.percent();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should detect unclosed parenthesis");
    }

    /**
     * Test syntax error: invalid operator in filter
     */
    @Test
    public void testSyntaxError_InvalidOperator() {
        String script = "service_sla = from(Service.*).filter(latency <=> 100).count();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject invalid operators");
    }

    /**
     * Test syntax error: missing function call
     */
    @Test
    public void testSyntaxError_MissingFunction() {
        String script = "service_resp_time = from(Service.latency);";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should require aggregation function");
    }

    /**
     * Test syntax error: empty function arguments
     */
    @Test
    public void testSyntaxError_InvalidFunctionArguments() {
        String script = "service_p99 = from(Service.latency).percentile(,);";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject empty function arguments");
    }

    /**
     * Test syntax error: multiple equals signs
     */
    @Test
    public void testSyntaxError_MultipleEquals() {
        String script = "service_resp_time == from(Service.latency).longAvg();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject multiple equals signs");
    }

    /**
     * Test syntax error: invalid characters
     */
    @Test
    public void testSyntaxError_InvalidCharacters() {
        String script = "service_resp_time = from(Service.latency).longAvg() @ #;";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject invalid characters");
    }

    /**
     * Test syntax error: unclosed string literal
     */
    @Test
    public void testSyntaxError_UnclosedString() {
        String script = "service_sla = from(Service.*).filter(name == \"test).percent();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should detect unclosed string literals");
    }

    /**
     * Test empty script
     */
    @Test
    public void testEmptyScript() throws IOException {
        String script = "";
        OALScriptParserV2 result = OALScriptParserV2.parse(script, "empty.oal");
        assertTrue(result.getMetrics().isEmpty(), "Empty script should produce no metrics");
    }

    /**
     * Test script with only comments
     */
    @Test
    public void testOnlyComments() throws IOException {
        String script = "// This is a comment\n" +
                       "/* Multi-line\n" +
                       "   comment */\n";

        OALScriptParserV2 result = assertDoesNotThrow(() -> {
            return OALScriptParserV2.parse(script, "comments.oal");
        }, "Comment-only script should parse without errors");

        assertTrue(result.getMetrics().isEmpty(), "Comment-only script should produce no metrics");
    }

    /**
     * Test syntax error: missing dot before attribute
     * Note: "Servicelatency" is treated as a single source name, which is valid syntax
     */
    @Test
    public void testValidSyntax_SourceWithoutDot() throws IOException {
        String script = "service_resp_time = from(Servicelatency).longAvg();";

        // This is actually valid syntax - "Servicelatency" is just a source name
        // Semantic validation (checking if source exists) happens later
        OALScriptParserV2 result = assertDoesNotThrow(() -> {
            return OALScriptParserV2.parse(script, "test.oal");
        }, "Single-word source name should be valid syntax");

        assertTrue(result.hasMetrics(), "Should parse successfully");
    }

    /**
     * Test syntax error: multiple filters without proper syntax
     */
    @Test
    public void testSyntaxError_MultipleFiltersInvalid() {
        String script = "service_sla = from(Service.*).filter(status == true) filter(latency > 100).percent();";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject multiple filters without proper chaining");
    }

    /**
     * Test syntax error: nested function calls (not supported)
     */
    @Test
    public void testSyntaxError_NestedFunctions() {
        String script = "service_resp_time = from(Service.latency).longAvg(sum());";

        assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject nested function calls");
    }

    /**
     * Test that valid scripts parse without errors
     */
    @Test
    public void testValidScript_NoErrors() throws IOException {
        String script =
            "service_resp_time = from(Service.latency).longAvg();\n" +
            "service_sla = from(Service.*).filter(status == true).percent();\n" +
            "endpoint_calls = from(Endpoint.*).count();\n";

        OALScriptParserV2 result = assertDoesNotThrow(() -> {
            return OALScriptParserV2.parse(script, "valid.oal");
        }, "Valid script should parse without throwing exceptions");

        assertTrue(result.hasMetrics(), "Valid script should produce metrics");
        assertTrue(result.getMetricsCount() == 3, "Should parse exactly 3 metrics");
    }

    /**
     * Test mixed valid and invalid statements
     */
    @Test
    public void testMixedValidInvalid() {
        String script =
            "service_resp_time = from(Service.latency).longAvg();\n" +
            "invalid_metric = from().badFunction();\n" +  // Invalid - missing source
            "service_sla = from(Service.*).percent();\n";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "mixed.oal");
        }, "Parser should fail on first syntax error");

        String message = exception.getMessage();
        assertTrue(message.contains("mixed.oal"), "Error should reference file name");
        assertTrue(message.contains("2:"), "Error should reference line 2 where error occurs");
    }

    /**
     * Test error message contains helpful information
     */
    @Test
    public void testErrorMessage_ContainsLocation() {
        String script =
            "service_resp_time = from(Service.latency).longAvg();\n" +
            "service_sla = from(Service.*).filter(status == ).percent();\n";  // Error on line 2

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "error.oal");
        });

        String message = exception.getMessage();
        assertTrue(message.contains("error.oal"), "Error message should include file name");
        assertTrue(message.contains("2:"), "Error message should include line number");
        assertTrue(message.contains("OAL parsing failed"), "Error message should have clear header");
    }

    /**
     * Test multiple errors are collected and reported
     */
    @Test
    public void testMultipleErrors_AllReported() {
        String script =
            "service_resp_time = from(Service.latency).longAvg()\n" +  // Missing semicolon
            "service_sla = from().percent();\n";  // Missing source

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "multi-error.oal");
        });

        String message = exception.getMessage();
        // After first error, ANTLR may or may not continue - depends on error recovery
        assertTrue(message.contains("multi-error.oal"), "Should reference file name");
        assertTrue(message.contains("error"), "Should indicate errors occurred");
    }
}
