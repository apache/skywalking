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

package org.apache.skywalking.oap.server.recevier.configuration.discovery;

import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Used to parse the configuration of the character type to {@link ConfigurationDiscoveryRules}
 */
@Slf4j
public class ConfigurationDiscoveryRulesReader {
    private Map yamlData;

    public ConfigurationDiscoveryRulesReader(InputStream inputStream) {
        Yaml yaml = new Yaml(new SafeConstructor());
        yamlData = (Map) yaml.load(inputStream);
    }

    public ConfigurationDiscoveryRulesReader(Reader io) {
        Yaml yaml = new Yaml(new SafeConstructor());
        yamlData = (Map) yaml.load(io);
    }

    public ConfigurationDiscoveryRules readRules() {
        ConfigurationDiscoveryRules configurationDiscoveryRules = new ConfigurationDiscoveryRules();
        try {
            if (Objects.nonNull(yamlData)) {
                Map rulesData = (Map) yamlData.get("configurations");
                if (rulesData != null) {
                    rulesData.forEach((k, v) -> {
                        Map map = (Map) v;
                        Map<String, String> config = new HashMap<>(map.size());
                        map.forEach((key, value) -> {
                            config.put(key.toString(), value.toString());
                        });

                        ServiceConfiguration serviceConfiguration = new ServiceConfiguration((String) k, config);
                        configurationDiscoveryRules.getRules().put(
                            serviceConfiguration.getService(), serviceConfiguration);
                    });
                }
            }
        } catch (Exception e) {
            log.error("Read ConfigurationDiscovery rules error.", e);
        }
        return configurationDiscoveryRules;
    }
}
