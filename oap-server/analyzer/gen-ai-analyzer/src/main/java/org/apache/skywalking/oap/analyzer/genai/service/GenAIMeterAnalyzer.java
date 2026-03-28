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
import org.apache.skywalking.oap.server.core.source.GenAIMetrics;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

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

        // calculate the total cost by the cost configs
        double totalCost = 0.0D;
        if (modelConfig != null) {
            if (modelConfig.getInputEstimatedCostPerM() > 0) {
                totalCost += inputTokens * modelConfig.getInputEstimatedCostPerM();
            }
            if (modelConfig.getOutputEstimatedCostPerM() > 0) {
                totalCost += outputTokens * modelConfig.getOutputEstimatedCostPerM();
            }
        }

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
    public GenAIMetrics extractMetricsFromZipKinSpan(ZipkinSpan zipkinSpan) {
        JsonObject tags = zipkinSpan.getTags();
        JsonElement element = tags.get(GenAITagKeys.RESPONSE_MODEL);
        if (element == null || StringUtil.isBlank(element.getAsString())) {
            return null;
        }

        String modelName = element.getAsString();
        String provider = getZipKinSpanTagValue(tags, GenAITagKeys.PROVIDER_NAME);

        GenAIProviderPrefixMatcher.MatchResult matchResult = matcher.match(modelName);
        if (StringUtil.isBlank(provider)) {
            provider = matchResult.getProvider();
        }

        GenAIConfig.Model modelConfig = matchResult.getModelConfig();

        long inputTokens = parseSafeLong(getZipKinSpanTagValue(tags, GenAITagKeys.INPUT_TOKENS));
        long outputTokens = parseSafeLong(getZipKinSpanTagValue(tags, GenAITagKeys.OUTPUT_TOKENS));

        double totalCost = calculateTotalCost(modelConfig, inputTokens, outputTokens);

        BigDecimal calculatedCost = BigDecimal.valueOf(totalCost)
                .divide(new BigDecimal("1000000"), 10, RoundingMode.HALF_UP);
        tags.addProperty(GenAITagKeys.ESTIMATED_COST, calculatedCost.stripTrailingZeros().toPlainString());
        zipkinSpan.setTags(tags);

        GenAIMetrics metrics = new GenAIMetrics();
        metrics.setServiceId(IDManager.ServiceID.buildId(provider, Layer.VIRTUAL_GENAI.isNormal()));
        metrics.setProviderName(provider);
        metrics.setModelName(modelName);
        metrics.setInputTokens(inputTokens);
        metrics.setOutputTokens(outputTokens);
        metrics.setTimeToFirstToken(parseSafeInt(getZipKinSpanTagValue(tags, GenAITagKeys.SERVER_TIME_TO_FIRST_TOKEN)));
        metrics.setTotalEstimatedCost(Math.round(totalCost));
        metrics.setLatency(zipkinSpan.getDuration());
        metrics.setStatus(Objects.isNull(getZipKinSpanTagValue(tags, "errors")));
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(zipkinSpan.getTimestamp() / 1000));
        return metrics;
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

    private String getZipKinSpanTagValue(JsonObject tags, String key) {
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
}
