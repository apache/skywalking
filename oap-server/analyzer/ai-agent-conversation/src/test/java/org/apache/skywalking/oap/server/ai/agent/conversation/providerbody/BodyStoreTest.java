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
