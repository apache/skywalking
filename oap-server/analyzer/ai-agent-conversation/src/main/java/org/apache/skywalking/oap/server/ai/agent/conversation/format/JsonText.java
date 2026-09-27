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
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;

/**
 * Reads JSON as RFC 8259 defines it, and nothing more: no comment, no single quote, no NaN, no word but true, false
 * and null, no control character or unknown escape in a string, and nothing after the value. Gson's reader, even set
 * to strict, takes TRUE, a raw tab and \' in a string, and refuses a number longer than its buffer, so it cannot be
 * the judge of what the formats are.
 *
 * <p>The text is read in one loop, not by recursion, so how deep a value may nest is a fixed limit and not what a
 * thread's stack holds, and the same text reads the same way on every run. A number keeps the text it was written in:
 * it is Gson's own number type, the one Gson's parser makes, which prints as written.
 */
final class JsonText {
    /** Why a text is not JSON; one instance and no stack trace, since the caller only needs to know that it is not. */
    private static final RuntimeException NOT_JSON = new RuntimeException(null, null, false, false) {
        private static final long serialVersionUID = 1L;
    };

    private final String text;
    private int pos;

    private JsonText(final String text) {
        this.text = text;
    }

    /**
     * @param text     JSON text
     * @param maxDepth the most objects and lists that may be open at once
     * @return the value, or null when the text is not JSON or nests deeper than the limit
     */
    @Nullable
    static JsonElement read(final String text, final int maxDepth) {
        try {
            return new JsonText(text).document(maxDepth);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private JsonElement document(final int maxDepth) {
        final Deque<JsonElement> open = new ArrayDeque<>();
        final Deque<String> names = new ArrayDeque<>();
        JsonElement root = null;
        while (true) {
            // a value: added to the object or list it is in, and an object or a list is then read into
            space();
            final JsonElement value;
            if (peek() == '{') {
                value = new JsonObject();
            } else if (peek() == '[') {
                value = new JsonArray();
            } else {
                value = scalar();
            }
            if (open.isEmpty()) {
                root = value;
            } else if (open.peek().isJsonObject()) {
                open.peek().getAsJsonObject().add(names.pop(), value);
            } else {
                open.peek().getAsJsonArray().add(value);
            }
            if (value.isJsonObject() || value.isJsonArray()) {
                pos++;
                if (open.size() == maxDepth) {
                    throw NOT_JSON;
                }
                open.push(value);
                space();
                if (peek() != (value.isJsonObject() ? '}' : ']')) {
                    if (value.isJsonObject()) {
                        names.push(member());
                    }
                    continue;
                }
                pos++;
                open.pop();
            }
            // after a value: the end of the text, the next element, or the end of an object or a list
            while (true) {
                space();
                if (open.isEmpty()) {
                    if (pos != text.length()) {
                        throw NOT_JSON;
                    }
                    return root;
                }
                final boolean object = open.peek().isJsonObject();
                final char c = peek();
                pos++;
                if (c == ',') {
                    if (object) {
                        names.push(member());
                    }
                    break;
                }
                if (c != (object ? '}' : ']')) {
                    throw NOT_JSON;
                }
                open.pop();
            }
        }
    }

    /** A member's name and the colon after it; the value is read next. */
    private String member() {
        space();
        if (peek() != '"') {
            throw NOT_JSON;
        }
        final String name = string();
        space();
        if (peek() != ':') {
            throw NOT_JSON;
        }
        pos++;
        return name;
    }

    private JsonElement scalar() {
        final char c = peek();
        if (c == '"') {
            return new JsonPrimitive(string());
        }
        if (c == '-' || c >= '0' && c <= '9') {
            return new JsonPrimitive(new LazilyParsedNumber(number()));
        }
        if (text.startsWith("true", pos)) {
            pos += 4;
            return new JsonPrimitive(true);
        }
        if (text.startsWith("false", pos)) {
            pos += 5;
            return new JsonPrimitive(false);
        }
        if (text.startsWith("null", pos)) {
            pos += 4;
            return JsonNull.INSTANCE;
        }
        throw NOT_JSON;
    }

    /** -? (0 | [1-9][0-9]*) (.[0-9]+)? ([eE][+-]?[0-9]+)?, as its text. */
    private String number() {
        final int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
        } else {
            digits();
        }
        if (pos < text.length() && text.charAt(pos) == '.') {
            pos++;
            digits();
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            digits();
        }
        return text.substring(start, pos);
    }

    private void digits() {
        final int start = pos;
        while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
            pos++;
        }
        if (pos == start) {
            throw NOT_JSON;
        }
    }

    private String string() {
        pos++;
        final StringBuilder out = new StringBuilder();
        while (true) {
            final char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw NOT_JSON;
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            final char escape = next();
            switch (escape) {
                case '"':
                case '\\':
                case '/':
                    out.append(escape);
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    out.append(hex());
                    break;
                default:
                    throw NOT_JSON;
            }
        }
    }

    /** The four hexadecimal digits after a backslash and u. JSON's digits are ASCII only, as Character.digit's are not. */
    private char hex() {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            final char c = next();
            final int digit;
            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                digit = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                digit = c - 'A' + 10;
            } else {
                throw NOT_JSON;
            }
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private void space() {
        while (pos < text.length() && " \t\n\r".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw NOT_JSON;
        }
        return text.charAt(pos);
    }

    private char next() {
        final char c = peek();
        pos++;
        return c;
    }
}
