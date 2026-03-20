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

import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfig;
import org.apache.skywalking.oap.analyzer.genai.config.GenAITagKey;
import org.apache.skywalking.oap.analyzer.genai.matcher.GenAIProviderPrefixMatcher;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.source.GenAIMetrics;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class GenAIMeterAnalyzer implements IGenAIMeterAnalyzerService {

    private static final Logger LOG = LoggerFactory.getLogger(GenAIMeterAnalyzer.class);

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

        String modelName = tags.get(GenAITagKey.RESPONSE_MODEL);

        if (StringUtil.isBlank(modelName)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Model name is missing in span [{}], skipping GenAI analysis", span.getOperationName());
            }
            return null;
        }
        String provider = tags.get(GenAITagKey.PROVIDER_NAME);
        GenAIProviderPrefixMatcher.MatchResult matchResult = matcher.match(modelName);

        if (StringUtil.isBlank(provider)) {
            provider = matchResult.getProvider();
        }

        GenAIConfig.Model modelConfig = matchResult.getModelConfig();

        long inputTokens = parseSafeLong(tags.get(GenAITagKey.INPUT_TOKENS));
        long outputTokens = parseSafeLong(tags.get(GenAITagKey.OUTPUT_TOKENS));

        // calculate the total cost by the cost configs
        double totalCost = 0.0;
        if (modelConfig != null) {
            if (modelConfig.getInputCostPerM() > 0) {
                totalCost += inputTokens * modelConfig.getInputCostPerM();
            }
            if (modelConfig.getOutputCostPerM() > 0) {
                totalCost += outputTokens * modelConfig.getOutputCostPerM();
            }
        }

        GenAIMetrics metrics = new GenAIMetrics();

        metrics.setServiceId(IDManager.ServiceID.buildId(provider, Layer.VIRTUAL_GENAI.isNormal()));
        metrics.setProviderName(provider);
        metrics.setModelName(modelName);
        metrics.setInputTokens(inputTokens);
        metrics.setOutputTokens(outputTokens);

        metrics.setTimeToFirstToken(parseSafeInt(tags.get(GenAITagKey.SERVER_TIME_TO_FIRST_TOKEN)));
        metrics.setTotalCost(totalCost);

        long latency = span.getEndTime() - span.getStartTime();
        metrics.setLatency(latency);
        metrics.setStatus(!span.getIsError());
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));

        return metrics;
    }

    private long parseSafeLong(String value) {
        if (StringUtil.isEmpty(value)) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LOG.warn("Failed to parse token count: {}", value);
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
            LOG.warn("Failed to parse token count: {}", value);
            return 0;
        }
    }
}
