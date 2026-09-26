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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * One Session Flow (<code>.sf</code>) round, decoded from its stored bytes: the header frame, the node, relation
 * and unresolved frames, and the commit frame whose digest covers every line before it.
 */
@Getter
public final class SessionFlowRound {
    /** The attribute a call node carries the provider bodies joined to it under. */
    public static final String PROVIDER_BODIES_ATTR = "provider_bodies";
    public static final String ROLE_REQUEST = "request";
    public static final String ROLE_RESPONSE = "response";
    /** The Sessionizer's <code>sessionflow.Ref</code>, field for field and tag for tag. */
    public static final GoJson.Struct REF = new GoJson.Struct()
        .field("seq", GoJson.Kind.UINT)
        .field("row", GoJson.Kind.UINT)
        .field("block,omitempty", GoJson.Kind.INT_POINTER);
    /** The Sessionizer's <code>sessionflow.ProviderBody</code>, field for field and tag for tag. */
    private static final GoJson.Struct PROVIDER_BODY = new GoJson.Struct()
        .field("role", GoJson.Kind.STRING)
        .field("ref", GoJson.Kind.STRUCT, REF);

    private final Header header;
    private final List<Node> nodes;
    private final List<Relation> relations;
    private final List<Unresolved> unresolved;
    /** The digest the commit frame claims. */
    private final String commitDigest;
    /** The digest computed over every line before the commit frame; equal to {@link #commitDigest} when intact. */
    private final String computedDigest;
    private final int lines;
    private final int bytes;

    private SessionFlowRound(final Header header, final List<Node> nodes, final List<Relation> relations,
                             final List<Unresolved> unresolved, final String commitDigest,
                             final String computedDigest, final int lines, final int bytes) {
        this.header = header;
        this.nodes = nodes;
        this.relations = relations;
        this.unresolved = unresolved;
        this.commitDigest = commitDigest;
        this.computedDigest = computedDigest;
        this.lines = lines;
        this.bytes = bytes;
    }

    /**
     * @param body the round bytes as stored
     * @return the decoded round
     * @throws IllegalArgumentException when the first frame is not a header or the round has no commit frame
     */
    public static SessionFlowRound parse(final byte[] body) {
        final String text = new String(body, StandardCharsets.UTF_8);
        final String[] rawLines = text.split("\n", -1);
        final MessageDigest hashed = Digests.sha256();
        Header header = null;
        final List<Node> nodes = new ArrayList<>();
        final List<Relation> relations = new ArrayList<>();
        final List<Unresolved> unresolved = new ArrayList<>();
        String commitDigest = null;
        JsonObject commitCounts = null;
        boolean sawCommit = false;
        // the checks the Sessionizer's own reader makes; a round that fails one is not a round
        final Map<String, String> ids = new HashMap<>();
        int lineNo = 0;
        for (final String line : rawLines) {
            lineNo++;
            if (line.isEmpty()) {
                continue;
            }
            // read as Go reads a frame: a line Go's decoder refuses is no frame, whatever Gson's lenient parser accepts
            final JsonElement frame = GoJson.parse(line);
            if (frame == null || !frame.isJsonObject()) {
                throw new IllegalArgumentException("line " + lineNo + " is not a JSON object");
            }
            final JsonObject json = frame.getAsJsonObject();
            final String t = SessionDataFile.string(json, "t");
            if (t == null) {
                throw new IllegalArgumentException("a Session Flow frame without a type");
            }
            if (sawCommit) {
                throw new IllegalArgumentException("content after the commit frame");
            }
            switch (t) {
                case "header":
                    if (header != null) {
                        throw new IllegalArgumentException("a second header");
                    }
                    header = new Header(json);
                    header.validate();
                    break;
                case "node": {
                    final String attrsText = attrsText(line);
                    // an attribute that names provider bodies but does not read as them is refused, not skipped
                    final List<ProviderBody> bodies;
                    try {
                        bodies = providerBodiesOf(attrsText);
                    } catch (final IllegalArgumentException e) {
                        throw new IllegalArgumentException("line " + lineNo + ": " + e.getMessage());
                    }
                    final Node n = new Node(json, attrsText, bodies);
                    claim(ids, lineNo, "node", n.getId());
                    checkRevision(lineNo, n.getRevision(), header);
                    checkRef(lineNo, n.getRef(), header);
                    checkRefs(lineNo, n.getRefs(), header);
                    // a provider body a call names is a reference like any other, so it must lie in the round's range
                    for (final ProviderBody y : bodies) {
                        checkRef(lineNo, y.getRef(), header);
                    }
                    nodes.add(n);
                    break;
                }
                case "relation": {
                    final Relation rel = new Relation(json);
                    claim(ids, lineNo, "relation", rel.getId());
                    checkRevision(lineNo, rel.getRevision(), header);
                    if (!rel.isTombstone() && (StringUtil.isEmpty(rel.getFrom()) || StringUtil.isEmpty(rel.getTo())
                        || StringUtil.isEmpty(rel.getType()))) {
                        throw new IllegalArgumentException(
                            "line " + lineNo + ": relation " + rel.getId() + " is missing an endpoint or a type");
                    }
                    checkRefs(lineNo, rel.getEvidence(), header);
                    relations.add(rel);
                    break;
                }
                case "unresolved": {
                    final Unresolved u = new Unresolved(json);
                    claim(ids, lineNo, "unresolved", u.getId());
                    checkRevision(lineNo, u.getRevision(), header);
                    if (!u.isTombstone() && !"open".equals(u.getState()) && !"resolved".equals(u.getState())
                        && !"terminal".equals(u.getState())) {
                        throw new IllegalArgumentException(
                            "line " + lineNo + ": unresolved entry " + u.getId() + " has state " + u.getState());
                    }
                    unresolved.add(u);
                    break;
                }
                case "commit":
                    commitDigest = SessionDataFile.string(json, "digest");
                    commitCounts = json.has("counts") && json.get("counts").isJsonObject()
                        ? json.getAsJsonObject("counts") : null;
                    sawCommit = true;
                    continue;
                default:
                    throw new IllegalArgumentException("unknown frame type " + t);
            }
            if (header == null) {
                throw new IllegalArgumentException("the first frame is not a header");
            }
            hashed.update(line.getBytes(StandardCharsets.UTF_8));
            hashed.update((byte) '\n');
        }
        if (header == null) {
            throw new IllegalArgumentException("the round has no header");
        }
        if (!sawCommit) {
            throw new IllegalArgumentException("the round has no commit frame; it is truncated");
        }
        // the commit frame is outside the digest, so its counts are what catches a tampered commit
        final long claimedNodes = commitCounts == null ? 0 : SessionDataFile.longOf(commitCounts, "nodes");
        final long claimedRelations = commitCounts == null ? 0 : SessionDataFile.longOf(commitCounts, "relations");
        final long claimedUnresolved = commitCounts == null ? 0 : SessionDataFile.longOf(commitCounts, "unresolved");
        if (claimedNodes != nodes.size() || claimedRelations != relations.size()
            || claimedUnresolved != unresolved.size()) {
            throw new IllegalArgumentException("counts mismatch: read nodes " + nodes.size() + " relations "
                                                   + relations.size() + " unresolved " + unresolved.size()
                                                   + ", round claims nodes " + claimedNodes + " relations "
                                                   + claimedRelations + " unresolved " + claimedUnresolved);
        }
        return new SessionFlowRound(
            header, Collections.unmodifiableList(nodes), Collections.unmodifiableList(relations),
            Collections.unmodifiableList(unresolved), commitDigest, Digests.hex(hashed.digest()),
            Digests.countLines(body), body.length);
    }

    private static void claim(final Map<String, String> ids, final int line, final String kind, final String id) {
        if (StringUtil.isEmpty(id)) {
            throw new IllegalArgumentException("line " + line + ": " + kind + " frame has no id");
        }
        final String prev = ids.put(id, kind);
        if (prev != null) {
            throw new IllegalArgumentException(
                "line " + line + ": id " + id + " appears twice in one round (as " + prev + " and " + kind + ")");
        }
    }

    /**
     * An entity names the round that produced it; one that names another was not produced by this round.
     */
    private static void checkRevision(final int line, final long revision, final Header header) {
        if (revision != header.getRound()) {
            throw new IllegalArgumentException("line " + line + ": revision " + revision + " in round " + header.getRound());
        }
    }

    /**
     * A reference past the range the header declares it read describes evidence the round did not claim to
     * have seen, and its input digest does not cover it.
     */
    private static void checkRef(final int line, @Nullable final Ref r, final Header header) {
        if (r == null) {
            return;
        }
        if (r.getSeq() == 0 && r.getRow() == 0) {
            throw new IllegalArgumentException("line " + line + ": a reference to seq 0 row 0 is not a position");
        }
        if (r.getSeq() > header.getThroughSeq()) {
            throw new IllegalArgumentException("line " + line + ": reference to landed sequence " + r.getSeq()
                                                   + ", past the round's declared " + header.getThroughSeq());
        }
    }

    private static void checkRefs(final int line, final List<Ref> refs, final Header header) {
        for (final Ref r : refs) {
            checkRef(line, r, header);
        }
    }

    /**
     * The text of a node's attrs as Go picks it: the value of the last key that names the field, in any case, as the
     * Sessionizer's reader keeps it as written.
     *
     * @param line a frame that reads as one JSON object, so it reads member by member
     */
    @Nullable
    private static String attrsText(final String line) {
        String text = null;
        for (final String[] m : GoJson.members(line)) {
            if (m[0].equalsIgnoreCase("attrs")) {
                text = m[1];
            }
        }
        return text;
    }

    /**
     * The provider bodies a node carries, read as the Sessionizer's <code>sessionflow.ProviderBodiesOf</code> reads
     * them: the attributes as a map of values kept as written, so a key given twice keeps its later value and the
     * earlier one is never decoded, then that value as a list of bodies. The join is made when the round is parsed,
     * and the round carries it, so no reader repeats it.
     *
     * @param attrs a node's attributes as written, or null
     * @return the bodies, none when the attributes are not an object or do not name them
     * @throws IllegalArgumentException when the attribute is there but is not a list of bodies: a body whose role is
     *                                  not request or response, or whose seq or row is zero, names nothing
     */
    static List<ProviderBody> providerBodiesOf(@Nullable final String attrs) {
        final List<String[]> members = attrs == null ? null : GoJson.members(attrs);
        if (members == null) {
            return Collections.emptyList();
        }
        String raw = null;
        for (final String[] m : members) {
            if (PROVIDER_BODIES_ATTR.equals(m[0])) {
                raw = m[1];
            }
        }
        if (raw == null) {
            return Collections.emptyList();
        }
        final JsonArray decoded = GoJson.decodeSlice(raw, PROVIDER_BODY);
        if (decoded == null) {
            throw new IllegalArgumentException(PROVIDER_BODIES_ATTR + " is not a list of provider bodies");
        }
        final List<ProviderBody> out = new ArrayList<>();
        for (final JsonElement e : decoded) {
            final String role = e.getAsJsonObject().get("role").getAsString();
            if (!ROLE_REQUEST.equals(role) && !ROLE_RESPONSE.equals(role)) {
                throw new IllegalArgumentException("a provider body has the role \"" + role + "\"");
            }
            final Ref ref = Ref.of(e.getAsJsonObject().getAsJsonObject("ref"));
            // both halves of the position, not just one: sequences and rows are counted from one, so a zero in either
            // names no landed record
            if (ref.getSeq() == 0 || ref.getRow() == 0) {
                throw new IllegalArgumentException("a provider body is at seq " + ref.getSeq() + " row " + ref.getRow()
                                                       + ", which is no position");
            }
            out.add(new ProviderBody(role, ref));
        }
        return out;
    }

    public boolean isIntact() {
        return commitDigest != null && commitDigest.equals(computedDigest);
    }

    /**
     * The header frame: which chain the round belongs to, where it sits in it, and what it consumed.
     */
    @Getter
    public static final class Header {
        private final String schema;
        private final String conversation;
        private final String session;
        private final long round;
        private final String previous;
        private final long fromSeq;
        private final long throughSeq;
        private final String inputDigest;
        private final String parser;
        private final String policy;
        private final String fromTime;
        private final String throughTime;
        private final String sessionFromTime;
        private final String sessionThroughTime;

        Header(final JsonObject json) {
            this.schema = SessionDataFile.string(json, "schema");
            this.conversation = SessionDataFile.string(json, "conversation");
            this.session = SessionDataFile.string(json, "session");
            this.round = SessionDataFile.longOf(json, "round");
            this.previous = SessionDataFile.string(json, "previous");
            this.fromSeq = SessionDataFile.longOf(json, "from_seq");
            this.throughSeq = SessionDataFile.longOf(json, "through_seq");
            this.inputDigest = SessionDataFile.string(json, "input_digest");
            this.parser = SessionDataFile.string(json, "parser");
            this.policy = SessionDataFile.string(json, "policy");
            this.fromTime = SessionDataFile.string(json, "from_time");
            this.throughTime = SessionDataFile.string(json, "through_time");
            this.sessionFromTime = SessionDataFile.string(json, "session_from_time");
            this.sessionThroughTime = SessionDataFile.string(json, "session_through_time");
        }

        /**
         * The header the Sessionizer's own reader would refuse: it cannot be acted on.
         */
        void validate() {
            if (!"sf/1".equals(schema)) {
                throw new IllegalArgumentException("unsupported schema " + schema + ", want sf/1");
            }
            if (StringUtil.isEmpty(conversation)) {
                throw new IllegalArgumentException("header missing conversation");
            }
            if (StringUtil.isEmpty(session)) {
                throw new IllegalArgumentException("header missing session");
            }
            if (round == 0) {
                throw new IllegalArgumentException("round must count from 1");
            }
            if (round > 1 && StringUtil.isEmpty(previous)) {
                throw new IllegalArgumentException("round " + round + " has no previous digest; the chain would be unverifiable");
            }
            if (round == 1 && StringUtil.isNotEmpty(previous)) {
                throw new IllegalArgumentException("round 1 must not name a previous digest");
            }
            if (StringUtil.isEmpty(parser)) {
                throw new IllegalArgumentException("header missing parser version");
            }
            if (StringUtil.isEmpty(policy)) {
                throw new IllegalArgumentException("header missing policy version");
            }
            if (StringUtil.isEmpty(inputDigest)) {
                throw new IllegalArgumentException("header missing input digest");
            }
            if (fromSeq == 0) {
                throw new IllegalArgumentException("landed sequences count from 1, so from_seq must not be 0");
            }
            if (throughSeq < fromSeq - 1) {
                throw new IllegalArgumentException("round " + round + " consumes sequences " + fromSeq + ".." + throughSeq + ", which is not a range");
            }
        }
    }

    /**
     * What every entity frame has: an id, the revision that produced it, and whether it is a tombstone.
     */
    @Getter
    public abstract static class Entity {
        private final String id;
        private final long revision;
        private final boolean tombstone;

        Entity(final JsonObject json) {
            this.id = SessionDataFile.string(json, "id");
            this.revision = SessionDataFile.longOf(json, "revision");
            final JsonElement t = json.get("tombstone");
            this.tombstone = t != null && !t.isJsonNull() && t.getAsBoolean();
        }
    }

    @Getter
    public static final class Node extends Entity {
        private final String kind;
        private final String parent;
        private final String stream;
        @Nullable
        private final Ref ref;
        private final List<Ref> refs;
        /** The attrs as written, for decoding as Go decodes them; null when the frame has none. */
        @Nullable
        private final String attrsText;
        /** The attrs as written, any JSON value, for rendering; null when the frame has none. */
        @Nullable
        private final JsonElement rawAttrs;
        /**
         * The attrs for lookups, as the Sessionizer decodes them into a map: null when they are not an object, or hold
         * a number past a double's range, which fails the whole decoding, so every lookup then finds nothing.
         */
        private final JsonObject attrs;
        /** The provider bodies the node carries, read from its attributes as written. */
        private final List<ProviderBody> providerBodies;

        Node(final JsonObject json, @Nullable final String attrsText, final List<ProviderBody> providerBodies) {
            super(json);
            this.kind = SessionDataFile.string(json, "kind");
            this.parent = SessionDataFile.string(json, "parent");
            this.stream = SessionDataFile.string(json, "stream");
            this.ref = json.has("ref") && json.get("ref").isJsonObject() ? Ref.of(json.getAsJsonObject("ref")) : null;
            final List<Ref> list = new ArrayList<>();
            if (json.has("refs") && json.get("refs").isJsonArray()) {
                for (final JsonElement e : json.getAsJsonArray("refs")) {
                    list.add(Ref.of(e.getAsJsonObject()));
                }
            }
            this.refs = Collections.unmodifiableList(list);
            this.attrsText = attrsText;
            this.rawAttrs = attrsText == null ? null : GoJson.parse(attrsText);
            this.attrs = rawAttrs != null && rawAttrs.isJsonObject() && GoJson.decodesAsAny(attrsText)
                ? rawAttrs.getAsJsonObject() : null;
            this.providerBodies = Collections.unmodifiableList(providerBodies);
        }

        /**
         * @return the attr when it is a string, as the Sessionizer's <code>attrString</code>; null for a number,
         * an object or nothing
         */
        @Nullable
        public String attr(final String key) {
            if (attrs == null) {
                return null;
            }
            final JsonElement e = attrs.get(key);
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
        }

        public double attrNumber(final String key) {
            if (attrs == null) {
                return 0;
            }
            final JsonElement e = attrs.get(key);
            return e == null || e.isJsonNull() || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()
                ? 0 : e.getAsDouble();
        }

        public boolean attrBool(final String key) {
            if (attrs == null) {
                return false;
            }
            final JsonElement e = attrs.get(key);
            return e != null && !e.isJsonNull() && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()
                && e.getAsBoolean();
        }
    }

    /**
     * One landed provider body joined to a call: whether it is the request or the response, and the record it
     * rebuilds from. The body itself is never in a round.
     */
    @Getter
    @RequiredArgsConstructor
    public static final class ProviderBody {
        private final String role;
        private final Ref ref;
    }

    @Getter
    public static final class Relation extends Entity {
        private final String type;
        private final String from;
        private final String to;
        private final String quality;
        private final String via;
        private final List<Ref> evidence;

        Relation(final JsonObject json) {
            super(json);
            this.type = SessionDataFile.string(json, "type");
            this.from = SessionDataFile.string(json, "from");
            this.to = SessionDataFile.string(json, "to");
            this.quality = SessionDataFile.string(json, "quality");
            this.via = SessionDataFile.string(json, "via");
            final List<Ref> list = new ArrayList<>();
            if (json.has("evidence") && json.get("evidence").isJsonArray()) {
                for (final JsonElement e : json.getAsJsonArray("evidence")) {
                    list.add(Ref.of(e.getAsJsonObject()));
                }
            }
            this.evidence = Collections.unmodifiableList(list);
        }
    }

    @Getter
    public static final class Unresolved extends Entity {
        private final String kind;
        private final String ref;
        private final String reason;
        private final String state;

        Unresolved(final JsonObject json) {
            super(json);
            this.kind = SessionDataFile.string(json, "kind");
            this.ref = SessionDataFile.string(json, "ref");
            this.reason = SessionDataFile.string(json, "reason");
            this.state = SessionDataFile.string(json, "state");
        }
    }
}
