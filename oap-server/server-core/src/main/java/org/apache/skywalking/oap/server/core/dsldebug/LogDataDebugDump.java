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

package org.apache.skywalking.oap.server.core.dsldebug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;

/**
 * Native renderer for {@link LogData} — emits a structured JSON view of
 * the agent-side log payload that mirrors the operator's mental model
 * rather than the protobuf wire form.
 *
 * <p>Body shape is detected via {@link LogDataBody#getContentCase()}:
 * <ul>
 *   <li>{@code TEXT} — the literal text under {@code body.text}.</li>
 *   <li>{@code JSON} — re-parsed inline so the operator sees a real
 *       JSON object instead of an escaped quoted blob; falls back to a
 *       quoted string on parse failure.</li>
 *   <li>{@code YAML} — the literal yaml string under {@code body.yaml}.</li>
 * </ul>
 *
 * <p>Lives in core so {@link org.apache.skywalking.oap.server.core.source.LALOutputBuilder}
 * implementations (e.g., {@code LogBuilder}) can render their cached
 * {@link LogData} input without crossing back into the analyzer module.
 */
public final class LogDataDebugDump {

    private LogDataDebugDump() {
    }

    /**
     * Build the structured JSON view of {@code data}. Returns the JSON
     * literal {@code "null"} when {@code data} is null so callers can
     * inline the result directly.
     */
    public static String toJson(final LogData data) {
        final JsonObject obj = toJsonObject(data);
        return obj == null ? "null" : obj.toString();
    }

    public static JsonObject toJsonObject(final LogData data) {
        if (data == null) {
            return null;
        }
        final JsonObject obj = new JsonObject();
        obj.addProperty("type", "LogData");
        obj.addProperty("timestamp", data.getTimestamp());
        obj.addProperty("service", data.getService());
        obj.addProperty("serviceInstance", data.getServiceInstance());
        obj.addProperty("endpoint", data.getEndpoint());
        obj.addProperty("layer", data.getLayer());
        if (data.hasTraceContext()) {
            obj.add("traceContext", traceContextJson(data.getTraceContext()));
        }
        if (data.hasTags()) {
            obj.add("tags", tagsJson(data.getTags()));
        }
        if (data.hasBody()) {
            obj.add("body", bodyJson(data.getBody()));
        }
        return obj;
    }

    private static JsonObject traceContextJson(final TraceContext ctx) {
        final JsonObject obj = new JsonObject();
        obj.addProperty("traceId", ctx.getTraceId());
        obj.addProperty("traceSegmentId", ctx.getTraceSegmentId());
        obj.addProperty("spanId", ctx.getSpanId());
        return obj;
    }

    private static JsonArray tagsJson(final LogTags tags) {
        final JsonArray arr = new JsonArray();
        for (final KeyStringValuePair t : tags.getDataList()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("key", t.getKey());
            entry.addProperty("value", t.getValue());
            arr.add(entry);
        }
        return arr;
    }

    private static JsonObject bodyJson(final LogDataBody body) {
        final JsonObject obj = new JsonObject();
        obj.addProperty("contentType", body.getType());
        switch (body.getContentCase()) {
            case TEXT:
                obj.addProperty("format", "TEXT");
                obj.addProperty("text", body.getText().getText());
                break;
            case JSON:
                obj.addProperty("format", "JSON");
                obj.add("json", parseJsonOrFallback(body.getJson().getJson()));
                break;
            case YAML:
                obj.addProperty("format", "YAML");
                obj.addProperty("yaml", body.getYaml().getYaml());
                break;
            case CONTENT_NOT_SET:
            default:
                obj.addProperty("format", "EMPTY");
                break;
        }
        return obj;
    }

    private static JsonElement parseJsonOrFallback(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        try {
            return JsonParser.parseString(raw);
        } catch (final RuntimeException e) {
            final JsonObject fallback = new JsonObject();
            fallback.addProperty("raw", raw);
            fallback.addProperty("parseError", e.getMessage());
            return fallback;
        }
    }
}
