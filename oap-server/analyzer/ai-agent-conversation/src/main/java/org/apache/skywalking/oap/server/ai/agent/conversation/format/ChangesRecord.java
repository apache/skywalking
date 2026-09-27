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

import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;

import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.BOOLEAN;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.INTEGER;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.NULLABLE_INTEGER;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.NULLABLE_OBJECT;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.OBJECT;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.OBJECTS;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.STRING;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.STRINGS;

/**
 * One workspace change record, <code>changes/1</code>: which files one tool call changed, as a git-style change
 * log. It is read from a <code>data</code> part, read by its {@link Schema}, so the document
 * carries the record as the Sessionizer prints it and not as its producer spelled it.
 */
public final class ChangesRecord implements ToolCallRecord {
    public static final String SCHEMA = "changes/1";
    /** The runtime recorded the patch itself, on its own editing tool. */
    public static final String CAPTURED_BY_CLAUDE_CODE = "claude-code";

    /** The fields of a <code>changes/1</code> record, in the format's order. */
    private static final Schema INTERVAL = new Schema()
        .field("from", STRING)
        .field("to", STRING);
    private static final Schema ENDPOINT = new Schema()
        .field("present", BOOLEAN)
        .field("bytes", NULLABLE_INTEGER)
        .optional("sha256", STRING)
        .optional("no_newline_at_end", BOOLEAN);
    private static final Schema HUNK = new Schema()
        .field("old_start", INTEGER)
        .field("old_lines", INTEGER)
        .field("new_start", INTEGER)
        .field("new_lines", INTEGER)
        .field("lines", STRINGS);
    private static final Schema FILE_CHANGE = new Schema()
        .field("path", STRING)
        .field("operation", STRING)
        .field("before", OBJECT, ENDPOINT)
        .field("after", OBJECT, ENDPOINT)
        .field("diff", STRING)
        .optional("attribution", STRING)
        .optional("windows", STRINGS)
        .field("additions", NULLABLE_INTEGER)
        .field("deletions", NULLABLE_INTEGER)
        .optional("hunks", OBJECTS, HUNK);
    private static final Schema RECORD = new Schema()
        .field("schema", STRING)
        .field("id", STRING)
        .field("captured_by", STRING)
        .field("session", STRING)
        .field("stream", STRING)
        .optional("tool", STRING)
        .optional("tool_name", STRING)
        .field("time", STRING)
        .field("basis", STRING)
        .optional("root", NULLABLE_OBJECT, new Schema()
            .field("path", STRING)
            .optional("id", STRING))
        .optional("policy", NULLABLE_OBJECT, new Schema()
            .optional("exclusions", STRING)
            .optional("read_only", STRING)
            .optional("expanded", STRINGS))
        .optional("window", NULLABLE_OBJECT, new Schema()
            .field("before", OBJECT, INTERVAL)
            .field("after", OBJECT, INTERVAL))
        .optional("outcome", NULLABLE_OBJECT, new Schema()
            .field("state", STRING)
            .field("exit_code", NULLABLE_INTEGER))
        .optional("coverage", STRING)
        .optional("gaps", STRINGS)
        .optional("overlaps", OBJECTS, new Schema()
            .field("capture", STRING)
            .field("session", STRING)
            .field("stream", STRING)
            .optional("tool", STRING)
            .optional("tool_name", STRING)
            .field("state", STRING))
        .field("changed_files", NULLABLE_INTEGER)
        .field("changes", OBJECTS, FILE_CHANGE);

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

    private ChangesRecord(final Map<String, Object> fields) {
        id = (String) fields.get("id");
        capturedBy = (String) fields.get("captured_by");
        tool = (String) fields.getOrDefault("tool", "");
        time = (String) fields.get("time");
        this.fields = Collections.unmodifiableMap(fields);
    }

    /**
     * @param raw the data of a part, as the Sessionizer wrote it
     * @return the record, or null when the data is not a change record: not an object, not this schema, or not the
     * shape of one. Anything else is not an error, because the same data part may hold other things.
     */
    @Nullable
    public static ChangesRecord decode(@Nullable final String raw) {
        final Map<String, Object> fields = RECORD.read(Schema.parse(raw));
        return fields != null && SCHEMA.equals(fields.get("schema")) ? new ChangesRecord(fields) : null;
    }

    @Override
    public Map<String, Object> fields() {
        return fields;
    }
}
