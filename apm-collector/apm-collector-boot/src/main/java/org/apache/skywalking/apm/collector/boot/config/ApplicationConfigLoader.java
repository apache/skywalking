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


package org.apache.skywalking.apm.collector.boot.config;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;
import org.apache.skywalking.apm.collector.core.module.ApplicationConfiguration;
import org.apache.skywalking.apm.collector.core.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * @author peng-yongsheng
 */
public class ApplicationConfigLoader implements ConfigLoader<ApplicationConfiguration> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationConfigLoader.class);

    private final Yaml yaml = new Yaml();

    @Override public ApplicationConfiguration load() throws ConfigFileNotFoundException {
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        this.loadConfig(configuration);
        this.loadDefaultConfig(configuration);
        return configuration;
    }

    private void loadConfig(ApplicationConfiguration configuration) throws ConfigFileNotFoundException {
        try {
            Reader applicationReader = ResourceUtils.read("application.yml");
            Map<String, Map<String, Map<String, ?>>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
            moduleConfig.forEach((moduleName, providerConfig) -> {
                if (providerConfig.size() > 0) {
                    logger.info("Get a module define from application.yml, module name: {}", moduleName);
                    ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.addModule(moduleName);
                    providerConfig.forEach((name, propertiesConfig) -> {
                        logger.info("Get a provider define belong to {} module, provider name: {}", moduleName, name);
                        Properties properties = new Properties();
                        if (propertiesConfig != null) {
                            propertiesConfig.forEach((key, value) -> {
                                properties.put(key, value);
                                logger.info("The property with key: {}, value: {}, in {} provider", key, value, name);
                            });
                        }
                        moduleConfiguration.addProviderConfiguration(name, properties);
                    });
                } else {
                    logger.warn("Get a module define from application.yml, but no provider define, use default, module name: {}", moduleName);
                }
            });
        } catch (FileNotFoundException e) {
            throw new ConfigFileNotFoundException(e.getMessage(), e);
        }
    }

    private void loadDefaultConfig(ApplicationConfiguration configuration) throws ConfigFileNotFoundException {
        try {
            Reader applicationReader = ResourceUtils.read("application-default.yml");
            Map<String, Map<String, Map<String, ?>>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
            moduleConfig.forEach((moduleName, providerConfig) -> {
                if (!configuration.has(moduleName)) {
                    logger.warn("The {} module did't define in application.yml, use default", moduleName);
                    ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.addModule(moduleName);
                    providerConfig.forEach((name, propertiesConfig) -> {
                        Properties properties = new Properties();
                        if (propertiesConfig != null) {
                            propertiesConfig.forEach(properties::put);
                        }
                        moduleConfiguration.addProviderConfiguration(name, properties);
                    });
                }
            });
        } catch (FileNotFoundException e) {
            throw new ConfigFileNotFoundException(e.getMessage(), e);
        }
    }
}
