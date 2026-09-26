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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;

import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.BOOL;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.INT;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.INT_POINTER;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.STRING;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.STRINGS;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.STRUCT;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.STRUCTS;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.STRUCT_POINTER;

/**
 * One workspace change record, <code>changes/1</code>: which files one tool call changed, as a git-style change
 * log. It is read from a <code>data</code> part, decoded and rendered as {@link GoJson} describes, so the document
 * carries the record as the Sessionizer prints it and not as its producer spelled it.
 */
public final class ChangesRecord implements ToolCallRecord {
    public static final String SCHEMA = "changes/1";
    /** The runtime recorded the patch itself, on its own editing tool. */
    public static final String CAPTURED_BY_CLAUDE_CODE = "claude-code";

    /** The Sessionizer's <code>changes.Record</code>, field for field and tag for tag. */
    private static final GoJson.Struct INTERVAL = new GoJson.Struct()
        .field("from", STRING)
        .field("to", STRING);
    private static final GoJson.Struct ENDPOINT = new GoJson.Struct()
        .field("present", BOOL)
        .field("bytes", INT_POINTER)
        .field("sha256,omitempty", STRING)
        .field("no_newline_at_end,omitempty", BOOL);
    private static final GoJson.Struct HUNK = new GoJson.Struct()
        .field("old_start", INT)
        .field("old_lines", INT)
        .field("new_start", INT)
        .field("new_lines", INT)
        .field("lines", STRINGS);
    private static final GoJson.Struct FILE_CHANGE = new GoJson.Struct()
        .field("path", STRING)
        .field("operation", STRING)
        .field("before", STRUCT, ENDPOINT)
        .field("after", STRUCT, ENDPOINT)
        .field("diff", STRING)
        .field("attribution,omitempty", STRING)
        .field("windows,omitempty", STRINGS)
        .field("additions", INT_POINTER)
        .field("deletions", INT_POINTER)
        .field("hunks,omitempty", STRUCTS, HUNK);
    private static final GoJson.Struct RECORD = new GoJson.Struct()
        .field("schema", STRING)
        .field("id", STRING)
        .field("captured_by", STRING)
        .field("session", STRING)
        .field("stream", STRING)
        .field("tool,omitempty", STRING)
        .field("tool_name,omitempty", STRING)
        .field("time", STRING)
        .field("basis", STRING)
        .field("root,omitempty", STRUCT_POINTER, new GoJson.Struct()
            .field("path", STRING)
            .field("id,omitempty", STRING))
        .field("policy,omitempty", STRUCT_POINTER, new GoJson.Struct()
            .field("exclusions,omitempty", STRING)
            .field("read_only,omitempty", STRING)
            .field("expanded,omitempty", STRINGS))
        .field("window,omitempty", STRUCT_POINTER, new GoJson.Struct()
            .field("before", STRUCT, INTERVAL)
            .field("after", STRUCT, INTERVAL))
        .field("outcome,omitempty", STRUCT_POINTER, new GoJson.Struct()
            .field("state", STRING)
            .field("exit_code", INT_POINTER))
        .field("coverage,omitempty", STRING)
        .field("gaps,omitempty", STRINGS)
        .field("overlaps,omitempty", STRUCTS, new GoJson.Struct()
            .field("capture", STRING)
            .field("session", STRING)
            .field("stream", STRING)
            .field("tool,omitempty", STRING)
            .field("tool_name,omitempty", STRING)
            .field("state", STRING))
        .field("changed_files", INT_POINTER)
        .field("changes", STRUCTS, FILE_CHANGE);

    /** The tool-use id of the call, so two producers observing one call share it. */
    @Getter
    private final String id;
    @Getter
    private final String capturedBy;
    @Getter
    private final String tool;
    @Getter
    private final String time;
    private final Map<String, Object> fields;

    private ChangesRecord(final JsonObject json) {
        id = json.get("id").getAsString();
        capturedBy = json.get("captured_by").getAsString();
        tool = json.has("tool") ? json.get("tool").getAsString() : "";
        time = json.get("time").getAsString();
        fields = Collections.unmodifiableMap(GoJson.toMap(json));
    }

    /**
     * @param raw the data of a part, as the Sessionizer wrote it
     * @return the record, or null when the data is not a change record: not an object, not this schema, or not the
     * shape of one. Anything else is not an error, because the same data part may hold other things.
     */
    @Nullable
    public static ChangesRecord decode(@Nullable final String raw) {
        final JsonObject json = GoJson.decode(raw, RECORD);
        return json != null && SCHEMA.equals(json.get("schema").getAsString()) ? new ChangesRecord(json) : null;
    }

    @Override
    public Map<String, Object> fields() {
        return fields;
    }
}
