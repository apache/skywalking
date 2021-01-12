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

package org.apache.skywalking.oap.server.core.alarm.provider;

import static java.util.Objects.requireNonNull;

public enum OP {
    GREATER {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() > requireNonNull(expected, "expected").doubleValue();
        }
    },

    GREATER_EQ {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() >= requireNonNull(expected, "expected").doubleValue();
        }
    },

    LESS {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() < requireNonNull(expected, "expected").doubleValue();
        }
    },

    LESS_EQ {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() <= requireNonNull(expected, "expected").doubleValue();
        }
    },

    // NOTICE: double equal is not reliable in Java,
    // match result is not predictable
    EQUAL {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() == requireNonNull(expected, "expected").doubleValue();
        }
    };

    public static OP get(String op) {
        switch (op) {
            case ">":
                return GREATER;
            case ">=":
                return GREATER_EQ;
            case "<":
                return LESS;
            case "<=":
                return LESS_EQ;
            case "==":
                return EQUAL;
            default:
                throw new IllegalArgumentException("unknown op, " + op);
        }
    }

    public abstract boolean test(final Number expected, final Number actual);
}
