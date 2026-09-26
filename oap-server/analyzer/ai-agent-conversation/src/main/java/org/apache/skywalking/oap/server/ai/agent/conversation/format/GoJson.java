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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Reads JSON the way the Sessionizer's Go code reads it, with <code>encoding/json</code>, and gives back what
 * <code>json.Marshal</code> writes for the struct it was read into.
 *
 * <p>The text is scanned here rather than by Gson, whose reader accepts syntax Go refuses and refuses numbers Go
 * accepts. The scan follows Go's scanner: its grammar for numbers, its escapes in strings, no control character
 * written as it is, and no more than 10,000 levels of objects and arrays. A value nested that deep is read without
 * recursion, so no text Go accepts can exhaust the stack.
 *
 * <p>Decoding into a struct follows <code>json.Unmarshal</code>. A key matches its field exactly, or else in another
 * case, and a key the struct does not have is dropped. Keys are applied in order, so a key given twice keeps its later
 * value, and an object given twice for one struct fills it twice. A null leaves a string, a number, a bool or a struct
 * held by value as it was, and empties a pointer or a slice. A value of the wrong type refuses the whole text, as Go's
 * error does, even when a later key would have replaced it.
 *
 * <p>Each record and attribute the Sessionizer's view decodes into a struct is read here, with that struct written
 * out tag for tag, so the document accepts and refuses what the Sessionizer's does.
 */
public final class GoJson {
    private static final Pattern INTEGER_LITERAL = Pattern.compile("-?\\d+");
    private static final Pattern UNSIGNED_LITERAL = Pattern.compile("\\d+");
    /** Go's scanner refuses a text whose objects and arrays nest deeper than this. */
    private static final int MAX_DEPTH = 10000;

    private GoJson() {
    }

    /** The type of one field of a Go struct, as far as a record uses one. */
    public enum Kind {
        /** <code>string</code> */
        STRING,
        /** <code>int</code> or <code>int64</code> */
        INT,
        /** <code>uint64</code>; a value past 2^63 is refused, which no count or position the Sessionizer writes reaches */
        UINT,
        /** <code>*int</code> or <code>*int64</code> */
        INT_POINTER,
        /** <code>bool</code> */
        BOOL,
        /** <code>[]string</code> */
        STRINGS,
        /** a struct held by value */
        STRUCT,
        /** a pointer to a struct */
        STRUCT_POINTER,
        /** a slice of structs */
        STRUCTS
    }

    /** A Go struct: its fields in declaration order, each with its JSON tag. */
    public static final class Struct {
        private final List<Field> fields = new ArrayList<>();

        /**
         * @param tag  the field's JSON tag as the Go source writes it: the name, and <code>,omitempty</code> when
         *             an empty value is left out
         * @param kind the field's type
         */
        public Struct field(final String tag, final Kind kind) {
            return field(tag, kind, null);
        }

        /**
         * @param struct for a struct type, the struct
         */
        public Struct field(final String tag, final Kind kind, @Nullable final Struct struct) {
            final int comma = tag.indexOf(',');
            final String name = comma < 0 ? tag : tag.substring(0, comma);
            fields.add(new Field(name, kind, struct, tag.endsWith(",omitempty")));
            return this;
        }

        /** The field a key names: the one of exactly that name, or else one whose name matches it in another case. */
        @Nullable
        private Field find(final String key) {
            for (final Field f : fields) {
                if (f.name.equals(key)) {
                    return f;
                }
            }
            for (final Field f : fields) {
                if (f.name.equalsIgnoreCase(key)) {
                    return f;
                }
            }
            return null;
        }
    }

    private static final class Field {
        final String name;
        final Kind kind;
        final Struct struct;
        final boolean omitEmpty;

        Field(final String name, final Kind kind, @Nullable final Struct struct, final boolean omitEmpty) {
            this.name = name;
            this.kind = kind;
            this.struct = struct;
            this.omitEmpty = omitEmpty;
        }
    }

    /**
     * @param text JSON text as it was written
     * @return the one JSON value the text holds, a number kept with the digits it was written with; or null when Go
     * refuses the text. A key given twice keeps its later value.
     */
    @Nullable
    public static JsonElement parse(@Nullable final String text) {
        if (text == null) {
            return null;
        }
        try {
            final Scanner s = new Scanner(text);
            final JsonElement v = s.value(true);
            s.end();
            return v;
        } catch (final Refused e) {
            return null;
        }
    }

    /**
     * The members of a JSON object as written, as Go keeps them when it decodes into a map of
     * <code>json.RawMessage</code>, or into a struct field of that type: a later key replaces an earlier one only then,
     * so a reader that must pick the value Go picks reads the members, not a tree that has already merged them.
     *
     * @param text JSON text as it was written
     * @return each key and the text of its value, in the order they appear, a key given twice listed twice; or null
     * when the text is not one JSON object Go reads
     */
    @Nullable
    public static List<String[]> members(@Nullable final String text) {
        if (text == null) {
            return null;
        }
        try {
            final Scanner s = new Scanner(text);
            final List<String[]> out = new ArrayList<>();
            s.begin('{');
            for (boolean first = true; s.next('}', first); first = false) {
                final String key = s.key();
                final int start = s.at();
                s.value(false);
                out.add(new String[] {key, text.substring(start, s.pos)});
            }
            s.end();
            return out;
        } catch (final Refused e) {
            return null;
        }
    }

    /**
     * @param raw    JSON text as it was written
     * @param struct the Go struct it is decoded into
     * @return what <code>json.Marshal</code> writes for the struct after <code>json.Unmarshal</code> of the text: every
     * field under its own name, in declaration order, an empty one left out only when its tag says
     * <code>omitempty</code>; or null when Go refuses the text: not JSON, or a value of the wrong type
     */
    @Nullable
    public static JsonObject decode(@Nullable final String raw, final Struct struct) {
        final JsonElement v = read(raw, new Field("", Kind.STRUCT, struct, false));
        return v == null ? null : v.getAsJsonObject();
    }

    /**
     * @param raw     JSON text as it was written
     * @param element the Go struct of each element
     * @return the slice as <code>json.Marshal</code> writes it after <code>json.Unmarshal</code> of the text, empty
     * for a JSON null; or null when Go refuses the text
     */
    @Nullable
    public static JsonArray decodeSlice(@Nullable final String raw, final Struct element) {
        final JsonElement v = read(raw, new Field("", Kind.STRUCTS, element, false));
        if (v == null) {
            return null;
        }
        return v.isJsonArray() ? v.getAsJsonArray() : new JsonArray();
    }

    /**
     * @return a JSON object as Java values: an object as an ordered map, an array as a list, a whole number as a long
     * or, past that, a big integer, any other number as a big decimal, so every number keeps the digits it was
     * written with
     */
    public static Map<String, Object> toMap(final JsonObject json) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonElement> e : json.entrySet()) {
            out.put(e.getKey(), toValue(e.getValue()));
        }
        return out;
    }

    /**
     * @return a JSON value as Java values, as {@link #toMap} converts one
     */
    @Nullable
    public static Object toValue(@Nullable final JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonObject()) {
            return toMap(e.getAsJsonObject());
        }
        if (e.isJsonArray()) {
            final List<Object> list = new ArrayList<>();
            for (final JsonElement x : e.getAsJsonArray()) {
                list.add(toValue(x));
            }
            return list;
        }
        final JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return p.getAsBoolean();
        }
        if (p.isNumber()) {
            final String literal = p.getAsString();
            if (!INTEGER_LITERAL.matcher(literal).matches()) {
                return new BigDecimal(literal);
            }
            final BigInteger n = new BigInteger(literal);
            return n.bitLength() < Long.SIZE ? (Object) n.longValue() : n;
        }
        return p.getAsString();
    }

    /**
     * @param text JSON text as it was written
     * @return whether Go decodes the text into a value of <code>any</code>: it reads, and every number in it, a key
     * given twice included, fits a double. A number past the range, such as 1e400, fails the whole decoding, even
     * where a later key replaces it.
     */
    public static boolean decodesAsAny(@Nullable final String text) {
        if (text == null) {
            return false;
        }
        try {
            final Scanner s = new Scanner(text);
            s.value(false);
            s.end();
            return !s.numberPastDouble;
        } catch (final Refused e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- json.Unmarshal

    /**
     * @return the field as <code>json.Marshal</code> writes it after <code>json.Unmarshal</code> of the text, or null
     * when Go refuses the text
     */
    @Nullable
    private static JsonElement read(@Nullable final String raw, final Field top) {
        if (raw == null) {
            return null;
        }
        try {
            final Scanner s = new Scanner(raw);
            final JsonElement held = s.unmarshal(top, null);
            s.end();
            return s.failed ? null : marshal(top, held);
        } catch (final Refused e) {
            return null;
        }
    }

    /** The text is not JSON Go reads. */
    private static final class Refused extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Refused() {
            super(null, null, false, false);
        }
    }

    /**
     * A cursor over JSON text that reads it as Go's scanner and decoder do. Every method that reads a value leaves the
     * cursor after it, and throws {@link Refused} at the first place Go's scanner stops.
     */
    private static final class Scanner {
        private final String text;
        private int pos;
        /** How many objects and arrays are open at the cursor. */
        private int depth;
        /** A value had the wrong type: Go reads on to check the rest of the text, then refuses it. */
        private boolean failed;
        /** A number was past a double's range, which Go refuses when it decodes a number into <code>any</code>. */
        private boolean numberPastDouble;

        Scanner(final String text) {
            this.text = text;
        }

        // ------------------------------------------------ structure

        /** Passes over the white space Go allows between tokens. */
        private void space() {
            while (pos < text.length() && " \t\n\r".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
        }

        /** @return where the next token starts, after any white space */
        int at() {
            space();
            if (pos >= text.length()) {
                throw new Refused();
            }
            return pos;
        }

        char peek() {
            return text.charAt(at());
        }

        void expect(final char c) {
            if (peek() != c) {
                throw new Refused();
            }
            pos++;
        }

        /** Checks that nothing but white space follows the value. */
        void end() {
            space();
            if (pos != text.length()) {
                throw new Refused();
            }
        }

        /** Opens an object or an array at the cursor. */
        void begin(final char open) {
            expect(open);
            if (++depth > MAX_DEPTH) {
                throw new Refused();
            }
        }

        /**
         * @param first whether nothing has been read since the container opened
         * @return whether another member or element follows, having read the comma before it; false when the
         * container closes here, having read the closing bracket
         */
        boolean next(final char close, final boolean first) {
            final char c = peek();
            if (c == close) {
                pos++;
                depth--;
                return false;
            }
            if (!first) {
                expect(',');
            }
            return true;
        }

        /** @return a member's key, having read the colon after it */
        String key() {
            final String key = string();
            expect(':');
            return key;
        }

        // ------------------------------------------------ values

        /**
         * Reads one value of any depth without recursion.
         *
         * @param keep whether to build the value, or only to check it and pass over it
         * @return the value, or null when it is not kept
         */
        @Nullable
        JsonElement value(final boolean keep) {
            // the open containers, innermost first: each one's closing bracket, its tree when the value is kept, and
            // for an object the key of the member being read
            final Deque<Character> closes = new ArrayDeque<>();
            final Deque<JsonElement> trees = new ArrayDeque<>();
            final Deque<String> keys = new ArrayDeque<>();
            while (true) {
                JsonElement v = null;
                final char c = peek();
                if (c == '{' || c == '[') {
                    begin(c);
                    final char close = c == '{' ? '}' : ']';
                    if (keep) {
                        v = c == '{' ? new JsonObject() : new JsonArray();
                    }
                    if (next(close, true)) {
                        closes.push(close);
                        if (keep) {
                            trees.push(v);
                        }
                        if (close == '}') {
                            keys.push(key());
                        }
                        continue;
                    }
                } else {
                    v = scalar(keep);
                }
                // a whole value: add it to its container, and close each container it completes
                while (!closes.isEmpty()) {
                    final char close = closes.peek();
                    final String key = close == '}' ? keys.pop() : null;
                    if (keep && key != null) {
                        trees.peek().getAsJsonObject().add(key, v);
                    } else if (keep) {
                        trees.peek().getAsJsonArray().add(v);
                    }
                    if (next(close, false)) {
                        if (close == '}') {
                            keys.push(key());
                        }
                        break;
                    }
                    closes.pop();
                    v = keep ? trees.pop() : null;
                }
                if (closes.isEmpty()) {
                    return v;
                }
            }
        }

        @Nullable
        private JsonElement scalar(final boolean keep) {
            switch (peek()) {
                case '"':
                    final String s = string();
                    return keep ? new JsonPrimitive(s) : null;
                case 't':
                    literal("true");
                    return keep ? new JsonPrimitive(true) : null;
                case 'f':
                    literal("false");
                    return keep ? new JsonPrimitive(false) : null;
                case 'n':
                    literal("null");
                    return keep ? JsonNull.INSTANCE : null;
                default:
                    final String n = number();
                    numberPastDouble |= Double.isInfinite(Double.parseDouble(n));
                    // the number Gson's own parser holds: the literal as written, read as a number only when asked
                    return keep ? new JsonPrimitive(new LazilyParsedNumber(n)) : null;
            }
        }

        private void literal(final String word) {
            if (!text.startsWith(word, at())) {
                throw new Refused();
            }
            pos += word.length();
        }

        /** @return a number as written, by Go's grammar: <code>-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?</code> */
        String number() {
            final int start = at();
            skip('-');
            if (!skip('0')) {
                // a first digit of 1 to 9, as a 0 is taken above, and any digits after it
                digits();
            }
            if (skip('.')) {
                digits();
            }
            if (skip('e') || skip('E')) {
                if (!skip('+')) {
                    skip('-');
                }
                digits();
            }
            return text.substring(start, pos);
        }

        private boolean skip(final char c) {
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        /** Reads one ASCII digit or more. */
        private void digits() {
            if (!isDigit(pos)) {
                throw new Refused();
            }
            while (isDigit(pos)) {
                pos++;
            }
        }

        private boolean isDigit(final int i) {
            return i < text.length() && text.charAt(i) >= '0' && text.charAt(i) <= '9';
        }

        /**
         * @return a string's value as Go decodes it: the escapes a quote, a backslash, a slash, b, f, n, r, t and u with
         * four hex digits, and nothing else; no control character written as it is; and a surrogate that is not half of
         * a pair replaced by U+FFFD
         */
        String string() {
            expect('"');
            final StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw new Refused();
                }
                final char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c < 0x20) {
                    throw new Refused();
                }
                if (c != '\\') {
                    if (Character.isHighSurrogate(c) && pos < text.length() && Character.isLowSurrogate(text.charAt(pos))) {
                        out.append(c).append(text.charAt(pos++));
                    } else {
                        out.append(Character.isSurrogate(c) ? '\ufffd' : c);
                    }
                    continue;
                }
                final char e = pos < text.length() ? text.charAt(pos++) : 0;
                switch (e) {
                    case '"':
                    case '\\':
                    case '/':
                        out.append(e);
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
                        final int u = hex4(pos);
                        if (u < 0) {
                            throw new Refused();
                        }
                        pos += 4;
                        if (!Character.isSurrogate((char) u)) {
                            out.append((char) u);
                            break;
                        }
                        // a high surrogate pairs with a low one written right after it as an escape; any other is
                        // U+FFFD, and what follows is read on its own
                        final int low = text.startsWith("\\u", pos) ? hex4(pos + 2) : -1;
                        if (Character.isHighSurrogate((char) u) && low >= 0 && Character.isLowSurrogate((char) low)) {
                            out.append((char) u).append((char) low);
                            pos += 6;
                        } else {
                            out.append('\ufffd');
                        }
                        break;
                    default:
                        throw new Refused();
                }
            }
        }

        /** @return the value of four ASCII hex digits at a place, or -1 when they are not */
        private int hex4(final int from) {
            if (from + 4 > text.length()) {
                return -1;
            }
            int v = 0;
            for (int i = from; i < from + 4; i++) {
                final char c = text.charAt(i);
                final int d = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10
                    : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
                if (d < 0) {
                    return -1;
                }
                v = v * 16 + d;
            }
            return v;
        }

        // ------------------------------------------------ into a struct

        /**
         * Applies one JSON value to a field. What a field holds while the text is read is only what the text gave it:
         * a struct holds the fields given, each once, and a field given nothing holds nothing.
         *
         * @param held what the field held before this value
         * @return what the field holds after it, or null when it holds nothing
         */
        @Nullable
        JsonElement unmarshal(final Field f, @Nullable final JsonElement held) {
            final char c = peek();
            if (c == 'n') {
                literal("null");
                switch (f.kind) {
                    case INT_POINTER:
                    case STRUCT_POINTER:
                    case STRINGS:
                    case STRUCTS:
                        return null;
                    default:
                        return held;
                }
            }
            switch (f.kind) {
                case STRING:
                    if (c == '"') {
                        return new JsonPrimitive(string());
                    }
                    break;
                case INT:
                case INT_POINTER:
                case UINT:
                    if (c == '-' || c >= '0' && c <= '9') {
                        return integer(f.kind, held);
                    }
                    break;
                case BOOL:
                    if (c == 't' || c == 'f') {
                        final boolean b = c == 't';
                        literal(b ? "true" : "false");
                        return new JsonPrimitive(b);
                    }
                    break;
                case STRUCT:
                case STRUCT_POINTER:
                    if (c == '{') {
                        return unmarshalStruct(f.struct, held == null ? new JsonObject() : held.getAsJsonObject());
                    }
                    break;
                case STRINGS:
                case STRUCTS:
                    if (c == '[') {
                        return unmarshalSlice(f, held == null ? new JsonArray() : held.getAsJsonArray());
                    }
                    break;
                default:
                    break;
            }
            failed = true;
            value(false);
            return held;
        }

        /**
         * Go decodes an integer from an integer literal only, so 1.0 and 1e2 are the wrong type, as is a sign on an
         * unsigned one, and a value past the type's range.
         */
        @Nullable
        private JsonElement integer(final Kind kind, @Nullable final JsonElement held) {
            final String literal = number();
            if ((kind == Kind.UINT ? UNSIGNED_LITERAL : INTEGER_LITERAL).matcher(literal).matches()) {
                try {
                    return new JsonPrimitive(Long.parseLong(literal));
                } catch (final NumberFormatException e) {
                    // past the range: the wrong type, as below
                }
            }
            failed = true;
            return held;
        }

        private JsonObject unmarshalStruct(final Struct struct, final JsonObject into) {
            begin('{');
            for (boolean first = true; next('}', first); first = false) {
                final Field f = struct.find(key());
                if (f == null) {
                    value(false);
                    continue;
                }
                final JsonElement v = unmarshal(f, into.get(f.name));
                if (v == null) {
                    into.remove(f.name);
                } else {
                    into.add(f.name, v);
                }
            }
            return into;
        }

        /**
         * An array into a slice: each element into the one already at its index, if any, as Go reuses the slice's
         * backing array, and the slice cut to the array's length. A null element leaves its element as it was.
         */
        private JsonArray unmarshalSlice(final Field f, final JsonArray held) {
            final boolean strings = f.kind == Kind.STRINGS;
            final Field element = new Field("", strings ? Kind.STRING : Kind.STRUCT, f.struct, false);
            final JsonArray out = new JsonArray();
            begin('[');
            for (int i = 0; next(']', i == 0); i++) {
                final JsonElement v = unmarshal(element, i < held.size() ? held.get(i) : null);
                // an element given nothing is there all the same, as its type's zero value
                out.add(v != null ? v : strings ? new JsonPrimitive("") : new JsonObject());
            }
            return out;
        }
    }

    // ---------------------------------------------------------------- json.Marshal

    /**
     * @param held what the field holds, or null for nothing
     * @return the field as <code>json.Marshal</code> writes it: nothing held is the type's zero value, which is null for
     * a pointer or a slice
     */
    private static JsonElement marshal(final Field f, @Nullable final JsonElement held) {
        switch (f.kind) {
            case STRING:
                return held == null ? new JsonPrimitive("") : held;
            case INT:
            case UINT:
                return held == null ? new JsonPrimitive(0L) : held;
            case BOOL:
                return held == null ? new JsonPrimitive(false) : held;
            case STRUCT:
                return marshalStruct(f.struct, held == null ? new JsonObject() : held.getAsJsonObject());
            case STRUCT_POINTER:
                return held == null ? JsonNull.INSTANCE : marshalStruct(f.struct, held.getAsJsonObject());
            case STRUCTS:
                if (held == null) {
                    return JsonNull.INSTANCE;
                }
                final JsonArray out = new JsonArray();
                for (final JsonElement e : held.getAsJsonArray()) {
                    out.add(marshalStruct(f.struct, e.getAsJsonObject()));
                }
                return out;
            default:
                // a pointer to a number, or a slice of strings, holds its value as it is
                return held == null ? JsonNull.INSTANCE : held;
        }
    }

    private static JsonObject marshalStruct(final Struct struct, final JsonObject held) {
        final JsonObject out = new JsonObject();
        for (final Field f : struct.fields) {
            final JsonElement v = marshal(f, held.get(f.name));
            if (!f.omitEmpty || !isEmpty(f.kind, v)) {
                out.add(f.name, v);
            }
        }
        return out;
    }

    /** Whether <code>omitempty</code> leaves the value out: false, 0, "", a nil pointer, a nil or empty slice. */
    private static boolean isEmpty(final Kind kind, final JsonElement v) {
        switch (kind) {
            case STRING:
                return v.getAsString().isEmpty();
            case INT:
            case UINT:
                return v.getAsLong() == 0;
            case BOOL:
                return !v.getAsBoolean();
            case STRINGS:
            case STRUCTS:
                return v.isJsonNull() || v.getAsJsonArray().size() == 0;
            case INT_POINTER:
            case STRUCT_POINTER:
                return v.isJsonNull();
            default:
                // a struct held by value is never empty
                return false;
        }
    }
}
