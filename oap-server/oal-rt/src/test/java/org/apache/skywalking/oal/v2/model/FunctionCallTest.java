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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FunctionCallTest {

    /**
     * Test function call with no arguments.
     *
     * Input:
     *   - functionName: "longAvg"
     *
     * Expected Output (YAML):
     *   FunctionCall:
     *     name: "longAvg"
     *     arguments: []
     *     toString: "longAvg()"
     */
    @Test
    public void testNoArguments() {
        FunctionCall func = FunctionCall.of("longAvg");

        assertEquals("longAvg", func.getName());
        assertTrue(func.getArguments().isEmpty());
        assertEquals("longAvg()", func.toString());
    }

    /**
     * Test function call with single literal argument.
     *
     * Input:
     *   - functionName: "percentile2"
     *   - literals: [10]
     *
     * Expected Output (YAML):
     *   FunctionCall:
     *     name: "percentile2"
     *     arguments:
     *       - type: LITERAL
     *         value: 10
     *     toString: "percentile2(10)"
     */
    @Test
    public void testLiteralArguments() {
        FunctionCall func = FunctionCall.ofLiterals("percentile2", 10);

        assertEquals("percentile2", func.getName());
        assertEquals(1, func.getArguments().size());

        FunctionArgument arg = func.getArguments().get(0);
        assertTrue(arg.isLiteral());
        assertEquals(10, arg.asLiteral());
        assertEquals("percentile2(10)", func.toString());
    }

    /**
     * Test function call with multiple literal arguments.
     *
     * Input:
     *   - functionName: "histogram"
     *   - literals: [100, 20]
     *
     * Expected Output (YAML):
     *   FunctionCall:
     *     name: "histogram"
     *     arguments:
     *       - type: LITERAL
     *         value: 100
     *       - type: LITERAL
     *         value: 20
     *     toString: "histogram(100, 20)"
     */
    @Test
    public void testMultipleLiterals() {
        FunctionCall func = FunctionCall.ofLiterals("histogram", 100, 20);

        assertEquals("histogram", func.getName());
        assertEquals(2, func.getArguments().size());

        assertEquals(100, func.getArguments().get(0).asLiteral());
        assertEquals(20, func.getArguments().get(1).asLiteral());
        assertEquals("histogram(100, 20)", func.toString());
    }

    /**
     * Test function call with attribute arguments (field references).
     *
     * Input:
     *   - functionName: "apdex"
     *   - attributes: ["name", "status"]
     *
     * Expected Output (YAML):
     *   FunctionCall:
     *     name: "apdex"
     *     arguments:
     *       - type: ATTRIBUTE
     *         value: "name"
     *       - type: ATTRIBUTE
     *         value: "status"
     */
    @Test
    public void testAttributeArguments() {
        FunctionCall func = FunctionCall.builder()
            .name("apdex")
            .addAttribute("name")
            .addAttribute("status")
            .build();

        assertEquals("apdex", func.getName());
        assertEquals(2, func.getArguments().size());

        FunctionArgument arg1 = func.getArguments().get(0);
        assertTrue(arg1.isAttribute());
        assertEquals("name", arg1.asAttribute());

        FunctionArgument arg2 = func.getArguments().get(1);
        assertTrue(arg2.isAttribute());
        assertEquals("status", arg2.asAttribute());
    }

    /**
     * Test function call with mixed argument types (expression + attribute).
     *
     * Input:
     *   - functionName: "rate"
     *   - expression: FilterExpression(status == true)
     *   - attribute: "count"
     *
     * Expected Output (YAML):
     *   FunctionCall:
     *     name: "rate"
     *     arguments:
     *       - type: EXPRESSION
     *         value:
     *           fieldName: "status"
     *           operator: EQUAL
     *           value: true
     *       - type: ATTRIBUTE
     *         value: "count"
     */
    @Test
    public void testMixedArguments() {
        FilterExpression expr = FilterExpression.of("status", "==", true);

        FunctionCall func = FunctionCall.builder()
            .name("rate")
            .addExpression(expr)
            .addAttribute("count")
            .build();

        assertEquals("rate", func.getName());
        assertEquals(2, func.getArguments().size());

        FunctionArgument arg1 = func.getArguments().get(0);
        assertTrue(arg1.isExpression());
        assertEquals(expr, arg1.asExpression());

        FunctionArgument arg2 = func.getArguments().get(1);
        assertTrue(arg2.isAttribute());
        assertEquals("count", arg2.asAttribute());
    }

    /**
     * Test type safety enforcement for FunctionArgument.
     *
     * Input:
     *   - literal: FunctionArgument.literal(10)
     *   - attribute: FunctionArgument.attribute("field")
     *
     * Expected Behavior:
     *   - literal.isLiteral() = true
     *   - literal.asAttribute() throws IllegalStateException
     *   - literal.asExpression() throws IllegalStateException
     *   - attribute.isAttribute() = true
     *   - attribute.asLiteral() throws IllegalStateException
     *   - attribute.asExpression() throws IllegalStateException
     */
    @Test
    public void testFunctionArgumentTypeSafety() {
        FunctionArgument literal = FunctionArgument.literal(10);
        assertTrue(literal.isLiteral());
        assertFalse(literal.isAttribute());
        assertFalse(literal.isExpression());

        assertThrows(IllegalStateException.class, literal::asAttribute);
        assertThrows(IllegalStateException.class, literal::asExpression);

        FunctionArgument attribute = FunctionArgument.attribute("field");
        assertTrue(attribute.isAttribute());
        assertFalse(attribute.isLiteral());

        assertThrows(IllegalStateException.class, attribute::asLiteral);
        assertThrows(IllegalStateException.class, attribute::asExpression);
    }

    /**
     * Test equality comparison for FunctionCall objects.
     *
     * Input:
     *   - func1: FunctionCall("percentile2", [10])
     *   - func2: FunctionCall("percentile2", [10])
     *   - func3: FunctionCall("percentile2", [20])
     *   - func4: FunctionCall("count", [])
     *
     * Expected Behavior:
     *   - func1.equals(func2) = true (same name and arguments)
     *   - func1.equals(func3) = false (different argument values)
     *   - func1.equals(func4) = false (different names)
     */
    @Test
    public void testEquality() {
        FunctionCall func1 = FunctionCall.ofLiterals("percentile2", 10);
        FunctionCall func2 = FunctionCall.ofLiterals("percentile2", 10);
        FunctionCall func3 = FunctionCall.ofLiterals("percentile2", 20);
        FunctionCall func4 = FunctionCall.of("count");

        assertEquals(func1, func2);
        assertNotEquals(func1, func3);
        assertNotEquals(func1, func4);
    }

    /**
     * Test hashCode consistency for equal FunctionCall objects.
     *
     * Input:
     *   - func1: FunctionCall("percentile2", [10])
     *   - func2: FunctionCall("percentile2", [10])
     *
     * Expected Behavior:
     *   - func1.hashCode() == func2.hashCode()
     */
    @Test
    public void testHashCode() {
        FunctionCall func1 = FunctionCall.ofLiterals("percentile2", 10);
        FunctionCall func2 = FunctionCall.ofLiterals("percentile2", 10);

        assertEquals(func1.hashCode(), func2.hashCode());
    }
}
