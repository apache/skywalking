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
import org.apache.skywalking.oal.v2.model.FilterValue;
import org.apache.skywalking.oal.v2.util.TypeCastUtil;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.StringMatch;

/**
 * How a generated filter compares strings: as written, quotes included. The matchers take a pair of quotes off a
 * string: {@code StringMatch#match(String, String)} off both sides, {@code InMatch} off each string of its list,
 * {@code ContainMatch} off the one it looks for and {@code LikeMatch} off its pattern, so {@code == "v1"} would match
 * {@code "\"v1\""} where {@code != "v1"} would too, and a lone quote would fail the event. Equality, lists and contain
 * are emitted here so that no quotes come off; a like pattern is read as {@code LikeMatch} reads it.
 */
final class StringComparison {
    private StringComparison() {
    }

    /**
     * A string or null {@code ==} with a field that may be a {@code String}, one of that type or of a type not plain to
     * see, compares as objects, through {@code StringMatch#match(Object, Object)}, the overload a field of another type
     * takes too.
     */
    static boolean asObjects(Class<?> matcher, FilterValue value, Class<?> type) {
        return matcher == StringMatch.class && (value.isString() || value.isNull())
            && (type == String.class || type == null);
    }

    static String asObject(String expression) {
        return "(Object)(" + expression + ")";
    }

    /**
     * A list holding strings, or constants for a field that may hold a string, takes {@code ExactInMatch}.
     */
    static boolean exactList(List<?> items, Class<?> type) {
        for (Object item : items) {
            if (item instanceof String
                || item instanceof FilterValue && type != null && !type.isPrimitive()
                && type.isAssignableFrom(String.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The pattern {@code LikeMatch} matches with: the string, with a pair of enclosing quotes taken off.
     */
    static String likePattern(String value) {
        return value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")
            ? value.substring(1, value.length() - 1) : value;
    }

    /**
     * The string {@code contain} looks for, as Java source: a quoted one, such as an ETag {@code "\"v1\""}, is wrapped
     * in another pair, which {@code ContainMatch} takes off.
     */
    static String containLiteral(String value) {
        final boolean quoted = value.startsWith("\"") && value.endsWith("\"");
        return TypeCastUtil.toJavaSource(quoted ? "\"" + value + "\"" : value);
    }
}
