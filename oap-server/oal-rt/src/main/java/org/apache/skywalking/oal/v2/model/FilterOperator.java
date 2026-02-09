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
import lombok.Getter;

/**
 * Enumeration of filter operators supported in OAL.
 */
@Getter
public enum FilterOperator {
    EQUAL("==", "equal"),
    NOT_EQUAL("!=", "notEqual"),
    GREATER(">", "greater"),
    LESS("<", "less"),
    GREATER_EQUAL(">=", "greaterEqual"),
    LESS_EQUAL("<=", "lessEqual"),
    LIKE("like", "like"),
    IN("in", "in"),
    CONTAIN("contain", "contain"),
    NOT_CONTAIN("not contain", "notContain");

    private final String symbol;
    private final String matcherType;

    FilterOperator(String symbol, String matcherType) {
        this.symbol = symbol;
        this.matcherType = matcherType;
    }

    public static FilterOperator fromString(String str) {
        return Arrays.stream(values())
            .filter(op -> op.symbol.equals(str))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown operator: " + str));
    }

    @Override
    public String toString() {
        return symbol;
    }
}
