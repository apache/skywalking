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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    /**
     * Whether the records end at a line that does not decode, before the closing line. The Sessionizer's raw reader
     * still returns that line, and a reader that needs every line, such as the gap check, must know it is there.
     */
    private final boolean stoppedEarly;
    /** The earliest and the latest record time in the file, in milliseconds; 0 when no record carries a time. */
    private final long fromTime;
    private final long throughTime;

    private SessionDataFile(final Header header, final List<Record> records, final int declaredRecords,
                            final String declaredDigest, final int lines, final int bytes,
                            final String fileDigest, final boolean stoppedEarly, final long fromTime,
                            final long throughTime) {
        this.header = header;
        this.records = records;
        this.declaredRecords = declaredRecords;
        this.declaredDigest = declaredDigest;
        this.lines = lines;
        this.bytes = bytes;
        this.fileDigest = fileDigest;
        this.stoppedEarly = stoppedEarly;
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
        final JsonObject headerJson = JsonParser.parseString(rawLines[0]).getAsJsonObject();
        if (!headerJson.has("h")) {
            throw new IllegalArgumentException("the first line is not a Session Data header");
        }
        final Header header = new Header(headerJson);
        final List<Record> records = new ArrayList<>(Math.max(0, lineCount - 2));
        int declaredRecords = -1;
        String declaredDigest = null;
        long from = 0;
        long through = 0;
        boolean stoppedEarly = false;
        // as the Sessionizer's reader: a header it would refuse yields no records, and an empty or undecodable
        // line ends the records there, so a later row is never read and the rows stay contiguous
        for (int i = 1; header.isValid() && i < lineCount; i++) {
            final String line = rawLines[i];
            if (line.isEmpty()) {
                stoppedEarly = true;
                break;
            }
            final JsonObject json;
            try {
                json = JsonParser.parseString(line).getAsJsonObject();
            } catch (final RuntimeException e) {
                stoppedEarly = true;
                break;
            }
            if (i == lineCount - 1 && "end".equals(string(json, "t"))) {
                declaredRecords = json.has("records") ? json.get("records").getAsInt() : -1;
                declaredDigest = string(json, "digest");
                break;
            }
            final Record record = new Record(i, line, json);
            if (!record.decodes()) {
                // The Sessionizer decodes a whole typed record and stops the file where one does not: a
                // record whose `off` is a string, say. Reading past it here would report bodies its own
                // reader never sees.
                stoppedEarly = true;
                break;
            }
            records.add(record);
            final long time = record.getTime();
            if (time != 0) {
                if (from == 0 || time < from) {
                    from = time;
                }
                if (time > through) {
                    through = time;
                }
            }
        }
        return new SessionDataFile(
            header, Collections.unmodifiableList(records), declaredRecords, declaredDigest,
            Digests.countLines(body), body.length, Digests.sha256Hex(body), stoppedEarly, from, through);
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
        final JsonObject json = JsonParser.parseString(new String(body, 0, end, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!json.has("h")) {
            throw new IllegalArgumentException("the first line is not a Session Data header");
        }
        return new Header(json);
    }

    /**
     * @param row the line number, the header being row 0
     * @return the record on that line, or null when there is none
     */
    @Nullable
    public Record record(final long row) {
        final int index = (int) row - 1;
        if (index < 0 || index >= records.size()) {
            return null;
        }
        return records.get(index);
    }

    @Nullable
    static String string(final JsonObject json, final String key) {
        final JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    static long longOf(final JsonObject json, final String key) {
        final JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? 0L : element.getAsLong();
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
         * @return whether the Sessionizer's reader would open the file: the envelope version, the schema, the
         * kind, the session, the source and the dialect are all there
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
        /**
         * The digits after the line's leading <code>{"ord":</code>, as the Sessionizer reads an ord without decoding
         * the line; empty when none follow, and null when the line does not start so.
         */
        @Nullable
        private final String leadingOrd;

        Record(final int row, final String line, final JsonObject json) {
            this.row = row;
            this.leadingOrd = leadingOrd(line);
            this.json = json;
            this.id = string(json, "id");
            this.timeNanos = Times.nanos(string(json, "time"));
            this.time = Math.floorDiv(timeNanos, 1_000_000L);
            final List<Part> list = new ArrayList<>();
            if (json.has("parts") && json.get("parts").isJsonArray()) {
                // the raw data text of each part, as the Sessionizer wrote it; see RawJson
                final List<String> raw = RawJson.partData(line);
                int i = 0;
                for (final JsonElement e : json.getAsJsonArray("parts")) {
                    list.add(new Part(e.getAsJsonObject(), i < raw.size() ? raw.get(i) : null));
                    i++;
                }
            }
            this.parts = Collections.unmodifiableList(list);
        }

        @Nullable
        private static String leadingOrd(final String line) {
            final String prefix = "{\"ord\":";
            if (!line.startsWith(prefix)) {
                return null;
            }
            int i = prefix.length();
            while (i < line.length() && line.charAt(i) >= '0' && line.charAt(i) <= '9') {
                i++;
            }
            return line.substring(prefix.length(), i);
        }

        /**
         * @return the record's readable text: every <code>text</code> part joined by a newline, as the
         * Sessionizer's <code>Record.Text()</code> returns it
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
                // a null element decodes to the empty string, as it does into a Go []string
                out.add(x.isJsonNull() ? "" : x.getAsString());
            }
            return out;
        }

        /**
         * @return whether the Sessionizer's own reader decodes this record. Its fields are typed, so a value
         * of another type fails the whole record there, and the file's records end with it. Only the types
         * are checked here; what the values mean is the reader's business.
         */
        boolean decodes() {
            for (final String key : STRING_FIELDS) {
                final JsonElement v = json.get(key);
                if (v != null && !v.isJsonNull() && !(v.isJsonPrimitive() && v.getAsJsonPrimitive().isString())) {
                    return false;
                }
            }
            for (final String key : UNSIGNED_FIELDS) {
                if (!wholeNumber(json.get(key), true)) {
                    return false;
                }
            }
            if (!wholeNumber(json.get("bytes"), false)) {
                return false;
            }
            for (final String key : ARRAY_FIELDS) {
                final JsonElement v = json.get(key);
                if (v != null && !v.isJsonNull() && !v.isJsonArray()) {
                    return false;
                }
            }
            final JsonElement flags = json.get("flags");
            if (flags != null && flags.isJsonArray()) {
                for (final JsonElement x : flags.getAsJsonArray()) {
                    if (!x.isJsonNull() && !(x.isJsonPrimitive() && x.getAsJsonPrimitive().isString())) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * @param unsigned whether the Go type is an unsigned 64-bit integer rather than a signed one
         * @return whether the value is absent, null, or a number Go decodes into that type: no fraction, no
         * exponent, no sign where the type has none, and within its range. A JSON number this reader would
         * round is one the Sessionizer refuses outright.
         *
         * <p>The record's own fields are checked, and the shape of the lists it carries; what is inside
         * `parts`, `dropped` and `usage` is not. That is on purpose: Go ignores a field it does not declare,
         * so a check that guessed at those types would refuse records the Sessionizer reads, and refusing
         * one hides it and every record behind it in the file. Reading a record the Sessionizer would refuse
         * is the safer way to be wrong.
         */
        private static boolean wholeNumber(@Nullable final JsonElement v, final boolean unsigned) {
            if (v == null || v.isJsonNull()) {
                return true;
            }
            if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
                return false;
            }
            final String text = v.getAsString();
            if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
                return false;
            }
            if (unsigned && text.startsWith("-")) {
                return false;
            }
            try {
                final java.math.BigInteger n = new java.math.BigInteger(text);
                return unsigned ? n.bitLength() <= 64 : n.bitLength() < 64;
            } catch (final NumberFormatException e) {
                return false;
            }
        }

        private static final String[] STRING_FIELDS = {
            "sha", "id", "parent", "call", "run", "continues", "tool", "child", "batch", "label",
            "started_by", "from", "time", "trigger", "model"
        };
        private static final String[] UNSIGNED_FIELDS = {"ord", "off"};

        private static final String[] ARRAY_FIELDS = {"flags", "parts", "dropped"};
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
        private final int bytes;
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
            this.bytes = (int) longOf(json, "bytes");
            final JsonElement f = json.get("failed");
            this.failed = f == null || f.isJsonNull() ? null : f.getAsBoolean();
        }

        /**
         * @return the part's <code>data</code> as the Sessionizer wrote it, or null when it has none
         */
        @Nullable
        public String data() {
            if (rawData != null) {
                return rawData;
            }
            final JsonElement d = json.get("data");
            // a literal null is kept as the text "null": Go reads it into a raw message and prints it
            return d == null ? null : d.toString();
        }
    }
}
