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

package org.apache.skywalking.oal.v2.generator;

import java.util.List;

/**
 * Matchers a test registers in place of the stock ones, under their names: none carries {@code @FilterMatcher}, so
 * the scan does not register them for every test.
 */
public final class ChecksCustomMatchers {
    private ChecksCustomMatchers() {
    }

    /**
     * {@code in} ignoring case.
     */
    public static class CaseInsensitiveIn {
        public boolean match(Object left, Object[] rights) {
            for (Object right : rights) {
                if (left != null && left.toString().equalsIgnoreCase(String.valueOf(right))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * {@code contain} as exact membership.
     */
    public static class ExactContain {
        public boolean match(List<String> left, String right) {
            return left != null && left.contains(right);
        }
    }

    /**
     * {@code like} as exact equality.
     */
    public static class ExactLike {
        public boolean match(String left, String right) {
            return right.equals(left);
        }
    }

    /**
     * {@code ==} against an enum's name.
     */
    public static class EnumName {
        public boolean match(Object left, String right) {
            return left != null && left.toString().equals(right);
        }
    }
}
