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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;

/**
 * The language's functions of logic, which every input has: ids and time buckets, numbers, text, JSON and empty
 * collections. A rule calls them by name; each holds no state, and one that logs writes under the calling script's
 * logger, which the run it takes first carries.
 */
@Slf4j
public class TalGeneralFunctions implements TALFunctionProvider {
    private static final Gson GSON = new Gson();

    @Override
    public String module() {
        return TraceAnalyzerModule.NAME;
    }

    /**
     * @return true: every input has these functions
     */
    @Override
    public boolean supportsInputType(final String inputType) {
        return true;
    }

    // ==================== Ids and time buckets ====================

    public String buildServiceId(final String name, final boolean isNormal) {
        return IDManager.ServiceID.buildId(name, isNormal);
    }

    public String buildInstanceId(final String serviceId, final String instanceName) {
        return IDManager.ServiceInstanceID.buildId(serviceId, instanceName);
    }

    public String buildEndpointId(final String serviceId, final String endpointName) {
        return IDManager.EndpointID.buildId(serviceId, endpointName);
    }

    public long minuteTimeBucket(final long timeMillis) {
        return TimeBucket.getMinuteTimeBucket(timeMillis);
    }

    public long recordTimeBucket(final long timeMillis) {
        return TimeBucket.getRecordTimeBucket(timeMillis);
    }

    // ==================== Numbers ====================

    public long round(final double value) {
        return Math.round(value);
    }

    public long floorMod(final long value, final long modulus) {
        return Math.floorMod(value, modulus);
    }

    /**
     * {@code murmur3Hash(text)}: the first 64 bits of the murmur3-128 hash of the text's UTF-8 bytes, as
     * {@code DefaultAIEvaluationSamplingPolicy} hashes a trace id.
     *
     * @param text the text to hash
     * @return the hash as a long
     */
    public long murmur3Hash(final String text) {
        return Hashing.murmur3_128().hashString(text, StandardCharsets.UTF_8).asLong();
    }

    /**
     * {@code parseLongOrZero(text)}, {@code GenAIMeterAnalyzer.parseSafeLong}: 0 for no value, the number, or 0
     * after a warning under the script's logger.
     *
     * @param h    the run
     * @param text the value to parse
     * @return the number, or 0
     */
    public long parseLongOrZero(final TalRuntimeHelper h, final String text) {
        if (StringUtil.isEmpty(text)) {
            return 0;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            h.scriptLogger().warn("Failed to parse value to long: {}", text);
            return 0;
        }
    }

    /**
     * {@code parseIntOrZero(text)}, {@code GenAIMeterAnalyzer.parseSafeInt}: 0 for no value, the number, or 0 after
     * a warning under the script's logger.
     *
     * @param h    the run
     * @param text the value to parse
     * @return the number, or 0
     */
    public int parseIntOrZero(final TalRuntimeHelper h, final String text) {
        if (StringUtil.isEmpty(text)) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            h.scriptLogger().warn("Failed to parse value to int: {}", text);
            return 0;
        }
    }

    /**
     * {@code parseStatusCode(text)}, as {@code RPCAnalysisListener.setPublicAttrs} reads a status code: the text as
     * an integer, or null after the warning {@code span {} has illegal status code {}} under the script's logger,
     * with the span as the input renders it.
     *
     * @param h    the run
     * @param text the status code tag's value
     * @return the code, or null
     */
    public Integer parseStatusCode(final TalRuntimeHelper h, final String text) {
        try {
            return Integer.valueOf(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            h.scriptLogger().warn("span {} has illegal status code {}", h.input(), text);
            return null;
        }
    }

    // ==================== Text ====================

    public String cut(final String text, final int length) {
        return StringUtil.cut(text, length);
    }

    public boolean isBlank(final String text) {
        return StringUtil.isBlank(text);
    }

    /**
     * The authority of a URL, host and port as written, so a client that names its target only as a URL still
     * gets a peer: the Python requests instrumentation sets {@code http.url} and no server.address.
     *
     * @param url an absolute URL, or anything else
     * @return {@code host} or {@code host:port}, without scheme, user info, path, query or fragment; empty when
     * the value has no authority, which a value without a {@code scheme://} prefix, a bare path, does not
     */
    public String urlHost(final String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        final int scheme = url.indexOf("://");
        if (scheme <= 0) {
            return "";
        }
        final int start = scheme + 3;
        int end = url.length();
        for (final char stop : new char[] {'/', '?', '#'}) {
            final int at = url.indexOf(stop, start);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        String authority = url.substring(start, end);
        final int userInfo = authority.lastIndexOf('@');
        if (userInfo >= 0) {
            authority = authority.substring(userInfo + 1);
        }
        return authority.trim();
    }

    // ==================== JSON ====================

    /**
     * @return the text as a JSON object, an empty one for no text or text that is not one
     */
    private static JsonObject parseObject(final String s) {
        if (s == null || s.isEmpty()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(s).getAsJsonObject();
        } catch (final RuntimeException e) {
            log.debug("TAL toJson() could not parse the value as a JSON object", e);
            return new JsonObject();
        }
    }

    /**
     * {@code toJson(value)}: a JSON object as it is, text parsed, an empty one for null or text that is not one, any
     * other object serialized by Gson. One overload, so a {@code null} argument has one function to call.
     */
    public JsonObject toJson(final Object obj) {
        if (obj == null) {
            return new JsonObject();
        }
        if (obj instanceof JsonObject) {
            return (JsonObject) obj;
        }
        if (obj instanceof String) {
            return parseObject((String) obj);
        }
        return GSON.toJsonTree(obj).getAsJsonObject();
    }

    /**
     * @return the text as a JSON array, an empty one for no text or text that is not one
     */
    private static JsonArray parseArray(final String s) {
        if (s == null || s.isEmpty()) {
            return new JsonArray();
        }
        try {
            return JsonParser.parseString(s).getAsJsonArray();
        } catch (final RuntimeException e) {
            log.debug("TAL toJsonArray() could not parse the value as a JSON array", e);
            return new JsonArray();
        }
    }

    /**
     * {@code toJsonArray(value)}: a JSON array as it is, text parsed, an empty one for null or text that is not one,
     * any other object serialized by Gson. One overload, so a {@code null} argument has one function to call.
     */
    public JsonArray toJsonArray(final Object obj) {
        if (obj == null) {
            return new JsonArray();
        }
        if (obj instanceof JsonArray) {
            return (JsonArray) obj;
        }
        if (obj instanceof String) {
            return parseArray((String) obj);
        }
        return GSON.toJsonTree(obj).getAsJsonArray();
    }

    /**
     * {@code parseJsonObject(text)}: {@code Gson.fromJson(text, JsonObject.class)}, so blank text gives null and
     * malformed or non-object text fails the rule with Gson's exception, as the agent path's logic-endpoint parse
     * does.
     *
     * @param text the JSON text
     * @return the object, or null
     */
    public JsonObject parseJsonObject(final String text) {
        return GSON.fromJson(text, JsonObject.class);
    }

    // ==================== Collections ====================

    /**
     * @return true when the list is null or holds nothing
     */
    public boolean isEmpty(final List<?> list) {
        return CollectionUtils.isEmpty(list);
    }

    /**
     * @return a new empty list, which a rule fills through its methods
     */
    public List<Object> emptyList() {
        return new ArrayList<>();
    }

    /**
     * @return a new empty map, which a rule fills through its methods
     */
    public Map<Object, Object> emptyMap() {
        return new HashMap<>();
    }
}
