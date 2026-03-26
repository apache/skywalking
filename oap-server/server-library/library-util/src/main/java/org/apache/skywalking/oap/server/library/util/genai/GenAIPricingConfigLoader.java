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

import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

/**
 * Loads {@link GenAIPricingConfig} from gen-ai-config.yml on the classpath.
 */
public class GenAIPricingConfigLoader {

    private static final String CONFIG_FILE = "gen-ai-config.yml";

    /**
     * Load the GenAI pricing configuration from the classpath.
     *
     * @return the loaded config, never null
     * @throws IOException if the config file cannot be found or read
     * @throws IllegalArgumentException if the config file has invalid structure
     */
    public static GenAIPricingConfig load() throws IOException {
        GenAIPricingConfig config = new GenAIPricingConfig();

        Map<String, List<Map<String, Object>>> configMap;
        try (Reader reader = ResourceUtils.read(CONFIG_FILE)) {
            Yaml yaml = new Yaml();
            configMap = yaml.loadAs(reader, Map.class);
        }

        if (configMap == null || !configMap.containsKey("providers")) {
            return config;
        }

        List<Map<String, Object>> providersConfig = configMap.get("providers");
        for (Map<String, Object> providerMap : providersConfig) {
            GenAIPricingConfig.Provider provider = new GenAIPricingConfig.Provider();

            Object name = providerMap.get("provider");
            if (name == null) {
                throw new IllegalArgumentException(
                    "Provider name is missing in [" + CONFIG_FILE + "].");
            }
            provider.setProvider(name.toString());

            Object prefixMatch = providerMap.get("prefix-match");
            if (prefixMatch instanceof List) {
                provider.getPrefixMatch().addAll((List<String>) prefixMatch);
            } else if (prefixMatch != null) {
                throw new IllegalArgumentException(
                    "prefix-match must be a list in [" + CONFIG_FILE + "] for provider: " + name);
            }

            Object modelsConfig = providerMap.get("models");
            if (modelsConfig instanceof List) {
                for (Object modelObj : (List<?>) modelsConfig) {
                    if (modelObj instanceof Map) {
                        Map<String, Object> modelMap = (Map<String, Object>) modelObj;
                        GenAIPricingConfig.Model model = new GenAIPricingConfig.Model();
                        model.setName(String.valueOf(modelMap.get("name")));
                        model.setInputEstimatedCostPerM(parseCost(modelMap.get("input-estimated-cost-per-m")));
                        model.setOutputEstimatedCostPerM(parseCost(modelMap.get("output-estimated-cost-per-m")));
                        Object aliases = modelMap.get("aliases");
                        if (aliases instanceof List) {
                            model.getAliases().addAll((List<String>) aliases);
                        }
                        provider.getModels().add(model);
                    }
                }
            }

            config.getProviders().add(provider);
        }

        return config;
    }

    private static double parseCost(Object value) {
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
