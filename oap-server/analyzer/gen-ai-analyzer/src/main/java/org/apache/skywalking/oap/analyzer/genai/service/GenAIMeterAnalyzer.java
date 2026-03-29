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
import org.apache.skywalking.oap.analyzer.genai.config.GenAITagKeys;
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
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Slf4j
public class GenAIMeterAnalyzer implements IGenAIMeterAnalyzerService {

    private final GenAIProviderPrefixMatcher matcher;
    
    public GenAIMeterAnalyzer(GenAIProviderPrefixMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public GenAIMetrics extractMetricsFromSWSpan(SpanObject span, SegmentObject segment) {
        Map<String, String> tags = span.getTagsList().stream()
                .collect(toMap(
                        KeyStringValuePair::getKey,
                        KeyStringValuePair::getValue,
                        (v1, v2) -> v1
                ));

        String modelName = tags.get(GenAITagKeys.RESPONSE_MODEL);

        if (StringUtil.isBlank(modelName)) {
            if (log.isDebugEnabled()) {
                log.debug("Model name is missing in span [{}], skipping GenAI analysis", span.getOperationName());
            }
            return null;
        }
        String provider = tags.get(GenAITagKeys.PROVIDER_NAME);
        GenAIProviderPrefixMatcher.MatchResult matchResult = matcher.match(modelName);

        if (StringUtil.isBlank(provider)) {
            provider = matchResult.getProvider();
        }

        GenAIConfig.Model modelConfig = matchResult.getModelConfig();

        long inputTokens = parseSafeLong(tags.get(GenAITagKeys.INPUT_TOKENS));
        long outputTokens = parseSafeLong(tags.get(GenAITagKeys.OUTPUT_TOKENS));

        double totalCost = calculateTotalCost(modelConfig, inputTokens, outputTokens);

        GenAIMetrics metrics = new GenAIMetrics();

        metrics.setServiceId(IDManager.ServiceID.buildId(provider, Layer.VIRTUAL_GENAI.isNormal()));
        metrics.setProviderName(provider);
        metrics.setModelName(modelName);
        metrics.setInputTokens(inputTokens);
        metrics.setOutputTokens(outputTokens);

        metrics.setTimeToFirstToken(parseSafeInt(tags.get(GenAITagKeys.SERVER_TIME_TO_FIRST_TOKEN)));
        metrics.setTotalEstimatedCost(Math.round(totalCost));

        long latency = span.getEndTime() - span.getStartTime();
        metrics.setLatency(latency);
        metrics.setStatus(!span.getIsError());
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));

        return metrics;
    }

    @Override
    public GenAIMetrics extractMetricsFromZipkinSpan(ZipkinSpan zipkinSpan) {
        JsonObject tags = zipkinSpan.getTags();
        JsonElement element = tags.get(GenAITagKeys.RESPONSE_MODEL);
        if (element == null || StringUtil.isBlank(element.getAsString())) {
            return null;
        }

        String modelName = element.getAsString();
        String provider = getZipkinSpanTagValue(tags, GenAITagKeys.PROVIDER_NAME);

        GenAIProviderPrefixMatcher.MatchResult matchResult = matcher.match(modelName);
        if (StringUtil.isBlank(provider)) {
            provider = matchResult.getProvider();
        }

        GenAIConfig.Model modelConfig = matchResult.getModelConfig();

        long inputTokens = parseSafeLong(getZipkinSpanTagValue(tags, GenAITagKeys.INPUT_TOKENS));
        long outputTokens = parseSafeLong(getZipkinSpanTagValue(tags, GenAITagKeys.OUTPUT_TOKENS));

        double totalCost = calculateTotalCost(modelConfig, inputTokens, outputTokens);

        GenAIMetrics metrics = new GenAIMetrics();
        metrics.setServiceId(IDManager.ServiceID.buildId(provider, Layer.VIRTUAL_GENAI.isNormal()));
        metrics.setProviderName(provider);
        metrics.setModelName(modelName);
        metrics.setInputTokens(inputTokens);
        metrics.setOutputTokens(outputTokens);
        metrics.setTimeToFirstToken(parseSafeInt(getZipkinSpanTagValue(tags, GenAITagKeys.SERVER_TIME_TO_FIRST_TOKEN)));
        metrics.setTotalEstimatedCost(Math.round(totalCost));
        metrics.setLatency(zipkinSpan.getDuration() / 1000);
        metrics.setStatus(StringUtil.isNotBlank(getZipkinSpanTagValue(tags, "error")));
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(zipkinSpan.getTimestamp() / 1000));
        return metrics;
    }

    @Override
    public List<Source> transferToSources(GenAIMetrics metrics, NamingControl namingControl) {
        if (metrics == null) {
            return Collections.emptyList();
        }

        List<Source> sources = new ArrayList<>();
        sources.add(toVirtualGenAIServiceMeta(metrics, namingControl));
        sources.add(toVirtualGenAIInstance(metrics, namingControl));
        sources.add(toProviderAccess(metrics, namingControl));
        sources.add(toModelAccess(metrics, namingControl));
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

    private ServiceMeta toVirtualGenAIServiceMeta(GenAIMetrics metrics, NamingControl namingControl) {
        ServiceMeta service = new ServiceMeta();
        service.setName(namingControl.formatServiceName(metrics.getProviderName()));
        service.setLayer(Layer.VIRTUAL_GENAI);
        service.setTimeBucket(metrics.getTimeBucket());
        return service;
    }

    private Source toVirtualGenAIInstance(GenAIMetrics metrics, NamingControl namingControl) {
        ServiceInstance instance = new ServiceInstance();
        instance.setTimeBucket(metrics.getTimeBucket());
        instance.setName(namingControl.formatInstanceName(metrics.getModelName()));
        instance.setServiceLayer(Layer.VIRTUAL_GENAI);
        instance.setServiceName(namingControl.formatServiceName(metrics.getProviderName()));
        return instance;
    }

    private GenAIProviderAccess toProviderAccess(GenAIMetrics metrics, NamingControl namingControl) {
        GenAIProviderAccess source = new GenAIProviderAccess();
        source.setName(namingControl.formatServiceName(metrics.getProviderName()));
        source.setInputTokens(metrics.getInputTokens());
        source.setOutputTokens(metrics.getOutputTokens());
        source.setTotalEstimatedCost(metrics.getTotalEstimatedCost());
        source.setLatency(metrics.getLatency());
        source.setStatus(metrics.isStatus());
        source.setTimeBucket(metrics.getTimeBucket());
        return source;
    }

    private GenAIModelAccess toModelAccess(GenAIMetrics metrics, NamingControl namingControl) {
        GenAIModelAccess source = new GenAIModelAccess();
        source.setServiceName(namingControl.formatServiceName(metrics.getProviderName()));
        source.setModelName(namingControl.formatInstanceName(metrics.getModelName()));
        source.setInputTokens(metrics.getInputTokens());
        source.setOutputTokens(metrics.getOutputTokens());
        source.setTotalEstimatedCost(metrics.getTotalEstimatedCost());
        source.setTimeToFirstToken(metrics.getTimeToFirstToken());
        source.setLatency(metrics.getLatency());
        source.setStatus(metrics.isStatus());
        source.setTimeBucket(metrics.getTimeBucket());
        return source;
    }
}
