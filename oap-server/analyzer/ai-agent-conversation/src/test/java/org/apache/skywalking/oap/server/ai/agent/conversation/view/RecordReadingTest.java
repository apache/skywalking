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
import org.apache.skywalking.oap.server.ai.agent.conversation.format.SessionDataFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The records the document reads a value out of: a queued command's prompt, a turn's reported duration, and the result
 * row that names a workflow's child. Each case is a record's data parts and what the document reads from them: a value
 * of another type means the part is not that record, the first result row decides, and a prompt that has text ends
 * the search.
 */
public class RecordReadingTest {
    private static final String HEADER = "{\"h\":1,\"schema\":\"sd/1\",\"seq\":1,\"at\":\"2026-01-01T00:00:00Z\","
        + "\"kind\":\"transcript\",\"adapter\":\"mock/0.2.0\",\"dialect\":\"mock/1\",\"src\":\"s/streams/main\","
        + "\"session\":\"s\",\"stream\":\"main\"}";

    /** The data parts; then what is read from them: the readable text, the duration, the child's name or null. */
    private static final Object[][] CASES = {
        {new String[] {"{\"type\":\"queued_command\",\"prompt\":[{\"text\":\"hi\"}]}"}, "hi", 0L, null},
        {new String[] {"{\"type\":\"queued_command\",\"prompt\":[{\"text\":\"a\"},null,{\"text\":\"b\"}]}"}, "a\nb", 0L, null},
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
        {new String[] {"{\"type\":\"result\",\"result\":{\"verdict\":\"V\",\"refuted_claims\":[null,{\"claim\":\"C\"}]}}"}, "", 0L, "V"},
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

    private static SessionDataFile.Record record(final String... data) {
        final StringBuilder line = new StringBuilder("{\"ord\":1,\"off\":0,\"sha\":\"0\",\"bytes\":1,\"parts\":[");
        for (int i = 0; i < data.length; i++) {
            line.append(i == 0 ? "" : ",").append("{\"k\":\"data\",\"data\":").append(data[i]).append('}');
        }
        line.append("]}");
        final String file = HEADER + "\n" + line + "\n";
        return SessionDataFile.parse(file.getBytes(StandardCharsets.UTF_8)).getRecords().get(0);
    }
}
