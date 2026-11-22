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

package org.apache.skywalking.oap.server.core.dsl;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Text in the DSL engines: the interpolation a double-quoted literal would carry in Groovy, and an explicit
 * {@code toString()} of a value, which writes an array, a collection or a map by its content.
 */
public final class DslStrings {

    private DslStrings() {
    }

    /**
     * @return whether a value of the static type can be an array, a collection or a map, which {@link #text} writes by
     *     content: one of them, or a type one of them also is ({@code Object}, {@code Iterable}, {@code Serializable})
     */
    public static boolean mayHoldItems(final Class<?> type) {
        return type.isArray() || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)
            || type.isAssignableFrom(Object[].class) || type.isAssignableFrom(ArrayList.class)
            || type.isAssignableFrom(HashMap.class);
    }

    /**
     * An explicit {@code value.toString()} of LAL and TAL: an array by its elements, at any depth inside a list or a map
     * too ({@code [a, b]}), a collection as {@code [a, b]}, a map as {@code {k=v}}, anything else as it prints itself.
     * A null receiver fails, as {@code toString()} on null always did; a null element prints {@code null}.
     */
    public static String text(final Object value) {
        if (value == null) {
            throw new NullPointerException("Cannot invoke toString() on null");
        }
        final StringBuilder sb = new StringBuilder();
        append(sb, value, Collections.newSetFromMap(new IdentityHashMap<>()));
        return sb.toString();
    }

    /**
     * @param printing the containers being printed further up: one met again inside itself prints as Java prints it,
     *                 {@code (this Collection)} or {@code (this Map)}
     */
    private static void append(final StringBuilder sb, final Object value, final Set<Object> printing) {
        if (value != null && printing.contains(value)) {
            sb.append(value instanceof Map ? "(this Map)" : "(this Collection)");
            return;
        }
        final boolean container = value != null
            && (value.getClass().isArray() || value instanceof Collection || value instanceof Map);
        if (container) {
            printing.add(value);
        }
        try {
            appendValue(sb, value, printing);
        } finally {
            if (container) {
                printing.remove(value);
            }
        }
    }

    private static void appendValue(final StringBuilder sb, final Object value, final Set<Object> printing) {
        if (value == null) {
            sb.append("null");
        } else if (value.getClass().isArray()) {
            sb.append('[');
            for (int i = 0; i < Array.getLength(value); i++) {
                sb.append(i > 0 ? ", " : "");
                append(sb, Array.get(value, i), printing);
            }
            sb.append(']');
        } else if (value instanceof Collection) {
            sb.append('[');
            final Iterator<?> it = ((Collection<?>) value).iterator();
            while (it.hasNext()) {
                append(sb, it.next(), printing);
                sb.append(it.hasNext() ? ", " : "");
            }
            sb.append(']');
        } else if (value instanceof Map) {
            sb.append('{');
            final Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) value).entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<?, ?> e = it.next();
                append(sb, e.getKey(), printing);
                sb.append('=');
                append(sb, e.getValue(), printing);
                sb.append(it.hasNext() ? ", " : "");
            }
            sb.append('}');
        } else {
            sb.append(value);
        }
    }

    /**
     * @return where the first interpolation of a double-quoted string's body starts (see {@link #interpolationAt}),
     *     past escaped characters; -1 when it has none
     */
    public static int firstInterpolation(final CharSequence body) {
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '\\') {
                i++;
            } else if (interpolationAt(body, i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @return whether the {@code $} at {@code i} of a double-quoted string's body starts what Groovy interpolates,
     *     {@code ${...}} or {@code $name}; any other {@code $} ({@code "$2/$1"}, {@code "US$"}, {@code "a-b$"}) is the
     *     character itself
     */
    public static boolean interpolationAt(final CharSequence body, final int i) {
        if (body.charAt(i) != '$' || i + 1 >= body.length()) {
            return false;
        }
        final char next = body.charAt(i + 1);
        return next == '{' || Character.isLetter(next) || next == '_';
    }
}
