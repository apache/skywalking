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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import static org.apache.skywalking.oap.server.library.util.YamlConfigLoaderUtils.copyProperties;
import static org.apache.skywalking.oap.server.library.util.YamlConfigLoaderUtils.replacePropertyAndLog;

@Slf4j
public class BanyanDBConfigLoader {
    private final ModuleProvider moduleProvider;
    private final Yaml yaml;

    public BanyanDBConfigLoader(final ModuleProvider moduleProvider) {
        this.moduleProvider = moduleProvider;
        this.yaml = new Yaml();
    }

    public BanyanDBStorageConfig loadConfig() throws ModuleStartException {
        BanyanDBStorageConfig config = new BanyanDBStorageConfig();
        Reader applicationReader = null;
        try {
            applicationReader = ResourceUtils.read("bydb.yml");
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Cannot find the BanyanDB configuration file [bydb.yml].", e);
        }
        Map<String, Map<String, ?>> configMap = yaml.loadAs(applicationReader, Map.class);
        if (configMap == null) {
            return config;
        }

        Map<String, Properties> configProperties = new HashMap<>();
        configMap.forEach((part, c) -> {
            final Properties properties = new Properties();
            if (c != null) {
                for (Map.Entry<String, ?> entry : c.entrySet()) {
                    String propertyName = entry.getKey();
                    Object propertyValue = entry.getValue();
                    if (propertyValue instanceof Map) {
                        Properties subProperties = new Properties();
                        for (Map.Entry<String, ?> e : ((Map<String, ?>) propertyValue).entrySet()) {
                            String key = e.getKey();
                            Object value = e.getValue();
                            subProperties.put(key, value);
                            replacePropertyAndLog(key, value, subProperties, this.moduleProvider.name(), yaml);
                        }
                        properties.put(propertyName, subProperties);
                    } else {
                        properties.put(propertyName, propertyValue);
                        replacePropertyAndLog(
                            propertyName, propertyValue, properties, this.moduleProvider.name(), yaml);
                    }
                }
                configProperties.put(part, properties);
            }
        });

        try {
            copyProperties(
                config.getGlobal(), configProperties.get("global"), moduleProvider.getModule().name(),
                moduleProvider.name()
            );
            Properties groups = configProperties.get("groups");
            copyProperties(
                config.getRecordsNormal(), (Properties) groups.get("recordsNormal"),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyProperties(
                config.getRecordsSuper(), (Properties) groups.get("recordsSuper"),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyProperties(
                config.getMetricsMin(), (Properties) groups.get("metricsMin"),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyProperties(
                config.getMetricsHour(), (Properties) groups.get("metricsHour"),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyProperties(
                config.getMetricsDay(), (Properties) groups.get("metricsDay"),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyProperties(
                config.getMetadata(), (Properties) groups.get("metadata"),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyProperties(
                config.getProperty(), (Properties) groups.get("property"),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
        } catch (IllegalAccessException e) {
            throw new ModuleStartException("Failed to load BanyanDB configuration.", e);
        }
        return config;
    }
}
