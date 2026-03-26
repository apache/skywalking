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

package org.apache.skywalking.oap.server.library.util.genai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GenAIModelMatcherTest {

    private GenAIModelMatcher matcher;

    @BeforeEach
    void setUp() {
        GenAIPricingConfig config = new GenAIPricingConfig();

        // OpenAI provider
        GenAIPricingConfig.Provider openai = new GenAIPricingConfig.Provider();
        openai.setProvider("openai");
        openai.setPrefixMatch(Collections.singletonList("gpt"));

        GenAIPricingConfig.Model gpt4o = new GenAIPricingConfig.Model();
        gpt4o.setName("gpt-4o");
        gpt4o.setInputEstimatedCostPerM(2.5);
        gpt4o.setOutputEstimatedCostPerM(10.0);

        GenAIPricingConfig.Model gpt4oMini = new GenAIPricingConfig.Model();
        gpt4oMini.setName("gpt-4o-mini");
        gpt4oMini.setInputEstimatedCostPerM(0.15);
        gpt4oMini.setOutputEstimatedCostPerM(0.6);

        openai.setModels(Arrays.asList(gpt4o, gpt4oMini));

        // Anthropic provider with aliases
        GenAIPricingConfig.Provider anthropic = new GenAIPricingConfig.Provider();
        anthropic.setProvider("anthropic");
        anthropic.setPrefixMatch(Collections.singletonList("claude"));

        GenAIPricingConfig.Model sonnet4 = new GenAIPricingConfig.Model();
        sonnet4.setName("claude-4-sonnet");
        sonnet4.setAliases(Collections.singletonList("claude-sonnet-4"));
        sonnet4.setInputEstimatedCostPerM(3.0);
        sonnet4.setOutputEstimatedCostPerM(15.0);

        GenAIPricingConfig.Model sonnet45 = new GenAIPricingConfig.Model();
        sonnet45.setName("claude-4.5-sonnet");
        sonnet45.setAliases(Collections.singletonList("claude-sonnet-4-5"));
        sonnet45.setInputEstimatedCostPerM(3.0);
        sonnet45.setOutputEstimatedCostPerM(15.0);

        GenAIPricingConfig.Model haiku35 = new GenAIPricingConfig.Model();
        haiku35.setName("claude-3.5-haiku");
        haiku35.setAliases(Collections.singletonList("claude-haiku-3-5"));
        haiku35.setInputEstimatedCostPerM(0.8);
        haiku35.setOutputEstimatedCostPerM(4.0);

        anthropic.setModels(Arrays.asList(sonnet4, sonnet45, haiku35));

        // DeepSeek provider (exact match, no aliases)
        GenAIPricingConfig.Provider deepseek = new GenAIPricingConfig.Provider();
        deepseek.setProvider("deepseek");
        deepseek.setPrefixMatch(Collections.singletonList("deepseek"));

        GenAIPricingConfig.Model dsChat = new GenAIPricingConfig.Model();
        dsChat.setName("deepseek-chat");
        dsChat.setInputEstimatedCostPerM(0.28);
        dsChat.setOutputEstimatedCostPerM(0.42);

        deepseek.setModels(Collections.singletonList(dsChat));

        config.setProviders(Arrays.asList(openai, anthropic, deepseek));
        matcher = GenAIModelMatcher.build(config);
    }

    @Test
    void testProviderPrefixMatch() {
        assertEquals("openai", matcher.match("gpt-4o").getProvider());
        assertEquals("openai", matcher.match("gpt-4o-2024-08-06").getProvider());
        assertEquals("anthropic", matcher.match("claude-sonnet-4-20250514").getProvider());
        assertEquals("anthropic", matcher.match("claude-4-sonnet").getProvider());
        assertEquals("deepseek", matcher.match("deepseek-chat").getProvider());
        assertEquals("unknown", matcher.match("totally-unknown").getProvider());
    }

    @Test
    void testModelPrefixMatch() {
        // OpenAI: date-stamped response model matches config entry via prefix
        GenAIModelMatcher.MatchResult result = matcher.match("gpt-4o-2024-08-06");
        assertNotNull(result.getModelConfig());
        assertEquals("gpt-4o", result.getModelConfig().getName());
        assertEquals(2.5, result.getModelConfig().getInputEstimatedCostPerM(), 0.001);

        // Longer prefix wins: gpt-4o-mini matches gpt-4o-mini, not gpt-4o
        result = matcher.match("gpt-4o-mini-2024-07-18");
        assertNotNull(result.getModelConfig());
        assertEquals("gpt-4o-mini", result.getModelConfig().getName());
        assertEquals(0.15, result.getModelConfig().getInputEstimatedCostPerM(), 0.001);
    }

    @Test
    void testAliasMatch() {
        // Anthropic API returns claude-sonnet-4-20250514, alias claude-sonnet-4 matches
        GenAIModelMatcher.MatchResult result = matcher.match("claude-sonnet-4-20250514");
        assertNotNull(result.getModelConfig());
        assertEquals("claude-4-sonnet", result.getModelConfig().getName());
        assertEquals(3.0, result.getModelConfig().getInputEstimatedCostPerM(), 0.001);

        // claude-sonnet-4-5 alias matches claude-4.5-sonnet (longer prefix wins over claude-sonnet-4)
        result = matcher.match("claude-sonnet-4-5-20250620");
        assertNotNull(result.getModelConfig());
        assertEquals("claude-4.5-sonnet", result.getModelConfig().getName());

        // claude-haiku-3-5 alias matches claude-3.5-haiku
        result = matcher.match("claude-haiku-3-5-20241022");
        assertNotNull(result.getModelConfig());
        assertEquals("claude-3.5-haiku", result.getModelConfig().getName());
    }

    @Test
    void testOriginalNameStillWorks() {
        // Client agent path uses original config names
        GenAIModelMatcher.MatchResult result = matcher.match("claude-4-sonnet");
        assertNotNull(result.getModelConfig());
        assertEquals("claude-4-sonnet", result.getModelConfig().getName());

        result = matcher.match("gpt-4o");
        assertNotNull(result.getModelConfig());
        assertEquals("gpt-4o", result.getModelConfig().getName());
    }

    @Test
    void testExactMatchWithNoSuffix() {
        GenAIModelMatcher.MatchResult result = matcher.match("deepseek-chat");
        assertNotNull(result.getModelConfig());
        assertEquals("deepseek-chat", result.getModelConfig().getName());
        assertEquals(0.28, result.getModelConfig().getInputEstimatedCostPerM(), 0.001);
    }

    @Test
    void testUnknownModel() {
        GenAIModelMatcher.MatchResult result = matcher.match("totally-unknown-model");
        assertNull(result.getModelConfig());
        assertEquals("unknown", result.getProvider());
    }

    @Test
    void testNullAndEmpty() {
        assertEquals("unknown", matcher.match(null).getProvider());
        assertNull(matcher.match(null).getModelConfig());
        assertEquals("unknown", matcher.match("").getProvider());
        assertNull(matcher.match("").getModelConfig());
    }
}
