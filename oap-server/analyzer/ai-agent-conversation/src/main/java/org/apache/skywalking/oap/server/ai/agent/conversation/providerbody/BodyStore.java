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

import com.google.gson.JsonElement;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;

/**
 * The provider body records of one session, in landing order, and any body rebuilt from them, as the Session Data
 * page's "Provider bodies" section says a reader does: a record refers only to what landed before it in the same
 * session, a piece is found by the digest of its bytes, a copy takes the front of an earlier body by its record id,
 * and a body rebuilds to the digest its manifest claims or not at all.
 *
 * <p>The page's two limits hold: a rebuild stops as soon as it passes the size the record claims, and a body may
 * claim at most {@link Manifest#MAX_BYTES}. A rebuilt body is kept, so a chain of copies costs each body once, and
 * the bodies kept longest go first when the kept ones pass the budget, as the Sessionizer's own reader keeps them:
 * that costs a rebuild, never a result.
 */
public final class BodyStore {
    private final Map<String, Held> records = new LinkedHashMap<>();
    /** The most bytes one rebuild may hold at once, and the most the kept bodies hold; see {@link #BodyStore(long)}. */
    private final long budget;
    /** The record and part holding each piece, by the piece's digest; the first record to hold a piece keeps it. */
    private final Map<String, int[]> pieceAt = new HashMap<>();
    private final Map<String, String> pieceRecord = new HashMap<>();
    /** Why each record that carries a body is not held, by its id: a size, a reference or a depth the page does not allow. */
    private final Map<String, String> refused = new HashMap<>();
    /** The rebuilt bodies kept, the one kept longest first. */
    private final Map<String, byte[]> rebuilt = new LinkedHashMap<>();
    private long kept;

    private static final class Held {
        /** The seq of the file the record is in, so the row held is known apart from a later one with its id. */
        final long seq;
        final SessionDataFile.Record record;
        final Manifest manifest;

        Held(final long seq, final SessionDataFile.Record record, final Manifest manifest) {
            this.seq = seq;
            this.record = record;
            this.manifest = manifest;
        }
    }

    /** A store that may keep as many rebuilt bytes as one body may claim. */
    public BodyStore() {
        this(Manifest.MAX_BYTES);
    }

    /**
     * @param budget the most bytes one rebuild may hold at once, and the most the kept bodies hold. A body's claimed
     *               size and its depth bound one body, not what a chain of them costs, so a body whose rebuild would
     *               hold more is refused rather than built: the pages' limits are not a limit on the heap
     */
    public BodyStore(final long budget) {
        this.budget = budget;
    }

    /**
     * Adds the records of one <code>provider_body</code> file, in row order. A record without a manifest, or with
     * no id, holds no body and is passed over.
     *
     * @param file a <code>provider_body</code> file of the session, added in seq order
     */
    public void add(final SessionDataFile file) {
        for (final SessionDataFile.Record rec : file.getRecords()) {
            add(file.getHeader().getSeq(), rec);
        }
    }

    /**
     * Holds a record's body as the Session Data page's "Provider bodies" section lets a reader: its manifest has the
     * shape the page gives it, every field of the type it gives and every segment one of its four kinds, its role is
     * request or response, it claims no more than {@link Manifest#MAX_BYTES}, every reference points at a record that landed
     * before it, a part it names is one it
     * has, a copy is of a body with the digest and at least the length it names, and the depth is one more than its
     * base's, or 0 when it copies nothing, and at most {@link Manifest#MAX_DEPTH}. A record that breaks one is not
     * held, so whether it is never depends on what lands after it, nor on which later files a reader happens to read.
     * A record landed again under an id already held, as one is after an interrupted pass, is not held either, as the
     * Sessionizer's own reader holds only the first: none of its pieces is held, so every piece points at a part of
     * the record that holds it.
     *
     * @param seq the seq of the file the record is in
     * @param rec one record of a <code>provider_body</code> file, after every record added before it
     * @return the manifest held under the record's id, or null when it holds no body or is not held
     */
    @Nullable
    private Manifest add(final long seq, final SessionDataFile.Record rec) {
        final Manifest m = Manifest.of(rec);
        if (m == null || rec.getId() == null || rec.getId().isEmpty()) {
            return null;
        }
        final Held already = records.get(rec.getId());
        if (already != null) {
            return already.manifest;
        }
        final String why = refusal(rec.getId(), m);
        if (why != null) {
            refused.putIfAbsent(rec.getId(), why);
            return null;
        }
        records.put(rec.getId(), new Held(seq, rec, m));
        final List<SessionDataFile.Part> parts = rec.getParts();
        for (int i = 0; i < m.getPartIndex(); i++) {
            final SessionDataFile.Part p = parts.get(i);
            final String data = p.data();
            if (!"data".equals(p.getKind()) || data == null) {
                continue;
            }
            final String digest = Digests.sha256Hex(data.getBytes(StandardCharsets.UTF_8));
            if (!pieceAt.containsKey(digest)) {
                pieceAt.put(digest, new int[] {i});
                pieceRecord.put(digest, rec.getId());
            }
        }
        return m;
    }

    /** @return why a record may not be held, as {@link #add(SessionDataFile.Record)} says, or null when it may */
    @Nullable
    private String refusal(final String id, final Manifest m) {
        if (!m.isWellFormed()) {
            return id + " has a manifest of another shape than the page gives it";
        }
        if (!m.isRequest() && !m.isResponse()) {
            return id + " is a body of neither role the page gives";
        }
        if (m.getBytes() < 0 || m.getBytes() > Manifest.MAX_BYTES) {
            return id + " claims " + m.getBytes() + " bytes, past the " + Manifest.MAX_BYTES + " a body may";
        }
        if (m.getDepth() < 0 || m.getDepth() > Manifest.MAX_DEPTH) {
            return id + " claims depth " + m.getDepth();
        }
        int copies = 0;
        for (final Manifest.Segment seg : m.getSegments()) {
            if (seg.isUnknown()) {
                // of the right shape, yet naming nothing a body is made of, such as a piece with no digest
                return id + " holds a segment of no known kind";
            }
            if (seg.getPart() != null && (seg.getPart() < 0 || seg.getPart() >= m.getPartIndex())) {
                return id + " names part " + seg.getPart() + ", which it does not have";
            }
            if (seg.getPiece() != null && !pieceRecord.containsKey(seg.getPiece())) {
                return id + " refers to piece " + seg.getPiece() + ", which no earlier record holds";
            }
            if (seg.getCopyFrom() == null) {
                continue;
            }
            copies++;
            final Held base = records.get(seg.getCopyFrom());
            if (base == null) {
                return id + " copies from " + seg.getCopyFrom() + ", which no earlier record holds";
            }
            if (!base.manifest.getSha256().equals(seg.getCopySha256()) || seg.getCopyLen() < 0
                || seg.getCopyLen() > base.manifest.getBytes()) {
                return id + " copies from " + seg.getCopyFrom() + " with a digest or length it does not have";
            }
            if (m.getDepth() != base.manifest.getDepth() + 1) {
                return id + " claims depth " + m.getDepth() + " over a base of depth " + base.manifest.getDepth();
            }
        }
        if (copies == 0 && m.getDepth() != 0) {
            return id + " claims depth " + m.getDepth() + " and copies nothing";
        }
        return null;
    }

    /** @return the bytes of the rebuilt bodies kept */
    long keptBytes() {
        return kept;
    }

    /**
     * @return whether the record on this row of the file with this seq is the one held under its id: not one the page
     * does not let a reader hold, and not one landed again under an id held before it
     */
    public boolean holds(final long seq, final int row, final String id) {
        final Held h = records.get(id);
        return h != null && h.seq == seq && h.record.getRow() == row;
    }

    /**
     * @return the manifest held under the record id, or null
     */
    @Nullable
    public Manifest manifest(final String id) {
        final Held h = records.get(id);
        return h == null ? null : h.manifest;
    }

    /**
     * @param id a record id
     * @return the body, rebuilt and checked against its manifest's digest
     * @throws BodyException when the body does not rebuild: a reference the session does not hold, a copy of a body
     *                       with another digest or length, a size past what the record claims, or a digest that
     *                       does not match
     */
    public byte[] body(final String id) throws BodyException {
        final byte[] done = rebuilt.get(id);
        if (done != null) {
            return done;
        }
        final Held h = records.get(id);
        if (h == null) {
            throw new BodyException(refused.getOrDefault(id, "the session holds no body " + id));
        }
        // Whether a body fits is decided from the manifests, before anything is built, so it depends on the body
        // and the bodies it copies from alone, never on which bodies this store happens to keep: a file asked for
        // alone and one asked for among others are served the same.
        final long need = need(h, 0, new HashMap<>());
        if (need > budget) {
            throw new BodyException(id + " would hold " + need + " bytes at once to rebuild, past the " + budget + " bytes allowed");
        }
        makeRoom(need);
        final byte[] out = rebuild(h, 0);
        keep(id, out);
        return out;
    }

    /**
     * The most bytes the rebuild of a body holds at once, read from the manifests: the body as it grows, and at each
     * copy the base beside it, whole, or as much as the base's own rebuild holds before it is whole. A chain of copies
     * that each take the front of the body before them, as the Sessionizer writes them, so holds about two bodies at
     * once, not the whole chain. A reference the session does not hold adds nothing: the rebuild fails on it anyway.
     */
    private long need(final Held h, final int depth, final Map<String, Long> memo) {
        final String id = h.record.getId();
        final Long known = memo.get(id);
        if (known != null) {
            return known;
        }
        final Manifest m = h.manifest;
        long most = Math.max(0, m.getBytes());
        long built = 0;
        for (final Manifest.Segment seg : m.getSegments()) {
            if (seg.getCopyFrom() != null) {
                final Held base = records.get(seg.getCopyFrom());
                if (base != null && depth < Manifest.MAX_DEPTH) {
                    final long beside = Math.max(need(base, depth + 1, memo), base.manifest.getBytes() + Math.max(0, seg.getCopyLen()));
                    most = Math.max(most, built + beside);
                }
                built += Math.max(0, seg.getCopyLen());
            } else if (seg.getLit() != null) {
                built += seg.getLit().getBytes(StandardCharsets.UTF_8).length;
            } else if (seg.getPart() != null && seg.getPart() >= 0 && seg.getPart() < m.getPartIndex()) {
                built += Math.max(0, h.record.getParts().get(seg.getPart().intValue()).getBytes());
            } else if (seg.getPiece() != null && pieceRecord.containsKey(seg.getPiece())) {
                // a piece is held only by the record whose part it is, so its index is within that record's parts
                final Held holder = records.get(pieceRecord.get(seg.getPiece()));
                built += Math.max(0, holder.record.getParts().get(pieceAt.get(seg.getPiece())[0]).getBytes());
            }
            built = Math.min(built, Math.max(0, m.getBytes()));
        }
        memo.put(id, most);
        return most;
    }

    /** Lets the kept bodies go, the one kept longest first, until a rebuild that holds this many bytes fits beside them. */
    private void makeRoom(final long need) {
        final Iterator<Map.Entry<String, byte[]>> oldest = rebuilt.entrySet().iterator();
        while (kept + need > budget && oldest.hasNext()) {
            kept -= oldest.next().getValue().length;
            oldest.remove();
        }
    }

    /**
     * @return the manifest of the record holding the piece with this digest, or null when no record held so far does
     */
    @Nullable
    public Manifest holderOf(final String pieceDigest) {
        final String holder = pieceRecord.get(pieceDigest);
        return holder == null ? null : records.get(holder).manifest;
    }

    /**
     * @return the id of the record holding the piece with this digest, or null when no record held so far does
     */
    @Nullable
    public String holderIdOf(final String pieceDigest) {
        return pieceRecord.get(pieceDigest);
    }

    private void keep(final String id, final byte[] body) {
        if (rebuilt.containsKey(id)) {
            return;
        }
        rebuilt.put(id, body);
        kept += body.length;
        makeRoom(0);
    }

    private byte[] rebuild(final Held h, final int depth) throws BodyException {
        final Manifest m = h.manifest;
        final String id = h.record.getId();
        if (m.getBytes() < 0 || m.getBytes() > Manifest.MAX_BYTES) {
            throw new BodyException(id + " claims " + m.getBytes() + " bytes, past the " + Manifest.MAX_BYTES + " a body may");
        }
        if (depth > Manifest.MAX_DEPTH) {
            throw new BodyException(id + " copies deeper than " + Manifest.MAX_DEPTH + " bodies");
        }
        return build(h, m, id, depth);
    }

    private byte[] build(final Held h, final Manifest m, final String id, final int depth) throws BodyException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final Manifest.Segment seg : m.getSegments()) {
            final byte[] bytes;
            if (seg.getLit() != null) {
                bytes = seg.getLit().getBytes(StandardCharsets.UTF_8);
            } else if (seg.getPart() != null) {
                bytes = partBytes(h, seg.getPart());
            } else if (seg.getPiece() != null) {
                bytes = pieceBytes(id, seg.getPiece());
            } else if (seg.getCopyFrom() != null) {
                bytes = copyBytes(id, seg, depth);
            } else {
                throw new BodyException(id + " holds a segment of no known kind");
            }
            if (out.size() + (long) bytes.length > m.getBytes()) {
                throw new BodyException(id + " rebuilds past the " + m.getBytes() + " bytes it claims");
            }
            out.write(bytes, 0, bytes.length);
        }
        final byte[] body = out.toByteArray();
        if (body.length != m.getBytes() || !Digests.sha256Hex(body).equals(m.getSha256())) {
            throw new BodyException(id + " rebuilds to " + body.length + " bytes that do not match its digest");
        }
        return body;
    }

    private static byte[] partBytes(final Held h, final long index) throws BodyException {
        final List<SessionDataFile.Part> parts = h.record.getParts();
        if (index < 0 || index >= h.manifest.getPartIndex()) {
            throw new BodyException(h.record.getId() + " names part " + index + ", which it does not have");
        }
        final SessionDataFile.Part part = parts.get((int) index);
        final String data = part.data();
        if (data == null) {
            throw new BodyException(h.record.getId() + " names part " + index + ", which holds no data");
        }
        if (!"unknown".equals(part.getKind())) {
            // a piece is the JSON the body holds, quotes and all, byte for byte
            return data.getBytes(StandardCharsets.UTF_8);
        }
        // A body kept whole, with a why, is an unknown part: the bytes inside one JSON string, or their base64 when
        // the part's encoding says so, as the Session Data page's "Parts" section reads one.
        final JsonElement s = Schema.parse(data);
        if (s == null || !s.isJsonPrimitive() || !s.getAsJsonPrimitive().isString()) {
            throw new BodyException(h.record.getId() + " names part " + index + ", an unknown part that holds no string");
        }
        final JsonElement encoding = part.getJson().get("encoding");
        if (encoding == null || encoding.isJsonNull()) {
            return s.getAsString().getBytes(StandardCharsets.UTF_8);
        }
        if (!encoding.isJsonPrimitive() || !"base64".equals(encoding.getAsString())) {
            throw new BodyException(h.record.getId() + " names part " + index + ", in an encoding this reader does not know");
        }
        try {
            return Base64.getDecoder().decode(s.getAsString());
        } catch (final IllegalArgumentException e) {
            throw new BodyException(h.record.getId() + " names part " + index + ", whose base64 does not decode");
        }
    }

    private byte[] pieceBytes(final String id, final String digest) throws BodyException {
        final String holder = pieceRecord.get(digest);
        if (holder == null) {
            throw new BodyException(id + " refers to piece " + digest + ", which no earlier record holds");
        }
        return partBytes(records.get(holder), pieceAt.get(digest)[0]);
    }

    private byte[] copyBytes(final String id, final Manifest.Segment seg, final int depth) throws BodyException {
        final Held base = records.get(seg.getCopyFrom());
        if (base == null) {
            throw new BodyException(id + " copies from " + seg.getCopyFrom() + ", which no earlier record holds");
        }
        if (!base.manifest.getSha256().equals(seg.getCopySha256()) || seg.getCopyLen() < 0
            || seg.getCopyLen() > base.manifest.getBytes()) {
            throw new BodyException(id + " copies from " + seg.getCopyFrom() + " with a digest or length it does not have");
        }
        byte[] whole = rebuilt.get(seg.getCopyFrom());
        if (whole == null) {
            whole = rebuild(base, depth + 1);
            keep(seg.getCopyFrom(), whole);
        }
        final byte[] out = new byte[(int) seg.getCopyLen()];
        System.arraycopy(whole, 0, out, 0, out.length);
        return out;
    }

    /**
     * A body that does not rebuild, and why.
     */
    public static final class BodyException extends Exception {
        BodyException(final String message) {
            super("providerbody: " + message);
        }
    }
}
