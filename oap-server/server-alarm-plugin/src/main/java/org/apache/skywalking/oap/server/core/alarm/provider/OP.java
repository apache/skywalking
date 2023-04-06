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
    GT {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() > requireNonNull(expected, "expected").doubleValue();
        }
    },

    GTE {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() >= requireNonNull(expected, "expected").doubleValue();
        }
    },

    LT {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() < requireNonNull(expected, "expected").doubleValue();
        }
    },

    LTE {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() <= requireNonNull(expected, "expected").doubleValue();
        }
    },

    // NOTICE: double equal is not reliable in Java,
    // match result is not predictable
    EQ {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() == requireNonNull(expected, "expected").doubleValue();
        }
    },

    NEQ {
        @Override
        public boolean test(final Number expected, final Number actual) {
            return requireNonNull(actual, "actual").doubleValue() != requireNonNull(expected, "expected").doubleValue();
        }
    };

    public static OP get(String op) {
        switch (op) {
            case ">":
                return GT;
            case ">=":
                return GTE;
            case "<":
                return LT;
            case "<=":
                return LTE;
            case "==":
                return EQ;
            case "!=":
                return NEQ;
            default:
                throw new IllegalArgumentException("unknown op, " + op);
        }
    }

    public abstract boolean test(final Number expected, final Number actual);
}
