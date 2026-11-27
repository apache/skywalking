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

package org.apache.skywalking.oap.genai.analyzer;

import com.google.gson.JsonObject;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfig;
import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfigLoader;
import org.apache.skywalking.oap.analyzer.genai.matcher.GenAIProviderPrefixMatcher;
import org.apache.skywalking.oap.server.library.util.genai.GenAISemanticAttributes;
import org.apache.skywalking.oap.analyzer.genai.service.GenAIMeterAnalyzer;
import org.apache.skywalking.oap.server.core.source.GenAIMetrics;
import org.apache.skywalking.oap.server.core.trace.OTLPSpanReader;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

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

        matcher = GenAIProviderPrefixMatcher.build();
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
                        .setKey(GenAISemanticAttributes.RESPONSE_MODEL)
                        .setValue("gpt-5.4")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.PROVIDER_NAME)
                        .setValue("openai")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.USAGE_INPUT_TOKENS)
                        .setValue("1000")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.USAGE_OUTPUT_TOKENS)
                        .setValue("500")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.SERVER_TIME_TO_FIRST_TOKEN)
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
                        .setKey(GenAISemanticAttributes.RESPONSE_MODEL)
                        .setValue("deepseek-chat")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.USAGE_INPUT_TOKENS)
                        .setValue("2000")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.USAGE_OUTPUT_TOKENS)
                        .setValue("1000")
                        .build())
                .build();

        SegmentObject segment = SegmentObject.newBuilder().build();
        GenAIMetrics metrics = analyzer.extractMetricsFromSWSpan(span, segment);

        assertNotNull(metrics);
        assertEquals("deepseek", metrics.getProviderName());
        assertEquals("deepseek-chat", metrics.getModelName());
        assertEquals(980L, metrics.getTotalEstimatedCost());
    }

    @Test
    void testExtractMetricsWithNoModelConfig() {
        SpanObject span = SpanObject.newBuilder()
                .setOperationName("genai_call")
                .setStartTime(1000000L)
                .setEndTime(1005000L)
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.RESPONSE_MODEL)
                        .setValue("unknown-model")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.USAGE_INPUT_TOKENS)
                        .setValue("1000")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.USAGE_OUTPUT_TOKENS)
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
                        .setKey(GenAISemanticAttributes.RESPONSE_MODEL)
                        .setValue("gpt-5.4")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.USAGE_INPUT_TOKENS)
                        .setValue("invalid")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.USAGE_OUTPUT_TOKENS)
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
                        .setKey(GenAISemanticAttributes.RESPONSE_MODEL)
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

        GenAIProviderPrefixMatcher matcher = GenAIProviderPrefixMatcher.build();

        GenAIProviderPrefixMatcher.MatchResult result = matcher.match("gpt-5.4-pro");
        assertNotNull(result.getModelConfig());
        assertEquals(30.0, result.getModelConfig().getInputEstimatedCostPerM(), 0.001);
        assertEquals(180.0, result.getModelConfig().getOutputEstimatedCostPerM(), 0.001);
    }

    @Test
    void testOTLPSpanReadsTimeToFirstChunkAsSeconds() {
        final Map<String, String> attributes = chatAttributes();
        attributes.put(GenAISemanticAttributes.RESPONSE_TIME_TO_FIRST_CHUNK, "0.4567");

        final GenAIMetrics metrics = analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK"));

        assertNotNull(metrics);
        assertEquals(457, metrics.getTimeToFirstToken());
        assertEquals("openai", metrics.getProviderName());
        assertEquals("gpt-5.4", metrics.getModelName());
        assertEquals(1000L, metrics.getInputTokens());
        assertEquals(500L, metrics.getOutputTokens());
        assertEquals(5000L, metrics.getLatency());
        assertTrue(metrics.isStatus());
    }

    @Test
    void testOTLPSpanFallsBackToTimeToFirstTokenInMillis() {
        final Map<String, String> attributes = chatAttributes();
        attributes.put(GenAISemanticAttributes.SERVER_TIME_TO_FIRST_TOKEN, "100");

        final GenAIMetrics metrics = analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK"));

        assertNotNull(metrics);
        assertEquals(100, metrics.getTimeToFirstToken());
    }

    @Test
    void testOTLPSpanPrefersTimeToFirstChunkOverTimeToFirstToken() {
        final Map<String, String> attributes = chatAttributes();
        attributes.put(GenAISemanticAttributes.RESPONSE_TIME_TO_FIRST_CHUNK, "0.4567");
        attributes.put(GenAISemanticAttributes.SERVER_TIME_TO_FIRST_TOKEN, "100");

        final GenAIMetrics metrics = analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK"));

        assertNotNull(metrics);
        assertEquals(457, metrics.getTimeToFirstToken());
    }

    @Test
    void testOTLPSpanWithUnusableTimeToFirstChunkDoesNotFallBack() {
        final Map<String, String> attributes = chatAttributes();
        attributes.put(GenAISemanticAttributes.RESPONSE_TIME_TO_FIRST_CHUNK, "soon");
        attributes.put(GenAISemanticAttributes.SERVER_TIME_TO_FIRST_TOKEN, "100");

        final GenAIMetrics metrics = analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK"));

        assertNotNull(metrics);
        assertEquals(0, metrics.getTimeToFirstToken());
    }

    @Test
    void testSWSpanReadsTimeToFirstChunkAsSeconds() {
        final SpanObject span = SpanObject.newBuilder()
                .setOperationName("genai_call")
                .setStartTime(1000000L)
                .setEndTime(1005000L)
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.RESPONSE_MODEL)
                        .setValue("gpt-5.4")
                        .build())
                .addTags(KeyStringValuePair.newBuilder()
                        .setKey(GenAISemanticAttributes.RESPONSE_TIME_TO_FIRST_CHUNK)
                        .setValue("0.1")
                        .build())
                .build();

        final GenAIMetrics metrics = analyzer.extractMetricsFromSWSpan(span, SegmentObject.newBuilder().build());

        assertNotNull(metrics);
        assertEquals(100, metrics.getTimeToFirstToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"soon", "-0.5", "NaN", "Infinity", "3000000"})
    void testOTLPSpanWithUnusableTimeToFirstChunk(final String timeToFirstChunk) {
        final Map<String, String> attributes = chatAttributes();
        attributes.put(GenAISemanticAttributes.RESPONSE_TIME_TO_FIRST_CHUNK, timeToFirstChunk);

        final GenAIMetrics metrics = analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK"));

        assertNotNull(metrics);
        assertEquals(0, metrics.getTimeToFirstToken());
    }

    @Test
    void testFailedOTLPSpanIsCountedByItsRequestModel() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(GenAISemanticAttributes.OPERATION_NAME, "chat");
        attributes.put(GenAISemanticAttributes.PROVIDER_NAME, "openai");
        attributes.put(GenAISemanticAttributes.REQUEST_MODEL, "gpt-5.4");

        final GenAIMetrics metrics = analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_ERROR"));

        assertNotNull(metrics);
        assertEquals("gpt-5.4", metrics.getModelName());
        assertEquals("openai", metrics.getProviderName());
        assertFalse(metrics.isStatus());
        assertEquals(0L, metrics.getInputTokens());
        assertEquals(0L, metrics.getOutputTokens());
    }

    @Test
    void testOTLPSpanPrefersResponseModel() {
        final Map<String, String> attributes = chatAttributes();
        attributes.put(GenAISemanticAttributes.REQUEST_MODEL, "gpt-5.4-mini");

        final GenAIMetrics metrics = analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK"));

        assertNotNull(metrics);
        assertEquals("gpt-5.4", metrics.getModelName());
    }

    @Test
    void testOTLPSpanWithoutAnyModel() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(GenAISemanticAttributes.OPERATION_NAME, "chat");
        attributes.put(GenAISemanticAttributes.PROVIDER_NAME, "openai");

        assertNull(analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "fetch_response", "create_agent", "invoke_agent", "invoke_workflow", "invoke_runner", "invoke_crew",
        "execute_tool", "plan", "retrieval", "user_speech", "search_memory", "create_memory", "update_memory",
        "upsert_memory", "delete_memory", "create_memory_store", "delete_memory_store"
    })
    void testOTLPSpanOfNonModelCallOperationIsSkipped(final String operationName) {
        final Map<String, String> attributes = chatAttributes();
        attributes.put(GenAISemanticAttributes.OPERATION_NAME, operationName);
        attributes.put(GenAISemanticAttributes.REQUEST_MODEL, "gpt-5.4");

        assertNull(analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "chat", "generate_content", "text_completion", "realtime_inference", "embeddings", "embedding", "image"
    })
    void testOTLPSpanOfModelCallOperationIsCounted(final String operationName) {
        final Map<String, String> attributes = chatAttributes();
        attributes.put(GenAISemanticAttributes.OPERATION_NAME, operationName);

        assertNotNull(analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK")));
    }

    @Test
    void testOTLPSpanWithoutOperationNameIsCounted() {
        final Map<String, String> attributes = chatAttributes();
        attributes.remove(GenAISemanticAttributes.OPERATION_NAME);

        assertNotNull(analyzer.extractMetricsFromOTLPSpan(otlpSpan(attributes, "STATUS_CODE_OK")));
    }

    @Test
    void testZipkinSpanReadsTheSameAttributes() {
        final JsonObject tags = new JsonObject();
        tags.addProperty(GenAISemanticAttributes.OPERATION_NAME, "chat");
        tags.addProperty(GenAISemanticAttributes.PROVIDER_NAME, "openai");
        tags.addProperty(GenAISemanticAttributes.REQUEST_MODEL, "gpt-5.4");
        tags.addProperty(GenAISemanticAttributes.RESPONSE_TIME_TO_FIRST_CHUNK, "1.2");
        tags.addProperty("error", "true");
        final ZipkinSpan failedCall = new ZipkinSpan();
        failedCall.setTags(tags);
        failedCall.setTimestamp(1_000_000_000L);
        failedCall.setDuration(5_000_000L);

        final GenAIMetrics metrics = analyzer.extractMetricsFromZipkinSpan(failedCall);

        assertNotNull(metrics);
        assertEquals("gpt-5.4", metrics.getModelName());
        assertEquals(1200, metrics.getTimeToFirstToken());
        assertEquals(5000L, metrics.getLatency());
        assertFalse(metrics.isStatus());

        tags.remove(GenAISemanticAttributes.RESPONSE_TIME_TO_FIRST_CHUNK);
        tags.addProperty(GenAISemanticAttributes.SERVER_TIME_TO_FIRST_TOKEN, "100");
        assertEquals(100, analyzer.extractMetricsFromZipkinSpan(failedCall).getTimeToFirstToken());

        tags.addProperty(GenAISemanticAttributes.OPERATION_NAME, "invoke_agent");
        assertNull(analyzer.extractMetricsFromZipkinSpan(failedCall));
    }

    private static Map<String, String> chatAttributes() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(GenAISemanticAttributes.OPERATION_NAME, "chat");
        attributes.put(GenAISemanticAttributes.PROVIDER_NAME, "openai");
        attributes.put(GenAISemanticAttributes.RESPONSE_MODEL, "gpt-5.4");
        attributes.put(GenAISemanticAttributes.USAGE_INPUT_TOKENS, "1000");
        attributes.put(GenAISemanticAttributes.USAGE_OUTPUT_TOKENS, "500");
        return attributes;
    }

    private static OTLPSpanReader otlpSpan(final Map<String, String> attributes, final String statusCode) {
        return new OTLPSpanReader() {
            @Override
            public String spanName() {
                return "chat gpt-5.4";
            }

            @Override
            public String spanKind() {
                return "SPAN_KIND_CLIENT";
            }

            @Override
            public long startTimeNanos() {
                return 1_000_000_000_000L;
            }

            @Override
            public long endTimeNanos() {
                return 1_005_000_000_000L;
            }

            @Override
            public String getAttribute(final String key) {
                return attributes.getOrDefault(key, "");
            }

            @Override
            public String statusCode() {
                return statusCode;
            }

            @Override
            public Map<String, String> attributes() {
                return attributes;
            }
        };
    }
}
