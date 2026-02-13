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
 * Immutable representation of an aggregation function call.
 *
 * Examples:
 * <pre>
 * longAvg()                      → name="longAvg", args=[]
 * percentile2(10)                → name="percentile2", args=[LITERAL:10]
 * apdex(name, status)            → name="apdex", args=[ATTRIBUTE:name, ATTRIBUTE:status]
 * rate(status == true, count)    → name="rate", args=[EXPRESSION, ATTRIBUTE]
 * </pre>
 */
@Getter
public final class FunctionCall {
    private final String name;
    private final List<FunctionArgument> arguments;

    private FunctionCall(String name, List<FunctionArgument> arguments) {
        this.name = Objects.requireNonNull(name, "function name cannot be null");
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    /**
     * Create a function call with no arguments.
     */
    public static FunctionCall of(String name) {
        return new FunctionCall(name, List.of());
    }

    /**
     * Create a function call with literal arguments.
     */
    public static FunctionCall ofLiterals(String name, Object... literalValues) {
        List<FunctionArgument> args = new ArrayList<>();
        for (Object value : literalValues) {
            args.add(FunctionArgument.literal(value));
        }
        return new FunctionCall(name, args);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionCall that = (FunctionCall) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, arguments);
    }

    @Override
    public String toString() {
        if (arguments.isEmpty()) {
            return name + "()";
        }
        return name + "(" + String.join(", ", arguments.stream().map(FunctionArgument::toString).toArray(String[]::new)) + ")";
    }

    public static class Builder {
        private String name;
        private List<FunctionArgument> arguments = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder addArgument(FunctionArgument argument) {
            this.arguments.add(argument);
            return this;
        }

        public Builder addLiteral(Object value) {
            this.arguments.add(FunctionArgument.literal(value));
            return this;
        }

        public Builder addAttribute(String fieldName) {
            this.arguments.add(FunctionArgument.attribute(fieldName));
            return this;
        }

        public Builder addExpression(FilterExpression expression) {
            this.arguments.add(FunctionArgument.expression(expression));
            return this;
        }

        public Builder arguments(List<FunctionArgument> arguments) {
            this.arguments = new ArrayList<>(arguments);
            return this;
        }

        public FunctionCall build() {
            return new FunctionCall(name, arguments);
        }
    }
}
