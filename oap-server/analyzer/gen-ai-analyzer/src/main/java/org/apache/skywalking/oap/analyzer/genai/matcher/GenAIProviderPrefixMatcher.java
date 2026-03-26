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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Delegates to {@link GenAIModelMatcher} in library-util.
 * Converts module-specific {@link GenAIConfig} to the shared {@link GenAIPricingConfig}.
 */
public class GenAIProviderPrefixMatcher {

    private final GenAIModelMatcher delegate;

    private GenAIProviderPrefixMatcher(GenAIModelMatcher delegate) {
        this.delegate = delegate;
    }

    public static GenAIProviderPrefixMatcher build(GenAIConfig config) {
        GenAIPricingConfig pricingConfig = toPricingConfig(config);
        return new GenAIProviderPrefixMatcher(GenAIModelMatcher.build(pricingConfig));
    }

    public MatchResult match(String modelName) {
        GenAIModelMatcher.MatchResult result = delegate.match(modelName);
        GenAIConfig.Model modelConfig = toModuleModel(result.getModelConfig());
        return new MatchResult(result.getProvider(), modelConfig);
    }

    private static GenAIPricingConfig toPricingConfig(GenAIConfig config) {
        GenAIPricingConfig pricingConfig = new GenAIPricingConfig();
        pricingConfig.setProviders(
            config.getProviders().stream().map(p -> {
                GenAIPricingConfig.Provider pp = new GenAIPricingConfig.Provider();
                pp.setProvider(p.getProvider());
                pp.setPrefixMatch(p.getPrefixMatch());
                pp.setModels(
                    p.getModels().stream().map(m -> {
                        GenAIPricingConfig.Model pm = new GenAIPricingConfig.Model();
                        pm.setName(m.getName());
                        pm.setAliases(m.getAliases());
                        pm.setInputEstimatedCostPerM(m.getInputEstimatedCostPerM());
                        pm.setOutputEstimatedCostPerM(m.getOutputEstimatedCostPerM());
                        return pm;
                    }).collect(Collectors.toList())
                );
                return pp;
            }).collect(Collectors.toList())
        );
        return pricingConfig;
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
