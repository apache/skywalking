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

import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfig;
import org.apache.skywalking.oap.server.library.util.genai.GenAIModelMatcher;
import org.apache.skywalking.oap.server.library.util.genai.GenAIPricingConfig;

/**
 * Thin wrapper over the singleton {@link GenAIModelMatcher} that converts
 * results to module-specific {@link GenAIConfig.Model} types.
 */
public class GenAIProviderPrefixMatcher {

    private GenAIProviderPrefixMatcher() {
    }

    public static GenAIProviderPrefixMatcher build() {
        // Ensure singleton is initialized (lazy init from gen-ai-config.yml)
        GenAIModelMatcher.getInstance();
        return new GenAIProviderPrefixMatcher();
    }

    public MatchResult match(String modelName) {
        GenAIModelMatcher.MatchResult result = GenAIModelMatcher.getInstance().match(modelName);
        GenAIConfig.Model modelConfig = toModuleModel(result.getModelConfig());
        return new MatchResult(result.getProvider(), modelConfig);
    }

    private static GenAIConfig.Model toModuleModel(GenAIPricingConfig.Model pm) {
        if (pm == null) {
            return null;
        }
        GenAIConfig.Model m = new GenAIConfig.Model();
        m.setName(pm.getName());
        m.setAliases(pm.getAliases());
        m.setInputEstimatedCostPerM(pm.getInputEstimatedCostPerM());
        m.setOutputEstimatedCostPerM(pm.getOutputEstimatedCostPerM());
        return m;
    }

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
}
