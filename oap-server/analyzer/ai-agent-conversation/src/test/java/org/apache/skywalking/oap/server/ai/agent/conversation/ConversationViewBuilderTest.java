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

package org.apache.skywalking.oap.server.ai.agent.conversation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.skywalking.oap.server.ai.agent.conversation.fold.ConversationFold;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionFlowRound;
import org.apache.skywalking.oap.server.ai.agent.conversation.view.ConversationViewBuilder;
import org.apache.skywalking.oap.server.ai.agent.conversation.view.ViewYaml;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConversationViewBuilderTest {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static Map<String, Object> view(final byte[] roundBytes, final Map<Long, SessionDataFile> files,
                                            final List<String> problems) {
        final SessionFlowRound round = SessionFlowRound.parse(roundBytes);
        final ConversationFold fold = new ConversationFold();
        final List<ConversationViewBuilder.RoundInput> rounds = new ArrayList<>();
        final List<String> all = new ArrayList<>(problems);
        if (round.isIntact()) {
            fold.apply(round);
            rounds.add(new ConversationViewBuilder.RoundInput(round, Digests.sha256Hex(roundBytes)));
        } else {
            // as the query service reports a round the Sessionizer's reader would refuse
            final String why = "sessionflow: digest mismatch";
            rounds.add(ConversationViewBuilder.RoundInput.unreadable(1, why));
            all.add("round 1 does not read: " + why);
        }
        return new ConversationViewBuilder(fold, rounds, files, all).build();
    }

    /**
     * The document the OAP builds equals, key for key and in key order, the one <code>asz conversation -json</code>
     * printed for the same three files and one round.
     */
    @Test
    public void buildsTheSameDocumentAsTheSessionizer() throws Exception {
        final Map<String, Object> doc = view(Fixtures.bytes(Fixtures.ROUND_FILE), Fixtures.dataFiles(), Collections.emptyList());
        final JsonElement expected = JsonParser.parseString(
            new String(Fixtures.bytes(Fixtures.VIEW_EXAMPLE_JSON), StandardCharsets.UTF_8));
        final JsonElement actual = GSON.toJsonTree(doc);
        // structural equality first, for a readable failure
        assertEquals(expected, actual);
        // then the key order, which the format page fixes
        assertEquals(GSON.toJson(expected), GSON.toJson(actual));
    }

    @Test
    public void yamlIsDeterministicAndReadsBackAsTheSameDocument() throws Exception {
        final Map<String, Object> doc = view(Fixtures.bytes(Fixtures.ROUND_FILE), Fixtures.dataFiles(), Collections.emptyList());
        final String yaml = ViewYaml.dump(doc);
        assertTrue(yaml.startsWith("format: asz.view\nversion: '1.0'\nconversation: " + Fixtures.SESSION + "\n"), yaml.substring(0, 80));
        assertEquals(yaml, ViewYaml.dump(view(Fixtures.bytes(Fixtures.ROUND_FILE), Fixtures.dataFiles(), Collections.emptyList())));
        assertFalse(yaml.contains("&id"), "no anchors");
        assertFalse(yaml.contains("!!"), "no type tags");
        final Object reloaded = new Yaml().load(yaml);
        assertEquals(GSON.toJsonTree(doc), GSON.toJsonTree(reloaded));
        // and the YAML the Sessionizer prints for the same conversation reads back as the same document
        final Object theirs = new Yaml().load(new String(Fixtures.bytes("asz-view-example.yaml"), StandardCharsets.UTF_8));
        assertEquals(GSON.toJsonTree(theirs), GSON.toJsonTree(reloaded));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aMissingFileIsIncompleteAndATamperedRoundIsAMismatch() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.dataFiles();
        files.remove(2L);
        final Map<String, Object> incomplete = view(Fixtures.bytes(Fixtures.ROUND_FILE), files, Collections.emptyList());
        final Map<String, Object> summary = (Map<String, Object>) incomplete.get("summary");
        assertEquals("incomplete", summary.get("state"));
        assertEquals(Collections.singletonList("round 1: landed file seq 2 is missing"), summary.get("problems"));
        final List<Map<String, Object>> rounds = (List<Map<String, Object>>) incomplete.get("rounds");
        assertEquals(Boolean.FALSE, rounds.get(0).get("verified"));
        // the rest of the document still holds what could be folded; seq 2 is the plugin's changes file, and the
        // one record it carried is gone with it, so the build's step names no change
        assertEquals(3, ((List<?>) incomplete.get("talks")).size());
        assertEquals(0, summary.get("changes"));
        assertEquals(Collections.emptyList(), incomplete.get("workspace_changes"));
        assertNull(node(incomplete, "tool/tool-run-make-build").get("changes"));

        final String tampered = new String(Fixtures.bytes(Fixtures.ROUND_FILE), StandardCharsets.UTF_8)
            .replace("\"trigger\":\"external\"", "\"trigger\":\"exterior\"");
        final Map<String, Object> mismatch = view(
            tampered.getBytes(StandardCharsets.UTF_8), Fixtures.dataFiles(), Collections.emptyList());
        assertEquals("mismatch", ((Map<String, Object>) mismatch.get("summary")).get("state"));
        assertEquals(0, ((List<?>) mismatch.get("rounds")).size());
    }

    /**
     * Rounds 2 to 4 never landed: the chain resumes at round 5, which is listed unverified because nothing links
     * it to what is absent, the gap and the files round 5 names are each one problem, and what round 1 folded is
     * still in the document.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void theChainResumesAfterAGapAndNamesItOnce() throws Exception {
        final SessionFlowRound first = Fixtures.round();
        final SessionFlowRound fifth = SessionFlowRound.parse(
            Fixtures.emptyRound(first, 5, UNKNOWN_DIGEST, 4, 6, first.getHeader().getParser()));
        final ConversationFold fold = new ConversationFold();
        assertNull(fold.apply(first));
        assertNull(fold.applyAfterGap(fifth));
        assertEquals(5L, fold.getRound());
        assertEquals(fifth.getCommitDigest(), fold.getDigest());
        // the hand-built round names no session range; the fold keeps the one round 1 gave, so the file read
        // that follows stays bounded to it rather than starting at the epoch
        assertEquals(first.getHeader().getSessionFromTime(), fold.getSessionFromTime());
        assertEquals(first.getHeader().getSessionThroughTime(), fold.getSessionThroughTime());

        final List<ConversationViewBuilder.RoundInput> rounds = new ArrayList<>();
        rounds.add(new ConversationViewBuilder.RoundInput(first, Digests.sha256Hex(Fixtures.bytes(Fixtures.ROUND_FILE))));
        rounds.add(new ConversationViewBuilder.RoundInput(fifth, UNKNOWN_DIGEST));
        final Map<String, Object> doc = new ConversationViewBuilder(
            fold, rounds, Fixtures.dataFiles(), Collections.emptyList()).build();
        final Map<String, Object> summary = (Map<String, Object>) doc.get("summary");
        assertEquals("incomplete", summary.get("state"));
        assertEquals(
            Arrays.asList("rounds 2-4 are missing before round 5", "round 5: landed files seq 5-6 are missing"),
            summary.get("problems"));
        assertEquals(5L, ((Map<String, Object>) doc.get("head")).get("round"));
        final List<Map<String, Object>> listed = (List<Map<String, Object>>) doc.get("rounds");
        assertEquals(2, listed.size());
        assertEquals(Boolean.TRUE, listed.get(0).get("verified"));
        assertEquals(Boolean.FALSE, listed.get(1).get("verified"));
        assertEquals(3, ((List<?>) doc.get("talks")).size());
    }

    private static final String UNKNOWN_DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    /**
     * The Sessionizer's workspace-changes scenario exercises every producer of a change record: a shell command the
     * plugin observed on the main stream, with two files; an <code>Edit</code> whose patch the runtime recorded on
     * its own result; and a shell command inside a subagent. The document equals the Sessionizer's, and the join
     * is by tool-use id: three entries, four files across them, one captured by the runtime and two by the plugin,
     * every entry joined to a step, and each step naming its record.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void workspaceChangesJoinTheirStepsAsTheSessionizerJoinsThem() throws Exception {
        final Map<String, Object> doc = view(
            Fixtures.bytes(Fixtures.WORKSPACE_CHANGES_DIR + Fixtures.WORKSPACE_CHANGES_ROUND_FILE),
            Fixtures.workspaceChangesDataFiles(), Collections.emptyList());
        final JsonElement expected = JsonParser.parseString(new String(
            Fixtures.bytes(Fixtures.WORKSPACE_CHANGES_DIR + Fixtures.VIEW_EXAMPLE_JSON), StandardCharsets.UTF_8));
        final JsonElement actual = GSON.toJsonTree(doc);
        assertEquals(expected, actual);
        assertEquals(GSON.toJson(expected), GSON.toJson(actual));

        final List<Map<String, Object>> changes = (List<Map<String, Object>>) doc.get("workspace_changes");
        assertEquals(3, changes.size());
        assertEquals(3, ((Map<String, Object>) doc.get("summary")).get("changes"));
        int files = 0;
        final Map<String, Integer> capturedBy = new TreeMap<>();
        for (final Map<String, Object> c : changes) {
            assertTrue(((String) c.get("step")).startsWith("tool/"), c.get("id") + " is joined to a step");
            files += ((List<?>) c.get("changes")).size();
            capturedBy.merge((String) c.get("captured_by"), 1, Integer::sum);
        }
        assertEquals(4, files);
        assertEquals(Map.of("asz-plugin", 2, "claude-code", 1), capturedBy);
        // the runtime's patch is read from the Edit's result record, the data part beside the raw result
        final Map<String, Object> edit = changes.get(1);
        assertEquals("claude-code", edit.get("captured_by"));
        assertEquals("runtime_reported", edit.get("basis"));
        assertEquals(Map.of("seq", 1L, "row", 8L, "block", 1L), edit.get("ref"));
        // and each step names its record, after its tool keys
        final Map<String, Object> step = node(doc, "tool/s3-tool");
        assertEquals(Collections.singletonList("s3-tool"), step.get("changes"));
        final List<String> keys = new ArrayList<>(step.keySet());
        assertEquals(keys.indexOf("request_to_result_join") + 1, keys.indexOf("changes"));
        assertEquals(Collections.singletonList("s2-tool"), node(doc, "tool/s2-tool").get("changes"));
        assertEquals(Collections.singletonList("tidier-s1-tool"), node(doc, "tool/tidier-s1-tool").get("changes"));
    }

    /**
     * The plugin's files did not land, and the runtime's own patch is still shown: it travels in the transcript, on
     * the result record the tool step reads. The two absent files are the chain's problems, and the plugin's two
     * records are gone with them.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void theRuntimesOwnPatchIsShownWithoutThePluginsFiles() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.workspaceChangesDataFiles();
        files.remove(2L);
        files.remove(4L);
        final Map<String, Object> doc = view(
            Fixtures.bytes(Fixtures.WORKSPACE_CHANGES_DIR + Fixtures.WORKSPACE_CHANGES_ROUND_FILE), files, Collections.emptyList());
        final Map<String, Object> summary = (Map<String, Object>) doc.get("summary");
        assertEquals("incomplete", summary.get("state"));
        assertEquals(
            Arrays.asList("round 1: landed file seq 2 is missing", "round 1: landed file seq 4 is missing"),
            summary.get("problems"));
        assertEquals(1, summary.get("changes"));
        final List<Map<String, Object>> changes = (List<Map<String, Object>>) doc.get("workspace_changes");
        assertEquals(1, changes.size());
        assertEquals("claude-code", changes.get(0).get("captured_by"));
        assertEquals("tool/s3-tool", changes.get(0).get("step"));
        assertEquals(Collections.singletonList("s3-tool"), node(doc, "tool/s3-tool").get("changes"));
        assertNull(node(doc, "tool/s2-tool").get("changes"));
        assertNull(node(doc, "tool/tidier-s1-tool").get("changes"));
    }

    /**
     * The files may come in any order: the document lists and reads them by seq, so it is still the Sessionizer's.
     */
    @Test
    public void filesGivenInAnyOrderAreReadBySeq() throws Exception {
        final String[][] sets = {
            {"", Fixtures.ROUND_FILE},
            {Fixtures.WORKSPACE_CHANGES_DIR, Fixtures.WORKSPACE_CHANGES_DIR + Fixtures.WORKSPACE_CHANGES_ROUND_FILE},
        };
        final List<Map<Long, SessionDataFile>> files = Arrays.asList(Fixtures.dataFiles(), Fixtures.workspaceChangesDataFiles());
        for (int i = 0; i < sets.length; i++) {
            final List<Long> seqs = new ArrayList<>(files.get(i).keySet());
            Collections.sort(seqs, Collections.reverseOrder());
            final Map<Long, SessionDataFile> reversed = new LinkedHashMap<>();
            for (final Long seq : seqs) {
                reversed.put(seq, files.get(i).get(seq));
            }
            final Map<String, Object> doc = view(Fixtures.bytes(sets[i][1]), reversed, Collections.emptyList());
            final JsonElement expected = JsonParser.parseString(
                new String(Fixtures.bytes(sets[i][0] + Fixtures.VIEW_EXAMPLE_JSON), StandardCharsets.UTF_8));
            assertEquals(GSON.toJson(expected), GSON.toJson(GSON.toJsonTree(doc)), sets[i][1]);
        }
    }

    /**
     * Two producers' records of one instant list the runtime's first, whatever their ids say, and a record landed
     * twice is listed once. The Sessionizer's document for the same edited files lists them in this order.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void theRuntimesChangeRecordLeadsAtOneInstantAndARecordLandedTwiceIsOne() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.workspaceChangesDataFiles();
        final String plugin = new String(
            Fixtures.bytes(Fixtures.WORKSPACE_CHANGES_DIR + Fixtures.WORKSPACE_CHANGES_DATA_FILES[1]), StandardCharsets.UTF_8);
        // the header, the one record, and the end frame, which the edited file goes without, as the Sessionizer read it
        final String[] lines = plugin.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains("\"time\":\"2026-01-01T00:00:01.600Z\""));
        final String atTheRuntimesInstant = lines[1].replace("\"time\":\"2026-01-01T00:00:01.600Z\"",
                                                             "\"time\":\"2026-01-01T00:00:02.900Z\"");
        files.put(2L, SessionDataFile.parse((lines[0] + "\n" + atTheRuntimesInstant + "\n" + atTheRuntimesInstant + "\n")
                                                .getBytes(StandardCharsets.UTF_8)));
        final Map<String, Object> doc = view(
            Fixtures.bytes(Fixtures.WORKSPACE_CHANGES_DIR + Fixtures.WORKSPACE_CHANGES_ROUND_FILE), files, Collections.emptyList());
        final List<String> listed = new ArrayList<>();
        for (final Map<String, Object> c : (List<Map<String, Object>>) doc.get("workspace_changes")) {
            listed.add(c.get("id") + " " + c.get("captured_by") + " " + c.get("time") + " " + c.get("ref"));
        }
        // the record landed twice is kept at its first line
        assertEquals(Arrays.asList("s3-tool claude-code 2026-01-01T00:00:02.900Z {seq=1, row=8, block=1}",
                                   "s2-tool asz-plugin 2026-01-01T00:00:02.900Z {seq=2, row=1, block=0}",
                                   "tidier-s1-tool asz-plugin 2026-01-01T00:00:06.400Z {seq=4, row=1, block=0}"), listed);
    }

    /**
     * The Sessionizer's provider-bodies scenario: a main stream and a subagent, every call's request and response
     * landed in one <code>provider_body</code> file. The document equals the Sessionizer's. Each call names its request
     * and then its response by where they landed, never their bytes, and the summary counts the bodies and the calls
     * whose request is captured.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void providerBodiesJoinTheirCallsAsTheSessionizerJoinsThem() throws Exception {
        final Map<String, Object> doc = view(
            Fixtures.bytes(Fixtures.PROVIDER_BODIES_DIR + Fixtures.PROVIDER_BODIES_ROUND_FILE),
            Fixtures.providerBodiesDataFiles(), Collections.emptyList());
        final JsonElement expected = JsonParser.parseString(new String(
            Fixtures.bytes(Fixtures.PROVIDER_BODIES_DIR + Fixtures.VIEW_EXAMPLE_JSON), StandardCharsets.UTF_8));
        final JsonElement actual = GSON.toJsonTree(doc);
        assertEquals(expected, actual);
        assertEquals(GSON.toJson(expected), GSON.toJson(actual));

        final Map<String, Object> summary = (Map<String, Object>) doc.get("summary");
        assertEquals(14L, summary.get("provider_bodies"));
        assertEquals(7, summary.get("captured_prompts"));
        final Map<String, Object> first = node(doc, "call/s2-call-fdae022ac306");
        assertEquals(Arrays.asList(
            Map.of("role", "request", "ref", Map.of("seq", 4L, "row", 1L)),
            Map.of("role", "response", "ref", Map.of("seq", 4L, "row", 2L))), first.get("provider_bodies"));
        // a subagent's calls join in its own stream, from the same file
        assertEquals(2, ((List<?>) node(doc, "call/searcher-s1-call-fdae022ac306").get("provider_bodies")).size());
    }

    /**
     * The session's <code>provider_body</code> file is missing. The round carries the join, so every call still names
     * where its bodies landed, and the missing file is the chain's problem, as in the Sessionizer's document for the
     * same files: 14 bodies named on the calls, 7 of them requests, and one problem.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aSessionMissingItsBodyFileStillNamesWhatTheRoundJoined() throws Exception {
        final byte[] round = Fixtures.bytes(Fixtures.PROVIDER_BODIES_DIR + Fixtures.PROVIDER_BODIES_ROUND_FILE);
        final Map<Long, SessionDataFile> files = Fixtures.providerBodiesDataFiles();
        final Map<String, Object> whole = view(round, files, Collections.emptyList());
        files.remove(4L);
        final Map<String, Object> without = view(round, files, Collections.emptyList());
        final Map<String, Object> summary = (Map<String, Object>) without.get("summary");
        assertEquals("incomplete", summary.get("state"));
        assertEquals(Collections.singletonList("round 1: landed file seq 4 is missing"), summary.get("problems"));
        assertEquals(14L, summary.get("provider_bodies"));
        assertEquals(7, summary.get("captured_prompts"));
        assertEquals(GSON.toJsonTree(whole.get("talks")), GSON.toJsonTree(without.get("talks")));
        assertEquals(GSON.toJsonTree(whole.get("loose")), GSON.toJsonTree(without.get("loose")));
    }

    /**
     * An API error the runtime wrote as a call, between two calls it sent. No provider was called for it, so it lists
     * no bodies and is not the call before the next one: the next call's request names the call before the error,
     * and joins. The document equals the Sessionizer's.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aSyntheticCallTakesPartInNoJoin() throws Exception {
        final Map<String, Object> doc = view(
            Fixtures.bytes(Fixtures.PROVIDER_BODIES_ERRORS_DIR + Fixtures.PROVIDER_BODIES_ERRORS_ROUND_FILE),
            Fixtures.providerBodiesErrorsDataFiles(), Collections.emptyList());
        final JsonElement expected = JsonParser.parseString(new String(
            Fixtures.bytes(Fixtures.PROVIDER_BODIES_ERRORS_DIR + Fixtures.VIEW_EXAMPLE_JSON), StandardCharsets.UTF_8));
        final JsonElement actual = GSON.toJsonTree(doc);
        assertEquals(expected, actual);
        assertEquals(GSON.toJson(expected), GSON.toJson(actual));

        final Map<String, Object> summary = (Map<String, Object>) doc.get("summary");
        assertEquals(4L, summary.get("provider_bodies"));
        assertEquals(2, summary.get("captured_prompts"));
        assertNull(node(doc, "call/s3-synthetic-call").get("provider_bodies"));
        assertEquals(Arrays.asList(
            Map.of("role", "request", "ref", Map.of("seq", 2L, "row", 3L)),
            Map.of("role", "response", "ref", Map.of("seq", 2L, "row", 4L))), node(doc, "call/s4-call-f7d240c7da38").get("provider_bodies"));
    }

    /**
     * The Sessionizer's mcp-calls scenario: calls to MCP servers on the main stream and in a subagent, each with the
     * record the plugin wrote after it, in an <code>execution</code> file of its stream. The document equals the
     * Sessionizer's. Every record joins its step by the tool-use id; the record the plugin wrote twice is listed once,
     * at its first line; the call whose name does not split into one server and one tool has no MCP attributes, and
     * no record, as none was written for it. Each step names its record after its tool keys.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void executionsJoinTheirStepsAsTheSessionizerJoinsThem() throws Exception {
        final Map<String, Object> doc = view(
            Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.MCP_CALLS_ROUND_FILE),
            Fixtures.mcpCallsDataFiles(), Collections.emptyList());
        final JsonElement expected = JsonParser.parseString(new String(
            Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.VIEW_EXAMPLE_JSON), StandardCharsets.UTF_8));
        final JsonElement actual = GSON.toJsonTree(doc);
        assertEquals(expected, actual);
        assertEquals(GSON.toJson(expected), GSON.toJson(actual));

        final List<Map<String, Object>> executions = (List<Map<String, Object>>) doc.get("tool_executions");
        assertEquals(4, executions.size());
        final Map<String, Integer> outcomes = new TreeMap<>();
        for (final Map<String, Object> e : executions) {
            assertTrue(((String) e.get("step")).startsWith("tool/"), e.get("id") + " is joined to a step");
            outcomes.merge((String) e.get("outcome"), 1, Integer::sum);
        }
        assertEquals(Map.of("returned", 3, "failed", 1), outcomes);
        // the line written twice is kept once, at the first of the two
        assertEquals("s2-tool/client_hook", executions.get(0).get("id"));
        assertEquals(Map.of("seq", 2L, "row", 1L, "block", 0L), executions.get(0).get("ref"));
        assertEquals(Map.of("name", "status", "source", "user"), executions.get(0).get("server"));
        assertEquals(380L, executions.get(0).get("duration_ms"));

        final Map<String, Object> step = node(doc, "tool/s2-tool");
        assertEquals(Collections.singletonList("s2-tool/client_hook"), step.get("executions"));
        final List<String> keys = new ArrayList<>(step.keySet());
        assertEquals(keys.indexOf("request_to_result_join") + 1, keys.indexOf("executions"));
        final Map<String, Object> attrs = (Map<String, Object>) step.get("attrs");
        assertEquals("status", attrs.get("mcp_server"));
        assertEquals("lookup", attrs.get("mcp_tool"));
        assertEquals(Collections.singletonList("auditor-s1-tool/client_hook"), node(doc, "tool/auditor-s1-tool").get("executions"));
        final Map<String, Object> odd = node(doc, "tool/s5-tool");
        assertNull(odd.get("executions"));
        assertFalse(((Map<String, Object>) odd.get("attrs")).containsKey("mcp_server"));
    }

    /**
     * Talks are ordered by when they began, at the nanosecond, as the Sessionizer orders them. Here the subagent's talk,
     * which landed after the main one, began 0.8 milliseconds before it, within the same millisecond: ordered by the
     * millisecond the two would tie, and the landed order would put the main talk first.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void talksBeginningInOneMillisecondKeepTheirOrder() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.mcpCallsDataFiles();
        final String main = new String(Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.MCP_CALLS_DATA_FILES[0]), StandardCharsets.UTF_8);
        final String child = new String(Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.MCP_CALLS_DATA_FILES[2]), StandardCharsets.UTF_8);
        assertTrue(main.contains("\"time\":\"2026-01-01T00:00:00.000Z\"") && child.contains("\"time\":\"2026-01-01T00:00:09.500Z\""));
        files.put(1L, SessionDataFile.parse(main.replace("\"time\":\"2026-01-01T00:00:00.000Z\"",
            "\"time\":\"2026-01-01T00:00:00.000900Z\"").getBytes(StandardCharsets.UTF_8)));
        files.put(3L, SessionDataFile.parse(child.replace("\"time\":\"2026-01-01T00:00:09.500Z\"",
            "\"time\":\"2026-01-01T00:00:00.000100Z\"").getBytes(StandardCharsets.UTF_8)));
        final Map<String, Object> doc = view(
            Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.MCP_CALLS_ROUND_FILE), files, Collections.emptyList());
        final List<Map<String, Object>> talks = (List<Map<String, Object>>) doc.get("talks");
        assertEquals("talk/a39a255c7ba239348", talks.get(0).get("id"));
        assertEquals("talk/main/s1-cycle", talks.get(1).get("id"));
        assertEquals(talks.get(0).get("from"), talks.get(1).get("from"));
    }

    /**
     * Two records of one instant are listed in the order they were read, never by id and never by how the time is
     * spelled: here the ids and the text of the times sort the other way. The Sessionizer's document for the same
     * edited files lists them so.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void recordsOfOneTimeAreInTheOrderTheyWereRead() throws Exception {
        final Map<Long, SessionDataFile> changes = Fixtures.workspaceChangesDataFiles();
        final String plugin = new String(
            Fixtures.bytes(Fixtures.WORKSPACE_CHANGES_DIR + Fixtures.WORKSPACE_CHANGES_DATA_FILES[1]), StandardCharsets.UTF_8);
        assertTrue(plugin.contains("\"time\":\"2026-01-01T00:00:01.600Z\"") && plugin.contains("\"id\":\"s2-tool\""));
        changes.put(2L, SessionDataFile.parse(plugin.replace("\"time\":\"2026-01-01T00:00:01.600Z\"", "\"time\":\"2026-01-01T00:00:06.4Z\"")
                                                    .replace("\"id\":\"s2-tool\"", "\"id\":\"zz-s2-tool\"")
                                                    .getBytes(StandardCharsets.UTF_8)));
        final List<String> listed = new ArrayList<>();
        for (final Map<String, Object> c : (List<Map<String, Object>>) view(
            Fixtures.bytes(Fixtures.WORKSPACE_CHANGES_DIR + Fixtures.WORKSPACE_CHANGES_ROUND_FILE), changes,
            Collections.emptyList()).get("workspace_changes")) {
            listed.add((String) c.get("id"));
        }
        assertEquals(Arrays.asList("s3-tool", "zz-s2-tool", "tidier-s1-tool"), listed);

        final Map<Long, SessionDataFile> executions = Fixtures.mcpCallsDataFiles();
        final String main = new String(Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.MCP_CALLS_DATA_FILES[1]), StandardCharsets.UTF_8);
        assertTrue(main.contains("\"time\":\"2026-01-01T00:00:06.200Z\""));
        executions.put(2L, SessionDataFile.parse(main.replace("\"time\":\"2026-01-01T00:00:06.200Z\"", "\"time\":\"2026-01-01T00:00:06.2Z\"")
                                                     .getBytes(StandardCharsets.UTF_8)));
        final String child = new String(Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.MCP_CALLS_DATA_FILES[3]), StandardCharsets.UTF_8);
        assertTrue(child.contains("\"time\":\"2026-01-01T00:00:10.900Z\""));
        executions.put(4L, SessionDataFile.parse(child.replace("\"time\":\"2026-01-01T00:00:10.900Z\"", "\"time\":\"2026-01-01T00:00:06.200Z\"")
                                                      .getBytes(StandardCharsets.UTF_8)));
        final List<String> observed = new ArrayList<>();
        for (final Map<String, Object> e : (List<Map<String, Object>>) view(
            Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.MCP_CALLS_ROUND_FILE), executions,
            Collections.emptyList()).get("tool_executions")) {
            observed.add((String) e.get("id"));
        }
        assertEquals(Arrays.asList("s2-tool/client_hook", "s3-tool/client_hook", "s4-tool/client_hook",
                                   "auditor-s1-tool/client_hook"), observed);
    }

    /**
     * A talk nothing under which has a time comes after every talk that has one, though its stream landed first. The
     * Sessionizer's document for the same edited files orders them so.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void anUntimedTalkComesAfterEveryTimedOne() throws Exception {
        final Map<Long, SessionDataFile> files = Fixtures.mcpCallsDataFiles();
        final String main = new String(Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.MCP_CALLS_DATA_FILES[0]), StandardCharsets.UTF_8);
        final String untimed = main.replaceAll(",\"time\":\"[^\"]*\"", "");
        assertFalse(untimed.contains("\"time\""));
        files.put(1L, SessionDataFile.parse(untimed.getBytes(StandardCharsets.UTF_8)));
        final Map<String, Object> doc = view(
            Fixtures.bytes(Fixtures.MCP_CALLS_DIR + Fixtures.MCP_CALLS_ROUND_FILE), files, Collections.emptyList());
        final List<Map<String, Object>> talks = (List<Map<String, Object>>) doc.get("talks");
        assertEquals("talk/a39a255c7ba239348", talks.get(0).get("id"));
        assertEquals("talk/main/s1-cycle", talks.get(1).get("id"));
        assertNull(talks.get(1).get("from"));
    }

    /**
     * A session's body count is an integer: written as 1.5 it is none, so the document counts 0 bodies while the calls
     * still name theirs. With the provider bodies taken out of a call's attributes, the rest are listed by code point:
     * U+E000 before U+10000, where Java's own order of strings puts it after. The Sessionizer's document for the same
     * round, re-signed, says 0 bodies, 7 captured prompts, and that key order.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aBodyCountIsAnIntegerAndAttrsKeysSortByCodePoint() throws Exception {
        String round = new String(
            Fixtures.bytes(Fixtures.PROVIDER_BODIES_DIR + Fixtures.PROVIDER_BODIES_ROUND_FILE), StandardCharsets.UTF_8);
        final String bodies = "\"provider_bodies\":[{\"role\":\"request\",\"ref\":{\"seq\":4,\"row\":1}}";
        assertTrue(round.contains(bodies) && round.contains("\"provider_bodies_landed\":14"));
        round = round.replace(bodies, "\"\\ue000\":1,\"\\ud800\\udc00\":2," + bodies)
                     .replace("\"provider_bodies_landed\":14", "\"provider_bodies_landed\":1.5");
        final Map<String, Object> doc = view(resign(round), Fixtures.providerBodiesDataFiles(), Collections.emptyList());
        final Map<String, Object> summary = (Map<String, Object>) doc.get("summary");
        assertEquals("verified", summary.get("state"));
        assertEquals(0L, summary.get("provider_bodies"));
        assertEquals(7, summary.get("captured_prompts"));
        final Map<String, Object> attrs = (Map<String, Object>) node(doc, "call/s2-call-fdae022ac306").get("attrs");
        assertEquals(Arrays.asList("fragments", "usage", "usage_at", "usage_from", "\ue000", "\ud800\udc00"),
                     new ArrayList<>(attrs.keySet()));
        // the count is a whole number; written any other way, there is none
        final String original = new String(
            Fixtures.bytes(Fixtures.PROVIDER_BODIES_DIR + Fixtures.PROVIDER_BODIES_ROUND_FILE), StandardCharsets.UTF_8);
        final String[][] counts = {
            {"\"provider_bodies_landed\":14", "14"},
            {"\"provider_bodies_landed\":\"14\"", "0"},
        };
        for (final String[] c : counts) {
            final Map<String, Object> changed = view(resign(original.replace("\"provider_bodies_landed\":14", c[0])),
                                                     Fixtures.providerBodiesDataFiles(), Collections.emptyList());
            assertEquals(Long.parseLong(c[1]), ((Map<String, Object>) changed.get("summary")).get("provider_bodies"), c[0]);
        }
    }

    /**
     * A stream more than one step could have started lists every one as an origin, in the order those steps happened,
     * by their position in the stream, whatever the relation ids say and whatever order the round wrote the relations
     * in. The ids are tried both ways round. Relations one record supports are listed by id, in Go's order of strings:
     * U+E000 before U+10000, which Java's own order reverses. The Sessionizer's document for the same round, re-signed,
     * lists them so.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aStreamsOriginsAreInTheOrderTheirStepsHappened() throws Exception {
        String round = new String(
            Fixtures.bytes(Fixtures.PROVIDER_BODIES_DIR + Fixtures.PROVIDER_BODIES_ROUND_FILE), StandardCharsets.UTF_8);
        final String starts = "{\"t\":\"relation\",\"id\":\"rel/starts/tool_s5-tool/stream_a02ba01b82410e9e3\"";
        final String counts = "\"counts\":{\"nodes\":40,\"relations\":9,\"unresolved\":0}";
        assertTrue(round.contains(starts) && round.contains(counts));
        final String more = "{\"t\":\"relation\",\"id\":\"rel/starts/zz-late/stream_a02ba01b82410e9e3\",\"revision\":1,"
            + "\"type\":\"starts\",\"from\":\"tool/s2-tool\",\"to\":\"stream/a02ba01b82410e9e3\",\"quality\":\"exact_unique\","
            + "\"via\":\"parent tool result\",\"evidence\":[{\"seq\":1,\"row\":9}]}\n"
            + "{\"t\":\"relation\",\"id\":\"rel/starts/aa-early/stream_a02ba01b82410e9e3\",\"revision\":1,"
            + "\"type\":\"starts\",\"from\":\"call/s2-call-fdae022ac306\",\"to\":\"stream/a02ba01b82410e9e3\","
            + "\"quality\":\"exact_unique\",\"via\":\"parent tool result\",\"evidence\":[{\"seq\":1,\"row\":9}]}\n";
        round = round.replace(starts, more + starts).replace(counts, counts.replace("\"relations\":9", "\"relations\":11"));
        final String[] renamed = {
            round,
            round.replace("rel/starts/aa-early/stream_a02ba01b82410e9e3", "rel/starts/zz-late-2")
                 .replace("rel/starts/zz-late/stream_a02ba01b82410e9e3", "rel/starts/aa-early-2"),
            round.replace("rel/starts/aa-early/stream_a02ba01b82410e9e3", "rel/starts/\ue000")
                 .replace("rel/starts/zz-late/stream_a02ba01b82410e9e3", "rel/starts/\ud800\udc00"),
        };
        for (final String r : renamed) {
            final Map<String, Object> doc = view(resign(r), Fixtures.providerBodiesDataFiles(), Collections.emptyList());
            assertEquals("verified", ((Map<String, Object>) doc.get("summary")).get("state"));
            final List<String> origins = new ArrayList<>();
            for (final Map<String, Object> st : (List<Map<String, Object>>) doc.get("streams")) {
                if ("stream/a02ba01b82410e9e3".equals(st.get("id"))) {
                    for (final Map<String, Object> o : (List<Map<String, Object>>) st.get("opened_by")) {
                        origins.add((String) o.get("step"));
                    }
                }
            }
            // rows 3, 4 and 8 of the main transcript
            assertEquals(Arrays.asList("call/s2-call-fdae022ac306", "tool/s2-tool", "tool/s5-tool"), origins);
        }
        final List<String> relations = new ArrayList<>();
        for (final Map<String, Object> rel : (List<Map<String, Object>>) view(
            resign(renamed[2]), Fixtures.providerBodiesDataFiles(), Collections.emptyList()).get("relations")) {
            relations.add((String) rel.get("id"));
        }
        assertEquals(relations.indexOf("rel/starts/\ue000") + 1, relations.indexOf("rel/starts/\ud800\udc00"));
    }

    /**
     * @return the round's bytes with its commit digest made the digest of every line before it, as a round the
     * Sessionizer wrote after the change would carry
     */
    /**
     * Each stream's origins are ordered on their own. One order over every stream's origins, split by stream after,
     * is not the same: an untimed step holds back the rest of its lane, so a step that started another stream moved
     * this one's origins. Here A1, untimed, starts X; A2 at time 10 and B1 at time 50 start S; and A0, untimed and in
     * no stream, starts S too: it is not listed, and it must not move the origins that are. The Sessionizer's
     * TestEachStreamsOriginsAreOrderedOnTheirOwn lists S's origins as A2 then B1 for the same fold.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void eachStreamsOriginsAreOrderedOnTheirOwn() throws Exception {
        final byte[] round = round(
            frameNode("stream/A", "stream", "A", 1, 1, ",\"attrs\":{\"role\":\"main\"}"),
            frameNode("stream/B", "stream", "B", 2, 1, ""),
            frameNode("stream/S", "stream", "S", 2, 1, ""),
            frameNode("stream/X", "stream", "X", 2, 1, ""),
            frameNode("tool/a0", "tool", "", 1, 1, ""),
            frameNode("tool/a1", "tool", "A", 1, 1, ""),
            frameNode("tool/a2", "tool", "A", 1, 2, ""),
            frameNode("tool/b1", "tool", "B", 2, 1, ""),
            frameStarts("rel/0", "tool/a0", "stream/S"),
            frameStarts("rel/1", "tool/a1", "stream/X"),
            frameStarts("rel/2", "tool/a2", "stream/S"),
            frameStarts("rel/3", "tool/b1", "stream/S"));
        final Map<Long, SessionDataFile> files = new TreeMap<>();
        files.put(1L, dataFile(1, "transcript", "A", "", "", "2026-01-01T00:00:10Z"));
        files.put(2L, dataFile(2, "transcript", "B", "", "2026-01-01T00:00:50Z"));
        final Map<String, Object> doc = view(round, files, Collections.emptyList());
        final List<String> origins = new ArrayList<>();
        for (final Map<String, Object> st : (List<Map<String, Object>>) doc.get("streams")) {
            if ("stream/S".equals(st.get("id"))) {
                for (final Map<String, Object> o : (List<Map<String, Object>>) st.get("opened_by")) {
                    origins.add((String) o.get("step"));
                }
            }
        }
        assertEquals(Arrays.asList("tool/a2", "tool/b1"), origins);
    }

    /**
     * A file is in the lane of the directory it lands in, as the Sessionizer reads the files by their place: a journal
     * is in its run's lane, even when its header also names a stream. Here r1 is supported by a transcript record at
     * 00:30 and r2 by a record of that journal at 00:10, so r2 comes first; in one lane, r1 would, by position.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aJournalIsInItsRunsLane() throws Exception {
        final byte[] round = round(
            frameNode("stream/A", "stream", "A", 1, 1, ",\"attrs\":{\"role\":\"main\"}"),
            frameNode("tool/a1", "tool", "A", 1, 1, ""),
            frameRelation("rel/1", "tool/a1", "stream/A", 1, 1),
            frameRelation("rel/2", "tool/a1", "stream/A", 2, 1));
        final Map<Long, SessionDataFile> files = new TreeMap<>();
        files.put(1L, dataFile(1, "transcript", "A", "", "2026-01-01T00:00:30Z"));
        files.put(2L, dataFile(2, "journal", "A", "R", "2026-01-01T00:00:10Z"));
        assertEquals(Arrays.asList("rel/2", "rel/1"), relationIds(view(round, files, Collections.emptyList())));
    }

    /**
     * The session's provider bodies are in no stream or run, and neither is a file that did not land: the Sessionizer
     * reads both in the one lane of the session, so position decides between them, not time.
     */
    @Test
    public void theSessionsProviderBodiesAreInNoLane() throws Exception {
        final byte[] round = round(
            frameNode("stream/A", "stream", "A", 3, 1, ",\"attrs\":{\"role\":\"main\"}"),
            frameNode("tool/a1", "tool", "A", 3, 1, ""),
            frameRelation("rel/1", "tool/a1", "stream/A", 1, 1),
            frameRelation("rel/2", "tool/a1", "stream/A", 2, 1));
        final Map<Long, SessionDataFile> files = new TreeMap<>();
        files.put(2L, dataFile(2, "provider_body", "", "", "2026-01-01T00:00:10Z"));
        files.put(3L, dataFile(3, "transcript", "A", "", "2026-01-01T00:00:30Z"));
        assertEquals(Arrays.asList("rel/1", "rel/2"), relationIds(view(round, files, Collections.emptyList())));
    }

    /**
     * A call's usage is read from the record its attrs name under <code>usage_at</code>. A reference with nothing in it
     * names no record, and one of another shape gives no usage, rather than failing the document.
     */
    @Test
    public void aCallsUsageIsReadFromTheRecordItsAttrsName() throws Exception {
        final String usage = "{\"h\":1,\"schema\":\"sd/1\",\"seq\":1,\"at\":\"2026-01-01T00:00:00Z\",\"kind\":\"transcript\","
            + "\"adapter\":\"mock/0.2.0\",\"dialect\":\"mock/1\",\"src\":\"x\",\"session\":\"s\",\"stream\":\"A\"}\n"
            + "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"time\":\"2026-01-01T00:00:01Z\",\"parts\":[]}\n"
            + "{\"ord\":2,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"time\":\"2026-01-01T00:00:02Z\",\"usage\":{\"in\":5},\"parts\":[]}\n";
        final Map<Long, SessionDataFile> files = new TreeMap<>();
        files.put(1L, SessionDataFile.parse(usage.getBytes(StandardCharsets.UTF_8)));
        final Object[][] cases = {
            {",\"attrs\":{\"usage_at\":{\"seq\":1,\"row\":2}}", Map.of("in", 5L)},
            {",\"attrs\":{\"usage_at\":{}}", null},
            {",\"attrs\":{\"usage_at\":{\"seq\":\"1\",\"row\":2}}", null},
        };
        for (final Object[] c : cases) {
            final Map<String, Object> doc = view(round(
                frameNode("stream/A", "stream", "A", 1, 1, ",\"attrs\":{\"role\":\"main\"}"),
                frameNode("call/c1", "llm.call", "A", 1, 1, (String) c[0])), files, Collections.emptyList());
            assertEquals(c[1], node(doc, "call/c1").get("usage"), (String) c[0]);
        }
    }

    /**
     * A run is loose only when no talk is above it however deep it sits, as the Sessionizer walks the whole parent
     * chain. A chain whose parents loop, which the Sessionizer would walk for ever, stops where it comes back round.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aRunDeepUnderATalkIsNotLoose() throws Exception {
        final List<String> frames = new ArrayList<>();
        frames.add(frameNode("stream/A", "stream", "A", 1, 1, ",\"attrs\":{\"role\":\"main\"}"));
        frames.add(frameNode("talk/a", "talk", "A", 1, 1, ""));
        for (int i = 1; i <= 70; i++) {
            frames.add(frameNode("run/" + i, "run", "A", 1, 1, ",\"parent\":\"" + (i == 1 ? "talk/a" : "run/" + (i - 1)) + "\""));
        }
        frames.add(frameNode("run/loop-1", "run", "A", 1, 1, ",\"parent\":\"run/loop-2\""));
        frames.add(frameNode("run/loop-2", "run", "A", 1, 1, ",\"parent\":\"run/loop-1\""));
        final Map<Long, SessionDataFile> files = new TreeMap<>();
        files.put(1L, dataFile(1, "transcript", "A", "", "2026-01-01T00:00:01Z"));
        final List<String> loose = new ArrayList<>();
        for (final Map<String, Object> n : (List<Map<String, Object>>) view(round(frames.toArray(new String[0])), files,
            Collections.emptyList()).get("loose")) {
            loose.add((String) n.get("id"));
        }
        // only the two runs that loop, which the Sessionizer never finishes walking; none of the seventy under the talk
        assertEquals(Arrays.asList("run/loop-1", "run/loop-2"), loose);
    }

    /** A stream's record count is a 64-bit integer, kept past 32 bits. */
    @Test
    @SuppressWarnings("unchecked")
    public void aStreamsRecordCountKeepsSixtyFourBits() throws Exception {
        final Map<Long, SessionDataFile> files = new TreeMap<>();
        files.put(1L, dataFile(1, "transcript", "A", "", "2026-01-01T00:00:01Z"));
        final Map<String, Object> doc = view(round(
            frameNode("stream/A", "stream", "A", 1, 1, ",\"attrs\":{\"role\":\"main\",\"records\":2147483648}")),
            files, Collections.emptyList());
        assertEquals(2147483648L, ((List<Map<String, Object>>) doc.get("streams")).get(0).get("records"));
    }

    /** A round of these node and relation frames, with its header and commit, signed. */
    private static byte[] round(final String... frames) {
        long nodes = 0;
        for (final String f : frames) {
            nodes += f.startsWith("{\"t\":\"node\"") ? 1 : 0;
        }
        return resign("{\"t\":\"header\",\"schema\":\"sf/1\",\"conversation\":\"c\",\"session\":\"s\",\"round\":1,"
            + "\"from_seq\":1,\"through_seq\":3,\"input_digest\":\"x\",\"parser\":\"p\",\"policy\":\"p\"}\n"
            + String.join("\n", frames) + "\n"
            + "{\"t\":\"commit\",\"digest\":\"0\",\"counts\":{\"nodes\":" + nodes + ",\"relations\":" + (frames.length - nodes)
            + ",\"unresolved\":0}}\n");
    }

    private static String frameRelation(final String id, final String from, final String to, final long seq, final long row) {
        return "{\"t\":\"relation\",\"id\":\"" + id + "\",\"revision\":1,\"type\":\"reports\",\"from\":\"" + from
            + "\",\"to\":\"" + to + "\",\"quality\":\"exact_unique\",\"evidence\":[{\"seq\":" + seq + ",\"row\":" + row + "}]}";
    }

    @SuppressWarnings("unchecked")
    private static List<String> relationIds(final Map<String, Object> doc) {
        final List<String> out = new ArrayList<>();
        for (final Map<String, Object> r : (List<Map<String, Object>>) doc.get("relations")) {
            out.add((String) r.get("id"));
        }
        return out;
    }

    private static String frameNode(final String id, final String kind, final String stream, final long seq, final long row,
                                    final String more) {
        return "{\"t\":\"node\",\"id\":\"" + id + "\",\"revision\":1,\"kind\":\"" + kind + "\",\"stream\":\"" + stream
            + "\",\"ref\":{\"seq\":" + seq + ",\"row\":" + row + "}" + more + "}";
    }

    private static String frameStarts(final String id, final String from, final String to) {
        return "{\"t\":\"relation\",\"id\":\"" + id + "\",\"revision\":1,\"type\":\"starts\",\"from\":\"" + from
            + "\",\"to\":\"" + to + "\",\"quality\":\"exact_unique\"}";
    }

    /**
     * A landed file of one kind, its stream and its run, with one record per time given, an empty time meaning a record
     * that carries none.
     */
    private static SessionDataFile dataFile(final long seq, final String kind, final String stream, final String run,
                                            final String... times) {
        final StringBuilder body = new StringBuilder("{\"h\":1,\"schema\":\"sd/1\",\"seq\":" + seq
            + ",\"at\":\"2026-01-01T00:00:00Z\",\"kind\":\"" + kind + "\",\"adapter\":\"mock/0.2.0\",\"dialect\":\"mock/1\","
            + "\"src\":\"x\",\"session\":\"s\",\"stream\":\"" + stream + "\""
            + (run.isEmpty() ? "" : ",\"batch\":\"" + run + "\"") + "}\n");
        for (int i = 0; i < times.length; i++) {
            body.append("{\"ord\":").append(i + 1).append(",\"off\":0,\"sha\":\"0\",\"bytes\":1")
                .append(times[i].isEmpty() ? "" : ",\"time\":\"" + times[i] + "\"").append(",\"parts\":[]}\n");
        }
        return SessionDataFile.parse(body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] resign(final String round) {
        final String body = round.substring(0, round.lastIndexOf("{\"t\":\"commit\""));
        final String commit = round.substring(body.length()).replaceFirst(
            "\"digest\":\"[0-9a-f]+\"", "\"digest\":\"" + Digests.sha256Hex(body.getBytes(StandardCharsets.UTF_8)) + "\"");
        return (body + commit).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A LangChain conversation the Sessionizer landed from what a real LangSmith client sent: a main stream, a nested
     * agent's child stream, and an auxiliary stream that summarises. The document equals the Sessionizer's. The
     * auxiliary stream is the same agent carrying another prompt, not a child, so only the nested agent's talk is a
     * child's.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aLangChainConversationIsTheSessionizersDocument() throws Exception {
        final Map<String, Object> doc = view(
            Fixtures.bytes(Fixtures.LANGCHAIN_SUBAGENT_DIR + Fixtures.LANGCHAIN_SUBAGENT_ROUND_FILE),
            Fixtures.langchainSubagentDataFiles(), Collections.emptyList());
        final JsonElement expected = JsonParser.parseString(new String(
            Fixtures.bytes(Fixtures.LANGCHAIN_SUBAGENT_DIR + Fixtures.VIEW_EXAMPLE_JSON), StandardCharsets.UTF_8));
        final JsonElement actual = GSON.toJsonTree(doc);
        assertEquals(expected, actual);
        assertEquals(GSON.toJson(expected), GSON.toJson(actual));
        final Map<String, Boolean> child = new TreeMap<>();
        for (final Map<String, Object> t : (List<Map<String, Object>>) doc.get("talks")) {
            child.put((String) t.get("stream"), Boolean.TRUE.equals(t.get("child")));
        }
        assertEquals(Map.of("main", false, "delegate-to-analyst-6e581483db74", true, "summarise-d6aa2d533d9e", false), child);
    }

    /**
     * @return the node of that id under <code>talks</code> or <code>loose</code>
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> node(final Map<String, Object> doc, final String id) {
        final List<Map<String, Object>> roots = new ArrayList<>((List<Map<String, Object>>) doc.get("talks"));
        roots.addAll((List<Map<String, Object>>) doc.get("loose"));
        final Map<String, Object> found = find(roots, id);
        assertNotNull(found, "no node " + id);
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> find(final List<Map<String, Object>> nodes, final String id) {
        for (final Map<String, Object> n : nodes) {
            if (id.equals(n.get("id"))) {
                return n;
            }
            final List<Map<String, Object>> children = (List<Map<String, Object>>) n.get("children");
            if (children != null) {
                final Map<String, Object> found = find(children, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * A round after a gap that another parser produced is refused, as in order it would be, and the head stays
     * at the last round folded rather than at the absent one the refused round claims to follow.
     */
    @Test
    public void aRefusedRoundAfterAGapLeavesTheHeadWhereItWas() throws Exception {
        final SessionFlowRound first = Fixtures.round();
        final ConversationFold fold = new ConversationFold();
        assertNull(fold.apply(first));
        final SessionFlowRound foreign = SessionFlowRound.parse(
            Fixtures.emptyRound(first, 3, UNKNOWN_DIGEST, 4, 4, first.getHeader().getParser() + "-other"));
        final String refused = fold.applyAfterGap(foreign);
        assertNotNull(refused);
        assertTrue(refused.contains("parser"), refused);
        assertEquals(1L, fold.getRound());
        assertEquals(first.getCommitDigest(), fold.getDigest());
        assertEquals(first.getHeader().getParser(), fold.getParser());
    }
}
