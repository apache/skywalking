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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.ai.agent.conversation.Fixtures;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every body the fixtures hold rebuilds to the digest its manifest claims, as <code>asz verify</code> checks them,
 * and a body that refers to what the session does not hold, or claims more than a body may, is refused in words.
 */
public class BodyStoreTest {
    @ParameterizedTest
    @ValueSource(strings = {"provider-bodies/", "provider-bodies-errors/", "langchain-subagent/", "prompt-snapshot-withheld/"})
    public void everyBodyRebuildsToItsDigest(final String dir) throws Exception {
        final Map<Long, SessionDataFile> files;
        switch (dir) {
            case "provider-bodies/":
                files = Fixtures.providerBodiesDataFiles();
                break;
            case "provider-bodies-errors/":
                files = Fixtures.providerBodiesErrorsDataFiles();
                break;
            case "langchain-subagent/":
                files = Fixtures.langchainSubagentDataFiles();
                break;
            default:
                files = Fixtures.promptSnapshotWithheldDataFiles();
        }
        final BodyStore store = new BodyStore();
        int bodies = 0;
        int requests = 0;
        for (final SessionDataFile f : files.values()) {
            if (!Manifest.KIND.equals(f.getHeader().getKind())) {
                continue;
            }
            store.add(f);
            for (final SessionDataFile.Record r : f.getRecords()) {
                final Manifest m = Manifest.of(r);
                assertNotNull(m, "row " + r.getRow() + " carries no manifest");
                final byte[] body = store.body(r.getId());
                assertEquals(m.getBytes(), body.length);
                assertEquals(m.getSha256(), Digests.sha256Hex(body));
                bodies++;
                if (m.isRequest()) {
                    requests++;
                    // a request is the JSON the model was sent
                    assertTrue(new String(body, StandardCharsets.UTF_8).startsWith("{"));
                }
            }
        }
        assertTrue(bodies > 0, "no body in " + dir);
        assertTrue(requests > 0, "no request in " + dir);
    }

    /**
     * Bodies of sixty bytes, each copying the first ten bytes of the one before and adding fifty of its own, as the
     * Sessionizer writes a conversation's requests. Rebuilding one holds the body before it, whole, beside its own
     * first ten bytes: seventy at most, however long the chain, so the budget is what one rebuild holds at once,
     * decided before anything is built, never the sum of the chain.
     */
    @Test
    public void theBudgetIsWhatOneRebuildHoldsAtOnce() throws Exception {
        final SessionDataFile parsed = SessionDataFile.parse(chain(33).getBytes(StandardCharsets.UTF_8));
        final BodyStore tight = new BodyStore(69);
        tight.add(parsed);
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> tight.body("b2")).getMessage().contains("would hold 70 bytes"));
        assertEquals(0, tight.keptBytes(), "a body refused is not built, and nothing of its chain is kept");
        final BodyStore enough = new BodyStore(70);
        enough.add(parsed);
        // the last of thirty-three, whose chain claims nearly two thousand bytes
        assertEquals(60, enough.body("b32").length);
        assertTrue(enough.keptBytes() <= 70, "the kept bodies stay within the budget: " + enough.keptBytes());
    }

    /**
     * Whether a body rebuilds does not depend on what the store rebuilt before it: five bodies of sixty bytes, with
     * room for two, all rebuild one after another, the oldest kept going first, as each does alone.
     */
    @Test
    public void aBodyRebuildsTheSameAloneOrAfterOthers() throws Exception {
        final StringBuilder file = new StringBuilder(HEADER);
        for (int i = 0; i < 5; i++) {
            final String body = String.valueOf((char) ('a' + i)).repeat(60);
            file.append(record("r" + i, body, 0, "[{\"lit\":\"" + body + "\"}]"));
        }
        file.append("{\"t\":\"end\",\"records\":5,\"digest\":\"x\"}\n");
        final SessionDataFile parsed = SessionDataFile.parse(file.toString().getBytes(StandardCharsets.UTF_8));
        final BodyStore all = new BodyStore(130);
        all.add(parsed);
        for (int i = 0; i < 5; i++) {
            assertEquals(60, all.body("r" + i).length);
            assertTrue(all.keptBytes() <= 130, "the kept bodies stay within the budget: " + all.keptBytes());
        }
        final BodyStore alone = new BodyStore(130);
        alone.add(parsed);
        assertArrayEquals(all.body("r4"), alone.body("r4"));
    }

    private static final String HEADER = "{\"h\":1,\"schema\":\"sd/1\",\"seq\":1,\"at\":\"2026-01-01T00:00:00Z\","
        + "\"kind\":\"provider_body\",\"adapter\":\"mock/0.2.0\",\"dialect\":\"mock/1\",\"src\":\".\",\"session\":\"s\"}\n";

    /** A file of n bodies b0 to b(n-1) of sixty bytes, each after the first copying ten bytes of the one before. */
    private static String chain(final int n) {
        final StringBuilder file = new StringBuilder(HEADER);
        String before = null;
        for (int i = 0; i < n; i++) {
            final String own = String.valueOf((char) ('a' + i % 26)).repeat(i == 0 ? 60 : 50);
            final String body = before == null ? own : before.substring(0, 10) + own;
            final String segments = before == null
                ? "[{\"lit\":\"" + own + "\"}]"
                : "[{\"copy\":{\"from\":\"b" + (i - 1) + "\",\"sha256\":\"" + Digests.sha256Hex(before.getBytes(StandardCharsets.UTF_8))
                    + "\",\"len\":10}},{\"lit\":\"" + own + "\"}]";
            file.append(record("b" + i, body, Math.min(i, 32), segments));
            before = body;
        }
        return file.append("{\"t\":\"end\",\"records\":").append(n).append(",\"digest\":\"x\"}\n").toString();
    }

    private static String record(final String id, final String body, final int depth, final String segments) {
        return "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"" + id + "\",\"parts\":[{\"k\":\"data\",\"data\":"
            + "{\"schema\":\"provider_body/1\",\"role\":\"request\",\"sha256\":\"" + Digests.sha256Hex(body.getBytes(StandardCharsets.UTF_8))
            + "\",\"bytes\":" + body.length() + ",\"depth\":" + depth + ",\"segments\":" + segments + "}}]}\n";
    }

    /**
     * A record landed again under its id is the one already held, and none of its pieces is held again: a later body
     * that uses a piece only the repeat holds is not held either, and says why, rather than reading a part the held
     * record does not have.
     */
    @Test
    public void aRecordLandedAgainIsTheOneHeld() throws Exception {
        final String p0 = "\"" + "p".repeat(20) + "\"";
        final String p1 = "\"" + "q".repeat(20) + "\"";
        final String later = "{\"b\":" + p1 + "}";
        final String file = HEADER
            + record("x", "{\"a\":1}", 0, "[{\"lit\":\"{\\\"a\\\":1}\"}]")
            + withPieces("x", "[" + p0 + "," + p1 + "]", Arrays.asList(p0, p1), "[{\"lit\":\"[\"},{\"part\":0},{\"lit\":\",\"},{\"part\":1},{\"lit\":\"]\"}]")
            + record("y", later, 0, "[{\"lit\":\"{\\\"b\\\":\"},{\"piece\":\"" + Digests.sha256Hex(p1.getBytes(StandardCharsets.UTF_8)) + "\"},{\"lit\":\"}\"}]")
            + "{\"t\":\"end\",\"records\":3,\"digest\":\"x\"}\n";
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(file.getBytes(StandardCharsets.UTF_8)));
        assertEquals("{\"a\":1}", new String(store.body("x"), StandardCharsets.UTF_8));
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> store.body("y")).getMessage().contains("which no earlier record holds"));
    }

    /**
     * A reference to a record that lands later is not held, as the page says no reference may be, so a body reads the
     * same whether or not the later file is read: here it is not held either way.
     */
    @Test
    public void aReferenceToALaterRecordIsNotHeldWhateverIsRead() throws Exception {
        final String p = "\"" + "r".repeat(20) + "\"";
        final String early = HEADER
            + record("e", "{\"e\":" + p + "}", 0, "[{\"lit\":\"{\\\"e\\\":\"},{\"piece\":\"" + Digests.sha256Hex(p.getBytes(StandardCharsets.UTF_8)) + "\"},{\"lit\":\"}\"}]")
            + "{\"t\":\"end\",\"records\":1,\"digest\":\"x\"}\n";
        final String late = HEADER.replace("\"seq\":1", "\"seq\":2")
            + withPieces("l", "[" + p + "]", Collections.singletonList(p), "[{\"lit\":\"[\"},{\"part\":0},{\"lit\":\"]\"}]")
            + "{\"t\":\"end\",\"records\":1,\"digest\":\"x\"}\n";
        final BodyStore alone = new BodyStore();
        alone.add(SessionDataFile.parse(early.getBytes(StandardCharsets.UTF_8)));
        final BodyStore both = new BodyStore();
        both.add(SessionDataFile.parse(early.getBytes(StandardCharsets.UTF_8)));
        both.add(SessionDataFile.parse(late.getBytes(StandardCharsets.UTF_8)));
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> alone.body("e")).getMessage().contains("which no earlier record holds"));
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> both.body("e")).getMessage().contains("which no earlier record holds"));
    }

    /** A depth the page does not allow is not held: one claimed without a copy, and one that is not its base's plus one. */
    @Test
    public void aDepthThePageDoesNotAllowIsNotHeld() throws Exception {
        final String file = HEADER
            + record("a", "a".repeat(20), 3, "[{\"lit\":\"" + "a".repeat(20) + "\"}]")
            + record("b", "b".repeat(20), 0, "[{\"lit\":\"" + "b".repeat(20) + "\"}]")
            + record("c", "b".repeat(10), 2, "[{\"copy\":{\"from\":\"b\",\"sha256\":\"" + Digests.sha256Hex("b".repeat(20).getBytes(StandardCharsets.UTF_8))
                + "\",\"len\":10}}]")
            + "{\"t\":\"end\",\"records\":3,\"digest\":\"x\"}\n";
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(file.getBytes(StandardCharsets.UTF_8)));
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> store.body("a")).getMessage().contains("claims depth 3 and copies nothing"));
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> store.body("c")).getMessage().contains("claims depth 2 over a base of depth 0"));
        assertEquals(20, store.body("b").length);
    }

    /**
     * What the page does not let a reader hold is not held, whatever a later rebuild would say: a part the record does
     * not have, or a negative one; a depth past 32 over a base at 32; a size past 256 MiB, so a later record that uses
     * its piece is not held either; a depth past what an int holds, which must not wrap to a small one; and an integer
     * spelled with a fraction or an exponent, or past what a long holds, which must not be read as another.
     */
    @Test
    public void whatThePageDoesNotAllowIsNotHeld() {
        final String p = "\"" + "s".repeat(20) + "\"";
        final String deep = chain(33);
        final StringBuilder file = new StringBuilder(deep.substring(0, deep.lastIndexOf("{\"t\":\"end\"")));
        final String base = chainBody(32);
        file.append(record("b33", base.substring(0, 10), 33, "[{\"copy\":{\"from\":\"b32\",\"sha256\":\""
            + Digests.sha256Hex(base.getBytes(StandardCharsets.UTF_8)) + "\",\"len\":10}}]"));
        file.append(withPieces("past", "[" + p + "," + p + "]", Collections.singletonList(p), "[{\"lit\":\"[\"},{\"part\":1},{\"lit\":\"]\"}]"));
        file.append(withPieces("minus", "[" + p + "]", Collections.singletonList(p), "[{\"lit\":\"[\"},{\"part\":-1},{\"lit\":\"]\"}]"));
        file.append(withPieces("big", "[" + p + "]", Collections.singletonList(p), "[{\"lit\":\"[\"},{\"part\":0},{\"lit\":\"]\"}]")
                        .replace("\"bytes\":" + ("[" + p + "]").length() + ",", "\"bytes\":300000000,"));
        file.append(record("user", "[" + p + "]", 0, "[{\"lit\":\"[\"},{\"piece\":\"" + Digests.sha256Hex(p.getBytes(StandardCharsets.UTF_8)) + "\"},{\"lit\":\"]\"}]"));
        file.append(record("wrap", "w".repeat(20), 0, "[{\"lit\":\"" + "w".repeat(20) + "\"}]").replace("\"depth\":0", "\"depth\":4294967296"));
        // an integer is written with no fraction and no exponent, as the page writes one, and a long holds it
        final String[] spelled = {"\"depth\":0.5", "\"depth\":0.0", "\"depth\":0e0", "\"depth\":18446744073709551616", "\"bytes\":20.0,\"depth\":0"};
        for (int i = 0; i < spelled.length; i++) {
            final String rec = record("spelled" + i, "z".repeat(20), 0, "[{\"lit\":\"" + "z".repeat(20) + "\"}]");
            file.append(spelled[i].startsWith("\"bytes") ? rec.replace("\"bytes\":20,\"depth\":0", spelled[i]) : rec.replace("\"depth\":0", spelled[i]));
        }
        file.append("{\"t\":\"end\",\"records\":44,\"digest\":\"x\"}\n");
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(file.toString().getBytes(StandardCharsets.UTF_8)));
        assertNotNull(store.manifest("b32"), "the base at depth 32 is held");
        for (final String id : new String[] {"b33", "past", "minus", "big", "user", "wrap", "spelled0", "spelled1", "spelled2", "spelled3", "spelled4"}) {
            assertNull(store.manifest(id), id + " is held");
        }
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> store.body("user")).getMessage().contains("which no earlier record holds"));
    }

    /**
     * A manifest of another shape than the page gives it is not held, and neither is a body that uses its piece: a
     * field of another type, as a reader that decodes it as the page writes it refuses it, and a segment that is not
     * one of the page's four kinds, null, empty, setting two or naming a piece with no digest, which the page says
     * nothing of.
     */
    @Test
    public void aManifestOfAnotherShapeIsNotHeld() {
        final String body = "t".repeat(20);
        final String lit = "[{\"lit\":\"" + body + "\"}]";
        final String sha = "\"sha256\":\"" + Digests.sha256Hex(body.getBytes(StandardCharsets.UTF_8)) + "\"";
        final String p = "\"" + "u".repeat(20) + "\"";
        final String[] typed = {
            record("digest", body, 0, lit).replace(sha, "\"sha256\":5"),
            record("src", body, 0, lit).replace("\"role\":\"request\"", "\"role\":\"request\",\"src\":true"),
            record("segment", body, 0, "[5]"),
            record("text", body, 0, "[{\"lit\":5}]"),
            record("piece", body, 0, "[{\"piece\":5}]"),
            record("copy", body, 0, "[{\"copy\":{\"from\":5,\"sha256\":\"x\",\"len\":1}}]"),
            record("segments", body, 0, "{}"),
            withPieces("holder", "[" + p + "]", Collections.singletonList(p), "[{\"lit\":\"[\"},{\"part\":0},{\"lit\":\"]\"}]")
                .replace("\"role\":\"request\"", "\"role\":\"request\",\"model\":[]"),
            record("user", "[" + p + "]", 0, "[{\"lit\":\"[\"},{\"piece\":\"" + Digests.sha256Hex(p.getBytes(StandardCharsets.UTF_8)) + "\"},{\"lit\":\"]\"}]"),
            record("empty", body, 0, "[{\"lit\":\"" + body + "\"},{}]"),
            record("null", body, 0, "[{\"lit\":\"" + body + "\"},null]"),
            record("two", body, 0, "[{\"lit\":\"" + body + "\",\"part\":7}]"),
            record("nodigest", body, 0, "[{\"lit\":\"" + body + "\"},{\"piece\":\"\"}]"),
            record("plain", body, 0, lit),
        };
        final StringBuilder file = new StringBuilder(HEADER);
        for (final String rec : typed) {
            file.append(rec);
        }
        file.append("{\"t\":\"end\",\"records\":").append(typed.length).append(",\"digest\":\"x\"}\n");
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(file.toString().getBytes(StandardCharsets.UTF_8)));
        for (final String id : new String[] {"digest", "src", "segment", "text", "piece", "copy", "segments", "holder", "user", "empty", "null", "two", "nodigest"}) {
            assertNull(store.manifest(id), id + " is held");
        }
        assertNotNull(store.manifest("plain"), "a manifest of the page's shape is held");
    }

    /** The body of b(i) in {@link #chain(int)}. */
    private static String chainBody(final int i) {
        String body = null;
        for (int j = 0; j <= i; j++) {
            final String own = String.valueOf((char) ('a' + j % 26)).repeat(j == 0 ? 60 : 50);
            body = body == null ? own : body.substring(0, 10) + own;
        }
        return body;
    }

    /** A request record with its pieces as data parts before the manifest. */
    private static String withPieces(final String id, final String body, final List<String> pieces, final String segments) {
        final StringBuilder parts = new StringBuilder();
        for (final String piece : pieces) {
            parts.append("{\"k\":\"data\",\"data\":").append(piece).append(",\"state\":\"available\",\"bytes\":").append(piece.length()).append("},");
        }
        return "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"" + id + "\",\"parts\":[" + parts
            + "{\"k\":\"data\",\"data\":{\"schema\":\"provider_body/1\",\"role\":\"request\",\"sha256\":\""
            + Digests.sha256Hex(body.getBytes(StandardCharsets.UTF_8)) + "\",\"bytes\":" + body.length() + ",\"depth\":0,\"segments\":" + segments + "}}]}\n";
    }

    @Test
    public void aReferenceTheSessionDoesNotHoldIsRefused() {
        final SessionDataFile f = SessionDataFile.parse((
            "{\"h\":1,\"schema\":\"sd/1\",\"seq\":1,\"at\":\"2026-01-01T00:00:00Z\",\"kind\":\"provider_body\","
                + "\"adapter\":\"mock/0.2.0\",\"dialect\":\"mock/1\",\"src\":\".\",\"session\":\"s\"}\n"
                + "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"a\",\"parts\":[{\"k\":\"data\",\"data\":"
                + "{\"schema\":\"provider_body/1\",\"role\":\"request\",\"sha256\":\"00\",\"bytes\":2,\"depth\":0,"
                + "\"segments\":[{\"piece\":\"deadbeef\"}]}}]}\n"
                + "{\"ord\":2,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"b\",\"parts\":[{\"k\":\"data\",\"data\":"
                + "{\"schema\":\"provider_body/1\",\"role\":\"request\",\"sha256\":\"00\",\"bytes\":2,\"depth\":1,"
                + "\"segments\":[{\"copy\":{\"from\":\"zz\",\"sha256\":\"00\",\"len\":1}}]}}]}\n"
                + "{\"ord\":3,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"c\",\"parts\":[{\"k\":\"data\",\"data\":"
                + "{\"schema\":\"provider_body/1\",\"role\":\"request\",\"sha256\":\"00\",\"bytes\":300000000,\"depth\":0,"
                + "\"segments\":[{\"lit\":\"{}\"}]}}]}\n"
                + "{\"ord\":4,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"id\":\"d\",\"parts\":[{\"k\":\"data\",\"data\":"
                + "{\"schema\":\"provider_body/1\",\"role\":\"request\",\"sha256\":\"00\",\"bytes\":1,\"depth\":0,"
                + "\"segments\":[{\"lit\":\"{}\"}]}}]}\n"
                + "{\"t\":\"end\",\"records\":4,\"digest\":\"x\"}\n").getBytes(StandardCharsets.UTF_8));
        final BodyStore store = new BodyStore();
        store.add(f);
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> store.body("a")).getMessage().contains("piece deadbeef"));
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> store.body("b")).getMessage().contains("copies from zz"));
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> store.body("c")).getMessage().contains("past the"));
        // one byte claimed, two rebuilt: stopped as soon as the claim is passed
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> store.body("d")).getMessage().contains("rebuilds past"));
        assertTrue(assertThrows(BodyStore.BodyException.class, () -> store.body("nobody")).getMessage().contains("no body"));
    }
}
