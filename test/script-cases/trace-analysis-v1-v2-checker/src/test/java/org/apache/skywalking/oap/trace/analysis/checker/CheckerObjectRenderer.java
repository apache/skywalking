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

package org.apache.skywalking.oap.trace.analysis.checker;

import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.skywalking.oap.server.core.analysis.Layer;

/**
 * Renders an object the two sides hand over, a source or an evaluation context, as text: its class, then every
 * non-static field of the class and its superclasses, by name, read by reflection and rendered recursively. No method of
 * the object is called. Lists render in order, maps sorted by key, byte arrays by their content, enums, layers and
 * classes by name, and null stays distinct from empty.
 */
final class CheckerObjectRenderer {
    private static final int MAX_DEPTH = 8;

    private CheckerObjectRenderer() {
    }

    static String render(final Object value) {
        final StringBuilder sb = new StringBuilder();
        render(sb, value, 0);
        return sb.toString();
    }

    private static void render(final StringBuilder sb, final Object value, final int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof CharSequence) {
            sb.append(quote(value.toString()));
        } else if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            sb.append(value);
        } else if (value instanceof Enum) {
            sb.append(((Enum<?>) value).name());
        } else if (value instanceof Class) {
            sb.append("class ").append(((Class<?>) value).getName());
        } else if (value instanceof Layer) {
            sb.append("Layer.").append(((Layer) value).name());
        } else if (value instanceof byte[]) {
            sb.append("bytes(").append(Base64.getEncoder().encodeToString((byte[]) value)).append(')');
        } else if (value instanceof Message) {
            sb.append(value.getClass().getSimpleName()).append('<')
              .append(TextFormat.shortDebugString((Message) value)).append('>');
        } else if (value instanceof Collection) {
            sb.append('[');
            boolean first = true;
            for (final Object element : (Collection<?>) value) {
                sb.append(first ? "" : ", ");
                render(sb, element, depth + 1);
                first = false;
            }
            sb.append(']');
        } else if (value instanceof Map) {
            final Map<String, Object> sorted = new TreeMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> sorted.put(render(k), v));
            sb.append('{');
            boolean first = true;
            for (final Map.Entry<String, Object> entry : sorted.entrySet()) {
                sb.append(first ? "" : ", ").append(entry.getKey()).append('=');
                render(sb, entry.getValue(), depth + 1);
                first = false;
            }
            sb.append('}');
        } else if (value.getClass().isArray()) {
            sb.append(value.getClass().getComponentType().getSimpleName()).append("[]");
        } else if (depth > MAX_DEPTH) {
            sb.append("<deeper>");
        } else {
            sb.append(value.getClass().getSimpleName()).append('{');
            boolean first = true;
            for (final Field field : fields(value.getClass())) {
                sb.append(first ? "" : ", ").append(field.getName()).append('=');
                try {
                    field.setAccessible(true);
                    render(sb, field.get(value), depth + 1);
                } catch (final ReflectiveOperationException | RuntimeException e) {
                    sb.append("<unreadable>");
                }
                first = false;
            }
            sb.append('}');
        }
    }

    /**
     * @return the text in double quotes, a quote, a backslash and a line break escaped, so no two texts render alike
     */
    static String quote(final String text) {
        final StringBuilder sb = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /**
     * @return the non-static fields of the class and its superclasses, the superclass's first, each class's by name
     */
    private static List<Field> fields(final Class<?> type) {
        final List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.add(0, c);
        }
        final List<Field> fields = new ArrayList<>();
        for (final Class<?> c : hierarchy) {
            final List<Field> declared = new ArrayList<>();
            for (final Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    declared.add(field);
                }
            }
            declared.sort(Comparator.comparing(Field::getName));
            fields.addAll(declared);
        }
        return fields;
    }
}
