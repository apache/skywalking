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

package org.apache.skywalking.oap.analyzer.genai.matcher;

import lombok.Data;
import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenAIProviderPrefixMatcher {
    private static final String UNKNOWN = "unknown";
    private final TrieNode root;
    private final Map<String, GenAIConfig.Model> modelMap;

    private static final MatchResult UNKNOWN_RESULT = new MatchResult(UNKNOWN, null);

    private GenAIProviderPrefixMatcher(TrieNode root, Map<String, GenAIConfig.Model> modelMap) {
        this.root = root;
        this.modelMap = modelMap;
    }

    @Data
    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private String providerName;
    }

    @Data
    public static class MatchResult {
        private final String provider;
        private final GenAIConfig.Model modelConfig;

        public MatchResult(String provider, GenAIConfig.Model modelConfig) {
            this.provider = provider;
            this.modelConfig = modelConfig;
        }

        public String getProvider() {
            return provider;
        }

        public GenAIConfig.Model getModelConfig() {
            return modelConfig;
        }
    }

    public static GenAIProviderPrefixMatcher build(GenAIConfig config) {
        TrieNode root = new TrieNode();
        Map<String, GenAIConfig.Model> modelMap = new HashMap<>();

        for (GenAIConfig.Provider p : config.getProviders()) {
            List<String> prefixes = p.getPrefixMatch();
            if (prefixes != null) {
                for (String prefix : prefixes) {
                    if (prefix == null || prefix.isEmpty()) continue;

                    TrieNode current = root;
                    for (int i = 0; i < prefix.length(); i++) {
                        char c = prefix.charAt(i);
                        current = current.children.computeIfAbsent(c, k -> new TrieNode());
                    }
                    current.providerName = p.getProvider();
                }
            }

            List<GenAIConfig.Model> models = p.getModels();
            if (models != null) {
                for (GenAIConfig.Model model : models) {
                    if (model.getName() != null) {
                        modelMap.put(model.getName(), model);
                    }
                }
            }
        }

        return new GenAIProviderPrefixMatcher(root, modelMap);
    }

    public MatchResult match(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return UNKNOWN_RESULT;
        }

        TrieNode current = root;
        String matchedProvider = null;

        for (int i = 0; i < modelName.length(); i++) {
            current = current.children.get(modelName.charAt(i));
            if (current == null) break;
            if (current.providerName != null) {
                matchedProvider = current.providerName;
            }
        }

        String provider = matchedProvider != null ? matchedProvider : UNKNOWN;
        GenAIConfig.Model modelConfig = modelMap.get(modelName);

        return new MatchResult(provider, modelConfig);
    }
}
