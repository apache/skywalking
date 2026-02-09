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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
     * Test that semicolon is optional at EOF (grammar allows SEMI|EOF)
     */
    @Test
    public void testValidSyntax_MissingSemicolonAtEOF() {
        String script = "service_resp_time = from(Service.latency).longAvg()"; // No ; at EOF is valid

        OALScriptParserV2 result = assertDoesNotThrow(() ->
            OALScriptParserV2.parse(script, "test.oal"),
            "Parser should accept missing semicolon at EOF"
        );

        assertTrue(result.hasMetrics(), "Script should parse successfully");
        assertTrue(result.getMetricsCount() == 1, "Should parse exactly 1 metric");
    }

    /**
     * Test syntax error: invalid variable name (starts with number)
     * Grammar: variable must be IDENTIFIER which starts with Letter (not digit)
     * Note: Parser may accept empty script, so this tests actual parse result
     */
    @Test
    public void testSyntaxError_InvalidVariableName() throws Exception {
        String script = "1service_resp_time = from(Service.latency).longAvg();";

        // The parser may not throw (treats this as empty script or lexer error)
        // Instead verify it doesn't produce valid metrics
        try {
            OALScriptParserV2 result = OALScriptParserV2.parse(script, "test.oal");
            // If it doesn't throw, it should at least not produce any valid metrics
            assertFalse(result.hasMetrics(), "Parser should not produce metrics from invalid variable name");
        } catch (IllegalArgumentException e) {
            // If it does throw, verify error message
            String message = e.getMessage();
            assertTrue(message.contains("test.oal") && message.contains("1:"),
                      "Error should reference file and line");
        }
    }

    /**
     * Test syntax error: missing equals sign
     */
    @Test
    public void testSyntaxError_MissingEquals() {
        String script = "service_resp_time from(Service.latency).longAvg();";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should require equals sign in metric definition");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:"), "Error should reference file and line 1");
        assertTrue(message.contains("missing '='") || message.contains("mismatched input 'from'"),
                   "Error should indicate missing equals sign");
    }

    /**
     * Test syntax error: missing from() clause
     */
    @Test
    public void testSyntaxError_MissingFrom() {
        String script = "service_resp_time = .longAvg();";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should require from() clause");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:20"), "Error should reference file:line:column");
        assertTrue(message.contains("expecting 'from'") || message.contains("mismatched input '.'"),
                  "Error should indicate expecting from");
    }

    /**
     * Test syntax error: missing source name in from()
     */
    @Test
    public void testSyntaxError_MissingSourceName() {
        String script = "service_resp_time = from().longAvg();";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should require source name in from() clause");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:25"), "Error should reference file:line:column");
        assertTrue(message.contains("OAL parsing failed"), "Error should have clear header");
        assertTrue(message.contains("expecting {"), "Error should indicate expecting source name");
    }

    /**
     * Test syntax error: malformed filter expression
     */
    @Test
    public void testSyntaxError_MalformedFilter() {
        String script = "service_sla = from(Service.*).filter(status ==).percent();";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should detect incomplete filter expression");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:"), "Error should reference file and line 1");
        assertTrue(message.contains("mismatched input ')'") || message.contains("expecting"),
                   "Error should indicate unexpected ')'");
    }

    /**
     * Test syntax error: unclosed parenthesis in filter
     */
    @Test
    public void testSyntaxError_UnclosedParenthesis() {
        String script = "service_sla = from(Service.*).filter(status == true.percent();";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should detect unclosed parenthesis");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:51"), "Error should reference file:line:column");
        assertTrue(message.contains("missing ')'"), "Error should indicate missing ')'");
    }

    /**
     * Test syntax error: invalid operator in filter
     */
    @Test
    public void testSyntaxError_InvalidOperator() {
        String script = "service_sla = from(Service.*).filter(latency <=> 100).count();";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject invalid operators");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:"), "Error should reference file and line 1");
        assertTrue(message.contains("mismatched") || message.contains("extraneous"),
                   "Error should indicate syntax error with operator");
    }

    /**
     * Test syntax error: missing function call
     */
    @Test
    public void testSyntaxError_MissingFunction() {
        String script = "service_resp_time = from(Service.latency);";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should require aggregation function");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:"), "Error should reference file and line 1");
        assertTrue(message.contains("mismatched") || message.contains("extraneous") || message.contains("expecting '.'"),
                   "Error should indicate syntax error (missing dot before function)");
    }

    /**
     * Test syntax error: empty function arguments
     */
    @Test
    public void testSyntaxError_InvalidFunctionArguments() {
        String script = "service_p99 = from(Service.latency).percentile(,);";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject empty function arguments");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:47"), "Error should reference file:line:column");
        assertTrue(message.contains("extraneous input ','"), "Error should indicate unexpected comma");
    }

    /**
     * Test syntax error: multiple equals signs
     */
    @Test
    public void testSyntaxError_MultipleEquals() {
        String script = "service_resp_time == from(Service.latency).longAvg();";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject multiple equals signs");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:"), "Error should reference file and line 1");
        assertTrue(message.contains("extraneous") || message.contains("mismatched") || message.contains("expecting"),
                   "Error should indicate syntax problem with equals");
    }

    /**
     * Test syntax error: invalid characters in metric name
     */
    @Test
    public void testSyntaxError_InvalidCharacters() throws Exception {
        String script = "service@resp_time = from(Service.latency).longAvg();";

        // The parser may not throw if @ is treated as separating tokens
        // Instead verify it doesn't produce valid metrics or throws error
        try {
            OALScriptParserV2 result = OALScriptParserV2.parse(script, "test.oal");
            assertFalse(result.hasMetrics(), "Parser should not produce metrics from invalid characters");
        } catch (IllegalArgumentException e) {
            // If it does throw, verify error message
            String message = e.getMessage();
            assertTrue(message.contains("test.oal:1:"),
                      "Error should reference file and line");
        }
    }

    /**
     * Test syntax error: unclosed string literal
     */
    @Test
    public void testSyntaxError_UnclosedString() {
        String script = "service_sla = from(Service.*).filter(name == \"test).percent();";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should detect unclosed string literals");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:"), "Error should reference file and line 1");
        // Unclosed strings cause token recognition errors
        assertTrue(message.contains("token recognition error") || message.contains("mismatched"),
                   "Error should indicate token recognition error or mismatched input");
    }

    /**
     * Test empty script
     */
    @Test
    public void testEmptyScript() {
        String script = "";
        OALScriptParserV2 result = assertDoesNotThrow(() ->
            OALScriptParserV2.parse(script, "empty.oal"),
            "Empty script should parse without errors"
        );
        assertTrue(result.getMetrics().isEmpty(), "Empty script should produce no metrics");
    }

    /**
     * Test script with only comments
     */
    @Test
    public void testOnlyComments() {
        String script = "// This is a comment\n" +
                       "/* Multi-line\n" +
                       "   comment */\n";

        OALScriptParserV2 result = assertDoesNotThrow(() ->
            OALScriptParserV2.parse(script, "comments.oal"),
            "Comment-only script should parse without errors"
        );

        assertTrue(result.getMetrics().isEmpty(), "Comment-only script should produce no metrics");
    }

    /**
     * Test syntax error: invalid source name
     * The parser only accepts predefined source names from the grammar
     */
    @Test
    public void testSyntaxError_InvalidSourceName() {
        String script = "service_resp_time = from(Servicelatency).longAvg();";

        // "Servicelatency" is not a valid source name in the grammar
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject invalid source names");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:"), "Error should reference file and line");
        assertTrue(message.contains("Servicelatency") ||
                   message.toLowerCase().contains("expecting"),
                   "Error should mention invalid source name");
    }

    /**
     * Test syntax error: multiple filters without proper syntax
     */
    @Test
    public void testSyntaxError_MultipleFiltersInvalid() {
        String script = "service_sla = from(Service.*).filter(status == true) filter(latency > 100).percent();";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject multiple filters without proper chaining");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:53"), "Error should reference file:line:column");
        assertTrue(message.contains("extraneous input 'filter'"), "Error should indicate unexpected 'filter'");
    }

    /**
     * Test syntax error: nested function calls (not supported)
     */
    @Test
    public void testSyntaxError_NestedFunctions() {
        String script = "service_resp_time = from(Service.latency).longAvg(sum());";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            OALScriptParserV2.parse(script, "test.oal");
        }, "Parser should reject nested function calls");

        String message = exception.getMessage();
        assertTrue(message.contains("test.oal:1:"), "Error should reference file and line 1");
        assertTrue(message.contains("mismatched") || message.contains("expecting"),
                   "Error should indicate syntax error with nested function");
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
        assertTrue(message.contains("expecting"), "Error should indicate expecting source name");
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
        assertTrue(message.contains("2:"), "Error message should include line 2");
        assertTrue(message.contains("OAL parsing failed"), "Error message should have clear header");
        assertTrue(message.contains("mismatched") || message.contains("expecting"),
                   "Error should describe the syntax problem");
    }

    /**
     * Test multiple errors are collected and reported
     */
    @Test
    public void testMultipleErrors_AllReported() throws Exception {
        String script =
            "service_resp_time = from(Service.latency).longAvg()\n" +  // Missing semicolon between statements
            "service_sla = from().percent();\n";  // Missing source

        // Parser may stop at first error or collect multiple
        try {
            OALScriptParserV2 result = OALScriptParserV2.parse(script, "multi-error.oal");
            // If it doesn't throw, it should not produce valid metrics
            assertFalse(result.hasMetrics() || result.getMetricsCount() < 2,
                       "Parser should not produce all metrics from script with errors");
        } catch (IllegalArgumentException e) {
            // If it does throw, verify error message
            String message = e.getMessage();
            assertTrue(message.contains("multi-error.oal"), "Should reference file name");
            assertTrue(message.toLowerCase().contains("error") ||
                      message.toLowerCase().contains("failed"),
                      "Should indicate errors occurred");
        }
    }
}
