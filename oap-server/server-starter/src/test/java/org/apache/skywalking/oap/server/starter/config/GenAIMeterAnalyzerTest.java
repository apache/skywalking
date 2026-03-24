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

package org.apache.skywalking.oap.server.starter.config;

import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfig;
import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfigLoader;
import org.apache.skywalking.oap.analyzer.genai.config.GenAITagKeys;
import org.apache.skywalking.oap.analyzer.genai.matcher.GenAIProviderPrefixMatcher;
import org.apache.skywalking.oap.analyzer.genai.service.GenAIMeterAnalyzer;
import org.apache.skywalking.oap.server.core.source.GenAIMetrics;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GenAIMeterAnalyzerTest {
    private GenAIConfig loadedConfig;
    private GenAIProviderPrefixMatcher matcher;
    private GenAIMeterAnalyzer analyzer;

    @BeforeEach
    void setUp() throws ModuleStartException {
        GenAIConfig config = new GenAIConfig();
        GenAIConfigLoader loader = new GenAIConfigLoader(config);
        loadedConfig = loader.loadConfig();

        matcher = GenAIProviderPrefixMatcher.build(loadedConfig);
        analyzer = new GenAIMeterAnalyzer(matcher);
    }

    @Test
    void testLoadConfig() {
        assertNotNull(loadedConfig);
        assertFalse(loadedConfig.getProviders().isEmpty(), "Providers list should not be empty after loading config");
    }

    @Test
    void testProviderMatching() {
        assertEquals("openai", matcher.match("gpt-5.4-pro").getProvider());
        assertEquals("deepseek", matcher.match("deepseek-chat").getProvider());
        assertEquals("anthropic", matcher.match("claude-4.6-opus").getProvider());
        assertEquals("gemini", matcher.match("gemini-3.1-pro-preview").getProvider());
    }

    @Test
    void testExtractMetricsWithValidSpan() {
        SpanObject span = SpanObject.newBuilder()
                .setOperationName("genai_call")
                .setStartTime(1000000L)
                .setEndTime(1005000L)
                .setIsError(false)
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.RESPONSE_MODEL)
                        .setValue("gpt-5.4")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.PROVIDER_NAME)
                        .setValue("openai")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.INPUT_TOKENS)
                        .setValue("1000")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.OUTPUT_TOKENS)
                        .setValue("500")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.SERVER_TIME_TO_FIRST_TOKEN)
                        .setValue("100")
                        .build())
                .build();

        SegmentObject segment = SegmentObject.newBuilder().build();
        GenAIMetrics metrics = analyzer.extractMetricsFromSWSpan(span, segment);

        assertNotNull(metrics);
        assertEquals("openai", metrics.getProviderName());
        assertEquals("gpt-5.4", metrics.getModelName());
        assertEquals(1000L, metrics.getInputTokens());
        assertEquals(500L, metrics.getOutputTokens());
        assertEquals(100, metrics.getTimeToFirstToken());


        assertEquals(10000L, metrics.getTotalEstimatedCost());
        assertEquals(5000L, metrics.getLatency());
        assertTrue(metrics.isStatus());
    }

    @Test
    void testExtractMetricsWithMissingModelName() {
        SpanObject span = SpanObject.newBuilder()
                .setOperationName("genai_call")
                .setStartTime(1000000L)
                .setEndTime(1005000L)
                .build();

        SegmentObject segment = SegmentObject.newBuilder().build();
        GenAIMetrics metrics = analyzer.extractMetricsFromSWSpan(span, segment);

        assertNull(metrics);
    }

    @Test
    void testExtractMetricsWithoutProviderName() {
        SpanObject span = SpanObject.newBuilder()
                .setOperationName("genai_call")
                .setStartTime(1000000L)
                .setEndTime(1005000L)
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.RESPONSE_MODEL)
                        .setValue("deepseek-chat")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.INPUT_TOKENS)
                        .setValue("2000")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.OUTPUT_TOKENS)
                        .setValue("1000")
                        .build())
                .build();

        SegmentObject segment = SegmentObject.newBuilder().build();
        GenAIMetrics metrics = analyzer.extractMetricsFromSWSpan(span, segment);

        assertNotNull(metrics);
        assertEquals("deepseek", metrics.getProviderName());
        assertEquals("deepseek-chat", metrics.getModelName());
        assertEquals(980L, metrics.getTotalEstimatedCost()); // 基于 0.28 和 0.42 预估价
    }

    @Test
    void testExtractMetricsWithNoModelConfig() {
        SpanObject span = SpanObject.newBuilder()
                .setOperationName("genai_call")
                .setStartTime(1000000L)
                .setEndTime(1005000L)
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.RESPONSE_MODEL)
                        .setValue("unknown-model")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.INPUT_TOKENS)
                        .setValue("1000")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.OUTPUT_TOKENS)
                        .setValue("500")
                        .build())
                .build();

        SegmentObject segment = SegmentObject.newBuilder().build();
        GenAIMetrics metrics = analyzer.extractMetricsFromSWSpan(span, segment);

        assertNotNull(metrics);
        assertEquals(0L, metrics.getTotalEstimatedCost());
    }

    @Test
    void testExtractMetricsWithInvalidTokenValues() {
        SpanObject span = SpanObject.newBuilder()
                .setOperationName("genai_call")
                .setStartTime(1000000L)
                .setEndTime(1005000L)
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.RESPONSE_MODEL)
                        .setValue("gpt-5.4")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.INPUT_TOKENS)
                        .setValue("invalid")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.OUTPUT_TOKENS)
                        .setValue("not-a-number")
                        .build())
                .build();

        SegmentObject segment = SegmentObject.newBuilder().build();
        GenAIMetrics metrics = analyzer.extractMetricsFromSWSpan(span, segment);

        assertNotNull(metrics);
        assertEquals(0L, metrics.getInputTokens());
        assertEquals(0L, metrics.getOutputTokens());
    }

    @Test
    void testExtractMetricsWithErrorSpan() {
        SpanObject span = SpanObject.newBuilder()
                .setOperationName("genai_call")
                .setStartTime(1000000L)
                .setEndTime(1005000L)
                .setIsError(true)
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAITagKeys.RESPONSE_MODEL)
                        .setValue("claude-4-sonnet")
                        .build())
                .build();

        SegmentObject segment = SegmentObject.newBuilder().build();
        GenAIMetrics metrics = analyzer.extractMetricsFromSWSpan(span, segment);

        assertNotNull(metrics);
        assertFalse(metrics.isStatus());
    }

    @Test
    void testEstimatedCost() throws ModuleStartException {
        GenAIConfig config = new GenAIConfig();
        GenAIConfigLoader loader = new GenAIConfigLoader(config);
        GenAIConfig loadedConfig = loader.loadConfig();

        GenAIProviderPrefixMatcher matcher = GenAIProviderPrefixMatcher.build(loadedConfig);

        GenAIProviderPrefixMatcher.MatchResult result = matcher.match("gpt-5.4-pro");
        assertNotNull(result.getModelConfig());
        assertEquals(30.0, result.getModelConfig().getInputEstimatedCostPerM(), 0.001);
        assertEquals(180.0, result.getModelConfig().getOutputEstimatedCostPerM(), 0.001);
    }
}
