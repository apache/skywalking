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

package org.apache.skywalking.oap.server.ai.agent.conversation.view;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The records the document reads a value out of: a queued command's prompt, a turn's reported duration, and the result
 * row that names a workflow's child. Each case is a record's data parts and what the document reads from them: a value
 * of another type, a null inside a list included, means the part is not that record, the first result row decides,
 * and a prompt that has text ends the search.
 */
public class RecordReadingTest {
    private static final String HEADER = "{\"h\":1,\"schema\":\"sd/1\",\"seq\":1,\"at\":\"2026-01-01T00:00:00Z\","
        + "\"kind\":\"transcript\",\"adapter\":\"mock/0.2.0\",\"dialect\":\"mock/1\",\"src\":\"s/streams/main\","
        + "\"session\":\"s\",\"stream\":\"main\"}";

    /** The data parts; then what is read from them: the readable text, the duration, the child's name or null. */
    private static final Object[][] CASES = {
        {new String[] {"{\"type\":\"queued_command\",\"prompt\":[{\"text\":\"hi\"}]}"}, "hi", 0L, null},
        {new String[] {"{\"type\":\"queued_command\",\"prompt\":[{\"text\":\"a\"},null,{\"text\":\"b\"}]}"}, "", 0L, null},
        {new String[] {"{\"type\":\"queued_command\",\"prompt\":[{\"text\":7}]}", "{\"type\":\"queued_command\",\"prompt\":[{\"text\":\"next\"}]}"}, "next", 0L, null},
        {new String[] {"{\"type\":\"queued_command\",\"prompt\":\"x\"}"}, "", 0L, null},
        {new String[] {"{\"type\":\"queued_command\",\"prompt\":[{\"text\":\"  \"}]}", "{\"type\":\"queued_command\",\"prompt\":[{\"text\":\"b\"}]}"}, "", 0L, null},
        {new String[] {"{\"type\":\"queued_command\",\"prompt\":[]}", "{\"type\":\"queued_command\",\"prompt\":[{\"text\":\" b \"}]}"}, "b", 0L, null},
        {new String[] {"{\"durationMs\":1500}"}, "", 1500L, null},
        {new String[] {"{\"durationMs\":1.5}"}, "", 0L, null},
        {new String[] {"{\"durationMs\":\"1500\"}"}, "", 0L, null},
        {new String[] {"{\"durationMs\":0}", "{\"durationMs\":7}"}, "", 7L, null},
        {new String[] {"{\"durationMs\":-7}"}, "", -7L, null},
        {new String[] {"{\"durationMs\":1e3}"}, "", 0L, null},
        {new String[] {"{\"durationMs\":9223372036854775808}"}, "", 0L, null},
        {new String[] {"{\"type\":\"result\",\"result\":{\"surface\":\"S\",\"summary\":\"M\"}}"}, "", 0L, "S"},
        {new String[] {"{\"type\":\"Result\",\"result\":{\"surface\":\"S\"}}"}, "", 0L, null},
        {new String[] {"{\"type\":\"result\",\"result\":{\"verdict\":\"V\",\"refuted_claims\":[{\"claim\":\"C\"}]}}"}, "", 0L, "V · C"},
        {new String[] {"{\"type\":\"result\",\"result\":{\"verdict\":\"V\",\"refuted_claims\":[null,{\"claim\":\"C\"}]}}"}, "", 0L, null},
        {new String[] {"{\"type\":\"result\",\"result\":null}", "{\"type\":\"result\",\"result\":{\"summary\":\"second\"}}"}, "", 0L, ""},
        {new String[] {"{\"type\":\"result\",\"result\":{\"surface\":7}}", "{\"type\":\"result\",\"result\":{\"summary\":\"second\"}}"}, "", 0L, "second"},
        {new String[] {"{\"type\":\"result\",\"result\":{\"summary\":\"  two\\u00a0words\\n\"}}"}, "", 0L, "two words"},
    };

    @Test
    public void aRecordIsReadByItsShape() {
        for (final Object[] c : CASES) {
            final String[] parts = (String[]) c[0];
            final SessionDataFile.Record rec = record(parts);
            final String what = String.join(" + ", parts);
            assertEquals(c[1], ConversationViewBuilder.readable(rec), what);
            assertEquals(c[2], ConversationViewBuilder.reportedDuration(rec), what);
            assertEquals(c[3], ConversationViewBuilder.resultName(rec), what);
        }
    }

    /**
     * A child's name is its words joined by one space and cut at 160 bytes, never inside a character, and marked when
     * cut.
     */
    @Test
    public void aLongNameIsCutOnACharacterBoundary() {
        final String[][] cases = {
            {"a".repeat(159) + "é tail", "a".repeat(159) + "…"},
            {"a".repeat(160), "a".repeat(160)},
            {" " + "b".repeat(80) + "\\n\\n" + "c".repeat(80) + " ", "b".repeat(80) + " " + "c".repeat(79) + "…"},
        };
        for (final String[] c : cases) {
            final String raw = "{\"type\":\"result\",\"result\":{\"summary\":\"" + c[0] + "\"}}";
            assertEquals(c[1], ConversationViewBuilder.resultName(record(raw)), c[0]);
        }
    }

    /**
     * A step on one part reads that part alone: a tool call that shares a record with the agent's text reads as its
     * arguments, never as that text, and a queued command kept as data beside other text reads as its own prompt.
     */
    @Test
    public void aStepOnAPartReadsThatPart() {
        final String args = "{\"command\":\"kubectl logs api-7f9c\"}";
        final Map<String, Object> call = new HashMap<>();
        ConversationViewBuilder.fill(call, new HashMap<>(), withParts(
            "{\"k\":\"text\",\"text\":\"I will look at the logs first.\"}",
            "{\"k\":\"call\",\"name\":\"Bash\",\"data\":" + args + "}"), 1L);
        assertEquals(args, call.get("text"));
        final Map<String, Object> queued = new HashMap<>();
        ConversationViewBuilder.fill(queued, new HashMap<>(), withParts(
            "{\"k\":\"text\",\"text\":\"unrelated prose\"}",
            "{\"k\":\"data\",\"data\":{\"type\":\"queued_command\",\"prompt\":[{\"type\":\"text\",\"text\":\"restart the worker\"}]}}"),
            1L);
        assertEquals("restart the worker", queued.get("text"));
    }

    private static SessionDataFile.Record record(final String... data) {
        final String[] parts = new String[data.length];
        for (int i = 0; i < data.length; i++) {
            parts[i] = "{\"k\":\"data\",\"data\":" + data[i] + "}";
        }
        return withParts(parts);
    }

    private static SessionDataFile.Record withParts(final String... parts) {
        final String line = "{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"parts\":[" + String.join(",", parts) + "]}";
        final String file = HEADER + "\n" + line + "\n";
        return SessionDataFile.parse(file.getBytes(StandardCharsets.UTF_8)).getRecords().get(0);
    }
}
