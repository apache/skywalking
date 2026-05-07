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

package org.apache.skywalking.oap.log.analyzer.v2.dsldebug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.server.core.dsldebug.LogDataDebugDump;
import org.apache.skywalking.oap.server.core.dsldebug.ToJson;

/**
 * Per-type renderers for LAL input/output payloads. All overloads are
 * statically typed — callers hold the typed reference (or narrow it
 * via {@code instanceof}) and the compiler picks the right overload.
 * No runtime {@code Object} dispatcher: dispatch belongs at the call
 * site where the static type is available.
 *
 * <p>Supported families:
 * <ul>
 *   <li>{@link ToJson} — explicit opt-in. Custom POJO inputs / outputs
 *       implement this and call {@link #toJson(ToJson)}.</li>
 *   <li>{@link LogData} / {@link LogData.Builder} — native renderer
 *       that surfaces service / instance / endpoint / timestamp / tags
 *       / trace context plus a body-type-aware view of the body
 *       (TEXT / JSON / YAML).</li>
 *   <li>Any {@link Message} / {@link Message.Builder} — protobuf
 *       {@link JsonFormat} printer. Walks the proto descriptor (a
 *       structural, compile-time form, not Java reflection) and
 *       emits the canonical proto3 JSON shape.</li>
 * </ul>
 *
 * <p>Each overload returns a JSON literal string suitable for inline
 * embedding in a parent object payload.
 *
 * <p>Use {@link #unknownTypeHint(Object)} when none of the supported
 * families match — the helper produces an operator-friendly hint
 * object pointing at the missing {@link ToJson} implementation.
 */
public final class LalPayloadDebugDump {

    private static final JsonFormat.Printer JSON_PRINTER =
        JsonFormat.printer().omittingInsignificantWhitespace();

    private LalPayloadDebugDump() {
    }

    /** Explicit opt-in renderer — delegates to the type's own contract. */
    public static String toJson(final ToJson obj) {
        if (obj == null) {
            return "null";
        }
        final String value = obj.toJson();
        return value == null ? "null" : value;
    }

    /** Native LogData renderer — body-type-aware (TEXT / JSON / YAML). */
    public static String toJson(final LogData data) {
        return LogDataDebugDump.toJson(data);
    }

    /** Convenience: builds the snapshot once and renders via {@link #toJson(LogData)}. */
    public static String toJson(final LogData.Builder builder) {
        return builder == null ? "null" : LogDataDebugDump.toJson(builder.build());
    }

    /** Generic protobuf renderer via {@link JsonFormat}. */
    public static String toJson(final Message msg) {
        if (msg == null) {
            return "null";
        }
        return printProtoOrError(msg, msg.getDescriptorForType().getFullName());
    }

    /** Convenience: builds once and renders via {@link #toJson(Message)}. */
    public static String toJson(final Message.Builder builder) {
        return builder == null ? "null" : toJson(builder.build());
    }

    /**
     * Defensive renderer for {@link MessageOrBuilder} — covers exotic
     * proto wrappers that aren't a direct {@link Message} but expose
     * the same printable shape.
     */
    public static String toJson(final MessageOrBuilder msg) {
        if (msg == null) {
            return "null";
        }
        return printProtoOrError(msg, msg.getClass().getSimpleName());
    }

    /**
     * Operator-friendly hint object emitted by callers when their
     * dispatch logic doesn't recognise the runtime type. The hint
     * surfaces the FQCN so the operator knows where to add the
     * {@link ToJson} implementation.
     */
    public static String unknownTypeHint(final Object obj) {
        if (obj == null) {
            return "null";
        }
        final JsonObject hint = new JsonObject();
        hint.addProperty("type", obj.getClass().getName());
        hint.addProperty("note", "requires-ToJson-impl");
        return hint.toString();
    }

    /**
     * Parse the result of any overload above into a {@link JsonElement}
     * so a caller building a parent {@link JsonObject} can splice the
     * result without going through a round-trip string parse.
     */
    public static JsonElement parse(final String json) {
        return JsonParser.parseString(json);
    }

    private static String printProtoOrError(final MessageOrBuilder msg, final String typeLabel) {
        try {
            return JSON_PRINTER.print(msg);
        } catch (final Exception e) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("type", typeLabel);
            obj.addProperty("error", "jsonformat-failed");
            obj.addProperty("detail", e.getMessage());
            return obj.toString();
        }
    }
}
