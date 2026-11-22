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
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.regex.Matcher;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALCallables;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.server.core.dsl.debug.ToJson;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;
import org.apache.skywalking.oap.server.core.dsl.DslStrings;
import org.apache.skywalking.oap.server.core.dsl.DslValues;

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
 *   // Generated:  h.toStr(h.prop(h.mapVal("client_process"), "address"))
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
 * <p><b>4. Metadata</b> — direct access to {@code LogMetadata} fields.
 * Not accessed through this helper; the compiler generates direct getter chains
 * like {@code h.ctx().metadata().getService()}.
 *
 * <p><b>5. LogData body/tags</b> — direct access to {@code LogData.Builder} fields.
 * Not accessed through this helper; the compiler generates typed cast + getter
 * chains like {@code ((LogData.Builder) h.ctx().input()).getBody()}.
 *
 * <p><b>6. Typed input proto data</b> — direct access to typed protobuf input.
 * Not accessed through this helper; the compiler generates typed cast + getter
 * chains like {@code ((HTTPAccessLogEntry) h.ctx().input()).getResponse()}.
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
 *   // LAL:  parsed?.x?.toString()       →  h.toStr(h.mapVal("x"))
 *   // LAL:  parsed?.x?.trim()           →  h.trim(h.mapVal("x"))
 * </pre>
 *
 * <h2>Boolean Evaluation Methods</h2>
 * <pre>
 *   // LAL if-condition:  if (parsed.flag as Boolean)  →  h.toBool(h.mapVal("flag"))
 *   // LAL if-condition:  if (parsed.name)             →  h.isNotEmpty(h.mapVal("name"))
 * </pre>
 */
public final class LalRuntimeHelper {

    private final ExecutionContext ctx;
    /**
     * The tags this rule's tag statements added, in order.
     */
    private final List<String[]> ruleTags = new ArrayList<>(2);
    /** Whether a trace field statement set a trace field, which gives the log a trace context. */
    private boolean traceWritten;

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
     * {@code parsed.<key>}, read as Groovy LAL's {@code parsed.getAt}: the regexp group of that name when the text
     * parser matched and set it, else the parsed map's non-null value, else the log's own field. So a rule may mix
     * parsers, and a key no parser answered (its parser in an {@code if} branch that did not run, a group or a
     * body key that is not there) reads the log.
     * <pre>
     *   // LAL:  parsed.service    →  h.mapVal("service")
     * </pre>
     */
    public Object mapVal(final String key) {
        final ExecutionContext.Parsed parsed = ctx.parsed();
        final Matcher matcher = parsed.getMatcher();
        if (matcher != null) {
            // A name that is not one of the regexp's groups fails, as Groovy LAL's did; one that did not take part
            // in the match reads on.
            final String group = matcher.group(key);
            if (group != null) {
                return group;
            }
        }
        final Map<String, Object> map = parsed.getMap();
        if (map != null) {
            final Object value = map.get(key);
            // A value copied from the log's metadata reads the log as it is now.
            if (value != null && !parsed.getFromMetadata().containsKey(key)) {
                return value;
            }
        }
        final Object input = ctx.input();
        if ((input instanceof MessageOrBuilder || input instanceof ToJson) && !(input instanceof LogData.Builder)) {
            final Object value = beanProperty(input, key);
            if (value != null) {
                return value;
            }
        }
        return logField(key);
    }

    /**
     * The log's own field of that name, which Groovy LAL's {@code parsed.<key>} fell back to. The standard fields
     * are read from the rule's metadata, which its standard field statements set, as Groovy LAL's set the log, and
     * as {@code log.<field>} reads them.
     */
    private Object logField(final String key) {
        final LogMetadata metadata = ctx.metadata();
        if (metadata == null) {
            return null;
        }
        switch (key) {
            case "service":
                return metadata.getService();
            case "serviceInstance":
                return metadata.getServiceInstance();
            case "endpoint":
                return metadata.getEndpoint();
            case "layer":
                return metadata.getLayer();
            case "timestamp":
                return Long.valueOf(metadata.getTimestamp());
            case "traceContext":
                return logTraceContext();
            case "body":
                return ctx.input() instanceof LogData.Builder ? ((LogData.Builder) ctx.input()).getBody() : null;
            case "tags":
                return logTags();
            default:
                return null;
        }
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }

    /**
     * {@code log.hasTraceContext()}: the log arrived with one, or the rule's trace field statements set one.
     */
    public boolean logHasTraceContext() {
        return ctx.input() instanceof LogData.Builder && ((LogData.Builder) ctx.input()).hasTraceContext()
            || traceWritten;
    }

    /**
     * The log as the rule left it, for a {@code log.getX()} the codegen does not read from the rule's metadata
     * ({@code getServiceBytes()}, {@code toString()}): its standard fields, trace context and tags as the rule's
     * statements set them.
     */
    public LogData logView() {
        final LogData.Builder view = ctx.input() instanceof LogData.Builder
            ? ((LogData.Builder) ctx.input()).clone() : LogData.newBuilder();
        final LogMetadata metadata = ctx.metadata();
        view.setService(nullToEmpty(metadata.getService()))
            .setServiceInstance(nullToEmpty(metadata.getServiceInstance()))
            .setEndpoint(nullToEmpty(metadata.getEndpoint()))
            .setLayer(nullToEmpty(metadata.getLayer()))
            .setTimestamp(metadata.getTimestamp());
        if (logHasTraceContext()) {
            view.setTraceContext(logTraceContext());
        }
        if (logHasTags()) {
            view.setTags(logTags());
        }
        return view.build();
    }

    /**
     * {@code log.hasTags()}: the log arrived with its tags, or the rule's tag statements added one.
     */
    public boolean logHasTags() {
        return ctx.input() instanceof LogData.Builder && ((LogData.Builder) ctx.input()).hasTags()
            || !ruleTags.isEmpty();
    }

    /**
     * The log's trace context, {@code log.getTraceContext()}, as the rule's trace field statements left it.
     */
    public TraceContext logTraceContext() {
        final LogMetadata.TraceContext current = ctx.metadata().getTraceContext();
        final TraceContext.Builder traceContext = TraceContext.newBuilder();
        if (current != null) {
            traceContext.setTraceId(nullToEmpty(current.getTraceId()))
                        .setTraceSegmentId(nullToEmpty(current.getTraceSegmentId()))
                        .setSpanId(current.getSpanId());
        }
        return traceContext.build();
    }

    /**
     * The log's tags, {@code log.tags}: those it arrived with, then those the rule's tag statements added.
     */
    public LogTags logTags() {
        final LogTags.Builder tags = ctx.input() instanceof LogData.Builder
            ? ((LogData.Builder) ctx.input()).getTags().toBuilder() : LogTags.newBuilder();
        for (final String[] tag : ruleTags) {
            tags.addData(KeyStringValuePair.newBuilder().setKey(tag[0]).setValue(tag[1]));
        }
        return tags.build();
    }

    /**
     * {@code parsed.<group>} of a text parser: the regexp's group (a name the regexp has no group of fails, as Groovy
     * LAL's did), else, for a group the match did not set or a regexp that did not run, the log's string field of
     * that name. A string.
     */
    public String group(final String name) {
        final Matcher matcher = ctx.parsed().getMatcher();
        final String group = matcher == null ? null : matcher.group(name);
        if (group != null) {
            return group;
        }
        final Object field = logField(name);
        return field instanceof String ? (String) field : null;
    }

    /**
     * {@code value.<name>}: a map's value of that key, the property of each element of a collection (Groovy's
     * spread), or a trace context's field. As Groovy's, it fails on a null value.
     * <pre>
     *   // LAL:  parsed.client_process.address    →  h.prop(h.mapVal("client_process"), "address")
     * </pre>
     */
    public Object prop(final Object value, final String name) {
        if (value == null) {
            throw new NullPointerException("Cannot get property '" + name + "' on null object");
        }
        return propertyOf(value, name);
    }

    /**
     * {@code value?.<name>}: as {@link #prop(Object, String)}, a null value reads null.
     */
    public Object safeProp(final Object value, final String name) {
        return value == null ? null : propertyOf(value, name);
    }

    private static Object propertyOf(final Object value, final String name) {
        if (value instanceof Map) {
            return ((Map<?, ?>) value).get(name);
        }
        if ("toString".equals(name)) {
            // the call every value takes, as written with (): an array or a collection by its elements
            return DslStrings.text(value);
        }
        if (value instanceof LogMetadata.TraceContext) {
            final LogMetadata.TraceContext traceContext = (LogMetadata.TraceContext) value;
            switch (name) {
                case "traceId":
                    return traceContext.getTraceId();
                case "traceSegmentId":
                    return traceContext.getTraceSegmentId();
                case "spanId":
                    return traceContext.getSpanId();
                default:
                    break;
            }
        }
        final Method getter = getter(value.getClass(), name);
        if (getter != null) {
            return invoke(allowed(value, getter), value);
        }
        if (value instanceof Collection) {
            // A property a collection does not have is each element's, as Groovy's spread.
            final List<Object> values = new ArrayList<>();
            for (final Object element : (Collection<?>) value) {
                if (element != null) {
                    values.add(propertyOf(element, name));
                }
            }
            return values;
        }
        throw new IllegalArgumentException("No property '" + name + "' on " + describe(value));
    }

    /**
     * A bean property of the value, Groovy's {@code value.name}, or null when it has none.
     */
    private static Object beanProperty(final Object value, final String name) {
        final Method getter = getter(value.getClass(), name);
        return getter == null ? null : invoke(allowed(value, getter), value);
    }

    /**
     * A getter a rule may call, as {@link LALCallables} lists them for a property the compiler could not see.
     */
    private static Method allowed(final Object value, final Method getter) {
        if (!LALCallables.get().allows(value.getClass(), getter)) {
            throw new IllegalArgumentException("The property behind " + getter.getName() + "() of " + describe(value)
                + " is not one LAL reads. " + LALCallables.get().describe(value.getClass()));
        }
        return getter;
    }

    /**
     * The public {@code getX()}, or boolean {@code isX()}, of a property, found where it is accessible: a public
     * method of a class that is not public is called through the public type declaring it.
     */
    private static Method getter(final Class<?> type, final String name) {
        if (name.isEmpty()) {
            return null;
        }
        final String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        final Method get = accessibleMethod(type, "get" + capitalized);
        if (get != null) {
            return get;
        }
        final Method is = accessibleMethod(type, "is" + capitalized);
        return is != null && (is.getReturnType() == boolean.class || is.getReturnType() == Boolean.class) ? is : null;
    }

    private static Method accessibleMethod(final Class<?> type, final String name) {
        if (type == null) {
            return null;
        }
        if (Modifier.isPublic(type.getModifiers())) {
            try {
                final Method method = type.getMethod(name);
                if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                    return method;
                }
            } catch (final NoSuchMethodException e) {
                return null;
            }
        }
        for (final Class<?> api : type.getInterfaces()) {
            final Method method = accessibleMethod(api, name);
            if (method != null) {
                return method;
            }
        }
        return accessibleMethod(type.getSuperclass(), name);
    }

    private static Object invoke(final Method getter, final Object value) {
        try {
            return getter.invoke(value);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + getter + " of " + describe(value), e);
        }
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
        final List dl = ((LogData.Builder) ctx.input()).getTags().getDataList();
        for (int i = 0; i < dl.size(); i++) {
            final KeyStringValuePair kv = (KeyStringValuePair) dl.get(i);
            if (key.equals(kv.getKey())) {
                return kv.getValue();
            }
        }
        // Groovy LAL's tag statement added to the log's tags, after those it arrived with.
        for (final String[] tag : ruleTags) {
            if (key.equals(tag[0])) {
                return tag[1];
            }
        }
        return "";
    }

    /**
     * A {@code tag} statement's value, as Groovy LAL's tag statement wrote it: none for a null or blank value, a
     * collection's non-null elements joined by commas. The tag is recorded for {@link #tagValue(String)}.
     * <pre>
     *   // LAL:  tag level: parsed.level    →  _o.addTag("level", h.tag("level", h.mapVal("level")))
     * </pre>
     *
     * @return the tag's value, or null for none
     */
    public String tag(final String key, final Object value) {
        if (StringUtil.isBlank(key) || value == null || StringUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        final String tagValue;
        if (value instanceof Collection) {
            final StringJoiner joined = new StringJoiner(",");
            for (final Object element : (Collection<?>) value) {
                if (element != null) {
                    joined.add(String.valueOf(element));
                }
            }
            tagValue = joined.toString();
        } else {
            tagValue = String.valueOf(value);
        }
        // The log keeps no tag of an empty value (LogBuilder#addTag): neither do the tags the rule reads back.
        if (!tagValue.isEmpty()) {
            ruleTags.add(new String[] {key, tagValue});
        }
        return tagValue;
    }

    /**
     * Source attribute lookup by key name. Reads from
     * {@link org.apache.skywalking.oap.server.core.source.LogMetadata#getSourceAttributes()}.
     * Returns empty string if the key is not found or sourceAttributes is empty.
     * <pre>
     *   // LAL:  sourceAttribute("os.name")
     *   // Generated:  h.sourceAttributeValue("os.name")
     * </pre>
     */
    public String sourceAttributeValue(final String key) {
        final Map<String, String> attrs = ctx.metadata().getSourceAttributes();
        if (attrs == null || attrs.isEmpty()) {
            return "";
        }
        final String val = attrs.get(key);
        return val != null ? val : "";
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
     * Converts a {@link Map} (from JSON/YAML parser output) into a {@link JsonObject}.
     * Recursively converts nested Maps and Lists to Gson elements.
     */
    @SuppressWarnings("unchecked")
    public JsonObject toJsonObject(final Map<String, ?> map) {
        if (map == null) {
            return null;
        }
        final JsonObject obj = new JsonObject();
        for (final Map.Entry<String, ?> entry : map.entrySet()) {
            obj.add(entry.getKey(), mapValueToJsonElement(entry.getValue()));
        }
        return obj;
    }

    /**
     * Fallback for {@code Map.get()} erasure — dispatches to typed overloads.
     */
    @SuppressWarnings("unchecked")
    public JsonObject toJsonObject(final Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return toJsonObject((Map<String, ?>) obj);
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
    @SuppressWarnings("unchecked")
    public JsonArray toJsonArray(final Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof List) {
            final JsonArray arr = new JsonArray();
            for (final Object item : (List<?>) obj) {
                arr.add(mapValueToJsonElement(item));
            }
            return arr;
        }
        if (obj instanceof String) {
            return toJsonArray((String) obj);
        }
        return null;
    }

    // ==================== Map/List → Gson recursive conversion ====================

    @SuppressWarnings("unchecked")
    private static JsonElement mapValueToJsonElement(final Object value) {
        if (value == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        if (value instanceof Map) {
            final JsonObject obj = new JsonObject();
            for (final Map.Entry<String, ?> e : ((Map<String, ?>) value).entrySet()) {
                obj.add(e.getKey(), mapValueToJsonElement(e.getValue()));
            }
            return obj;
        }
        if (value instanceof List) {
            final JsonArray arr = new JsonArray();
            for (final Object item : (List<?>) value) {
                arr.add(mapValueToJsonElement(item));
            }
            return arr;
        }
        if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        }
        if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        }
        return new JsonPrimitive(value.toString());
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

    // ==================== Standard fields ====================
    // Groovy LAL's extractor set these on the log itself, so a later `log.<field>` read and the traffic built from the
    // log saw the rule's value; the rule's own metadata copy plays that log. A null left the field as it was. Each
    // returns its value, the output builder's setter argument.

    public String setLogService(final String service) {
        if (service != null) {
            ctx.metadata().setService(service);
        }
        return service;
    }

    public String setLogServiceInstance(final String instance) {
        if (instance != null) {
            ctx.metadata().setServiceInstance(instance);
        }
        return instance;
    }

    public String setLogEndpoint(final String endpoint) {
        if (endpoint != null) {
            ctx.metadata().setEndpoint(endpoint);
        }
        return endpoint;
    }

    public String setLogTraceId(final String traceId) {
        if (traceId != null) {
            ctx.metadata().setTraceContext(traceContext().traceId(traceId).build());
            traceWritten = true;
        }
        return traceId;
    }

    public String setLogSegmentId(final String segmentId) {
        if (segmentId != null) {
            ctx.metadata().setTraceContext(traceContext().traceSegmentId(segmentId).build());
            traceWritten = true;
        }
        return segmentId;
    }

    /**
     * @return the span id trimmed, as {@code as Integer} reads a number
     */
    public String setLogSpanId(final String spanId) {
        if (spanId == null) {
            return null;
        }
        final String trimmed = numberText(spanId);
        ctx.metadata().setTraceContext(traceContext().spanId(Integer.parseInt(trimmed)).build());
        traceWritten = true;
        return trimmed;
    }

    /**
     * A copy of the trace context to change: every rule's metadata copy shares the original.
     */
    private LogMetadata.TraceContext.TraceContextBuilder traceContext() {
        final LogMetadata.TraceContext current = ctx.metadata().getTraceContext();
        final LogMetadata.TraceContext.TraceContextBuilder copy = LogMetadata.TraceContext.builder();
        if (current != null) {
            copy.traceId(current.getTraceId())
                .traceSegmentId(current.getTraceSegmentId())
                .spanId(current.getSpanId())
                .otlpSpanId(current.getOtlpSpanId());
        }
        return copy;
    }

    // ==================== Timestamp parsing ====================

    /**
     * The value of a {@code timestamp} statement, or null when Groovy LAL left the timestamp as it was: an empty
     * value, one that is not a number (without a format), or one the format does not parse.
     */
    public Long timestampOf(final Object value, final String formatPattern) {
        if (value instanceof Number && StringUtil.isEmpty(formatPattern)) {
            return ((Number) value).longValue();
        }
        final String timestamp = value == null ? null : value.toString();
        if (StringUtil.isEmpty(timestamp)) {
            return null;
        }
        if (StringUtil.isEmpty(formatPattern)) {
            return StringUtils.isNumeric(timestamp) ? Long.valueOf(timestamp) : null;
        }
        try {
            return new SimpleDateFormat(formatPattern).parse(timestamp).getTime();
        } catch (final ParseException e) {
            return null;
        }
    }

    // ==================== Type conversion ====================
    // A cast to a primitive. Groovy cannot convert null to a primitive either: where a null is expected, the
    // generated code checks for it first (a comparison, a setter), or keeps it with the as* casts below.

    /**
     * {@code as String} cast — null-safe, returns null for null input.
     * <pre>
     *   // LAL:  parsed.service as String    →  h.toStr(h.mapVal("service"))
     * </pre>
     */
    public String toStr(final Object obj) {
        return obj == null ? null : DslStrings.text(obj);
    }

    /**
     * {@code as Long} cast — Number or String to long. A string is trimmed first, as Groovy's {@code as Long} does.
     * <pre>
     *   // LAL:  parsed.latency as Long    →  Long.valueOf(h.toLong(h.mapVal("latency")))
     * </pre>
     */
    public long toLong(final Object obj) {
        return DslNumbers.toLong(obj);
    }

    /**
     * {@code as Integer} cast — Number or String to int.
     * <pre>
     *   // LAL:  parsed.code as Integer    →  Integer.valueOf(h.toInt(h.mapVal("code")))
     * </pre>
     */
    public int toInt(final Object obj) {
        return DslNumbers.toInt(obj);
    }

    /**
     * {@code as Double} cast — Number or String to double.
     * <pre>
     *   // LAL:  parsed.ratio as Double    →  Double.valueOf(h.toDouble(h.mapVal("ratio")))
     * </pre>
     */
    public double toDouble(final Object obj) {
        return DslNumbers.toDouble(obj);
    }

    /**
     * {@code as Float} cast — Number or String to float.
     * <pre>
     *   // LAL:  parsed.ratio as Float    →  Float.valueOf(h.toFloat(h.mapVal("ratio")))
     * </pre>
     */
    public float toFloat(final Object obj) {
        return DslNumbers.toFloat(obj);
    }

    /**
     * {@code as Boolean} cast — a boolean, the text true or false, a number by zero ({@link DslNumbers#toBoolean});
     * a null or a blank text is false, any other value fails the log.
     * <pre>
     *   // LAL:  parsed.ssl as Boolean    →  Boolean.valueOf(h.toBool(h.mapVal("ssl")))
     * </pre>
     */
    public boolean toBool(final Object obj) {
        return !DslNumbers.isBlank(obj) && DslNumbers.toBoolean(obj);
    }

    // ==================== Operators on run-time types ====================
    // `==`, ordering, `+` and `[i]` on values whose types are known only at run time.

    /**
     * A value typed only at run time joined after a text: see {@link DslNumbers#joined}.
     */
    public String joined(final Object value) {
        return DslNumbers.joined(value);
    }

    /**
     * {@code ==}, as every DSL compares values: see {@link DslValues#equal}.
     */
    public boolean equal(final Object left, final Object right) {
        return DslValues.equal(left, right);
    }

    private static List<?> asList(final Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }
        if (value.getClass().isArray()) {
            final List<Object> list = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                list.add(Array.get(value, i));
            }
            return list;
        }
        return null;
    }

    /**
     * The order of an ordering ({@code <}, {@code <=}, ...): values as every DSL orders them
     * ({@link DslValues#order}); any other pair by the left one's {@link Comparable}, which a map, a list or an array
     * is not, against itself neither.
     *
     * @return the order, null for a NaN, which has none
     */
    @SuppressWarnings("unchecked")
    private static Integer ordering(final Object left, final Object right) {
        if (left == null || right == null || DslValues.ordersByValue(left, right)) {
            return DslValues.order(left, right);
        }
        if (!(left instanceof Comparable)) {
            throw new IllegalArgumentException("Cannot compare " + describe(left) + " with " + describe(right));
        }
        if (left == right) {
            return 0;
        }
        try {
            return ((Comparable<Object>) left).compareTo(right);
        } catch (final ClassCastException e) {
            throw new IllegalArgumentException("Cannot compare " + describe(left) + " with " + describe(right), e);
        }
    }

    /**
     * {@code +} on a value whose type is known only at run time, which Groovy LAL did not compile: see
     * {@link DslNumbers#plus}.
     */
    public Object plus(final Object left, final Object right) {
        return DslNumbers.plus(left, right);
    }

    /**
     * {@code /} on two whole numbers: Groovy's BigDecimal quotient (7 / 2 is 3.5), see {@link DslNumbers#quotient}.
     */
    public BigDecimal quotient(final long dividend, final long divisor) {
        return DslNumbers.quotient(BigDecimal.valueOf(dividend), BigDecimal.valueOf(divisor));
    }

    /**
     * {@code +}, {@code -}, {@code *} or {@code /} of such a quotient with a whole number or another quotient, which
     * Groovy keeps a BigDecimal.
     */
    public BigDecimal decimal(final Number left, final String op, final Number right) {
        final BigDecimal l = DslNumbers.exact(left);
        final BigDecimal r = DslNumbers.exact(right);
        switch (op) {
            case "+":
                return l.add(r);
            case "-":
                return l.subtract(r);
            case "*":
                return l.multiply(r);
            case "/":
                return DslNumbers.quotient(l, r);
            default:
                throw new IllegalArgumentException("Unknown operator " + op);
        }
    }

    private static String numberText(final CharSequence value) {
        return value.toString().trim();
    }

    private static BigDecimal toBigDecimal(final Number n) {
        return n instanceof BigDecimal ? (BigDecimal) n : new BigDecimal(n.toString());
    }

    private static String describe(final Object value) {
        return value == null ? "null" : value.getClass().getName() + " '" + value + "'";
    }

    /**
     * A subscript, as Groovy's: an index past the end of a list or an iterable reads null, one past the end of a
     * string, an array or a JsonArray fails, and so does a subscript of a null.
     */
    public Object at(final Object receiver, final int index) {
        if (receiver == null) {
            throw new NullPointerException("Cannot invoke method getAt() on null object");
        }
        if (receiver instanceof List) {
            final List<?> list = (List<?>) receiver;
            return index < list.size() ? list.get(index) : null;
        }
        if (receiver instanceof JsonArray) {
            // A JsonArray (toJsonArray) checks its index.
            return ((JsonArray) receiver).get(index);
        }
        if (receiver instanceof CharSequence) {
            return String.valueOf(((CharSequence) receiver).charAt(index));
        }
        if (receiver.getClass().isArray()) {
            return Array.get(receiver, index);
        }
        if (receiver instanceof Iterable) {
            int i = 0;
            for (final Object item : (Iterable<?>) receiver) {
                if (i++ == index) {
                    return item;
                }
            }
            return null;
        }
        throw new IllegalArgumentException("Cannot index " + describe(receiver));
    }

    /**
     * {@code value as <class>} in a {@code def}, as Groovy's {@code as} converts: the value itself when it already
     * is one, a collection or array as a list or set, a number or numeric string as a {@code BigInteger} or
     * {@code BigDecimal}. Any other conversion fails, as Groovy's cast did.
     */
    public Object asClass(final Object value, final Class<?> type) {
        if (value == null || type.isInstance(value)) {
            return value;
        }
        final List<?> elements = value instanceof Collection
            ? new ArrayList<>((Collection<?>) value) : value.getClass().isArray() ? asList(value) : null;
        if (elements != null) {
            if (type.isAssignableFrom(ArrayList.class)) {
                return new ArrayList<>(elements);
            }
            if (type.isAssignableFrom(LinkedHashSet.class)) {
                return new LinkedHashSet<>(elements);
            }
            if (type.isAssignableFrom(TreeSet.class)) {
                return new TreeSet<>(elements);
            }
        }
        if (type.isEnum() && value instanceof CharSequence) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            final Object constant = Enum.valueOf((Class) type, value.toString());
            return constant;
        }
        if (value instanceof Number || value instanceof String) {
            if (type == BigDecimal.class) {
                return value instanceof Number ? toBigDecimal((Number) value) : new BigDecimal(numberText((String) value));
            }
            if (type == BigInteger.class) {
                return value instanceof Number
                    ? toBigDecimal((Number) value).toBigInteger() : new BigInteger(numberText((String) value));
            }
        }
        throw new ClassCastException("Cannot cast " + describe(value) + " to class '" + type.getName() + "'");
    }

    /**
     * The orderings of values typed only at run time, as {@link #ordering} orders them; a NaN compares false, as Java
     * compares it.
     */
    public boolean lt(final Object left, final Object right) {
        final Integer order = ordering(left, right);
        return order != null && order < 0;
    }

    public boolean le(final Object left, final Object right) {
        final Integer order = ordering(left, right);
        return order != null && order <= 0;
    }

    public boolean gt(final Object left, final Object right) {
        final Integer order = ordering(left, right);
        return order != null && order > 0;
    }

    public boolean ge(final Object left, final Object right) {
        final Integer order = ordering(left, right);
        return order != null && order >= 0;
    }

    // ==================== Null-preserving casts ====================
    // `x as Integer` where the result stays an object (an == operand, a bare condition): as in Groovy, a null
    // stays null instead of becoming 0.

    public Integer asInteger(final Object obj) {
        return obj == null ? null : Integer.valueOf(toInt(obj));
    }

    public Long asLong(final Object obj) {
        return obj == null ? null : Long.valueOf(toLong(obj));
    }

    public Double asDouble(final Object obj) {
        return obj == null ? null : Double.valueOf(toDouble(obj));
    }

    public Float asFloat(final Object obj) {
        return obj == null ? null : Float.valueOf(toFloat(obj));
    }

    public Boolean asBoolean(final Object obj) {
        return obj == null ? null : Boolean.valueOf(toBool(obj));
    }

    // ==================== Boolean evaluation ====================

    /**
     * Groovy truth for if-conditions, as LAL and TAL test a value: see {@link DslValues#isNotEmpty}.
     * <pre>
     *   // LAL if-condition:  if (parsed.name)        →  h.isNotEmpty(h.mapVal("name"))
     * </pre>
     */
    public boolean isNotEmpty(final Object obj) {
        return DslValues.isNotEmpty(obj);
    }

    // ==================== Safe navigation ====================

    /**
     * {@code value.toString()}: an array as its elements, as Groovy writes one ({@code [low, high]}); {@code ?.toString()}
     * is {@link #toStr}.
     */
    public String text(final Object obj) {
        return DslStrings.text(obj);
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
