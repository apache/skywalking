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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * One Session Data (<code>.sd</code>) file, decoded from its stored bytes: the header line, the records one per
 * line in source order, and the closing line with the count and the digest.
 *
 * <p>Row numbers are line numbers: the header is row 0, the first record is row 1, which is how a Session Flow
 * reference addresses a record.
 */
@Getter
public final class SessionDataFile {
    private final Header header;
    private final List<Record> records;
    private final int declaredRecords;
    private final String declaredDigest;
    private final int lines;
    private final int bytes;
    /** sha256 of the whole file, the digest on the wire and the one a round's input digest chains. */
    private final String fileDigest;
    /** The earliest and the latest record time in the file, in milliseconds; 0 when no record carries a time. */
    private final long fromTime;
    private final long throughTime;

    private SessionDataFile(final Header header, final List<Record> records, final int declaredRecords,
                            final String declaredDigest, final int lines, final int bytes,
                            final String fileDigest, final long fromTime, final long throughTime) {
        this.header = header;
        this.records = records;
        this.declaredRecords = declaredRecords;
        this.declaredDigest = declaredDigest;
        this.lines = lines;
        this.bytes = bytes;
        this.fileDigest = fileDigest;
        this.fromTime = fromTime;
        this.throughTime = throughTime;
    }

    /**
     * @param body the file bytes as stored
     * @return the decoded file
     * @throws IllegalArgumentException when the first line is not a Session Data header
     */
    public static SessionDataFile parse(final byte[] body) {
        final String text = new String(body, StandardCharsets.UTF_8);
        final String[] rawLines = text.split("\n", -1);
        int lineCount = rawLines.length;
        if (lineCount > 0 && rawLines[lineCount - 1].isEmpty()) {
            lineCount--;
        }
        if (lineCount < 1) {
            throw new IllegalArgumentException("empty Session Data file");
        }
        final Header header = new Header(headerLine(rawLines[0]));
        final List<Record> records = new ArrayList<>(Math.max(0, lineCount - 2));
        int declaredRecords = -1;
        String declaredDigest = null;
        long from = 0;
        long through = 0;
        // a header the page does not let a reader open yields no records, and an empty line, or one that is not a
        // record, ends the records there, so a later row is never read and the rows stay contiguous
        for (int i = 1; header.isValid() && i < lineCount; i++) {
            final String line = rawLines[i];
            if (line.isEmpty()) {
                break;
            }
            final JsonElement parsed = Schema.parse(line);
            if (parsed == null || !parsed.isJsonObject()) {
                break;
            }
            final JsonObject json = parsed.getAsJsonObject();
            if (i == lineCount - 1 && "end".equals(string(json, "t"))) {
                // a count that is not an integer, or more than a file can hold, is no count
                final JsonElement count = json.get("records");
                declaredRecords = isInteger(count) && count.getAsLong() >= 0 && count.getAsLong() <= Integer.MAX_VALUE
                    ? count.getAsInt() : -1;
                declaredDigest = string(json, "digest");
                break;
            }
            if (!Record.typed(json)) {
                // a field of another type than the page gives it, an `off` that is a string, say: the line is not a
                // record, and the file stops there as it does at a line that is not JSON
                break;
            }
            final Record record = new Record(i, line, json);
            records.add(record);
            final long time = record.getTime();
            if (time != 0) {
                if (from == 0 || time < from) {
                    from = time;
                }
                if (through == 0 || time > through) {
                    through = time;
                }
            }
        }
        return new SessionDataFile(
            header, Collections.unmodifiableList(records), declaredRecords, declaredDigest,
            Digests.countLines(body), body.length, Digests.sha256Hex(body), from, through);
    }

    /**
     * @param body the file bytes as stored
     * @return the header line alone, without reading the records: what the file is and where it belongs
     * @throws IllegalArgumentException when the first line is not a Session Data header
     */
    public static Header header(final byte[] body) {
        int end = 0;
        while (end < body.length && body[end] != '\n') {
            end++;
        }
        return new Header(headerLine(new String(body, 0, end, StandardCharsets.UTF_8)));
    }

    private static JsonObject headerLine(final String line) {
        final JsonElement json = Schema.parse(line);
        if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("h")) {
            throw new IllegalArgumentException("the first line is not a Session Data header");
        }
        return json.getAsJsonObject();
    }

    /**
     * @param row the line number, the header being row 0
     * @return the record on that line, or null when there is none
     */
    @Nullable
    public Record record(final long row) {
        return row >= 1 && row <= records.size() ? records.get((int) row - 1) : null;
    }

    /** A field's value when it is a JSON string; null when it is absent or of another type. */
    @Nullable
    static String string(final JsonObject json, final String key) {
        final JsonElement element = json.get(key);
        return isString(element) ? element.getAsString() : null;
    }

    /** A field's value when it is an integer as the formats write one; 0 when it is absent or of another type. */
    static long longOf(final JsonObject json, final String key) {
        final JsonElement element = json.get(key);
        return isInteger(element) ? element.getAsLong() : 0L;
    }

    private static boolean isString(@Nullable final JsonElement v) {
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString();
    }

    private static boolean isBoolean(@Nullable final JsonElement v) {
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean();
    }

    /** A whole number with no fraction and no exponent that fits in 64 bits with its sign. */
    private static boolean isInteger(@Nullable final JsonElement v) {
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        try {
            Long.parseLong(v.getAsString());
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /**
     * The header line: what the file is and where its records came from.
     */
    @Getter
    public static final class Header {
        private final JsonObject json;
        private final String schema;
        private final long seq;
        private final String at;
        private final String kind;
        private final String adapter;
        private final String dialect;
        private final String src;
        private final String session;
        private final String stream;
        private final String batch;

        Header(final JsonObject json) {
            this.json = json;
            this.schema = string(json, "schema");
            this.seq = longOf(json, "seq");
            this.at = string(json, "at");
            this.kind = string(json, "kind");
            this.adapter = string(json, "adapter");
            this.dialect = string(json, "dialect");
            this.src = string(json, "src");
            this.session = string(json, "session");
            this.stream = string(json, "stream");
            this.batch = string(json, "batch");
        }

        /**
         * @return whether the Session Data page lets a reader open the file: the envelope version, the schema,
         * the kind, the session, the source and the dialect are all there
         */
        public boolean isValid() {
            return longOf(json, "h") == 1 && "sd/1".equals(schema) && kind != null && !kind.isEmpty()
                && session != null && !session.isEmpty() && src != null && !src.isEmpty()
                && dialect != null && !dialect.isEmpty();
        }
    }

    /**
     * One record, kept as its JSON so any field the viewer wants is one lookup away.
     */
    @Getter
    public static final class Record {
        private final int row;
        private final JsonObject json;
        private final String id;
        /** When the runtime wrote it, in milliseconds; 0 when the record carries no time. */
        private final long time;
        /** The same moment in nanoseconds, the precision the Sessionizer computes intervals with. */
        private final long timeNanos;
        private final List<Part> parts;

        Record(final int row, final String line, final JsonObject json) {
            this.row = row;
            this.json = json;
            this.id = string(json, "id");
            this.timeNanos = Times.nanos(string(json, "time"));
            this.time = Math.floorDiv(timeNanos, 1_000_000L);
            final List<Part> list = new ArrayList<>();
            if (json.has("parts") && json.get("parts").isJsonArray()) {
                // the data of each part as the line holds it; see RawJson
                final List<String> raw = RawJson.partData(line);
                int i = 0;
                for (final JsonElement e : json.getAsJsonArray("parts")) {
                    list.add(new Part(e.getAsJsonObject(), i < raw.size() ? raw.get(i) : null));
                    i++;
                }
            }
            this.parts = Collections.unmodifiableList(list);
        }

        /**
         * @return the record's readable text: every <code>text</code> part that is not empty, joined by a
         * newline
         */
        public String text() {
            final StringBuilder out = new StringBuilder();
            for (final Part p : parts) {
                if ("text".equals(p.getKind()) && p.getText() != null && !p.getText().isEmpty()) {
                    if (out.length() > 0) {
                        out.append('\n');
                    }
                    out.append(p.getText());
                }
            }
            return out.toString();
        }

        /**
         * @return the record's <code>flags</code>, empty when none
         */
        public List<String> flags() {
            return strings("flags");
        }

        /**
         * @return the record's <code>usage</code> object, or null
         */
        @Nullable
        public JsonObject usage() {
            final JsonElement u = json.get("usage");
            return u != null && u.isJsonObject() ? u.getAsJsonObject() : null;
        }

        /**
         * @return the record's <code>dropped</code> list, or null
         */
        @Nullable
        public JsonArray dropped() {
            final JsonElement d = json.get("dropped");
            return d != null && d.isJsonArray() ? d.getAsJsonArray() : null;
        }

        /**
         * @return the record's <code>child</code>, or null
         */
        @Nullable
        public String child() {
            return string(json, "child");
        }

        private List<String> strings(final String key) {
            final JsonElement e = json.get(key);
            if (e == null || !e.isJsonArray()) {
                return Collections.emptyList();
            }
            final List<String> out = new ArrayList<>();
            for (final JsonElement x : e.getAsJsonArray()) {
                out.add(x.getAsString());
            }
            return out;
        }

        /**
         * @return whether every field the Session Data page lists has the type the page gives it, inside the parts,
         * the dropped entries and the usage too, a null standing for a field left out. A field the page does not
         * list is not checked, and neither is what a value means: that is the reader's business.
         */
        static boolean typed(final JsonObject json) {
            return fields(json, STRING_FIELDS, INTEGER_FIELDS)
                && absentOr(json.get("ord"), v -> v.getAsLong() >= 0)
                && absentOr(json.get("off"), v -> v.getAsLong() >= 0)
                && absentOr(json.get("flags"), v -> listOf(v, SessionDataFile::isString))
                && absentOr(json.get("usage"), v -> v.isJsonObject()
                    && fields(v.getAsJsonObject(), NONE, USAGE_FIELDS))
                && absentOr(json.get("parts"), v -> listOf(v, p -> p.isJsonObject()
                    && fields(p.getAsJsonObject(), PART_STRING_FIELDS, BYTES)
                    && absentOr(p.getAsJsonObject().get("failed"), SessionDataFile::isBoolean)))
                && absentOr(json.get("dropped"), v -> listOf(v, d -> d.isJsonObject()
                    && fields(d.getAsJsonObject(), DROP_STRING_FIELDS, BYTES)));
        }

        /** Whether each field named is absent, null, or of its type: a string, or an integer as the page writes one. */
        private static boolean fields(final JsonObject json, final String[] strings, final String[] integers) {
            for (final String key : strings) {
                if (!absentOr(json.get(key), SessionDataFile::isString)) {
                    return false;
                }
            }
            for (final String key : integers) {
                if (!absentOr(json.get(key), SessionDataFile::isInteger)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean absentOr(@Nullable final JsonElement v, final Predicate<JsonElement> type) {
            return v == null || v.isJsonNull() || type.test(v);
        }

        private static boolean listOf(final JsonElement v, final Predicate<JsonElement> element) {
            if (!v.isJsonArray()) {
                return false;
            }
            for (final JsonElement x : v.getAsJsonArray()) {
                if (!element.test(x)) {
                    return false;
                }
            }
            return true;
        }

        private static final String[] NONE = {};
        private static final String[] BYTES = {"bytes"};
        private static final String[] STRING_FIELDS = {
            "sha", "id", "parent", "call", "run", "continues", "tool", "child", "batch", "label",
            "started_by", "from", "time", "trigger", "model"
        };
        private static final String[] INTEGER_FIELDS = {"ord", "off", "bytes"};
        private static final String[] USAGE_FIELDS = {"in", "out", "cache_read", "cache_write"};
        private static final String[] PART_STRING_FIELDS = {
            "k", "text", "id", "name", "of", "server", "server_tool", "media", "encoding", "state"
        };
        private static final String[] DROP_STRING_FIELDS = {"what", "why"};
    }

    /**
     * One piece of a record: readable text, a call, a result, or data kept whole.
     */
    @Getter
    public static final class Part {
        private final JsonObject json;
        private final String kind;
        private final String text;
        private final String name;
        private final String id;
        private final String of;
        private final String state;
        private final long bytes;
        private final Boolean failed;
        private final String rawData;

        Part(final JsonObject json, @Nullable final String rawData) {
            this.json = json;
            this.rawData = rawData;
            this.kind = string(json, "k");
            this.text = string(json, "text");
            this.name = string(json, "name");
            this.id = string(json, "id");
            this.of = string(json, "of");
            this.state = string(json, "state");
            this.bytes = longOf(json, "bytes");
            final JsonElement f = json.get("failed");
            this.failed = f == null || f.isJsonNull() ? null : f.getAsBoolean();
        }

        /**
         * @return the part's <code>data</code> as the line holds it, or null when it has none
         */
        @Nullable
        public String data() {
            if (rawData != null) {
                return rawData;
            }
            final JsonElement d = json.get("data");
            // data is kept as written, so a data of null is the text "null"
            return d == null ? null : d.toString();
        }
    }
}
