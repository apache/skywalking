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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.ChangesRecord;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.ExecutionRecord;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A record is read as the Sessionizer's Go code reads it, whatever its producer wrote. Each case pairs a line with
 * what Go's <code>json.Unmarshal</code> into the Sessionizer's struct and <code>json.Marshal</code> back gave for
 * it, or null where Go refused the line: keys in another case, a key given twice, an object given twice, a null over
 * a value, a value of the wrong type, an escape Go does not know, a control character in a string, a lone surrogate,
 * and text after the object. The document must hold what Go holds, key for key and in key order.
 */
public class GoDecodingTest {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static final String[][] EXECUTIONS = {
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"Schema\":\"execution/1\",\"ID\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"Server\":{\"Name\":\"a\",\"SOURCE\":\"user\"},\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"server\":{\"name\":\"a\",\"source\":\"user\"},\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"id\":null,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"duration_ms\":\"bad\",\"duration_ms\":1,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"duration_ms\":1,\"duration_ms\":null,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"server\":{\"name\":\"a\",\"source\":\"x\"},\"server\":{\"name\":\"b\"},\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"server\":{\"name\":\"b\",\"source\":\"x\"},\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"server\":{\"name\":\"a\"},\"server\":null,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"arguments\":{\"bytes\":1},\"arguments\":{\"state\":\"s\"},\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\",\"arguments\":{\"state\":\"s\",\"bytes\":1}}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"tool\":\"a\",\"TOOL\":\"b\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\",\"TOOL\":\"b\",\"tool\":\"a\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"a\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"arguments\":{\"bytes\":1.0},\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"arguments\":{\"bytes\":\"1\"},\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"server\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"server\":[],\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"outcome\":1,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"time\":null,\"arguments\":null,\"duration_ms\":null,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"unknown\":{\"a\":[1,{\"b\":null}]},\"server\":{\"name\":\"a\",\"extra\":[1]},\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"server\":{\"name\":\"a\"},\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"unknown\":{\"a\":[184467440737095516160,{\"b\":null}]},\"server\":{\"name\":\"a\",\"extra\":[1]},\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"server\":{\"name\":\"a\"},\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\",}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}x", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}  ", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"xA\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"xA\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\\'\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\ty\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\\ud800y\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\ufffdy\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\ud83d\ude00\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\ud83d\ude00\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"<&>\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"\\u003c\\u0026\\u003e\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"duration_ms\":-0,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"duration_ms\":0,\"outcome\":\"returned\"}"},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"duration_ms\":9223372036854775808,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"duration_ms\":01,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"duration_ms\":1E2,\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", null},
            {"{\"schema\":\"execution/1\",\"id\":\"x\",\"result\":{},\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\",\"result\":{\"state\":\"\",\"bytes\":0}}"},
            {"{\"\u017fchema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}", "{\"schema\":\"execution/1\",\"id\":\"x\",\"observed_by\":\"asz-plugin\",\"boundary\":\"client_hook\",\"session\":\"s\",\"stream\":\"main\",\"tool\":\"t1\",\"protocol\":\"mcp\",\"time\":\"2026-01-01T00:00:01Z\",\"outcome\":\"returned\"}"}
    };

    private static final String[][] CHANGES = {
            {"{\"schema\":\"changes/1\",\"id\":\"t\",\"captured_by\":\"asz-plugin\",\"session\":\"s\",\"stream\":\"main\",\"time\":\"t\",\"basis\":\"b\",\"changed_files\":1,\"changes\":[{\"path\":\"a\",\"operation\":\"modify\",\"before\":{\"present\":true,\"bytes\":1},\"after\":{\"present\":true},\"diff\":\"available\",\"hunks\":[{\"old_start\":1,\"lines\":[\"+a\"]}]}]}", "{\"schema\":\"changes/1\",\"id\":\"t\",\"captured_by\":\"asz-plugin\",\"session\":\"s\",\"stream\":\"main\",\"time\":\"t\",\"basis\":\"b\",\"changed_files\":1,\"changes\":[{\"path\":\"a\",\"operation\":\"modify\",\"before\":{\"present\":true,\"bytes\":1},\"after\":{\"present\":true,\"bytes\":null},\"diff\":\"available\",\"additions\":null,\"deletions\":null,\"hunks\":[{\"old_start\":1,\"old_lines\":0,\"new_start\":0,\"new_lines\":0,\"lines\":[\"+a\"]}]}]}"},
            {"{\"schema\":\"changes/1\",\"id\":\"t\",\"CHANGES\":[{\"Path\":\"a\",\"Before\":{\"Present\":true}}],\"changes\":[{\"operation\":\"create\"}]}", "{\"schema\":\"changes/1\",\"id\":\"t\",\"captured_by\":\"\",\"session\":\"\",\"stream\":\"\",\"time\":\"\",\"basis\":\"\",\"changed_files\":null,\"changes\":[{\"path\":\"a\",\"operation\":\"create\",\"before\":{\"present\":true,\"bytes\":null},\"after\":{\"present\":false,\"bytes\":null},\"diff\":\"\",\"additions\":null,\"deletions\":null}]}"},
            {"{\"schema\":\"changes/1\",\"id\":\"t\",\"changes\":[{\"path\":\"a\",\"hunks\":[{\"lines\":[\"x\",\"y\"]}]}],\"changes\":[{\"hunks\":[{\"lines\":[null]}]},{\"path\":\"b\"}]}", "{\"schema\":\"changes/1\",\"id\":\"t\",\"captured_by\":\"\",\"session\":\"\",\"stream\":\"\",\"time\":\"\",\"basis\":\"\",\"changed_files\":null,\"changes\":[{\"path\":\"a\",\"operation\":\"\",\"before\":{\"present\":false,\"bytes\":null},\"after\":{\"present\":false,\"bytes\":null},\"diff\":\"\",\"additions\":null,\"deletions\":null,\"hunks\":[{\"old_start\":0,\"old_lines\":0,\"new_start\":0,\"new_lines\":0,\"lines\":[\"x\"]}]},{\"path\":\"b\",\"operation\":\"\",\"before\":{\"present\":false,\"bytes\":null},\"after\":{\"present\":false,\"bytes\":null},\"diff\":\"\",\"additions\":null,\"deletions\":null}]}"},
            {"{\"schema\":\"changes/1\",\"id\":\"t\",\"window\":{\"before\":{\"from\":\"a\"}},\"window\":{\"before\":{\"to\":\"b\"}},\"window\":{\"after\":null}}", "{\"schema\":\"changes/1\",\"id\":\"t\",\"captured_by\":\"\",\"session\":\"\",\"stream\":\"\",\"time\":\"\",\"basis\":\"\",\"window\":{\"before\":{\"from\":\"a\",\"to\":\"b\"},\"after\":{\"from\":\"\",\"to\":\"\"}},\"changed_files\":null,\"changes\":null}"},
            {"{\"schema\":\"changes/1\",\"id\":\"t\",\"gaps\":[\"a\"],\"gaps\":null,\"policy\":{\"expanded\":[]},\"outcome\":{\"exit_code\":null,\"state\":\"x\"}}", "{\"schema\":\"changes/1\",\"id\":\"t\",\"captured_by\":\"\",\"session\":\"\",\"stream\":\"\",\"time\":\"\",\"basis\":\"\",\"policy\":{},\"outcome\":{\"state\":\"x\",\"exit_code\":null},\"changed_files\":null,\"changes\":null}"},
            {"{\"schema\":\"changes/1\",\"id\":\"t\",\"changes\":[{\"path\":\"a\",\"before\":{\"present\":\"yes\"}}]}", null},
            {"{\"schema\":\"changes/1\",\"id\":\"t\",\"changes\":[{\"path\":\"a\",\"additions\":2.5}]}", null},
            {"{\"schema\":\"changes/1\",\"id\":\"t\",\"overlaps\":[null,{\"capture\":\"c\"}],\"changed_files\":null}", "{\"schema\":\"changes/1\",\"id\":\"t\",\"captured_by\":\"\",\"session\":\"\",\"stream\":\"\",\"time\":\"\",\"basis\":\"\",\"overlaps\":[{\"capture\":\"\",\"session\":\"\",\"stream\":\"\",\"state\":\"\"},{\"capture\":\"c\",\"session\":\"\",\"stream\":\"\",\"state\":\"\"}],\"changed_files\":null,\"changes\":null}"},
            {"{\"schema\":\"changes/1\",\"id\":\"t\",\"changes\":[],\"changed_files\":0}", "{\"schema\":\"changes/1\",\"id\":\"t\",\"captured_by\":\"\",\"session\":\"\",\"stream\":\"\",\"time\":\"\",\"basis\":\"\",\"changed_files\":0,\"changes\":[]}"}
    };

    @Test
    public void anExecutionRecordIsReadAsGoReadsIt() {
        check(EXECUTIONS, raw -> {
            final ExecutionRecord r = ExecutionRecord.decode(raw);
            return r == null ? null : r.fields();
        });
    }

    @Test
    public void aChangeRecordIsReadAsGoReadsIt() {
        check(CHANGES, raw -> {
            final ChangesRecord r = ChangesRecord.decode(raw);
            return r == null ? null : r.fields();
        });
    }

    /**
     * A text is read as Go's <code>encoding/json</code> reads it. Each case in the fixture pairs a text with whether
     * Go accepts it and, when it does, the value Go decodes, written back with numbers as written: the syntax Gson
     * alone accepts is refused, and every number Go's grammar allows is read, however long. Go's scanner takes 10,000
     * levels of objects and arrays and refuses the next, and a text nested far deeper is refused, not a stack overflow.
     */
    @Test
    public void aTextIsReadAsGoReadsIt() throws Exception {
        final JsonArray cases = JsonParser.parseString(
            new String(Fixtures.bytes("go-json-syntax.json"), StandardCharsets.UTF_8)).getAsJsonArray();
        assertEquals(82, cases.size());
        for (final JsonElement e : cases) {
            final JsonObject c = e.getAsJsonObject();
            final String text = c.get("text").getAsString();
            final JsonElement read = GoJson.parse(text);
            if (!c.get("valid").getAsBoolean()) {
                assertNull(read, text);
                continue;
            }
            assertNotNull(read, text);
            assertEquals(GoJson.toValue(GoJson.parse(c.get("canonical").getAsString())), GoJson.toValue(read), text);
        }
        assertNotNull(GoJson.parse("[".repeat(10000) + "0" + "]".repeat(10000)));
        assertNull(GoJson.parse("[".repeat(10001) + "0" + "]".repeat(10001)));
        assertNull(GoJson.parse("[".repeat(50000) + "0" + "]".repeat(50000)));
        assertNull(GoJson.members("{\"a\":" + "[".repeat(10001) + "0" + "]".repeat(10001) + "}"));
        // every number keeps the digits it was written with, checked here without the scanner on both sides
        for (final String literal : new String[] {"184467440737095516160", "123456789012345678901234567890", "1e400", "-1.0e+10"}) {
            assertEquals(literal, GoJson.parse("[" + literal + "]").getAsJsonArray().get(0).getAsString());
        }
    }

    private static void check(final String[][] cases, final Function<String, Map<String, Object>> decode) {
        for (final String[] c : cases) {
            final Map<String, Object> fields = decode.apply(c[0]);
            if (c[1] == null) {
                assertNull(fields, c[0]);
                continue;
            }
            final JsonElement want = JsonParser.parseString(c[1]);
            final JsonElement got = GSON.toJsonTree(fields);
            assertEquals(want, got, c[0]);
            assertEquals(order(want), order(got), c[0]);
        }
    }

    /** The keys of every object, depth first, so two trees with one order of keys give one list. */
    private static String order(final JsonElement e) {
        final StringBuilder sb = new StringBuilder();
        if (e.isJsonObject()) {
            final JsonObject o = e.getAsJsonObject();
            sb.append(new ArrayList<>(o.keySet()));
            for (final String k : o.keySet()) {
                sb.append(order(o.get(k)));
            }
        } else if (e.isJsonArray()) {
            for (final JsonElement x : e.getAsJsonArray()) {
                sb.append(order(x));
            }
        }
        return sb.toString();
    }
}
