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
        assertEquals(Map.of("seq", 1L, "row", 8L, "block", 1), edit.get("ref"));
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
