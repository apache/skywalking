/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.skywalking.oap.analyzer.genai.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfig;
import org.apache.skywalking.oap.analyzer.genai.matcher.GenAIProviderPrefixMatcher;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.GenAIMetrics;
import org.apache.skywalking.oap.server.core.source.GenAIModelAccess;
import org.apache.skywalking.oap.server.core.source.GenAIProviderAccess;
import org.apache.skywalking.oap.server.core.source.ServiceInstance;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.core.trace.OTLPSpanReader;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.library.util.genai.GenAIContextResolver;
import org.apache.skywalking.oap.server.library.util.genai.GenAISemanticAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toMap;

@Slf4j
public class GenAIMeterAnalyzer implements IGenAIMeterAnalyzerService {
    private static final String STATUS_CODE_ERROR = "STATUS_CODE_ERROR";
    /**
     * The attributes {@link #extractMetrics} reads, copied out of a Zipkin span's tag object.
     */
    private static final String[] GEN_AI_KEYS = {
        GenAISemanticAttributes.OPERATION_NAME,
        GenAISemanticAttributes.RESPONSE_MODEL,
        GenAISemanticAttributes.REQUEST_MODEL,
        GenAISemanticAttributes.PROVIDER_NAME,
        GenAISemanticAttributes.SYSTEM_NAME,
        GenAISemanticAttributes.USAGE_INPUT_TOKENS,
        GenAISemanticAttributes.USAGE_OUTPUT_TOKENS,
        GenAISemanticAttributes.RESPONSE_TIME_TO_FIRST_CHUNK,
        // Read when a span has no time to first chunk: SkyWalking Java Agent 9.7 and 9.8, and the earlier
        // virtual-genai.md
        GenAISemanticAttributes.SERVER_TIME_TO_FIRST_TOKEN
    };
    /**
     * The {@code gen_ai.operation.name} values of the OpenTelemetry GenAI conventions that describe no model call of
     * their own. Their spans can repeat the model and the token usage of the model calls under them, or of a call
     * made earlier, so counting them counts the same tokens twice. A name outside the conventions is not listed and
     * stays counted, since an instrumentation may have made it up for a model call.
     */
    private static final Set<String> NON_MODEL_CALL_OPERATIONS = Set.of(
        "fetch_response",
        "create_agent",
        "execute_tool",
        "plan",
        "retrieval",
        "user_speech",
        "search_memory",
        "create_memory",
        "update_memory",
        "upsert_memory",
        "delete_memory",
        "create_memory_store",
        "delete_memory_store"
    );
    /**
     * {@code invoke_agent}, {@code invoke_workflow}, {@code invoke_runner} and the conventions' open
     * {@code invoke_{framework_entrypoint}} pattern.
     */
    private static final String INVOKE_OPERATION_PREFIX = "invoke_";

    private final GenAIProviderPrefixMatcher matcher;

    private NamingControl namingControl;

    public GenAIMeterAnalyzer(GenAIProviderPrefixMatcher matcher) {
        this.matcher = matcher;
    }

    public void setNamingControl(NamingControl namingControl) {
        this.namingControl = namingControl;
    }

    @Override
    public GenAIMetrics extractMetricsFromSWSpan(SpanObject span, SegmentObject segment) {
        Map<String, String> tags = span.getTagsList().stream()
                .collect(toMap(
                        KeyStringValuePair::getKey,
                        KeyStringValuePair::getValue,
                        (v1, v2) -> v1
                ));

        final GenAIContextResolver.Result resolvedContext = GenAIContextResolver.resolve(tags);
        String modelName = resolvedContext.getModelName();

        if (StringUtil.isBlank(modelName)) {
            if (log.isDebugEnabled()) {
                log.debug("Model name is missing in span [{}], skipping GenAI analysis", span.getOperationName());
            }
            return null;
        }
        String provider = resolvedContext.getProviderName();

        GenAIProviderPrefixMatcher.MatchResult matchResult = matcher.match(modelName);

        GenAIConfig.Model modelConfig = matchResult.getModelConfig();

        long inputTokens = parseSafeLong(tags.get(GenAISemanticAttributes.USAGE_INPUT_TOKENS));
        long outputTokens = parseSafeLong(tags.get(GenAISemanticAttributes.USAGE_OUTPUT_TOKENS));

        double totalCost = calculateTotalCost(modelConfig, inputTokens, outputTokens);

        GenAIMetrics metrics = new GenAIMetrics();

        metrics.setServiceId(IDManager.ServiceID.buildId(provider, Layer.VIRTUAL_GENAI.isNormal()));
        metrics.setProviderName(provider);
        metrics.setModelName(modelName);
        metrics.setInputTokens(inputTokens);
        metrics.setOutputTokens(outputTokens);

        metrics.setTimeToFirstToken(timeToFirstTokenMillis(tags));
        metrics.setTotalEstimatedCost(totalCost);

        long latency = span.getEndTime() - span.getStartTime();
        metrics.setLatency(latency);
        metrics.setStatus(!span.getIsError());
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));

        return metrics;
    }

    @Override
    public GenAIMetrics extractMetricsFromZipkinSpan(ZipkinSpan zipkinSpan) {
        final JsonObject tags = zipkinSpan.getTags();
        final Map<String, String> attributes = new HashMap<>();
        for (final String key : GEN_AI_KEYS) {
            final String value = getZipkinSpanTagValue(tags, key);
            if (StringUtil.isNotBlank(value)) {
                attributes.put(key, value);
            }
        }
        return extractMetrics(
            attributes,
            zipkinSpan.getDuration() / 1000,
            StringUtil.isBlank(getZipkinSpanTagValue(tags, "error")),
            zipkinSpan.getTimestamp() / 1000
        );
    }

    @Override
    public GenAIMetrics extractMetricsFromOTLPSpan(final OTLPSpanReader span) {
        return extractMetrics(
            span.attributes(),
            TimeUnit.NANOSECONDS.toMillis(span.endTimeNanos() - span.startTimeNanos()),
            !STATUS_CODE_ERROR.equals(span.statusCode()),
            TimeUnit.NANOSECONDS.toMillis(span.startTimeNanos())
        );
    }

    /**
     * The attribute-driven part shared by the Zipkin and the native OTLP path: both carry the GenAI semantic
     * conventions as string attributes; only latency, status and the timestamp are read differently.
     *
     * @param attributes      span attributes as strings
     * @param latencyMillis   the span's duration in milliseconds
     * @param status          true when the span did not fail
     * @param startTimeMillis the span's start in milliseconds since the epoch
     * @return null when the span names no model, or when its operation is not a model call
     */
    private GenAIMetrics extractMetrics(final Map<String, String> attributes,
                                        final long latencyMillis,
                                        final boolean status,
                                        final long startTimeMillis) {
        final String operationName = attributes.get(GenAISemanticAttributes.OPERATION_NAME);
        if (StringUtil.isNotBlank(operationName)
            && (operationName.startsWith(INVOKE_OPERATION_PREFIX)
            || NON_MODEL_CALL_OPERATIONS.contains(operationName))) {
            return null;
        }

        // A call that failed before the provider answered has no response model; the requested one still names it.
        String calledModel = attributes.get(GenAISemanticAttributes.RESPONSE_MODEL);
        if (StringUtil.isBlank(calledModel)) {
            calledModel = attributes.get(GenAISemanticAttributes.REQUEST_MODEL);
        }
        if (StringUtil.isBlank(calledModel)) {
            return null;
        }

        final Map<String, String> contextTags = new HashMap<>();
        contextTags.put(GenAISemanticAttributes.RESPONSE_MODEL, calledModel);
        putAttribute(contextTags, attributes, GenAISemanticAttributes.PROVIDER_NAME);
        putAttribute(contextTags, attributes, GenAISemanticAttributes.SYSTEM_NAME);
        final GenAIContextResolver.Result resolvedContext = GenAIContextResolver.resolve(contextTags);
        String modelName = resolvedContext.getModelName();
        String provider = resolvedContext.getProviderName();

        GenAIProviderPrefixMatcher.MatchResult matchResult = matcher.match(modelName);

        GenAIConfig.Model modelConfig = matchResult.getModelConfig();

        long inputTokens = parseSafeLong(attributes.get(GenAISemanticAttributes.USAGE_INPUT_TOKENS));
        long outputTokens = parseSafeLong(attributes.get(GenAISemanticAttributes.USAGE_OUTPUT_TOKENS));

        double totalCost = calculateTotalCost(modelConfig, inputTokens, outputTokens);

        GenAIMetrics metrics = new GenAIMetrics();
        metrics.setServiceId(IDManager.ServiceID.buildId(provider, Layer.VIRTUAL_GENAI.isNormal()));
        metrics.setProviderName(provider);
        metrics.setModelName(modelName);
        metrics.setInputTokens(inputTokens);
        metrics.setOutputTokens(outputTokens);
        metrics.setTimeToFirstToken(timeToFirstTokenMillis(attributes));
        metrics.setTotalEstimatedCost(totalCost);
        metrics.setLatency(latencyMillis);
        metrics.setStatus(status);
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(startTimeMillis));
        return metrics;
    }

    /**
     * @param tags span tags or attributes as strings
     * @return the time to first token in milliseconds: {@code gen_ai.response.time_to_first_chunk}, in seconds, the
     *         span attribute of the OpenTelemetry GenAI conventions; else {@code gen_ai.server.time_to_first_token}, in
     *         milliseconds, which SkyWalking Java Agent 9.7 and 9.8 report, as do OTLP / Zipkin producers that followed
     *         the earlier virtual-genai.md
     */
    private int timeToFirstTokenMillis(final Map<String, String> tags) {
        final String timeToFirstChunk = tags.get(GenAISemanticAttributes.RESPONSE_TIME_TO_FIRST_CHUNK);
        if (StringUtil.isNotBlank(timeToFirstChunk)) {
            return parseSecondsAsMillis(timeToFirstChunk);
        }
        // SkyWalking Java Agent 9.7 and 9.8
        return parseSafeInt(tags.get(GenAISemanticAttributes.SERVER_TIME_TO_FIRST_TOKEN));
    }

    private static void putAttribute(final Map<String, String> target,
                                     final Map<String, String> source,
                                     final String key) {
        final String value = source.get(key);
        if (StringUtil.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    @Override
    public List<Source> transferToSources(GenAIMetrics metrics) {
        if (metrics == null) {
            return Collections.emptyList();
        }

        List<Source> sources = new ArrayList<>();
        sources.add(toVirtualGenAIServiceMeta(metrics));
        sources.add(toVirtualGenAIInstance(metrics));
        sources.add(toProviderAccess(metrics));
        sources.add(toModelAccess(metrics));
        return sources;
    }

    private long parseSafeLong(String value) {
        if (StringUtil.isEmpty(value)) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse value to long: {}", value);
            return 0;
        }
    }

    private int parseSafeInt(String value) {
        if (StringUtil.isEmpty(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse value to int: {}", value);
            return 0;
        }
    }

    /**
     * @param seconds a number of seconds, as text
     * @return the whole milliseconds nearest the value; 0 for no value, and 0 after a warning for a value that does
     *         not parse, is negative or is more milliseconds than an int holds
     */
    private int parseSecondsAsMillis(final String seconds) {
        if (StringUtil.isEmpty(seconds)) {
            return 0;
        }
        try {
            final double millis = Double.parseDouble(seconds) * 1000;
            if (millis >= 0 && millis <= Integer.MAX_VALUE) {
                return (int) Math.round(millis);
            }
        } catch (NumberFormatException ignored) {
            // warned below, with the values that parse and are out of range
        }
        log.warn("Failed to parse value to seconds: {}", seconds);
        return 0;
    }

    private String getZipkinSpanTagValue(JsonObject tags, String key) {
        JsonElement element = tags.get(key);
        return element != null ? element.getAsString() : null;
    }

    private double calculateTotalCost(GenAIConfig.Model modelConfig, long inputTokens, long outputTokens) {
        if (modelConfig == null) {
            return 0.0D;
        }
        double cost = 0.0D;
        if (modelConfig.getInputEstimatedCostPerM() > 0) {
            cost += inputTokens * modelConfig.getInputEstimatedCostPerM();
        }
        if (modelConfig.getOutputEstimatedCostPerM() > 0) {
            cost += outputTokens * modelConfig.getOutputEstimatedCostPerM();
        }
        return cost;
    }

    private ServiceMeta toVirtualGenAIServiceMeta(GenAIMetrics metrics) {
        ServiceMeta service = new ServiceMeta();
        service.setName(namingControl.formatServiceName(metrics.getProviderName()));
        service.setLayer(Layer.VIRTUAL_GENAI);
        service.setTimeBucket(metrics.getTimeBucket());
        return service;
    }

    private Source toVirtualGenAIInstance(GenAIMetrics metrics) {
        ServiceInstance instance = new ServiceInstance();
        instance.setTimeBucket(metrics.getTimeBucket());
        instance.setName(namingControl.formatInstanceName(metrics.getModelName()));
        instance.setServiceLayer(Layer.VIRTUAL_GENAI);
        instance.setServiceName(namingControl.formatServiceName(metrics.getProviderName()));
        return instance;
    }

    private GenAIProviderAccess toProviderAccess(GenAIMetrics metrics) {
        GenAIProviderAccess source = new GenAIProviderAccess();
        source.setName(namingControl.formatServiceName(metrics.getProviderName()));
        source.setInputTokens(metrics.getInputTokens());
        source.setOutputTokens(metrics.getOutputTokens());
        source.setTotalEstimatedCost(Math.round(metrics.getTotalEstimatedCost()));
        source.setLatency(metrics.getLatency());
        source.setStatus(metrics.isStatus());
        source.setTimeBucket(metrics.getTimeBucket());
        return source;
    }

    private GenAIModelAccess toModelAccess(GenAIMetrics metrics) {
        GenAIModelAccess source = new GenAIModelAccess();
        source.setServiceName(namingControl.formatServiceName(metrics.getProviderName()));
        source.setModelName(namingControl.formatInstanceName(metrics.getModelName()));
        source.setInputTokens(metrics.getInputTokens());
        source.setOutputTokens(metrics.getOutputTokens());
        source.setTotalEstimatedCost(Math.round(metrics.getTotalEstimatedCost()));
        source.setTimeToFirstToken(metrics.getTimeToFirstToken());
        source.setLatency(metrics.getLatency());
        source.setStatus(metrics.isStatus());
        source.setTimeBucket(metrics.getTimeBucket());
        return source;
    }
}
