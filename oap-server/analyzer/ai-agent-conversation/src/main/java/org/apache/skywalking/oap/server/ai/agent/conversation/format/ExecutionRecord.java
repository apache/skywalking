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

import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.INT;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.INT_POINTER;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.STRING;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.GoJson.Kind.STRUCT_POINTER;

/**
 * One tool execution record, <code>execution/1</code>: what one observer saw a tool call do after the model asked
 * for it, such as which MCP server ran it, how it ended and how long it took. It is read from a <code>data</code>
 * part of an <code>execution</code> file, decoded and rendered as {@link GoJson} describes.
 *
 * <p>One call can have several records, one per observation, and each has an id of its own. All of them name the
 * call by its tool-use id, which is the only join.
 */
public final class ExecutionRecord implements ToolCallRecord {
    public static final String SCHEMA = "execution/1";

    /** The Sessionizer's <code>execution.Record</code>, field for field and tag for tag. */
    private static final GoJson.Struct CONTENT = new GoJson.Struct()
        .field("state", STRING)
        .field("bytes", INT)
        .field("sha256,omitempty", STRING);
    private static final GoJson.Struct RECORD = new GoJson.Struct()
        .field("schema", STRING)
        .field("id", STRING)
        .field("observed_by", STRING)
        .field("boundary", STRING)
        .field("session", STRING)
        .field("stream", STRING)
        .field("tool", STRING)
        .field("tool_name,omitempty", STRING)
        .field("cwd,omitempty", STRING)
        .field("protocol", STRING)
        .field("server,omitempty", STRUCT_POINTER, new GoJson.Struct()
            .field("name", STRING)
            .field("source,omitempty", STRING))
        .field("time", STRING)
        .field("duration_ms,omitempty", INT_POINTER)
        .field("outcome", STRING)
        .field("arguments,omitempty", STRUCT_POINTER, CONTENT)
        .field("result,omitempty", STRUCT_POINTER, CONTENT);

    /** This observation's own identity; the document keeps a record once by it. */
    @Getter
    private final String id;
    @Getter
    private final String tool;
    @Getter
    private final String time;
    private final Map<String, Object> fields;

    private ExecutionRecord(final JsonObject json) {
        id = json.get("id").getAsString();
        tool = json.get("tool").getAsString();
        time = json.get("time").getAsString();
        fields = Collections.unmodifiableMap(GoJson.toMap(json));
    }

    /**
     * @param raw the data of a part, as the Sessionizer wrote it
     * @return the record, or null when the data is not an execution record: not an object, not this schema, or not
     * the shape of one
     */
    @Nullable
    public static ExecutionRecord decode(@Nullable final String raw) {
        final JsonObject json = GoJson.decode(raw, RECORD);
        return json != null && SCHEMA.equals(json.get("schema").getAsString()) ? new ExecutionRecord(json) : null;
    }

    @Override
    public Map<String, Object> fields() {
        return fields;
    }
}
