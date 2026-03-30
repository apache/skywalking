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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GenAIPricingConfigLoaderTest {

    @Test
    void testLoadConfig() throws IOException {
        GenAIPricingConfig config = GenAIPricingConfigLoader.load();
        assertNotNull(config);
        assertFalse(config.getProviders().isEmpty());
    }

    @Test
    void testLoadedProviders() throws IOException {
        GenAIPricingConfig config = GenAIPricingConfigLoader.load();

        // Verify key providers exist
        boolean hasOpenai = false;
        boolean hasAnthropic = false;
        for (GenAIPricingConfig.Provider p : config.getProviders()) {
            if ("openai".equals(p.getProvider())) hasOpenai = true;
            if ("anthropic".equals(p.getProvider())) hasAnthropic = true;
        }
        assertFalse(!hasOpenai, "OpenAI provider should be loaded");
        assertFalse(!hasAnthropic, "Anthropic provider should be loaded");
    }

    @Test
    void testAliasesLoaded() throws IOException {
        GenAIPricingConfig config = GenAIPricingConfigLoader.load();

        // Build matcher from loaded config and verify aliases work
        GenAIModelMatcher matcher = GenAIModelMatcher.build(config);

        // Anthropic alias: claude-sonnet-4 → claude-4-sonnet config entry
        GenAIModelMatcher.MatchResult result = matcher.match("claude-sonnet-4-20250514");
        assertNotNull(result.getModelConfig());
        assertEquals("claude-4-sonnet", result.getModelConfig().getName());
        assertEquals(3.0, result.getModelConfig().getInputEstimatedCostPerM(), 0.001);

        // OpenAI prefix match: gpt-4o-2024-08-06 → gpt-4o config entry
        result = matcher.match("gpt-4o-2024-08-06");
        assertNotNull(result.getModelConfig());
        assertEquals("gpt-4o", result.getModelConfig().getName());
    }
}
