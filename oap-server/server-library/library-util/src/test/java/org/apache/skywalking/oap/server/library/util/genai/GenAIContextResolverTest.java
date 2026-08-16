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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GenAIContextResolverTest {
    private GenAIModelMatcher matcher;

    @BeforeEach
    void setUp() {
        final GenAIPricingConfig.Provider openai = new GenAIPricingConfig.Provider();
        openai.setProvider("openai");
        openai.setPrefixMatch(List.of("gpt"));

        final GenAIPricingConfig.Provider anthropic = new GenAIPricingConfig.Provider();
        anthropic.setProvider("anthropic");
        anthropic.setPrefixMatch(List.of("claude"));

        final GenAIPricingConfig config = new GenAIPricingConfig();
        config.setProviders(List.of(openai, anthropic));
        matcher = GenAIModelMatcher.build(config);
    }

    @Test
    void shouldPreferDeclaredProvider() {
        final GenAIContextResolver.Result result = resolve(Map.of(
            GenAISemanticAttributes.PROVIDER_NAME, "azure-openai-prod",
            GenAISemanticAttributes.SYSTEM_NAME, "az.ai.openai",
            GenAISemanticAttributes.RESPONSE_MODEL, "gpt-4o"
        ));

        assertEquals("azure-openai-prod", result.getProviderName());
        assertEquals("gpt-4o", result.getModelName());
    }

    @Test
    void shouldPreferModelMatchOverLegacySystem() {
        final GenAIContextResolver.Result result = resolve(Map.of(
            GenAISemanticAttributes.SYSTEM_NAME, "az.ai.openai",
            GenAISemanticAttributes.RESPONSE_MODEL, "gpt-4o"
        ));

        assertEquals("openai", result.getProviderName());
    }

    @Test
    void shouldUseLegacySystemForUnknownModel() {
        final GenAIContextResolver.Result result = resolve(Map.of(
            GenAISemanticAttributes.SYSTEM_NAME, "custom-provider",
            GenAISemanticAttributes.RESPONSE_MODEL, "private-model"
        ));

        assertEquals("custom-provider", result.getProviderName());
    }

    @Test
    void shouldReturnUnknownWhenNoProviderCanBeResolved() {
        final GenAIContextResolver.Result result = resolve(Map.of(
            GenAISemanticAttributes.RESPONSE_MODEL, "private-model"
        ));

        assertEquals("unknown", result.getProviderName());
    }

    private GenAIContextResolver.Result resolve(final Map<String, String> attributes) {
        return GenAIContextResolver.resolve(attributes, matcher);
    }
}
