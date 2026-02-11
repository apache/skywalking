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

import java.util.List;
import java.util.Objects;
import lombok.Getter;

/**
 * Immutable representation of a filter expression.
 *
 * Examples:
 * <pre>
 * latency > 100          → fieldName="latency", operator=">", value=NUMBER:100
 * status == true         → fieldName="status", operator="==", value=BOOLEAN:true
 * name like "serv%"      → fieldName="name", operator="like", value=STRING:"serv%"
 * tag["key"] != null     → fieldName="tag[key]", operator="!=", value=NULL
 * code in [404, 500]     → fieldName="code", operator="in", value=ARRAY:[404,500]
 * </pre>
 */
@Getter
public final class FilterExpression {
    private final String fieldName;
    private final FilterOperator operator;
    private final FilterValue value;
    private final SourceLocation location;

    private FilterExpression(Builder builder) {
        this.fieldName = Objects.requireNonNull(builder.fieldName, "fieldName cannot be null");
        this.operator = Objects.requireNonNull(builder.operator, "operator cannot be null");
        this.value = Objects.requireNonNull(builder.value, "value cannot be null");
        this.location = builder.location != null ? builder.location : SourceLocation.UNKNOWN;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create filter expression with auto-detection of value type.
     */
    public static FilterExpression of(String fieldName, String operatorStr, Object value) {
        FilterValue filterValue = convertToFilterValue(value);
        return builder()
            .fieldName(fieldName)
            .operator(FilterOperator.fromString(operatorStr))
            .value(filterValue)
            .build();
    }

    private static FilterValue convertToFilterValue(Object value) {
        if (value == null) {
            return FilterValue.ofNull();
        } else if (value instanceof Long || value instanceof Integer) {
            return FilterValue.ofNumber(((Number) value).longValue());
        } else if (value instanceof Double || value instanceof Float) {
            return FilterValue.ofNumber(((Number) value).doubleValue());
        } else if (value instanceof String) {
            return FilterValue.ofString((String) value);
        } else if (value instanceof Boolean) {
            return FilterValue.ofBoolean((Boolean) value);
        } else if (value instanceof List) {
            return FilterValue.ofArray((List<?>) value);
        } else if (value instanceof FilterValue) {
            return (FilterValue) value;
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterExpression that = (FilterExpression) o;
        return Objects.equals(fieldName, that.fieldName) &&
            operator == that.operator &&
            Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, operator, value);
    }

    @Override
    public String toString() {
        return fieldName + " " + operator.getSymbol() + " " + value;
    }

    public static class Builder {
        private String fieldName;
        private FilterOperator operator;
        private FilterValue value;
        private SourceLocation location;

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder operator(FilterOperator operator) {
            this.operator = operator;
            return this;
        }

        public Builder operator(String operatorStr) {
            this.operator = FilterOperator.fromString(operatorStr);
            return this;
        }

        public Builder value(FilterValue value) {
            this.value = value;
            return this;
        }

        /**
         * Set value with auto-type detection.
         */
        public Builder value(Object value) {
            this.value = convertToFilterValue(value);
            return this;
        }

        public Builder numberValue(long value) {
            this.value = FilterValue.ofNumber(value);
            return this;
        }

        public Builder numberValue(double value) {
            this.value = FilterValue.ofNumber(value);
            return this;
        }

        public Builder stringValue(String value) {
            this.value = FilterValue.ofString(value);
            return this;
        }

        public Builder booleanValue(boolean value) {
            this.value = FilterValue.ofBoolean(value);
            return this;
        }

        public Builder nullValue() {
            this.value = FilterValue.ofNull();
            return this;
        }

        public Builder arrayValue(List<?> values) {
            this.value = FilterValue.ofArray(values);
            return this;
        }

        public Builder enumValue(String value) {
            this.value = FilterValue.ofEnum(value);
            return this;
        }

        public Builder location(SourceLocation location) {
            this.location = location;
            return this;
        }

        public FilterExpression build() {
            return new FilterExpression(this);
        }
    }
}
