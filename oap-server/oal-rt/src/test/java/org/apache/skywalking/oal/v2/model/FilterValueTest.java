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
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterValueTest {

    /**
     * Test FilterValue with long number type.
     *
     * Input:
     *   - number: 100L
     *
     * Expected Output (YAML):
     *   FilterValue:
     *     type: NUMBER
     *     value: 100
     *     asLong: 100
     *     asDouble: 100.0
     *     toString: "100"
     */
    @Test
    public void testNumberValueLong() {
        FilterValue value = FilterValue.ofNumber(100L);

        assertTrue(value.isNumber());
        assertFalse(value.isString());
        assertFalse(value.isBoolean());
        assertFalse(value.isNull());
        assertFalse(value.isArray());

        assertEquals(100L, value.asLong());
        assertEquals(100.0, value.asDouble(), 0.001);
        assertEquals("100", value.toString());
    }

    /**
     * Test FilterValue with double number type.
     *
     * Input:
     *   - number: 99.5
     *
     * Expected Output (YAML):
     *   FilterValue:
     *     type: NUMBER
     *     value: 99.5
     *     asDouble: 99.5
     *     asLong: 99 (truncated)
     *     toString: "99.5"
     */
    @Test
    public void testNumberValueDouble() {
        FilterValue value = FilterValue.ofNumber(99.5);

        assertTrue(value.isNumber());
        assertEquals(99.5, value.asDouble(), 0.001);
        assertEquals(99L, value.asLong());
        assertEquals("99.5", value.toString());
    }

    /**
     * Test FilterValue with string type.
     *
     * Input:
     *   - string: "test"
     *
     * Expected Output (YAML):
     *   FilterValue:
     *     type: STRING
     *     value: "test"
     *     asString: "test"
     *     toString: "\"test\""
     */
    @Test
    public void testStringValue() {
        FilterValue value = FilterValue.ofString("test");

        assertTrue(value.isString());
        assertFalse(value.isNumber());
        assertEquals("test", value.asString());
        assertEquals("\"test\"", value.toString());
    }

    /**
     * Test FilterValue with boolean type (true and false).
     *
     * Input:
     *   - boolean: true
     *   - boolean: false
     *
     * Expected Output (YAML):
     *   trueValue:
     *     type: BOOLEAN
     *     value: true
     *     asBoolean: true
     *     toString: "true"
     *
     *   falseValue:
     *     type: BOOLEAN
     *     value: false
     *     asBoolean: false
     *     toString: "false"
     */
    @Test
    public void testBooleanValue() {
        FilterValue trueValue = FilterValue.ofBoolean(true);
        FilterValue falseValue = FilterValue.ofBoolean(false);

        assertTrue(trueValue.isBoolean());
        assertTrue(trueValue.asBoolean());
        assertEquals("true", trueValue.toString());

        assertTrue(falseValue.isBoolean());
        assertFalse(falseValue.asBoolean());
        assertEquals("false", falseValue.toString());
    }

    /**
     * Test FilterValue with null type.
     *
     * Input:
     *   - null value
     *
     * Expected Output (YAML):
     *   FilterValue:
     *     type: NULL
     *     value: null
     *     toString: "null"
     */
    @Test
    public void testNullValue() {
        FilterValue value = FilterValue.ofNull();

        assertTrue(value.isNull());
        assertFalse(value.isNumber());
        assertFalse(value.isString());
        assertEquals("null", value.toString());
    }

    /**
     * Test FilterValue with array type.
     *
     * Input:
     *   - array: [404L, 500L, 503L]
     *
     * Expected Output (YAML):
     *   FilterValue:
     *     type: ARRAY
     *     value: [404, 500, 503]
     *     asArray:
     *       - 404
     *       - 500
     *       - 503
     */
    @Test
    public void testArrayValue() {
        List<Long> numbers = Arrays.asList(404L, 500L, 503L);
        FilterValue value = FilterValue.ofArray(numbers);

        assertTrue(value.isArray());
        assertFalse(value.isNumber());

        List<?> array = value.asArray();
        assertEquals(3, array.size());
        assertEquals(404L, array.get(0));
        assertEquals(500L, array.get(1));
        assertEquals(503L, array.get(2));
    }

    /**
     * Test type safety enforcement for NUMBER type.
     *
     * Input:
     *   - number: 100L
     *
     * Expected Behavior:
     *   - value.asString() throws IllegalStateException
     *   - value.asBoolean() throws IllegalStateException
     *   - value.asArray() throws IllegalStateException
     */
    @Test
    public void testTypeSafetyNumber() {
        FilterValue value = FilterValue.ofNumber(100L);

        assertThrows(IllegalStateException.class, value::asString);
        assertThrows(IllegalStateException.class, value::asBoolean);
        assertThrows(IllegalStateException.class, value::asArray);
    }

    /**
     * Test type safety enforcement for STRING type.
     *
     * Input:
     *   - string: "test"
     *
     * Expected Behavior:
     *   - value.asNumber() throws IllegalStateException
     *   - value.asBoolean() throws IllegalStateException
     *   - value.asArray() throws IllegalStateException
     */
    @Test
    public void testTypeSafetyString() {
        FilterValue value = FilterValue.ofString("test");

        assertThrows(IllegalStateException.class, value::asNumber);
        assertThrows(IllegalStateException.class, value::asBoolean);
        assertThrows(IllegalStateException.class, value::asArray);
    }

    /**
     * Test equality comparison for FilterValue objects.
     *
     * Input:
     *   - num1: FilterValue(100L)
     *   - num2: FilterValue(100L)
     *   - num3: FilterValue(200L)
     *   - str1: FilterValue("test")
     *   - str2: FilterValue("test")
     *   - str3: FilterValue("other")
     *
     * Expected Behavior:
     *   - num1.equals(num2) = true (same type and value)
     *   - num1.equals(num3) = false (different values)
     *   - str1.equals(str2) = true (same type and value)
     *   - str1.equals(str3) = false (different values)
     *   - num1.equals(str1) = false (different types)
     */
    @Test
    public void testEquality() {
        FilterValue num1 = FilterValue.ofNumber(100L);
        FilterValue num2 = FilterValue.ofNumber(100L);
        FilterValue num3 = FilterValue.ofNumber(200L);

        assertEquals(num1, num2);
        assertNotEquals(num1, num3);

        FilterValue str1 = FilterValue.ofString("test");
        FilterValue str2 = FilterValue.ofString("test");
        FilterValue str3 = FilterValue.ofString("other");

        assertEquals(str1, str2);
        assertNotEquals(str1, str3);
        assertNotEquals(num1, str1);
    }

    /**
     * Test hashCode consistency for equal FilterValue objects.
     *
     * Input:
     *   - num1: FilterValue(100L)
     *   - num2: FilterValue(100L)
     *   - str1: FilterValue("test")
     *   - str2: FilterValue("test")
     *
     * Expected Behavior:
     *   - num1.hashCode() == num2.hashCode()
     *   - str1.hashCode() == str2.hashCode()
     */
    @Test
    public void testHashCode() {
        FilterValue num1 = FilterValue.ofNumber(100L);
        FilterValue num2 = FilterValue.ofNumber(100L);

        assertEquals(num1.hashCode(), num2.hashCode());

        FilterValue str1 = FilterValue.ofString("test");
        FilterValue str2 = FilterValue.ofString("test");

        assertEquals(str1.hashCode(), str2.hashCode());
    }
}
