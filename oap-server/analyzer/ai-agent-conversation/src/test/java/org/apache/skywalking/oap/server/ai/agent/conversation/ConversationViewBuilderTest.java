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
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        // the rest of the document still holds what could be folded
        assertEquals(3, ((List<?>) incomplete.get("talks")).size());

        final String tampered = new String(Fixtures.bytes(Fixtures.ROUND_FILE), StandardCharsets.UTF_8)
            .replace("\"trigger\":\"external\"", "\"trigger\":\"exterior\"");
        final Map<String, Object> mismatch = view(
            tampered.getBytes(StandardCharsets.UTF_8), Fixtures.dataFiles(), Collections.emptyList());
        assertEquals("mismatch", ((Map<String, Object>) mismatch.get("summary")).get("state"));
        assertEquals(0, ((List<?>) mismatch.get("rounds")).size());
    }
}
