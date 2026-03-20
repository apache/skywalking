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
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

public class GenAIConfigLoader {

    private final GenAIConfig config;

    public GenAIConfigLoader(GenAIConfig config) {
        this.config = config;
    }

    public GenAIConfig loadConfig() throws ModuleStartException {
        Map<String, List<Map<String, Object>>> configMap;
        try (Reader applicationReader = ResourceUtils.read("gen-ai-config.yml")) {
            Yaml yaml = new Yaml();
            configMap = yaml.loadAs(applicationReader, Map.class);
        } catch (FileNotFoundException e) {
            throw new ModuleStartException(
                    "Cannot find the GenAI configuration file [gen-ai-config.yml].", e);
        } catch (IOException e) {
            throw new ModuleStartException(
                    "Failed to read the GenAI configuration file [gen-ai-config.yml].", e);
        }

        if (configMap == null || !configMap.containsKey("providers")) {
            return config;
        }

        List<Map<String, Object>> providersConfig = configMap.get("providers");
        for (Map<String, Object> providerMap : providersConfig) {
            GenAIConfig.Provider provider = new GenAIConfig.Provider();

            Object name = providerMap.get("provider");
            if (name == null) {
                throw new ModuleStartException("Provider name is missing in [gen-ai-config.yml].");
            }
            provider.setProvider(name.toString());

            Object baseUrl = providerMap.get("base-url");
            if (baseUrl != null && StringUtil.isNotBlank(baseUrl.toString())) {
                provider.setBaseUrl(baseUrl.toString());
            }

            Object prefixMatch = providerMap.get("prefix-match");
            if (prefixMatch instanceof List) {
                provider.getPrefixMatch().addAll((List<String>) prefixMatch);
            } else if (prefixMatch != null) {
                throw new ModuleStartException("prefix-match must be a list in [gen-ai-config.yml] for provider: " + name);
            }

            // Parse specific model overrides
            Object modelsConfig = providerMap.get("models");
            if (modelsConfig instanceof List) {
                for (Object modelObj : (List<?>) modelsConfig) {
                    if (modelObj instanceof Map) {
                        Map<String, Object> modelMap = (Map<String, Object>) modelObj;
                        GenAIConfig.Model model = new GenAIConfig.Model();
                        model.setName(String.valueOf(modelMap.get("name")));
                        model.setInputCostPerM(parseCost(modelMap.get("input-cost-per-m")));
                        model.setOutputCostPerM(parseCost(modelMap.get("output-cost-per-m")));
                        provider.getModels().add(model);
                    }
                }
            }

            config.getProviders().add(provider);
        }

        return config;
    }

    private double parseCost(Object value) {
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
