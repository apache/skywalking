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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The shape of a record the Sessionizer's formats define: its fields in the order the format lists them, each with
 * its type. A record is read against its shape: a field of another type means the data is not such a record, a key
 * the shape does not list is left out, and the fields come back in the format's order, an optional one left out when
 * it is empty. A field that is absent or null holds its type's empty value.
 */
public final class Schema {
    /** The type of one field. */
    public enum Kind {
        /** a string; empty when absent */
        STRING,
        /** an integer; 0 when absent */
        INTEGER,
        /** an integer; null when absent */
        NULLABLE_INTEGER,
        /** true or false; false when absent */
        BOOLEAN,
        /** a list of strings; null when absent */
        STRINGS,
        /** an object of a shape; its empty fields when absent */
        OBJECT,
        /** an object of a shape; null when absent */
        NULLABLE_OBJECT,
        /** a list of objects of a shape; null when absent */
        OBJECTS
    }

    private final List<Field> fields = new ArrayList<>();

    /** A field that is always written. */
    public Schema field(final String name, final Kind kind) {
        return field(name, kind, null);
    }

    /** A field that is always written, of an object shape. */
    public Schema field(final String name, final Kind kind, @Nullable final Schema shape) {
        fields.add(new Field(name, kind, shape, false));
        return this;
    }

    /**
     * A field that is left out when empty: an empty string, 0, false, null or an empty list. A nullable field is empty
     * only when null: a 0 it holds was written, and is kept.
     */
    public Schema optional(final String name, final Kind kind) {
        return optional(name, kind, null);
    }

    /** A field that is left out when empty, of an object shape. */
    public Schema optional(final String name, final Kind kind, @Nullable final Schema shape) {
        fields.add(new Field(name, kind, shape, true));
        return this;
    }

    /**
     * @param text JSON text
     * @return the value, or null when the text is not JSON
     */
    @Nullable
    public static JsonElement parse(@Nullable final String text) {
        if (text == null) {
            return null;
        }
        try {
            return JsonParser.parseString(text);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * @param json a JSON value
     * @return the record's fields in the format's order, or null when the value is not an object of this shape
     */
    @Nullable
    public Map<String, Object> read(@Nullable final JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return null;
        }
        try {
            return readObject(json.getAsJsonObject());
        } catch (final WrongType e) {
            return null;
        }
    }

    private Map<String, Object> readObject(final JsonObject json) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Field f : fields) {
            final JsonElement v = json.get(f.name);
            final Object value = f.value(v == null || v.isJsonNull() ? null : v);
            if (!f.optional || !f.isEmpty(value)) {
                out.put(f.name, value);
            }
        }
        return out;
    }

    /** A value of another type than its field's. */
    private static final class WrongType extends RuntimeException {
        private static final long serialVersionUID = 1L;

        WrongType() {
            super(null, null, false, false);
        }
    }

    private static final class Field {
        final String name;
        final Kind kind;
        final Schema shape;
        final boolean optional;

        Field(final String name, final Kind kind, @Nullable final Schema shape, final boolean optional) {
            this.name = name;
            this.kind = kind;
            this.shape = shape;
            this.optional = optional;
        }

        boolean isEmpty(@Nullable final Object value) {
            if (kind == Kind.NULLABLE_INTEGER || kind == Kind.NULLABLE_OBJECT) {
                return value == null;
            }
            return value == null || "".equals(value) || Long.valueOf(0).equals(value) || Boolean.FALSE.equals(value)
                || value instanceof List && ((List<?>) value).isEmpty();
        }

        /** The field's value from its JSON, or its empty value when it has none. */
        @Nullable
        Object value(@Nullable final JsonElement v) {
            switch (kind) {
                case STRING:
                    return v == null ? "" : string(v);
                case INTEGER:
                    return v == null ? 0L : integer(v);
                case NULLABLE_INTEGER:
                    return v == null ? null : integer(v);
                case BOOLEAN:
                    return v != null && bool(v);
                case STRINGS:
                    if (v == null) {
                        return null;
                    }
                    final List<Object> strings = new ArrayList<>();
                    for (final JsonElement e : array(v)) {
                        strings.add(e.isJsonNull() ? "" : string(e));
                    }
                    return strings;
                case OBJECT:
                    return shape.readObject(v == null ? new JsonObject() : object(v));
                case NULLABLE_OBJECT:
                    return v == null ? null : shape.readObject(object(v));
                default:
                    if (v == null) {
                        return null;
                    }
                    final List<Object> objects = new ArrayList<>();
                    for (final JsonElement e : array(v)) {
                        objects.add(shape.readObject(e.isJsonNull() ? new JsonObject() : object(e)));
                    }
                    return objects;
            }
        }
    }

    private static String string(final JsonElement v) {
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
            return v.getAsString();
        }
        throw new WrongType();
    }

    /** A whole number written as one: 1.0 and 1e2 are not integers, and neither is one past 64 bits. */
    private static long integer(final JsonElement v) {
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
            throw new WrongType();
        }
        try {
            return Long.parseLong(v.getAsString());
        } catch (final NumberFormatException e) {
            throw new WrongType();
        }
    }

    private static boolean bool(final JsonElement v) {
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
            return v.getAsBoolean();
        }
        throw new WrongType();
    }

    private static JsonObject object(final JsonElement v) {
        if (v.isJsonObject()) {
            return v.getAsJsonObject();
        }
        throw new WrongType();
    }

    private static JsonArray array(final JsonElement v) {
        if (v.isJsonArray()) {
            return v.getAsJsonArray();
        }
        throw new WrongType();
    }
}
