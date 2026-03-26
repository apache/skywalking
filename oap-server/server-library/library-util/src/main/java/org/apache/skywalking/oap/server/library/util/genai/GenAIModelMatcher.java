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

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trie-based matcher for GenAI provider and model name resolution.
 * Uses longest-prefix matching for both provider identification and model cost lookup.
 * Supports model aliases so a single pricing entry can match multiple naming conventions
 * (e.g., "claude-4-sonnet" from client agents and "claude-sonnet-4" from Anthropic API).
 */
public class GenAIModelMatcher {
    private static final String UNKNOWN = "unknown";
    private final TrieNode providerTrie;
    private final TrieNode modelTrie;

    private static volatile GenAIModelMatcher INSTANCE;

    private static final MatchResult UNKNOWN_RESULT = new MatchResult(UNKNOWN, null);

    private GenAIModelMatcher(TrieNode providerTrie, TrieNode modelTrie) {
        this.providerTrie = providerTrie;
        this.modelTrie = modelTrie;
    }

    /**
     * Get the singleton instance. Lazily initialized from {@code gen-ai-config.yml}
     * on first access.
     */
    public static GenAIModelMatcher getInstance() {
        if (INSTANCE == null) {
            synchronized (GenAIModelMatcher.class) {
                if (INSTANCE == null) {
                    try {
                        INSTANCE = build(GenAIPricingConfigLoader.load());
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(
                            "Failed to load gen-ai-config.yml for GenAIModelMatcher", e);
                    }
                }
            }
        }
        return INSTANCE;
    }

    @Data
    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private String providerName;
        private GenAIPricingConfig.Model modelConfig;
    }

    @Data
    public static class MatchResult {
        private final String provider;
        private final GenAIPricingConfig.Model modelConfig;

        public MatchResult(String provider, GenAIPricingConfig.Model modelConfig) {
            this.provider = provider;
            this.modelConfig = modelConfig;
        }

        public String getProvider() {
            return provider;
        }

        public GenAIPricingConfig.Model getModelConfig() {
            return modelConfig;
        }
    }

    public static GenAIModelMatcher build(GenAIPricingConfig config) {
        final TrieNode providerTrie = new TrieNode();
        final TrieNode modelTrie = new TrieNode();

        for (GenAIPricingConfig.Provider p : config.getProviders()) {
            List<String> prefixes = p.getPrefixMatch();
            if (prefixes != null) {
                for (String prefix : prefixes) {
                    if (prefix == null || prefix.isEmpty()) continue;
                    insertProvider(providerTrie, prefix, p.getProvider());
                }
            }

            List<GenAIPricingConfig.Model> models = p.getModels();
            if (models != null) {
                for (GenAIPricingConfig.Model model : models) {
                    if (model.getName() != null) {
                        insertModel(modelTrie, model.getName(), model);
                    }
                    List<String> aliases = model.getAliases();
                    if (aliases != null) {
                        for (String alias : aliases) {
                            if (alias != null && !alias.isEmpty()) {
                                insertModel(modelTrie, alias, model);
                            }
                        }
                    }
                }
            }
        }

        return new GenAIModelMatcher(providerTrie, modelTrie);
    }

    private static void insertProvider(TrieNode root, String key, String providerName) {
        TrieNode current = root;
        for (int i = 0; i < key.length(); i++) {
            current = current.children.computeIfAbsent(key.charAt(i), k -> new TrieNode());
        }
        current.providerName = providerName;
    }

    private static void insertModel(TrieNode root, String key, GenAIPricingConfig.Model model) {
        TrieNode current = root;
        for (int i = 0; i < key.length(); i++) {
            current = current.children.computeIfAbsent(key.charAt(i), k -> new TrieNode());
        }
        current.modelConfig = model;
    }

    /**
     * Match a model name against provider prefixes and model name/alias prefixes.
     * Uses longest-prefix match for both provider and model resolution.
     *
     * @param modelName the model name to match (e.g., "gpt-4o-2024-08-06", "claude-sonnet-4-20250514")
     * @return match result containing the provider name and model pricing config (if found)
     */
    public MatchResult match(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return UNKNOWN_RESULT;
        }

        String matchedProvider = longestPrefixProvider(modelName);
        GenAIPricingConfig.Model matchedModel = longestPrefixModel(modelName);

        String provider = matchedProvider != null ? matchedProvider : UNKNOWN;
        return new MatchResult(provider, matchedModel);
    }

    private String longestPrefixProvider(String input) {
        TrieNode current = providerTrie;
        String matched = null;
        for (int i = 0; i < input.length(); i++) {
            current = current.children.get(input.charAt(i));
            if (current == null) break;
            if (current.providerName != null) {
                matched = current.providerName;
            }
        }
        return matched;
    }

    private GenAIPricingConfig.Model longestPrefixModel(String input) {
        TrieNode current = modelTrie;
        GenAIPricingConfig.Model matched = null;
        for (int i = 0; i < input.length(); i++) {
            current = current.children.get(input.charAt(i));
            if (current == null) break;
            if (current.modelConfig != null) {
                matched = current.modelConfig;
            }
        }
        return matched;
    }
}
