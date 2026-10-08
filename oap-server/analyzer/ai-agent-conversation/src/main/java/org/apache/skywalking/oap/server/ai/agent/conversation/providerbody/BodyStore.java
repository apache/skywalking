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
    /** The rebuilt bodies kept, the one kept longest first. */
    private final Map<String, byte[]> rebuilt = new LinkedHashMap<>();
    private long kept;

    private static final class Held {
        final SessionDataFile.Record record;
        final Manifest manifest;

        Held(final SessionDataFile.Record record, final Manifest manifest) {
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
            add(rec);
        }
    }

    /**
     * @param rec one record of a <code>provider_body</code> file
     * @return the manifest the record carries, or null when it holds no body
     */
    @Nullable
    public Manifest add(final SessionDataFile.Record rec) {
        final Manifest m = Manifest.of(rec);
        if (m == null || rec.getId() == null || rec.getId().isEmpty()) {
            return null;
        }
        // a record landed again under its id, as one is after an interrupted pass, is the one already held
        if (!records.containsKey(rec.getId())) {
            records.put(rec.getId(), new Held(rec, m));
        }
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

    /** @return the bytes of the rebuilt bodies kept */
    long keptBytes() {
        return kept;
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
            throw new BodyException("the session holds no body " + id);
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
            } else if (seg.getPart() >= 0 && seg.getPart() < m.getPartIndex()) {
                built += Math.max(0, h.record.getParts().get(seg.getPart()).getBytes());
            } else if (seg.getPiece() != null && pieceRecord.containsKey(seg.getPiece())) {
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
            } else if (seg.getPart() >= 0) {
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

    private static byte[] partBytes(final Held h, final int index) throws BodyException {
        final List<SessionDataFile.Part> parts = h.record.getParts();
        if (index >= h.manifest.getPartIndex()) {
            throw new BodyException(h.record.getId() + " names part " + index + ", which it does not have");
        }
        final SessionDataFile.Part part = parts.get(index);
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
