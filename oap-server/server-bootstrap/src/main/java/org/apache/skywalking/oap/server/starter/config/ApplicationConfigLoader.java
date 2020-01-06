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

package org.apache.skywalking.oap.server.starter.config;

import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;

/**
 * Initialize collector settings with following sources. Use application.yml as primary setting, and fix missing setting
 * by default settings in application-default.yml.
 *
 * At last, override setting by system.properties and system.envs if the key matches moduleName.provideName.settingKey.
 *
 * @author peng-yongsheng, wusheng
 */
public class ApplicationConfigLoader implements ConfigLoader<ApplicationConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfigLoader.class);

    private final Yaml yaml = new Yaml();

    @Override public ApplicationConfiguration load() throws ConfigFileNotFoundException {
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        this.loadConfig(configuration);
        this.overrideConfigBySystemEnv(configuration);
        return configuration;
    }

    @SuppressWarnings("unchecked")
    private void loadConfig(ApplicationConfiguration configuration) throws ConfigFileNotFoundException {
        try {
            Reader applicationReader = ResourceUtils.read("application.yml");
            Map<String, Map<String, Map<String, ?>>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
            if (CollectionUtils.isNotEmpty(moduleConfig)) {
                moduleConfig.forEach((moduleName, providerConfig) -> {
                    if (providerConfig.size() > 0) {
                        logger.info("Get a module define from application.yml, module name: {}", moduleName);
                        ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.addModule(moduleName);
                        providerConfig.forEach((providerName, propertiesConfig) -> {
                            logger.info("Get a provider define belong to {} module, provider name: {}", moduleName, providerName);
                            Properties properties = new Properties();
                            if (propertiesConfig != null) {
                                propertiesConfig.forEach((propertyName, propertyValue) -> {
                                    if (propertyValue instanceof Map) {
                                        Properties subProperties = new Properties();
                                        ((Map) propertyValue).forEach((key, value) -> {
                                            subProperties.put(key, value);
                                            replacePropertyAndLog(key, value, subProperties, providerName);
                                        });
                                        properties.put(propertyName, subProperties);
                                    } else {
                                        properties.put(propertyName, propertyValue);
                                        replacePropertyAndLog(propertyName, propertyValue, properties, providerName);
                                    }
                                });
                            }
                            moduleConfiguration.addProviderConfiguration(providerName, properties);
                        });
                    } else {
                        logger.warn("Get a module define from application.yml, but no provider define, use default, module name: {}", moduleName);
                    }
                });
            }
        } catch (FileNotFoundException e) {
            throw new ConfigFileNotFoundException(e.getMessage(), e);
        }
    }
    
    private void replacePropertyAndLog(final Object propertyName, final Object propertyValue, final Properties target, final Object providerName) {
        final Object replaceValue = yaml.load(PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(propertyValue + "", target));
        if (replaceValue != null) {
            target.replace(propertyName, replaceValue);
            logger.info("The property with key: {}, value: {}, in {} provider", propertyName, replaceValue.toString(), providerName);
        }
    }

    private void overrideConfigBySystemEnv(ApplicationConfiguration configuration) {
        for (Map.Entry<Object, Object> prop : System.getProperties().entrySet()) {
            overrideModuleSettings(configuration, prop.getKey().toString(), prop.getValue().toString());
        }
    }

    private void overrideModuleSettings(ApplicationConfiguration configuration, String key, String value) {
        int moduleAndConfigSeparator = key.indexOf('.');
        if (moduleAndConfigSeparator <= 0) {
            return;
        }
        String moduleName = key.substring(0, moduleAndConfigSeparator);
        String providerSettingSubKey = key.substring(moduleAndConfigSeparator + 1);
        ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.getModuleConfiguration(moduleName);
        if (moduleConfiguration == null) {
            return;
        }
        int providerAndConfigSeparator = providerSettingSubKey.indexOf('.');
        if (providerAndConfigSeparator <= 0) {
            return;
        }
        String providerName = providerSettingSubKey.substring(0, providerAndConfigSeparator);
        String settingKey = providerSettingSubKey.substring(providerAndConfigSeparator + 1);
        if (!moduleConfiguration.has(providerName)) {
            return;
        }
        Properties providerSettings = moduleConfiguration.getProviderConfiguration(providerName);
        if (!providerSettings.containsKey(settingKey)) {
            return;
        }
        Object originValue = providerSettings.get(settingKey);
        Class<?> type = originValue.getClass();
        if (type.equals(int.class) || type.equals(Integer.class))
            providerSettings.put(settingKey, Integer.valueOf(value));
        else if (type.equals(String.class))
            providerSettings.put(settingKey, value);
        else if (type.equals(long.class) || type.equals(Long.class))
            providerSettings.put(settingKey, Long.valueOf(value));
        else if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            providerSettings.put(settingKey, Boolean.valueOf(value));
        } else {
            return;
        }

        logger.info("The setting has been override by key: {}, value: {}, in {} provider of {} module through {}",
            settingKey, value, providerName, moduleName, "System.properties");
    }
}
