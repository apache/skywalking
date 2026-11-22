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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/**
 * Represents a typed value in a filter expression.
 *
 * Filter values can be:
 * - NUMBER: Long or Double values
 * - STRING: String literals
 * - BOOLEAN: true or false
 * - NULL: null value
 * - ARRAY: Collection of values (for 'in' operator)
 * - ENUM: Enum reference (e.g., RequestType.MQ)
 *
 * Examples:
 * <pre>
 * latency &gt; 100              → NUMBER: 100L
 * status == true             → BOOLEAN: true
 * name like "serv%"          → STRING: "serv%"
 * type == RequestType.MQ     → ENUM: RequestType.MQ
 * code in [404, 500, 503]    → ARRAY: [404L, 500L, 503L]
 * type in [RequestType.MQ]   → ARRAY: [ENUM: RequestType.MQ], each constant an ENUM value, so a quoted "a.b" stays a string
 * tag["key"] != null         → NULL
 * </pre>
 */
@Getter
public final class FilterValue {
    private final ValueType type;
    private final Object value;
    /**
     * The value as written, where that differs in kind or form (a number written with an L or a _, or out of the long
     * range, is its text, a list's items are their text, a string keeps its escapes): what a matcher registered in
     * place of the stock one receives. Null when there is none; it takes no part in equality.
     */
    private final FilterValue writtenForm;
    /**
     * Why the stock matchers refuse the value as written (a number out of the long range, a leading 0), raised only
     * when a stock matcher takes it: a matcher registered in place of one gets the written form. Null when none.
     */
    private final String refusal;

    private FilterValue(ValueType type, Object value) {
        this(type, value, null, null);
    }

    private FilterValue(ValueType type, Object value, FilterValue writtenForm, String refusal) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = value;
        this.writtenForm = writtenForm;
        this.refusal = refusal;
    }

    public FilterValue withWrittenForm(FilterValue written) {
        return new FilterValue(type, value, written, refusal);
    }

    public FilterValue refused(String why) {
        return new FilterValue(type, value, writtenForm, why);
    }

    // Factory methods for each type

    public static FilterValue ofNumber(long value) {
        return new FilterValue(ValueType.NUMBER, value);
    }

    public static FilterValue ofNumber(double value) {
        return new FilterValue(ValueType.NUMBER, value);
    }

    public static FilterValue ofString(String value) {
        return new FilterValue(ValueType.STRING, Objects.requireNonNull(value));
    }

    public static FilterValue ofBoolean(boolean value) {
        return new FilterValue(ValueType.BOOLEAN, value);
    }

    public static FilterValue ofNull() {
        return new FilterValue(ValueType.NULL, null);
    }

    public static FilterValue ofArray(List<?> values) {
        return new FilterValue(ValueType.ARRAY, Collections.unmodifiableList(new ArrayList<>(values)));
    }

    public static FilterValue ofEnum(String value) {
        return new FilterValue(ValueType.ENUM, Objects.requireNonNull(value));
    }

    // Type checking methods

    public boolean isNumber() {
        return type == ValueType.NUMBER;
    }

    public boolean isString() {
        return type == ValueType.STRING;
    }

    public boolean isBoolean() {
        return type == ValueType.BOOLEAN;
    }

    public boolean isNull() {
        return type == ValueType.NULL;
    }

    public boolean isArray() {
        return type == ValueType.ARRAY;
    }

    public boolean isEnum() {
        return type == ValueType.ENUM;
    }

    // Accessor methods with type checking

    public Number asNumber() {
        if (type != ValueType.NUMBER) {
            throw new IllegalStateException("Value is not a number: " + type);
        }
        return (Number) value;
    }

    public long asLong() {
        if (type != ValueType.NUMBER) {
            throw new IllegalStateException("Value is not a number: " + type);
        }
        Number num = (Number) value;
        return num.longValue();
    }

    public double asDouble() {
        if (type != ValueType.NUMBER) {
            throw new IllegalStateException("Value is not a number: " + type);
        }
        Number num = (Number) value;
        return num.doubleValue();
    }

    public String asString() {
        if (type != ValueType.STRING) {
            throw new IllegalStateException("Value is not a string: " + type);
        }
        return (String) value;
    }

    public boolean asBoolean() {
        if (type != ValueType.BOOLEAN) {
            throw new IllegalStateException("Value is not a boolean: " + type);
        }
        return (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    public List<?> asArray() {
        if (type != ValueType.ARRAY) {
            throw new IllegalStateException("Value is not an array: " + type);
        }
        return (List<?>) value;
    }

    public String asEnum() {
        if (type != ValueType.ENUM) {
            throw new IllegalStateException("Value is not an enum: " + type);
        }
        return (String) value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterValue that = (FilterValue) o;
        return type == that.type && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        switch (type) {
            case NUMBER:
                return String.valueOf(value);
            case STRING:
                return "\"" + value + "\"";
            case BOOLEAN:
                return String.valueOf(value);
            case NULL:
                return "null";
            case ARRAY:
                return value.toString();
            case ENUM:
                return (String) value;  // No quotes for enum
            default:
                throw new IllegalStateException("Unknown type: " + type);
        }
    }

    /**
     * Value type enumeration.
     */
    public enum ValueType {
        /**
         * Numeric value (long or double).
         */
        NUMBER,

        /**
         * String literal.
         */
        STRING,

        /**
         * Boolean value (true/false).
         */
        BOOLEAN,

        /**
         * Null value.
         */
        NULL,

        /**
         * Array of values (for 'in' operator).
         */
        ARRAY,

        /**
         * Enum reference (e.g., RequestType.MQ, DetectPoint.CLIENT).
         */
        ENUM
    }
}
