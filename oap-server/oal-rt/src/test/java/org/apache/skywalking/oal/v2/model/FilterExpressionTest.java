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

package org.apache.skywalking.oal.v2.model;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterExpressionTest {

    /**
     * Test filter expression with number value.
     * OAL example: filter(latency > 100)
     *
     * Input:
     *   - fieldName: "latency"
     *   - operator: GREATER (>)
     *   - value: 100L
     *
     * Expected Output (YAML):
     *   FilterExpression:
     *     fieldName: "latency"
     *     operator: GREATER
     *     value:
     *       type: NUMBER
     *       value: 100
     *     toString: "latency > 100"
     */
    @Test
    public void testNumberFilter() {
        FilterExpression filter = FilterExpression.builder()
            .fieldName("latency")
            .operator(FilterOperator.GREATER)
            .numberValue(100L)
            .build();

        assertEquals("latency", filter.getFieldName());
        assertEquals(FilterOperator.GREATER, filter.getOperator());
        assertTrue(filter.getValue().isNumber());
        assertEquals(100L, filter.getValue().asLong());
        assertEquals("latency > 100", filter.toString());
    }

    /**
     * Test filter expression with boolean value.
     * OAL example: filter(status == true)
     *
     * Input:
     *   - fieldName: "status"
     *   - operator: EQUAL (==)
     *   - value: true
     *
     * Expected Output (YAML):
     *   FilterExpression:
     *     fieldName: "status"
     *     operator: EQUAL
     *     value:
     *       type: BOOLEAN
     *       value: true
     *     toString: "status == true"
     */
    @Test
    public void testBooleanFilter() {
        FilterExpression filter = FilterExpression.builder()
            .fieldName("status")
            .operator(FilterOperator.EQUAL)
            .booleanValue(true)
            .build();

        assertEquals("status", filter.getFieldName());
        assertEquals(FilterOperator.EQUAL, filter.getOperator());
        assertTrue(filter.getValue().isBoolean());
        assertTrue(filter.getValue().asBoolean());
        assertEquals("status == true", filter.toString());
    }

    /**
     * Test filter expression with string value and LIKE operator.
     * OAL example: filter(name like "serv%")
     *
     * Input:
     *   - fieldName: "name"
     *   - operator: LIKE
     *   - value: "serv%"
     *
     * Expected Output (YAML):
     *   FilterExpression:
     *     fieldName: "name"
     *     operator: LIKE
     *     value:
     *       type: STRING
     *       value: "serv%"
     *     toString: "name like \"serv%\""
     */
    @Test
    public void testStringFilter() {
        FilterExpression filter = FilterExpression.builder()
            .fieldName("name")
            .operator(FilterOperator.LIKE)
            .stringValue("serv%")
            .build();

        assertEquals("name", filter.getFieldName());
        assertEquals(FilterOperator.LIKE, filter.getOperator());
        assertTrue(filter.getValue().isString());
        assertEquals("serv%", filter.getValue().asString());
        assertEquals("name like \"serv%\"", filter.toString());
    }

    /**
     * Test filter expression with array value for IN operator.
     * OAL example: filter(code in [404, 500, 503])
     *
     * Input:
     *   - fieldName: "code"
     *   - operator: IN
     *   - value: [404L, 500L, 503L]
     *
     * Expected Output (YAML):
     *   FilterExpression:
     *     fieldName: "code"
     *     operator: IN
     *     value:
     *       type: ARRAY
     *       value: [404, 500, 503]
     */
    @Test
    public void testArrayFilter() {
        FilterExpression filter = FilterExpression.builder()
            .fieldName("code")
            .operator(FilterOperator.IN)
            .arrayValue(Arrays.asList(404L, 500L, 503L))
            .build();

        assertEquals("code", filter.getFieldName());
        assertEquals(FilterOperator.IN, filter.getOperator());
        assertTrue(filter.getValue().isArray());
        assertEquals(3, filter.getValue().asArray().size());
    }

    /**
     * Test filter expression with null value.
     * OAL example: filter(tag != null)
     *
     * Input:
     *   - fieldName: "tag"
     *   - operator: NOT_EQUAL (!=)
     *   - value: null
     *
     * Expected Output (YAML):
     *   FilterExpression:
     *     fieldName: "tag"
     *     operator: NOT_EQUAL
     *     value:
     *       type: NULL
     *       value: null
     *     toString: "tag != null"
     */
    @Test
    public void testNullFilter() {
        FilterExpression filter = FilterExpression.builder()
            .fieldName("tag")
            .operator(FilterOperator.NOT_EQUAL)
            .nullValue()
            .build();

        assertEquals("tag", filter.getFieldName());
        assertEquals(FilterOperator.NOT_EQUAL, filter.getOperator());
        assertTrue(filter.getValue().isNull());
        assertEquals("tag != null", filter.toString());
    }

    /**
     * Test shorthand creation of filter expressions using static factory method.
     * Demonstrates auto-detection of value types.
     *
     * Input Cases:
     *   1. ("latency", ">", 100L)
     *   2. ("status", "==", true)
     *   3. ("name", "like", "test%")
     *
     * Expected Output (YAML):
     *   filter1:
     *     fieldName: "latency"
     *     operator: GREATER
     *     value: {type: NUMBER, value: 100}
     *
     *   filter2:
     *     fieldName: "status"
     *     operator: EQUAL
     *     value: {type: BOOLEAN, value: true}
     *
     *   filter3:
     *     fieldName: "name"
     *     operator: LIKE
     *     value: {type: STRING, value: "test%"}
     */
    @Test
    public void testShorthandCreation() {
        FilterExpression filter1 = FilterExpression.of("latency", ">", 100L);
        assertEquals("latency", filter1.getFieldName());
        assertEquals(FilterOperator.GREATER, filter1.getOperator());
        assertEquals(100L, filter1.getValue().asLong());

        FilterExpression filter2 = FilterExpression.of("status", "==", true);
        assertEquals("status", filter2.getFieldName());
        assertTrue(filter2.getValue().asBoolean());

        FilterExpression filter3 = FilterExpression.of("name", "like", "test%");
        assertEquals("name", filter3.getFieldName());
        assertEquals("test%", filter3.getValue().asString());
    }

    /**
     * Test filter expression with source location tracking.
     *
     * Input:
     *   - fieldName: "latency"
     *   - operator: GREATER
     *   - value: 100L
     *   - location: SourceLocation("test.oal", line=10, column=5)
     *
     * Expected Output (YAML):
     *   FilterExpression:
     *     fieldName: "latency"
     *     operator: GREATER
     *     value: {type: NUMBER, value: 100}
     *     location:
     *       fileName: "test.oal"
     *       line: 10
     *       column: 5
     *       toString: "test.oal:10:5"
     */
    @Test
    public void testWithLocation() {
        SourceLocation location = SourceLocation.of("test.oal", 10, 5);

        FilterExpression filter = FilterExpression.builder()
            .fieldName("latency")
            .operator(FilterOperator.GREATER)
            .numberValue(100L)
            .location(location)
            .build();

        assertEquals(location, filter.getLocation());
        assertEquals("test.oal:10:5", filter.getLocation().toString());
    }

    /**
     * Test equality comparison for FilterExpression objects.
     *
     * Input:
     *   - filter1: FilterExpression("latency", GREATER, 100L)
     *   - filter2: FilterExpression("latency", GREATER, 100L)
     *   - filter3: FilterExpression("latency", GREATER, 200L)
     *
     * Expected Behavior:
     *   - filter1.equals(filter2) = true (same field, operator, and value)
     *   - filter1.equals(filter3) = false (different values)
     */
    @Test
    public void testEquality() {
        FilterExpression filter1 = FilterExpression.of("latency", ">", 100L);
        FilterExpression filter2 = FilterExpression.of("latency", ">", 100L);
        FilterExpression filter3 = FilterExpression.of("latency", ">", 200L);

        assertEquals(filter1, filter2);
        assertNotEquals(filter1, filter3);
    }

    /**
     * Test operator string conversion to FilterOperator enum.
     *
     * Input/Output Mapping:
     *   - "==" -> EQUAL
     *   - "!=" -> NOT_EQUAL
     *   - ">" -> GREATER
     *   - "<" -> LESS
     *   - ">=" -> GREATER_EQUAL
     *   - "<=" -> LESS_EQUAL
     *   - "like" -> LIKE
     *   - "in" -> IN
     *   - "contain" -> CONTAIN
     *   - "not contain" -> NOT_CONTAIN
     */
    @Test
    public void testOperatorConversion() {
        assertEquals(FilterOperator.EQUAL, FilterOperator.fromString("=="));
        assertEquals(FilterOperator.NOT_EQUAL, FilterOperator.fromString("!="));
        assertEquals(FilterOperator.GREATER, FilterOperator.fromString(">"));
        assertEquals(FilterOperator.LESS, FilterOperator.fromString("<"));
        assertEquals(FilterOperator.GREATER_EQUAL, FilterOperator.fromString(">="));
        assertEquals(FilterOperator.LESS_EQUAL, FilterOperator.fromString("<="));
        assertEquals(FilterOperator.LIKE, FilterOperator.fromString("like"));
        assertEquals(FilterOperator.IN, FilterOperator.fromString("in"));
        assertEquals(FilterOperator.CONTAIN, FilterOperator.fromString("contain"));
        assertEquals(FilterOperator.NOT_CONTAIN, FilterOperator.fromString("not contain"));
    }

    /**
     * Test error handling for invalid operator strings.
     *
     * Input:
     *   - operatorString: "invalid"
     *
     * Expected Behavior:
     *   - FilterOperator.fromString("invalid") throws IllegalArgumentException
     */
    @Test
    public void testInvalidOperator() {
        assertThrows(IllegalArgumentException.class, () -> {
            FilterOperator.fromString("invalid");
        });
    }
}
