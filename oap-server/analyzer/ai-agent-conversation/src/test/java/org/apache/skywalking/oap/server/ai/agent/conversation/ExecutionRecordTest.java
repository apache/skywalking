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
import org.apache.skywalking.oap.server.ai.agent.conversation.format.ExecutionRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ExecutionRecordTest {
    private static final Gson GSON = new Gson();

    /**
     * Each line decoded and rendered as the Sessionizer's <code>execution.Decode</code> and <code>json.Marshal</code>
     * do. The right-hand side of every case is what Go printed for the same line: the keys in the struct's order, a key
     * the struct does not have dropped, an empty optional left out, a server given as <code>{}</code> kept with an
     * empty name, a duration of 0 kept, and a size that was not given written as 0.
     */
    @Test
    public void aRecordIsRenderedAsTheSessionizerRendersIt() {
        final String[][] cases = {
            {
                "{\"outcome\":\"returned\",\"time\":\"2026-01-01T00:00:01.5Z\",\"schema\":\"execution/1\",\"id\":\"t1/client_hook\","
                    + "\"tool\":\"t1\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\","
                    + "\"tool_name\":\"mcp__status__lookup\",\"cwd\":\"/w\",\"protocol\":\"mcp\","
                    + "\"server\":{\"source\":\"user\",\"name\":\"status\",\"extra\":1},\"duration_ms\":0,"
                    + "\"arguments\":{\"state\":\"size_only\",\"bytes\":21,\"sha256\":\"ab\"},\"a_later_key\":true}",
                "{\"schema\":\"execution/1\",\"id\":\"t1/client_hook\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\","
                    + "\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"tool_name\":\"mcp__status__lookup\",\"cwd\":\"/w\","
                    + "\"protocol\":\"mcp\",\"server\":{\"name\":\"status\",\"source\":\"user\"},\"time\":\"2026-01-01T00:00:01.5Z\","
                    + "\"duration_ms\":0,\"outcome\":\"returned\",\"arguments\":{\"state\":\"size_only\",\"bytes\":21,\"sha256\":\"ab\"}}",
            },
            {
                "{\"schema\":\"execution/1\",\"id\":\"t2/client_hook\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\","
                    + "\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t2\",\"tool_name\":\"\",\"protocol\":\"mcp\",\"server\":{},"
                    + "\"time\":\"t\",\"outcome\":\"failed\",\"arguments\":null,\"result\":{\"state\":\"size_only\"}}",
                "{\"schema\":\"execution/1\",\"id\":\"t2/client_hook\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\","
                    + "\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t2\",\"protocol\":\"mcp\",\"server\":{\"name\":\"\"},\"time\":\"t\","
                    + "\"outcome\":\"failed\",\"result\":{\"state\":\"size_only\",\"bytes\":0}}",
            },
            {
                "{\"schema\":\"execution/1\",\"id\":\"t3\",\"server\":null,\"duration_ms\":null}",
                "{\"schema\":\"execution/1\",\"id\":\"t3\",\"observed_by\":\"\",\"boundary\":\"\",\"session\":\"\",\"stream\":\"\","
                    + "\"tool\":\"\",\"protocol\":\"\",\"time\":\"\",\"outcome\":\"\"}",
            },
            {
                "{\"schema\":\"execution/1\",\"id\":\"t9\",\"duration_ms\":-3,\"arguments\":{\"bytes\":-1}}",
                "{\"schema\":\"execution/1\",\"id\":\"t9\",\"observed_by\":\"\",\"boundary\":\"\",\"session\":\"\",\"stream\":\"\","
                    + "\"tool\":\"\",\"protocol\":\"\",\"time\":\"\",\"duration_ms\":-3,\"outcome\":\"\","
                    + "\"arguments\":{\"state\":\"\",\"bytes\":-1}}",
            },
        };
        for (final String[] c : cases) {
            final ExecutionRecord r = ExecutionRecord.decode(c[0]);
            assertNotNull(r, c[0]);
            assertEquals(c[1], GSON.toJson(r.fields()));
        }
        final ExecutionRecord first = ExecutionRecord.decode(cases[0][0]);
        assertEquals("t1/client_hook", first.getId());
        assertEquals("t1", first.getTool());
        assertEquals("2026-01-01T00:00:01.5Z", first.getTime());
    }

    /**
     * What Go refuses to decode into the struct is no record: a fraction or a string where an integer goes, a number
     * where a string goes, another schema, and a value that is not an object.
     */
    @Test
    public void whatGoRefusesIsNoRecord() {
        for (final String raw : new String[] {
            "{\"schema\":\"execution/1\",\"id\":\"t4\",\"duration_ms\":1.5}",
            "{\"schema\":\"execution/1\",\"id\":\"t5\",\"duration_ms\":\"380\"}",
            "{\"schema\":\"execution/1\",\"id\":\"t6\",\"arguments\":{\"bytes\":1e2}}",
            "{\"schema\":\"execution/1\",\"id\":\"t7\",\"server\":{\"name\":7}}",
            "{\"schema\":\"changes/1\",\"id\":\"t8\"}",
            "[{\"schema\":\"execution/1\"}]",
            "",
        }) {
            assertNull(ExecutionRecord.decode(raw), raw);
        }
        assertNull(ExecutionRecord.decode(null));
    }
}
