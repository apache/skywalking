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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.ai.agent.conversation.Fixtures;
import org.apache.skywalking.oap.server.ai.agent.conversation.fold.ConversationFold;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.ai.agent.conversation.providerbody.Manifest;
import org.apache.skywalking.oap.server.ai.agent.conversation.view.ConversationViewBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The withheld document the OAP builds equals, key for key and in key order, the one <code>asz view</code> serves for
 * the same files with the same names withheld, apart from the two places the OAP's rules differ by design: every
 * call keeps its <code>provider_bodies</code> and the summary its <code>captured_prompts</code>, since a body is served
 * masked rather than refused, and <code>summary.withheld</code> counts under <code>provider_bodies</code> the request
 * bodies that are masked, where the Sessionizer counts every landed body it refuses.
 */
public class WithheldViewTest {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @ParameterizedTest
    @CsvSource({
        "prompt-snapshot-withheld/, r000001-c081727f2581.sf, system_prompt",
        "prompt-snapshot-withheld/, r000001-c081727f2581.sf, tool_schemas",
        "prompt-snapshot-withheld/, r000001-c081727f2581.sf, system_prompt+tool_schemas",
        "provider-bodies/, r000001-e292bd0f6de6.sf, system_prompt",
        "provider-bodies/, r000001-e292bd0f6de6.sf, tool_schemas",
        "provider-bodies/, r000001-e292bd0f6de6.sf, system_prompt+tool_schemas",
        "input-withheld/, r000001-f6917d7d5f75.sf, system_prompt",
        "input-withheld/, r000001-f6917d7d5f75.sf, tool_schemas",
        "input-withheld/, r000001-f6917d7d5f75.sf, system_prompt+tool_schemas",
    })
    public void theWithheldDocumentIsTheSessionizersWithTheBodiesKept(final String dir, final String roundFile,
                                                                     final String names) throws Exception {
        final List<String> hide = Arrays.asList(names.split("\\+"));
        final Map<Long, SessionDataFile> files = files(dir);
        final Map<String, Object> whole = view(dir, roundFile, files, Hide.NONE);
        final Map<String, Object> withheld = view(dir, roundFile, files, hide);

        final JsonObject expected = JsonParser.parseString(new String(
            Fixtures.bytes(dir + Fixtures.viewWithheld(names)), StandardCharsets.UTF_8)).getAsJsonObject();
        final JsonObject wholeJson = GSON.toJsonTree(whole).getAsJsonObject();
        keepBodies(expected, wholeJson);
        final JsonObject summary = expected.getAsJsonObject("summary");
        summary.add("captured_prompts", wholeJson.getAsJsonObject("summary").get("captured_prompts"));
        summary.getAsJsonObject("withheld").addProperty("provider_bodies", requests(files));

        final JsonElement actual = GSON.toJsonTree(withheld);
        assertEquals(expected, actual);
        assertEquals(GSON.toJson(expected), GSON.toJson(actual));

        // what is withheld is gone from the document, and nothing else moved
        final String text = GSON.toJson(actual);
        if (hide.contains(Hide.SYSTEM_PROMPT)) {
            assertFalse(text.contains("helpful assistant"), "the system prompt is in the document");
            assertFalse(text.contains("You check links"), "the child's system prompt is in the document");
        }
        if (hide.contains(Hide.TOOL_SCHEMAS)) {
            assertFalse(text.contains("Fetch a page"), "a tool schema is in the document");
        }
        for (final String count : new String[] {"talks", "steps", "streams", "segments", "rounds", "unresolved", "provider_bodies", "captured_prompts"}) {
            assertEquals(((Map<?, ?>) whole.get("summary")).get(count), ((Map<?, ?>) withheld.get("summary")).get(count), count);
        }
        assertEquals(whole.get("rounds"), withheld.get("rounds"));
        assertEquals(whole.get("files"), withheld.get("files"));
    }

    /**
     * With nothing withheld the document is the one <code>asz conversation -json</code> printed for the same files.
     * Under <code>input-withheld/</code> the person's input carries <code>system_prompt</code>, and still names its
     * talk, as the Sessionizer names it: only a reader who withholds what it carries sees no label.
     */
    @ParameterizedTest
    @CsvSource({"prompt-snapshot-withheld/, r000001-c081727f2581.sf", "input-withheld/, r000001-f6917d7d5f75.sf"})
    public void theWholeDocumentIsTheSessionizers(final String dir, final String roundFile) throws Exception {
        final JsonElement expected = JsonParser.parseString(new String(
            Fixtures.bytes(dir + Fixtures.VIEW_EXAMPLE_JSON), StandardCharsets.UTF_8));
        final JsonElement actual = GSON.toJsonTree(view(dir, roundFile, files(dir), Hide.NONE));
        assertEquals(expected, actual);
        assertEquals(GSON.toJson(expected), GSON.toJson(actual));
    }

    /** With nothing withheld the summary says so with an empty object, and the document is the whole one. */
    @ParameterizedTest
    @CsvSource({"prompt-snapshot-withheld/, r000001-c081727f2581.sf", "provider-bodies/, r000001-e292bd0f6de6.sf",
        "input-withheld/, r000001-f6917d7d5f75.sf"})
    public void nothingWithheldIsAnEmptyObject(final String dir, final String roundFile) throws Exception {
        final Map<String, Object> doc = view(dir, roundFile, files(dir), Hide.NONE);
        assertEquals(Collections.emptyMap(), ((Map<?, ?>) doc.get("summary")).get("withheld"));
        assertTrue(GSON.toJson(doc).contains("helpful assistant") || dir.startsWith("provider-bodies"));
    }

    private static Map<Long, SessionDataFile> files(final String dir) throws IOException {
        if (dir.startsWith("prompt-snapshot")) {
            return Fixtures.promptSnapshotWithheldDataFiles();
        }
        return dir.startsWith("input-withheld") ? Fixtures.inputWithheldDataFiles() : Fixtures.providerBodiesDataFiles();
    }

    private static Map<String, Object> view(final String dir, final String roundFile, final Map<Long, SessionDataFile> files,
                                            final List<String> hide) throws IOException {
        final byte[] bytes = Fixtures.bytes(dir + roundFile);
        final SessionFlowRound round = SessionFlowRound.parse(bytes);
        final ConversationFold fold = new ConversationFold();
        fold.apply(round);
        final List<ConversationViewBuilder.RoundInput> rounds = new ArrayList<>();
        rounds.add(new ConversationViewBuilder.RoundInput(round, Digests.sha256Hex(bytes)));
        return new ConversationViewBuilder(fold, rounds, files, new ArrayList<>(), hide).build();
    }

    /** How many request bodies the files hold, once per record id. */
    private static int requests(final Map<Long, SessionDataFile> files) {
        final Map<String, Boolean> seen = new HashMap<>();
        for (final SessionDataFile f : files.values()) {
            if (!Manifest.KIND.equals(f.getHeader().getKind())) {
                continue;
            }
            for (final SessionDataFile.Record r : f.getRecords()) {
                final Manifest m = Manifest.of(r);
                if (m != null && m.isRequest()) {
                    seen.put(r.getId(), true);
                }
            }
        }
        return seen.size();
    }

    /**
     * Puts each call's <code>provider_bodies</code> back into the Sessionizer's withheld document, from the whole
     * one, where the OAP writes it: before <code>children</code>, after everything else.
     */
    private static void keepBodies(final JsonObject withheld, final JsonObject whole) {
        final Map<String, JsonElement> bodies = new HashMap<>();
        for (final String list : new String[] {"talks", "loose"}) {
            collect(whole.getAsJsonArray(list), bodies);
        }
        for (final String list : new String[] {"talks", "loose"}) {
            restore(withheld.getAsJsonArray(list), bodies);
        }
    }

    private static void collect(final JsonArray nodes, final Map<String, JsonElement> bodies) {
        for (final JsonElement e : nodes) {
            final JsonObject n = e.getAsJsonObject();
            if (n.has("provider_bodies")) {
                bodies.put(n.get("id").getAsString(), n.get("provider_bodies"));
            }
            if (n.has("children")) {
                collect(n.getAsJsonArray("children"), bodies);
            }
        }
    }

    private static void restore(final JsonArray nodes, final Map<String, JsonElement> bodies) {
        for (int i = 0; i < nodes.size(); i++) {
            final JsonObject n = nodes.get(i).getAsJsonObject();
            final JsonElement own = bodies.get(n.get("id").getAsString());
            if (own != null) {
                final JsonObject rebuilt = new JsonObject();
                for (final Map.Entry<String, JsonElement> entry : n.entrySet()) {
                    if ("children".equals(entry.getKey())) {
                        rebuilt.add("provider_bodies", own);
                    }
                    rebuilt.add(entry.getKey(), entry.getValue());
                }
                if (!rebuilt.has("provider_bodies")) {
                    rebuilt.add("provider_bodies", own);
                }
                nodes.set(i, rebuilt);
            }
            if (n.has("children")) {
                restore(n.getAsJsonArray("children"), bodies);
            }
        }
    }

    /**
     * The rule holds for every record a step reads, not only an injection: a model's own message that carried a
     * withheld name would lose its text and the talk its reply. No adapter names such a record, so one is made here
     * by adding the flag to a stored record; the file's closing line then no longer checks, which the document says
     * in its state and nowhere else.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void anyRecordCarryingAWithheldNameIsReadWithheld() throws Exception {
        final String dir = Fixtures.PROMPT_SNAPSHOT_WITHHELD_DIR;
        final Map<Long, SessionDataFile> files = new java.util.TreeMap<>(Fixtures.promptSnapshotWithheldDataFiles());
        final String[] lines = new String(Fixtures.bytes(dir + Fixtures.PROMPT_SNAPSHOT_WITHHELD_DATA_FILES[0]), StandardCharsets.UTF_8).split("\n", -1);
        // row 10 is the model's "Checking." on the main stream, the talk's reply
        final JsonObject reply = JsonParser.parseString(lines[10]).getAsJsonObject();
        reply.getAsJsonArray("flags").add(Hide.SYSTEM_PROMPT);
        lines[10] = reply.toString();
        files.put(1L, SessionDataFile.parse(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));

        final Map<String, Object> doc = view(dir, Fixtures.PROMPT_SNAPSHOT_WITHHELD_ROUND_FILE, files, Collections.singletonList(Hide.SYSTEM_PROMPT));
        final Map<String, Object> step = node(doc, "msg/1/10:0");
        assertEquals("omitted", step.get("state"));
        assertFalse(step.containsKey("text"));
        final Map<String, Object> talk = node(doc, "talk/main/s1-cycle");
        assertFalse(talk.containsKey("reply"), "the reply is read from the withheld record");
        assertEquals("mismatch", ((Map<String, Object>) doc.get("summary")).get("state"));
        // and whole, the same record is the reply
        final Map<String, Object> whole = view(dir, Fixtures.PROMPT_SNAPSHOT_WITHHELD_ROUND_FILE, files, Hide.NONE);
        assertEquals("Checking.", node(whole, "msg/1/10:0").get("text"));
        assertEquals("Checking.", node(whole, "talk/main/s1-cycle").get("reply"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> node(final Map<String, Object> doc, final String id) {
        final java.util.ArrayDeque<Map<String, Object>> open = new java.util.ArrayDeque<>();
        open.addAll((List<Map<String, Object>>) doc.get("talks"));
        open.addAll((List<Map<String, Object>>) doc.get("loose"));
        while (!open.isEmpty()) {
            final Map<String, Object> n = open.poll();
            if (id.equals(n.get("id"))) {
                return n;
            }
            if (n.get("children") != null) {
                open.addAll((List<Map<String, Object>>) n.get("children"));
            }
        }
        throw new AssertionError("no node " + id);
    }

    /**
     * A change record carrying a withheld name lists nothing in the document, as no record carrying one shows its
     * content there. No adapter names such a record, so one is made by adding the flag to a stored one.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aChangeRecordCarryingAWithheldNameListsNothing() throws Exception {
        final String dir = Fixtures.WORKSPACE_CHANGES_DIR;
        final Map<Long, SessionDataFile> files = new java.util.TreeMap<>(Fixtures.workspaceChangesDataFiles());
        final Map<String, Object> whole = view(dir, Fixtures.WORKSPACE_CHANGES_ROUND_FILE, files, Collections.singletonList(Hide.SYSTEM_PROMPT));
        final int listed = ((List<?>) whole.get("workspace_changes")).size();
        assertTrue(listed > 0);

        final String[] lines = new String(Fixtures.bytes(dir + Fixtures.WORKSPACE_CHANGES_DATA_FILES[1]), StandardCharsets.UTF_8).split("\n", -1);
        final JsonObject change = JsonParser.parseString(lines[1]).getAsJsonObject();
        final JsonArray flags = new JsonArray();
        flags.add(Hide.SYSTEM_PROMPT);
        change.add("flags", flags);
        lines[1] = change.toString();
        files.put(2L, SessionDataFile.parse(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
        final Map<String, Object> withheld = view(dir, Fixtures.WORKSPACE_CHANGES_ROUND_FILE, files, Collections.singletonList(Hide.SYSTEM_PROMPT));
        assertEquals(listed - 1, ((List<?>) withheld.get("workspace_changes")).size());
        // and with nothing withheld, the flag alone changes nothing
        final Map<String, Object> plain = view(dir, Fixtures.WORKSPACE_CHANGES_ROUND_FILE, files, Hide.NONE);
        assertEquals(listed, ((List<?>) plain.get("workspace_changes")).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void anExecutionRecordCarryingAWithheldNameListsNothing() throws Exception {
        final String dir = Fixtures.MCP_CALLS_DIR;
        final Map<Long, SessionDataFile> files = new java.util.TreeMap<>(Fixtures.mcpCallsDataFiles());
        final Map<String, Object> whole = view(dir, Fixtures.MCP_CALLS_ROUND_FILE, files, Collections.singletonList(Hide.TOOL_SCHEMAS));
        final int listed = ((List<?>) whole.get("tool_executions")).size();
        assertTrue(listed > 0);
        final String[] lines = new String(Fixtures.bytes(dir + Fixtures.MCP_CALLS_DATA_FILES[1]), StandardCharsets.UTF_8).split("\n", -1);
        // row 3: rows 1 and 2 are one execution written twice, kept once by its id, so flagging one of them alone
        // would leave the other
        final JsonObject execution = JsonParser.parseString(lines[3]).getAsJsonObject();
        final JsonArray flags = new JsonArray();
        flags.add(Hide.TOOL_SCHEMAS);
        execution.add("flags", flags);
        lines[3] = execution.toString();
        files.put(2L, SessionDataFile.parse(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
        final Map<String, Object> withheld = view(dir, Fixtures.MCP_CALLS_ROUND_FILE, files, Collections.singletonList(Hide.TOOL_SCHEMAS));
        assertEquals(listed - 1, ((List<?>) withheld.get("tool_executions")).size());
        assertEquals(listed, ((List<?>) view(dir, Fixtures.MCP_CALLS_ROUND_FILE, files, Hide.NONE).get("tool_executions")).size());
    }
}
