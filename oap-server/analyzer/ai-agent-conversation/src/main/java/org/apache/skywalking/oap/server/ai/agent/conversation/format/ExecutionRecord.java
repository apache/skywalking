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

import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.INTEGER;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.NULLABLE_INTEGER;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.NULLABLE_OBJECT;
import static org.apache.skywalking.oap.server.ai.agent.conversation.format.Schema.Kind.STRING;

/**
 * One tool execution record, <code>execution/1</code>: what one observer saw a tool call do after the model asked
 * for it, such as which MCP server ran it, how it ended and how long it took. It is read from a <code>data</code>
 * part of an <code>execution</code> file, read by its {@link Schema}.
 *
 * <p>One call can have several records, one per observation, and each has an id of its own. All of them name the
 * call by its tool-use id, which is the only join.
 */
public final class ExecutionRecord implements ToolCallRecord {
    public static final String SCHEMA = "execution/1";

    /** The fields of an <code>execution/1</code> record, in the format's order. */
    private static final Schema CONTENT = new Schema()
        .field("state", STRING)
        .field("bytes", INTEGER)
        .optional("sha256", STRING);
    private static final Schema RECORD = new Schema()
        .field("schema", STRING)
        .field("id", STRING)
        .field("observed_by", STRING)
        .field("boundary", STRING)
        .field("session", STRING)
        .field("stream", STRING)
        .field("tool", STRING)
        .optional("tool_name", STRING)
        .optional("cwd", STRING)
        .field("protocol", STRING)
        .optional("server", NULLABLE_OBJECT, new Schema()
            .field("name", STRING)
            .optional("source", STRING))
        .field("time", STRING)
        .optional("duration_ms", NULLABLE_INTEGER)
        .field("outcome", STRING)
        .optional("arguments", NULLABLE_OBJECT, CONTENT)
        .optional("result", NULLABLE_OBJECT, CONTENT);

    /** This observation's own identity; the document keeps a record once by it. */
    @Getter
    private final String id;
    @Getter
    private final String tool;
    @Getter
    private final String time;
    private final Map<String, Object> fields;

    private ExecutionRecord(final Map<String, Object> fields) {
        id = (String) fields.get("id");
        tool = (String) fields.get("tool");
        time = (String) fields.get("time");
        this.fields = Collections.unmodifiableMap(fields);
    }

    /**
     * @param raw the data of a part, as the Sessionizer wrote it
     * @return the record, or null when the data is not an execution record: not an object, not this schema, or not
     * the shape of one
     */
    @Nullable
    public static ExecutionRecord decode(@Nullable final String raw) {
        final Map<String, Object> fields = RECORD.read(Schema.parse(raw));
        return fields != null && SCHEMA.equals(fields.get("schema")) ? new ExecutionRecord(fields) : null;
    }

    @Override
    public Map<String, Object> fields() {
        return fields;
    }
}
