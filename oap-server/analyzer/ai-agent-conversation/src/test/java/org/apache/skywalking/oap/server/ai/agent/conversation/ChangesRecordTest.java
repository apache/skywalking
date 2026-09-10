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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.ChangesRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChangesRecordTest {
    /**
     * A record is rendered in the format's key order whatever order its producer wrote, without a key the format
     * does not have, without an empty optional, and with an unknown count as null.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aRecordIsRenderedAsTheFormatListsIt() {
        final ChangesRecord r = ChangesRecord.decode(
            "{\"captured_by\":\"asz-plugin\",\"schema\":\"changes/1\",\"id\":\"t1\",\"session\":\"s\",\"stream\":\"main\","
                + "\"tool\":\"t1\",\"time\":\"2026-01-01T00:00:01Z\",\"basis\":\"tool_window\",\"a_later_key\":1,"
                + "\"outcome\":{\"state\":\"returned\"},\"changed_files\":null,\"gaps\":[],"
                + "\"changes\":[{\"path\":\"a.txt\",\"operation\":\"create\",\"before\":{\"present\":false},"
                + "\"after\":{\"present\":true,\"bytes\":3,\"sha256\":\"ab\",\"no_newline_at_end\":true},"
                + "\"diff\":\"available\",\"hunks\":[{\"new_start\":1,\"new_lines\":1,\"lines\":[\"+abc\"]}]}]}");
        assertNotNull(r);
        assertEquals("t1", r.getId());
        assertEquals("asz-plugin", r.getCapturedBy());
        assertEquals("t1", r.getTool());
        assertEquals("2026-01-01T00:00:01Z", r.getTime());
        assertEquals(
            Arrays.asList("schema", "id", "captured_by", "session", "stream", "tool", "time", "basis", "outcome",
                          "changed_files", "changes"),
            new ArrayList<>(r.fields().keySet()));
        final Map<String, Object> outcome = (Map<String, Object>) r.fields().get("outcome");
        assertEquals(Arrays.asList("state", "exit_code"), new ArrayList<>(outcome.keySet()));
        assertNull(outcome.get("exit_code"));
        assertTrue(r.fields().containsKey("changed_files"));
        assertNull(r.fields().get("changed_files"));
        final Map<String, Object> change = ((List<Map<String, Object>>) r.fields().get("changes")).get(0);
        assertEquals(
            Arrays.asList("path", "operation", "before", "after", "diff", "additions", "deletions", "hunks"),
            new ArrayList<>(change.keySet()));
        final Map<String, Object> before = (Map<String, Object>) change.get("before");
        assertEquals(Arrays.asList("present", "bytes"), new ArrayList<>(before.keySet()));
        assertEquals(false, before.get("present"));
        assertNull(before.get("bytes"));
        final Map<String, Object> after = (Map<String, Object>) change.get("after");
        assertEquals(Arrays.asList("present", "bytes", "sha256", "no_newline_at_end"), new ArrayList<>(after.keySet()));
        assertEquals(3L, after.get("bytes"));
        assertNull(change.get("additions"));
        final Map<String, Object> hunk = ((List<Map<String, Object>>) change.get("hunks")).get(0);
        assertEquals(Arrays.asList("old_start", "old_lines", "new_start", "new_lines", "lines"), new ArrayList<>(hunk.keySet()));
        assertEquals(0L, hunk.get("old_start"));
        assertEquals(1L, hunk.get("new_start"));
        assertEquals(Collections.singletonList("+abc"), hunk.get("lines"));
    }

    /**
     * What is not a change record is not one, and not an error: another schema, not an object, or a value of a type
     * the record cannot hold, as the Sessionizer's reader refuses them. A record never given its list of changes
     * says so with null, which is not the same as an empty list.
     */
    @Test
    public void whatIsNotAChangeRecordIsNotOne() {
        assertNull(ChangesRecord.decode(null));
        assertNull(ChangesRecord.decode(""));
        assertNull(ChangesRecord.decode("[1]"));
        assertNull(ChangesRecord.decode("{\"stderr\":\"\",\"stdout\":\"\"}"));
        assertNull(ChangesRecord.decode("{\"schema\":\"changes/2\",\"id\":\"t1\"}"));
        assertNull(ChangesRecord.decode("{\"schema\":\"changes/1\",\"changed_files\":1.5}"));
        assertNull(ChangesRecord.decode("{\"schema\":\"changes/1\",\"changed_files\":\"1\"}"));
        assertNull(ChangesRecord.decode("{\"schema\":\"changes/1\",\"changes\":{}}"));
        assertNull(ChangesRecord.decode("{\"schema\":\"changes/1\",\"id\":7}"));
        assertNull(ChangesRecord.decode("{\"schema\":\"changes/1\",\"root\":\"/tmp\"}"));
        assertNull(ChangesRecord.decode("{\"schema\":\"changes/1\",\"changes\":[{\"path\":\"a\",\"before\":{\"present\":\"yes\"}}]}"));

        final ChangesRecord none = ChangesRecord.decode("{\"schema\":\"changes/1\"}");
        assertNotNull(none);
        assertTrue(none.fields().containsKey("changes"));
        assertNull(none.fields().get("changes"));
        assertFalse(none.fields().containsKey("tool"));
        assertEquals("", none.getTool());
        final ChangesRecord empty = ChangesRecord.decode("{\"schema\":\"changes/1\",\"changes\":[]}");
        assertNotNull(empty);
        assertEquals(Collections.emptyList(), empty.fields().get("changes"));
    }
}
