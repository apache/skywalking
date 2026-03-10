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
 */

package org.apache.skywalking.oap.log.analyzer.v2.compiler.rt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Runtime helper for compiled LAL expressions.
 *
 * <p>Created once per {@code execute()} call, holds the {@link ExecutionContext}
 * and provides data-source-specific access and type conversion methods.
 *
 * <h2>Data Source Methods</h2>
 *
 * <p><b>1. JSON/YAML map data</b> — used when the LAL script has a {@code json {}} or
 * {@code yaml {}} parser. The parsed body is stored in {@code ctx.parsed().getMap()}.
 * <pre>
 *   // LAL:  parsed.service as String
 *   // Generated:  h.toStr(h.mapVal("service"))
 *
 *   // LAL:  parsed.client_process.address as String  (nested map)
 *   // Generated:  h.toStr(h.mapVal("client_process", "address"))
 * </pre>
 *
 * <p><b>2. Text regexp matcher data</b> — used when the LAL script has a
 * {@code text { regexp '...' }} parser. The parsed body is stored as a
 * {@link java.util.regex.Matcher} in {@code ctx.parsed().getMatcher()}.
 * <pre>
 *   // LAL:  parsed.level as String
 *   // Generated:  h.toStr(h.group("level"))
 * </pre>
 *
 * <p><b>3. Tag data</b> — accesses log tags (protobuf {@code KeyStringValuePair} list).
 * Available regardless of parser type.
 * <pre>
 *   // LAL:  tag("LOG_KIND")
 *   // Generated:  h.tagValue("LOG_KIND")
 * </pre>
 *
 * <p><b>4. Log proto data</b> — direct access to {@code LogData.Builder} fields.
 * Not accessed through this helper; the compiler generates direct getter chains
 * like {@code h.ctx().log().getService()}.
 *
 * <p><b>5. ExtraLog proto data</b> — direct access to typed protobuf extraLog.
 * Not accessed through this helper; the compiler generates typed cast + getter
 * chains like {@code ((HTTPAccessLogEntry) h.ctx().extraLog()).getResponse()}.
 *
 * <h2>Type Conversion Methods</h2>
 *
 * <p>Convert parsed values (typically {@code Object}) to typed values for
 * spec method calls.
 * <pre>
 *   // LAL:  parsed.service as String    →  h.toStr(h.mapVal("service"))
 *   // LAL:  parsed.latency as Long      →  Long.valueOf(h.toLong(h.mapVal("latency")))
 *   // LAL:  parsed.ssl as Boolean       →  Boolean.valueOf(h.toBool(h.mapVal("ssl")))
 *   // LAL:  parsed.code as Integer      →  Integer.valueOf(h.toInt(h.mapVal("code")))
 * </pre>
 *
 * <h2>Safe Navigation Methods</h2>
 * <pre>
 *   // LAL:  parsed?.x?.toString()       →  h.toString(h.mapVal("x"))
 *   // LAL:  parsed?.x?.trim()           →  h.trim(h.mapVal("x"))
 * </pre>
 *
 * <h2>Boolean Evaluation Methods</h2>
 * <pre>
 *   // LAL if-condition:  if (parsed.flag)        →  h.isTrue(h.mapVal("flag"))
 *   // LAL if-condition:  if (parsed.name)        →  h.isNotEmpty(h.mapVal("name"))
 * </pre>
 */
public final class LalRuntimeHelper {

    private final ExecutionContext ctx;

    public LalRuntimeHelper(final ExecutionContext ctx) {
        this.ctx = ctx;
    }

    public ExecutionContext ctx() {
        return ctx;
    }

    // ==================== Data source: JSON/YAML map ====================
    // Used when LAL has json{} or yaml{} parser.
    // Returns raw Object from the parsed Map<String,Object>.

    /**
     * Single-key map access.
     * <pre>
     *   // LAL:  parsed.service    →  h.mapVal("service")
     * </pre>
     */
    public Object mapVal(final String key) {
        return ctx.parsed().getMap().get(key);
    }

    /**
     * Two-level nested map access.
     * <pre>
     *   // LAL:  parsed.a.b    →  h.mapVal("a", "b")
     * </pre>
     */
    public Object mapVal(final String k1, final String k2) {
        return mapGet(mapVal(k1), k2);
    }

    /**
     * Three-level nested map access.
     * <pre>
     *   // LAL:  parsed.a.b.c    →  h.mapVal("a", "b", "c")
     * </pre>
     */
    public Object mapVal(final String k1, final String k2, final String k3) {
        return mapGet(mapVal(k1, k2), k3);
    }

    private static Object mapGet(final Object obj, final String key) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return ((Map) obj).get(key);
        }
        return null;
    }

    // ==================== Data source: Text regexp matcher ====================
    // Used when LAL has text { regexp '...' } parser.
    // Returns String from named matcher group.

    /**
     * Named matcher group access.
     * <pre>
     *   // LAL:  parsed.level    →  h.group("level")
     * </pre>
     */
    public String group(final String name) {
        return ctx.parsed().getMatcher().group(name);
    }

    // ==================== Data source: Log tags ====================
    // Available for all LAL scripts.

    /**
     * Log tag lookup by key name.
     * <pre>
     *   // LAL:  tag("LOG_KIND")    →  h.tagValue("LOG_KIND")
     * </pre>
     */
    public String tagValue(final String key) {
        final List dl = ctx.log().getTags().getDataList();
        for (int i = 0; i < dl.size(); i++) {
            final KeyStringValuePair kv = (KeyStringValuePair) dl.get(i);
            if (key.equals(kv.getKey())) {
                return kv.getValue();
            }
        }
        return "";
    }

    // ==================== JSON conversion (for def variables) ====================
    //
    // toJsonObject: converts to JsonObject. Overloaded for Struct, String, Object.
    // toJsonArray:  converts to JsonArray.  Overloaded for String, Object.
    //
    // Primary use case: extract JWT claims from envoy ALS filter_metadata:
    //   def jwt = toJson(parsed?.commonProperties?.metadata
    //       ?.filterMetadataMap?.get("envoy.filters.http.jwt_authn"))
    //   tag 'email': jwt?.getAsJsonObject("payload")?.get("email")?.getAsString()

    /**
     * Converts a protobuf {@link Struct} to a Gson {@link JsonObject}.
     * Recursively converts nested Struct/Value/ListValue to Gson types.
     */
    public JsonObject toJsonObject(final Struct struct) {
        if (struct == null) {
            return null;
        }
        return structToJsonObject(struct);
    }

    /**
     * Parses a JSON string into a {@link JsonObject}.
     */
    public JsonObject toJsonObject(final String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        final JsonElement el = JsonParser.parseString(s);
        return el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    /**
     * Fallback for {@code Map.get()} erasure — dispatches to typed overloads.
     */
    public JsonObject toJsonObject(final Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Struct) {
            return toJsonObject((Struct) obj);
        }
        if (obj instanceof String) {
            return toJsonObject((String) obj);
        }
        return null;
    }

    /**
     * Parses a JSON string into a {@link JsonArray}.
     */
    public JsonArray toJsonArray(final String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        final JsonElement el = JsonParser.parseString(s);
        return el.isJsonArray() ? el.getAsJsonArray() : null;
    }

    /**
     * Fallback for {@code Map.get()} erasure.
     */
    public JsonArray toJsonArray(final Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return toJsonArray((String) obj);
        }
        return null;
    }

    // ==================== Struct → Gson recursive conversion ====================

    private static JsonObject structToJsonObject(final Struct struct) {
        final JsonObject obj = new JsonObject();
        for (final Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
            obj.add(entry.getKey(), valueToJsonElement(entry.getValue()));
        }
        return obj;
    }

    private static JsonElement valueToJsonElement(final Value value) {
        switch (value.getKindCase()) {
            case STRING_VALUE:
                return new JsonPrimitive(value.getStringValue());
            case NUMBER_VALUE:
                return new JsonPrimitive(value.getNumberValue());
            case BOOL_VALUE:
                return new JsonPrimitive(value.getBoolValue());
            case STRUCT_VALUE:
                return structToJsonObject(value.getStructValue());
            case LIST_VALUE:
                return listValueToJsonArray(value.getListValue());
            case NULL_VALUE:
            case KIND_NOT_SET:
            default:
                return com.google.gson.JsonNull.INSTANCE;
        }
    }

    private static JsonArray listValueToJsonArray(final ListValue listValue) {
        final JsonArray arr = new JsonArray();
        for (final Value v : listValue.getValuesList()) {
            arr.add(valueToJsonElement(v));
        }
        return arr;
    }

    // ==================== Timestamp parsing ====================

    public long parseTimestamp(final String timestamp, final String formatPattern) {
        if (StringUtil.isEmpty(timestamp)) {
            return 0L;
        }
        if (StringUtil.isEmpty(formatPattern)) {
            if (StringUtils.isNumeric(timestamp)) {
                return Long.parseLong(timestamp);
            }
            return 0L;
        }
        final SimpleDateFormat format = new SimpleDateFormat(formatPattern);
        try {
            return format.parse(timestamp).getTime();
        } catch (final ParseException e) {
            return 0L;
        }
    }

    // ==================== Type conversion ====================

    /**
     * {@code as String} cast — null-safe, returns null for null input.
     * <pre>
     *   // LAL:  parsed.service as String    →  h.toStr(h.mapVal("service"))
     * </pre>
     */
    public String toStr(final Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    /**
     * {@code as Long} cast — Number or String to long.
     * <pre>
     *   // LAL:  parsed.latency as Long    →  Long.valueOf(h.toLong(h.mapVal("latency")))
     * </pre>
     */
    public long toLong(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        if (obj instanceof String) {
            return Long.parseLong((String) obj);
        }
        return 0L;
    }

    /**
     * {@code as Integer} cast — Number or String to int.
     * <pre>
     *   // LAL:  parsed.code as Integer    →  Integer.valueOf(h.toInt(h.mapVal("code")))
     * </pre>
     */
    public int toInt(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        return 0;
    }

    /**
     * {@code as Boolean} cast — Boolean, String, or non-null to boolean.
     * <pre>
     *   // LAL:  parsed.ssl as Boolean    →  Boolean.valueOf(h.toBool(h.mapVal("ssl")))
     * </pre>
     */
    public boolean toBool(final Object obj) {
        if (obj instanceof Boolean) {
            return ((Boolean) obj).booleanValue();
        }
        if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        }
        return obj != null;
    }

    // ==================== Boolean evaluation ====================

    /**
     * Boolean truthiness for if-conditions: null is false, Boolean delegates,
     * String parses, anything else is true.
     * <pre>
     *   // LAL:  if (parsed.flag)    →  h.isTrue(h.mapVal("flag"))
     * </pre>
     */
    public boolean isTrue(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return ((Boolean) obj).booleanValue();
        }
        if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        }
        return true;
    }

    /**
     * String non-emptiness for if-conditions: null is false, otherwise checks
     * that toString() is non-empty.
     * <pre>
     *   // LAL:  if (parsed.name)    →  h.isNotEmpty(h.mapVal("name"))
     * </pre>
     */
    public boolean isNotEmpty(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof String) {
            return !((String) obj).isEmpty();
        }
        return !obj.toString().isEmpty();
    }

    /**
     * Primitive boolean overload — needed when chained methods (e.g.
     * {@code .endsWith()}) return primitive {@code boolean} which Javassist
     * cannot auto-box to match {@code isNotEmpty(Object)}.
     */
    public boolean isNotEmpty(final boolean value) {
        return value;
    }

    /**
     * Primitive boolean overload for {@link #isTrue(Object)}.
     */
    public boolean isTrue(final boolean value) {
        return value;
    }

    // ==================== Safe navigation ====================

    /**
     * Null-safe {@code ?.toString()}: returns null when input is null.
     * <pre>
     *   // LAL:  parsed?.x?.toString()    →  h.toString(h.mapVal("x"))
     * </pre>
     */
    public String toString(final Object obj) {
        return obj == null ? null : obj.toString();
    }

    /**
     * Null-safe {@code ?.trim()}: returns null when input is null.
     * <pre>
     *   // LAL:  parsed?.x?.trim()    →  h.trim(h.mapVal("x"))
     * </pre>
     */
    public String trim(final Object obj) {
        return obj == null ? null : obj.toString().trim();
    }

}
