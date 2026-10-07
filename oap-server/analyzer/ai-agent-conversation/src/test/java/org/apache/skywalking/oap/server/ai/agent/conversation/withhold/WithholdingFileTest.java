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

package org.apache.skywalking.oap.server.ai.agent.conversation.withhold;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.ai.agent.conversation.Fixtures;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.providerbody.BodyStore;
import org.apache.skywalking.oap.server.ai.agent.conversation.providerbody.Manifest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stored file served with names withheld: what carries them is masked, everything else is the stored bytes, and the
 * served file still reads and checks on its own.
 */
public class WithholdingFileTest {
    private static final String DIR = Fixtures.PROMPT_SNAPSHOT_WITHHELD_DIR;
    private static final List<String> BOTH = Arrays.asList(Hide.SYSTEM_PROMPT, Hide.TOOL_SCHEMAS);
    /** What the scenario's snapshots and tool records say, which must be in no served transcript once withheld. */
    private static final String SYSTEM = "helpful assistant";
    private static final String TOOL = "Fetch a page";
    /** What the scenario's request bodies say, which must be in no served body once withheld. */
    private static final String BODY_SYSTEM = "working in a scenario";
    private static final String BODY_TOOL = "Reads a file from the local filesystem";

    @Test
    public void aTranscriptLosesTheNamedRecordsAndNothingElse() throws Exception {
        final byte[] stored = Fixtures.bytes(DIR + Fixtures.PROMPT_SNAPSHOT_WITHHELD_DATA_FILES[0]);
        // the stored bytes hold what is withheld, so a check that finds nothing below is a check of the masking
        assertTrue(new String(stored, StandardCharsets.UTF_8).contains(SYSTEM));
        final Withholding.Served served = Withholding.file(stored, Collections.singletonList(Hide.SYSTEM_PROMPT), null);
        assertTrue(served.isChanged());
        final String text = new String(served.getBytes(), StandardCharsets.UTF_8);
        assertFalse(text.contains(SYSTEM));
        assertTrue(text.contains(TOOL), "a tool schema is withheld only by its own name");

        final SessionDataFile before = SessionDataFile.parse(stored);
        final SessionDataFile after = SessionDataFile.parse(served.getBytes());
        assertEquals(before.getRecords().size(), after.getRecords().size());
        assertEquals(before.getDeclaredRecords(), after.getDeclaredRecords());
        assertClosingLineChecks(served.getBytes());

        final String[] storedLines = new String(stored, StandardCharsets.UTF_8).split("\n", -1);
        final String[] servedLines = text.split("\n", -1);
        int masked = 0;
        for (final SessionDataFile.Record rec : before.getRecords()) {
            final SessionDataFile.Record now = after.getRecords().get(rec.getRow() - 1);
            assertEquals(rec.getId(), now.getId());
            assertEquals(rec.flags(), now.flags(), "the flags stay");
            if (rec.flags().contains(Hide.SYSTEM_PROMPT)) {
                masked++;
                for (final SessionDataFile.Part p : now.getParts()) {
                    assertEquals("omitted", p.getState());
                    assertNull(p.data());
                    assertTrue(p.getText() == null || p.getText().isEmpty());
                    assertTrue(p.getBytes() > 0, "the size stays");
                }
            } else {
                assertEquals(storedLines[rec.getRow()], servedLines[rec.getRow()], "row " + rec.getRow() + " is the stored line");
            }
        }
        // the two snapshots, and the second once more as the runtime replayed it
        assertEquals(3, masked);
    }

    @Test
    public void onlyTheNamedRecordsGoForEachName() throws Exception {
        final byte[] stored = Fixtures.bytes(DIR + Fixtures.PROMPT_SNAPSHOT_WITHHELD_DATA_FILES[0]);
        final SessionDataFile after = SessionDataFile.parse(
            Withholding.file(stored, Collections.singletonList(Hide.TOOL_SCHEMAS), null).getBytes());
        int withheld = 0;
        int shown = 0;
        for (final SessionDataFile.Record rec : after.getRecords()) {
            if (rec.flags().contains(Hide.TOOL_SCHEMAS)) {
                withheld++;
                assertEquals("omitted", rec.getParts().get(0).getState());
            } else if (rec.flags().contains(Hide.SYSTEM_PROMPT)) {
                // a snapshot that holds the prompt and no tools is shown to a reader hiding the tools alone
                shown++;
                assertNotNull(rec.getParts().get(0).data());
            }
        }
        // the snapshot with tools and the deferred tools record, each once more as the runtime replayed them
        assertEquals(4, withheld);
        assertEquals(1, shown);
    }

    @Test
    public void aFileWithNothingNamedIsTheStoredBytes() throws Exception {
        final byte[] meta = Fixtures.bytes(DIR + Fixtures.PROMPT_SNAPSHOT_WITHHELD_DATA_FILES[2]);
        final Withholding.Served served = Withholding.file(meta, BOTH, null);
        assertFalse(served.isChanged());
        assertSame(meta, served.getBytes());
        final byte[] transcript = Fixtures.bytes(DIR + Fixtures.PROMPT_SNAPSHOT_WITHHELD_DATA_FILES[0]);
        assertSame(transcript, Withholding.file(transcript, Hide.NONE, null).getBytes());
        // bytes that are not a Session Data file: a reader opens none of them, and a hiding reader is served none
        final byte[] junk = "not a file".getBytes(StandardCharsets.UTF_8);
        assertEquals(0, Withholding.file(junk, BOTH, null).getBytes().length);
        assertSame(junk, Withholding.file(junk, Hide.NONE, null).getBytes());
    }

    @Test
    public void aRequestBodyIsServedMaskedAndStandalone() throws Exception {
        final byte[] stored = Fixtures.bytes(DIR + Fixtures.PROMPT_SNAPSHOT_WITHHELD_DATA_FILES[3]);
        assertTrue(new String(stored, StandardCharsets.UTF_8).contains(BODY_SYSTEM));
        assertTrue(new String(stored, StandardCharsets.UTF_8).contains(BODY_TOOL));
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        final Withholding.Served served = Withholding.file(stored, BOTH, store);
        assertTrue(served.isChanged());
        final String text = new String(served.getBytes(), StandardCharsets.UTF_8);
        assertFalse(text.contains(BODY_SYSTEM), "the system prompt is in a served body");
        assertFalse(text.contains(BODY_TOOL), "a tool's description is in a served body");
        assertClosingLineChecks(served.getBytes());

        final SessionDataFile before = SessionDataFile.parse(stored);
        final SessionDataFile after = SessionDataFile.parse(served.getBytes());
        assertEquals(before.getRecords().size(), after.getRecords().size());
        final String[] storedLines = new String(stored, StandardCharsets.UTF_8).split("\n", -1);
        final String[] servedLines = text.split("\n", -1);
        // every served request rebuilds on its own, to the digest its new manifest claims
        final BodyStore servedStore = new BodyStore();
        servedStore.add(after);
        int requests = 0;
        for (final SessionDataFile.Record rec : after.getRecords()) {
            final Manifest m = Manifest.of(rec);
            assertNotNull(m);
            if (!m.isRequest()) {
                assertEquals(storedLines[rec.getRow()], servedLines[rec.getRow()], "a response is the stored line");
                continue;
            }
            requests++;
            assertEquals(0, m.getDepth());
            assertEquals(1, m.getSegments().size());
            assertNotNull(m.getSegments().get(0).getLit());
            assertEquals(BOTH, strings(m.getJson().get("withheld")));
            final byte[] body = servedStore.body(rec.getId());
            final JsonObject request = Schema.parse(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            for (final JsonElement block : request.getAsJsonArray("system")) {
                assertEquals(Withholding.MARKER_SYSTEM, block.getAsJsonObject().get("text").getAsString());
            }
            for (final JsonElement tool : request.getAsJsonArray("tools")) {
                assertEquals(2, tool.getAsJsonObject().size(), "a tool keeps its name and says it is withheld");
                assertEquals(Hide.TOOL_SCHEMAS, tool.getAsJsonObject().get("withheld").getAsString());
            }
            // the messages, and the stored manifest's other fields, stay
            assertTrue(request.has("messages"));
            final Manifest was = Manifest.of(before.getRecords().get(rec.getRow() - 1));
            assertEquals(was.getJson().get("model"), m.getJson().get("model"));
            assertEquals(was.getJson().get("chain"), m.getJson().get("chain"));
        }
        assertEquals(3, requests);
    }

    @Test
    public void onlyTheToolsGoWhenThePromptIsNotHidden() throws Exception {
        final byte[] stored = Fixtures.bytes(DIR + Fixtures.PROMPT_SNAPSHOT_WITHHELD_DATA_FILES[3]);
        final BodyStore store = new BodyStore();
        store.add(SessionDataFile.parse(stored));
        final String text = new String(
            Withholding.file(stored, Collections.singletonList(Hide.TOOL_SCHEMAS), store).getBytes(), StandardCharsets.UTF_8);
        assertTrue(text.contains(BODY_SYSTEM), "the system prompt stays when only the tools are hidden");
        assertFalse(text.contains(BODY_TOOL));
    }

    @Test
    public void aFileCutShortEndsWhereAReaderStops() throws Exception {
        final byte[] stored = Fixtures.bytes(DIR + Fixtures.PROMPT_SNAPSHOT_WITHHELD_DATA_FILES[0]);
        final String text = new String(stored, StandardCharsets.UTF_8);
        // the closing line is gone, as a file cut short has none
        final byte[] cut = text.substring(0, text.lastIndexOf("{\"t\":\"end\"")).getBytes(StandardCharsets.UTF_8);
        final Withholding.Served served = Withholding.file(cut, Collections.singletonList(Hide.SYSTEM_PROMPT), null);
        assertTrue(served.isChanged());
        final String servedText = new String(served.getBytes(), StandardCharsets.UTF_8);
        assertFalse(servedText.contains(SYSTEM));
        assertFalse(servedText.contains("\"t\":\"end\""), "a file cut short gets no closing line it never had");
        assertEquals(-1, SessionDataFile.parse(served.getBytes()).getDeclaredRecords());
    }

    private static void assertClosingLineChecks(final byte[] served) {
        final String text = new String(served, StandardCharsets.UTF_8);
        final int closing = text.lastIndexOf("{\"t\":\"end\"");
        assertTrue(closing > 0);
        final SessionDataFile file = SessionDataFile.parse(served);
        assertEquals(file.getRecords().size(), file.getDeclaredRecords());
        assertEquals(Digests.sha256Hex(text.substring(0, closing).getBytes(StandardCharsets.UTF_8)), file.getDeclaredDigest());
    }

    private static List<String> strings(final JsonElement array) {
        final List<String> out = new java.util.ArrayList<>();
        for (final JsonElement e : array.getAsJsonArray()) {
            out.add(e.getAsString());
        }
        return out;
    }
}
