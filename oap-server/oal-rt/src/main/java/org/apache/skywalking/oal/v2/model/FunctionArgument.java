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

import java.util.Objects;
import lombok.Getter;

/**
 * Represents a typed argument to a function call.
 *
 * Function arguments can be:
 * - LITERAL: Constant values (numbers, strings, booleans)
 * - ATTRIBUTE: References to source fields
 * - EXPRESSION: Filter expressions
 *
 * Examples:
 * <pre>
 * percentile2(10)              → LITERAL argument: 10
 * apdex(name, status)          → ATTRIBUTE arguments: "name", "status"
 * rate(status == true, ...)    → EXPRESSION argument
 * </pre>
 */
@Getter
public final class FunctionArgument {
    private final ArgumentType type;
    private final Object value;

    private FunctionArgument(ArgumentType type, Object value) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = value;
    }

    /**
     * Create a literal argument.
     */
    public static FunctionArgument literal(Object value) {
        return new FunctionArgument(ArgumentType.LITERAL, value);
    }

    /**
     * Create an attribute argument (source field reference).
     */
    public static FunctionArgument attribute(String fieldName) {
        return new FunctionArgument(ArgumentType.ATTRIBUTE, fieldName);
    }

    /**
     * Create an expression argument (filter condition).
     */
    public static FunctionArgument expression(FilterExpression expression) {
        return new FunctionArgument(ArgumentType.EXPRESSION, expression);
    }

    public boolean isLiteral() {
        return type == ArgumentType.LITERAL;
    }

    public boolean isAttribute() {
        return type == ArgumentType.ATTRIBUTE;
    }

    public boolean isExpression() {
        return type == ArgumentType.EXPRESSION;
    }

    /**
     * Get value as a literal (number, string, boolean).
     */
    public Object asLiteral() {
        if (type != ArgumentType.LITERAL) {
            throw new IllegalStateException("Argument is not a literal: " + type);
        }
        return value;
    }

    /**
     * Get value as an attribute name.
     */
    public String asAttribute() {
        if (type != ArgumentType.ATTRIBUTE) {
            throw new IllegalStateException("Argument is not an attribute: " + type);
        }
        return (String) value;
    }

    /**
     * Get value as an expression.
     */
    public FilterExpression asExpression() {
        if (type != ArgumentType.EXPRESSION) {
            throw new IllegalStateException("Argument is not an expression: " + type);
        }
        return (FilterExpression) value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionArgument that = (FunctionArgument) o;
        return type == that.type && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        switch (type) {
            case LITERAL:
                return String.valueOf(value);
            case ATTRIBUTE:
                return value.toString();
            case EXPRESSION:
                return ((FilterExpression) value).toString();
            default:
                throw new IllegalStateException("Unknown type: " + type);
        }
    }

    /**
     * Argument type enumeration.
     */
    public enum ArgumentType {
        /**
         * Constant literal value (number, string, boolean).
         */
        LITERAL,

        /**
         * Source field attribute reference.
         */
        ATTRIBUTE,

        /**
         * Filter expression.
         */
        EXPRESSION
    }
}
