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

package org.apache.skywalking.oap.analyzer.genai.config;

import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.genai.GenAIPricingConfig;
import org.apache.skywalking.oap.server.library.util.genai.GenAIPricingConfigLoader;

import java.io.IOException;

/**
 * Loads {@link GenAIConfig} by delegating to {@link GenAIPricingConfigLoader}
 * and converting to the module-specific config (adds baseUrl support).
 */
public class GenAIConfigLoader {

    private final GenAIConfig config;

    public GenAIConfigLoader(GenAIConfig config) {
        this.config = config;
    }

    public GenAIConfig loadConfig() throws ModuleStartException {
        GenAIPricingConfig pricingConfig;
        try {
            pricingConfig = GenAIPricingConfigLoader.load();
        } catch (IOException e) {
            throw new ModuleStartException(
                "Failed to load GenAI configuration file.", e);
        } catch (IllegalArgumentException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        for (GenAIPricingConfig.Provider pp : pricingConfig.getProviders()) {
            GenAIConfig.Provider provider = new GenAIConfig.Provider();
            provider.setProvider(pp.getProvider());
            provider.setPrefixMatch(pp.getPrefixMatch());

            for (GenAIPricingConfig.Model pm : pp.getModels()) {
                GenAIConfig.Model model = new GenAIConfig.Model();
                model.setName(pm.getName());
                model.setAliases(pm.getAliases());
                model.setInputEstimatedCostPerM(pm.getInputEstimatedCostPerM());
                model.setOutputEstimatedCostPerM(pm.getOutputEstimatedCostPerM());
                provider.getModels().add(model);
            }

            config.getProviders().add(provider);
        }

        return config;
    }
}
