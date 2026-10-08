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

    /** The manifest as written, so a rewrite keeps every field it does not change. */
    private final JsonObject json;
    private final String role;
    private final String sha256;
    private final long bytes;
    private final int depth;
    private final List<Segment> segments;
    /** The part the manifest is, so the parts before it are the record's own pieces. */
    private final int partIndex;

    private Manifest(final JsonObject json, final int partIndex) {
        this.json = json;
        this.partIndex = partIndex;
        this.role = string(json, "role");
        this.sha256 = string(json, "sha256");
        this.bytes = longOf(json, "bytes");
        this.depth = (int) longOf(json, "depth");
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

    static long longOf(final JsonObject json, final String key) {
        final JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        try {
            return e.getAsLong();
        } catch (final NumberFormatException ex) {
            return 0;
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
        /** The index of a part of this record, or -1. */
        private final int part;
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

        private Segment(@Nullable final String lit, final int part, @Nullable final String piece,
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
                return new Segment(null, -1, null, null, null, 0, true);
            }
            final JsonObject o = e.getAsJsonObject();
            final JsonElement lit = o.get("lit");
            if (lit != null && lit.isJsonPrimitive() && lit.getAsJsonPrimitive().isString()) {
                return new Segment(lit.getAsString(), -1, null, null, null, 0, false);
            }
            final JsonElement part = o.get("part");
            if (part != null && part.isJsonPrimitive() && part.getAsJsonPrimitive().isNumber()) {
                return new Segment(null, (int) longOf(o, "part"), null, null, null, 0, false);
            }
            final String piece = string(o, "piece");
            if (!piece.isEmpty()) {
                return new Segment(null, -1, piece, null, null, 0, false);
            }
            final JsonElement copy = o.get("copy");
            if (copy != null && copy.isJsonObject()) {
                final JsonObject c = copy.getAsJsonObject();
                return new Segment(null, -1, null, string(c, "from"), string(c, "sha256"), longOf(c, "len"), false);
            }
            return new Segment(null, -1, null, null, null, 0, true);
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
