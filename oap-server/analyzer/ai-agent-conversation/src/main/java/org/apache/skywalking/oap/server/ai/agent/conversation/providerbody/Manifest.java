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
 */

package org.apache.skywalking.oap.server.ai.agent.conversation.providerbody;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;

/**
 * The manifest of a provider body record, schema <code>provider_body/1</code>, as the Session Data page defines
 * it: the last part of the record, which says what the body is and how to put it back together from the segments
 * it lists. Joined in order, the segments are the body: <code>lit</code> is a string whose UTF-8 bytes are literal
 * bytes of the body, <code>part</code> is a part of this record, <code>piece</code> is the digest of a piece an
 * earlier record of the session holds, and <code>copy</code> is the first <code>len</code> bytes of an earlier body.
 */
@Getter
public final class Manifest {
    public static final String SCHEMA = "provider_body/1";
    /** The kind of a Session Data file that holds bodies. */
    public static final String KIND = "provider_body";
    public static final String ROLE_REQUEST = "request";
    public static final String ROLE_RESPONSE = "response";
    /** A body may claim at most this many bytes, as the page says. */
    public static final long MAX_BYTES = 256L * 1024 * 1024;
    /** How many copies may lie between a body and one with none, as the page says. */
    public static final int MAX_DEPTH = 32;
    /** The four kinds of segment the page gives, each a key of its own. */
    private static final List<String> SEGMENT_KINDS = Arrays.asList("lit", "part", "piece", "copy");
    /** The manifest's fields the page gives as text. */
    private static final List<String> STRING_KEYS = Arrays.asList(
        "schema", "role", "src", "sha256", "chain", "why", "model", "session", "run", "call", "request", "previous_request");

    /** The manifest as written, so a rewrite keeps every field it does not change. */
    private final JsonObject json;
    private final String role;
    private final String sha256;
    private final long bytes;
    private final long depth;
    private final List<Segment> segments;
    /** The part the manifest is, so the parts before it are the record's own pieces. */
    private final int partIndex;
    /** Whether the manifest has the shape the page gives it; see {@link #wellFormed(JsonObject)}. */
    private final boolean wellFormed;

    private Manifest(final JsonObject json, final int partIndex) {
        this.json = json;
        this.partIndex = partIndex;
        this.role = string(json, "role");
        this.sha256 = string(json, "sha256");
        this.bytes = longOf(json, "bytes");
        this.depth = longOf(json, "depth");
        this.wellFormed = wellFormed(json);
        final List<Segment> list = new ArrayList<>();
        final JsonElement segs = json.get("segments");
        if (segs != null && segs.isJsonArray()) {
            for (final JsonElement e : segs.getAsJsonArray()) {
                list.add(Segment.of(e));
            }
        }
        this.segments = Collections.unmodifiableList(list);
    }

    /**
     * @param rec a record of a <code>provider_body</code> file
     * @return its manifest, or null when the record carries none: its last part is not a <code>data</code> part
     * holding an object of the schema
     */
    @Nullable
    public static Manifest of(final SessionDataFile.Record rec) {
        final List<SessionDataFile.Part> parts = rec.getParts();
        if (parts.isEmpty()) {
            return null;
        }
        final int last = parts.size() - 1;
        final SessionDataFile.Part p = parts.get(last);
        if (!"data".equals(p.getKind())) {
            return null;
        }
        final JsonElement parsed = Schema.parse(p.data());
        if (parsed == null || !parsed.isJsonObject() || !SCHEMA.equals(string(parsed.getAsJsonObject(), "schema"))) {
            return null;
        }
        return new Manifest(parsed.getAsJsonObject(), last);
    }

    public boolean isRequest() {
        return ROLE_REQUEST.equals(role);
    }

    public boolean isResponse() {
        return ROLE_RESPONSE.equals(role);
    }

    /**
     * @return the <code>why</code> the page sets when the body was kept whole in one part because it could not
     * be cut, or null
     */
    @Nullable
    public String why() {
        final String why = string(json, "why");
        return why.isEmpty() ? null : why;
    }

    static String string(final JsonObject json, final String key) {
        final JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : "";
    }

    /**
     * Whether the manifest has the shape the page gives it: every field it gives has the type it gives it, where it is
     * present and not null, its text fields text, its numbers integers, and its segments a list in which each is one of
     * the four kinds the page gives, a <code>lit</code>, a <code>part</code>, a <code>piece</code> or a
     * <code>copy</code>, with that one key set to a value of its type, and a copy's own fields theirs. A segment that is
     * null, sets none of the four or sets more than one is none of them: the page says nothing of what it adds to a
     * body, so it is refused rather than read one way or another. The Sessionizer's reader, decoding into its own
     * types, reads a null or empty segment as one that adds nothing.
     */
    private static boolean wellFormed(final JsonObject json) {
        for (final String key : STRING_KEYS) {
            if (!textOrAbsent(json.get(key))) {
                return false;
            }
        }
        if (!integerOrAbsent(json.get("bytes")) || !integerOrAbsent(json.get("depth"))) {
            return false;
        }
        final JsonElement segs = json.get("segments");
        if (segs == null || segs.isJsonNull()) {
            return true;
        }
        if (!segs.isJsonArray()) {
            return false;
        }
        for (final JsonElement e : segs.getAsJsonArray()) {
            if (!e.isJsonObject()) {
                return false;
            }
            final JsonObject seg = e.getAsJsonObject();
            if (!textOrAbsent(seg.get("lit")) || !textOrAbsent(seg.get("piece")) || !integerOrAbsent(seg.get("part"))) {
                return false;
            }
            int kinds = 0;
            for (final String kind : SEGMENT_KINDS) {
                if (seg.get(kind) != null && !seg.get(kind).isJsonNull()) {
                    kinds++;
                }
            }
            if (kinds != 1) {
                return false;
            }
            final JsonElement copy = seg.get("copy");
            if (copy == null || copy.isJsonNull()) {
                continue;
            }
            if (!copy.isJsonObject()) {
                return false;
            }
            final JsonObject c = copy.getAsJsonObject();
            if (!textOrAbsent(c.get("from")) || !textOrAbsent(c.get("sha256")) || !integerOrAbsent(c.get("len"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean textOrAbsent(@Nullable final JsonElement e) {
        return e == null || e.isJsonNull() || e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    private static boolean integerOrAbsent(@Nullable final JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return true;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        try {
            Long.parseLong(e.getAsString());
            return true;
        } catch (final NumberFormatException ex) {
            return false;
        }
    }

    /**
     * @return the integer under the key: 0 when the key is absent, and -1, which no field of a manifest may hold,
     * when the value is not an integer as the page writes one, with no fraction and no exponent, in the range of a
     * long, so a value that is not is refused rather than read as another, as <code>0.5</code>, <code>0e0</code> or a
     * number past the range would be. It reads a number as the other integer fields of a file are read.
     */
    static long longOf(final JsonObject json, final String key) {
        final JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return 0;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return -1;
        }
        try {
            return Long.parseLong(e.getAsString());
        } catch (final NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * One segment of a manifest. Exactly one of its fields is set, by the key the segment was written under.
     */
    @Getter
    public static final class Segment {
        /** Literal text whose UTF-8 bytes are bytes of the body. */
        @Nullable
        private final String lit;
        /** The index of a part of this record, or null when the segment is not a part; -1 when it is not an index. */
        @Nullable
        private final Long part;
        /** The digest of a piece an earlier record holds, or null. */
        @Nullable
        private final String piece;
        /** The record id whose body's front is copied, or null. */
        @Nullable
        private final String copyFrom;
        /** The digest that body must have. */
        @Nullable
        private final String copySha256;
        /** How many of its first bytes are copied. */
        private final long copyLen;
        /** Set when the segment is none of the four, so a rebuild can refuse it. */
        private final boolean unknown;

        private Segment(@Nullable final String lit, @Nullable final Long part, @Nullable final String piece,
                        @Nullable final String copyFrom, @Nullable final String copySha256, final long copyLen,
                        final boolean unknown) {
            this.lit = lit;
            this.part = part;
            this.piece = piece;
            this.copyFrom = copyFrom;
            this.copySha256 = copySha256;
            this.copyLen = copyLen;
            this.unknown = unknown;
        }

        static Segment of(final JsonElement e) {
            if (!e.isJsonObject()) {
                return new Segment(null, null, null, null, null, 0, true);
            }
            final JsonObject o = e.getAsJsonObject();
            final JsonElement lit = o.get("lit");
            if (lit != null && lit.isJsonPrimitive() && lit.getAsJsonPrimitive().isString()) {
                return new Segment(lit.getAsString(), null, null, null, null, 0, false);
            }
            final JsonElement part = o.get("part");
            if (part != null && !part.isJsonNull()) {
                return new Segment(null, longOf(o, "part"), null, null, null, 0, false);
            }
            final String piece = string(o, "piece");
            if (!piece.isEmpty()) {
                return new Segment(null, null, piece, null, null, 0, false);
            }
            final JsonElement copy = o.get("copy");
            if (copy != null && copy.isJsonObject()) {
                final JsonObject c = copy.getAsJsonObject();
                return new Segment(null, null, null, string(c, "from"), string(c, "sha256"), longOf(c, "len"), false);
            }
            return new Segment(null, null, null, null, null, 0, true);
        }

        /**
         * @return a manifest's segments array holding one literal of the whole body
         */
        public static JsonArray literal(final String body) {
            final JsonObject seg = new JsonObject();
            seg.addProperty("lit", body);
            final JsonArray out = new JsonArray();
            out.add(seg);
            return out;
        }
    }
}
