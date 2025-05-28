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
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
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
            if (c != null) {
                final Properties properties = parseConfig(c);
                configProperties.put(part, properties);
            }
        });

        try {
            copyProperties(
                config.getGlobal(), configProperties.get("global"), moduleProvider.getModule().name(),
                moduleProvider.name()
            );
            Properties groups = configProperties.get("groups");
            Properties recordsNormal = (Properties) groups.get("recordsNormal");
            copyProperties(
                config.getRecordsNormal(), recordsNormal,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(recordsNormal, config.getRecordsNormal());

            Properties log = (Properties) groups.get(BanyanDB.StreamGroup.RECORDS_LOG.getName());
            copyProperties(
                    config.getRecordsLog(), log,
                    moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(log, config.getRecordsLog());

            Properties segment = (Properties) groups.get(BanyanDB.StreamGroup.RECORDS_TRACE.getName());
            copyProperties(
                config.getRecordsTrace(), segment,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(segment, config.getRecordsTrace());

            Properties zipkinSpan = (Properties) groups.get(BanyanDB.StreamGroup.RECORDS_ZIPKIN_TRACE.getName());
            copyProperties(
                config.getRecordsZipkinTrace(), zipkinSpan,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(zipkinSpan, config.getRecordsZipkinTrace());

            Properties browserErrorLog = (Properties) groups.get(BanyanDB.StreamGroup.RECORDS_BROWSER_ERROR_LOG.getName());
            copyProperties(
                config.getRecordsBrowserErrorLog(), browserErrorLog,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(browserErrorLog, config.getRecordsBrowserErrorLog());

            Properties metricsMin = (Properties) groups.get(BanyanDB.MeasureGroup.METRICS_MIN.getName());
            copyProperties(
                config.getMetricsMin(), metricsMin,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(metricsMin, config.getMetricsMin());

            Properties metricsHour = (Properties) groups.get(BanyanDB.MeasureGroup.METRICS_HOUR.getName());
            copyProperties(
                config.getMetricsHour(), metricsHour,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(metricsHour, config.getMetricsHour());

            Properties metricsDay = (Properties) groups.get(BanyanDB.MeasureGroup.METRICS_DAY.getName());
            copyProperties(
                config.getMetricsDay(), metricsDay,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(metricsDay, config.getMetricsDay());

            copyProperties(
                config.getMetadata(), (Properties) groups.get(BanyanDB.MeasureGroup.METADATA.getName()),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyProperties(
                config.getProperty(), (Properties) groups.get(BanyanDB.PropertyGroup.PROPERTY.getName()),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
        } catch (IllegalAccessException e) {
            throw new ModuleStartException("Failed to load BanyanDB configuration.", e);
        }
        return config;
    }

    private Properties parseConfig(final Map<String, ?> config) {
        final Properties properties = new Properties();
        for (Map.Entry<String, ?> entry : config.entrySet()) {
            String propertyName = entry.getKey();
            Object propertyValue = entry.getValue();
            if (propertyValue instanceof Map) {
                Properties subProperties = parseConfig((Map<String, ?>) propertyValue);
                properties.put(propertyName, subProperties);
            } else {
                properties.put(propertyName, propertyValue);
                replacePropertyAndLog(
                    propertyName, propertyValue, properties, this.moduleProvider.name(), yaml);
            }
        }
        return properties;
    }

    private void copyStages(final Properties group,
                            final BanyanDBStorageConfig.GroupResource groupResource) throws IllegalAccessException {
        if (groupResource.isEnableWarmStage()) {
            BanyanDBStorageConfig.Stage warm = new BanyanDBStorageConfig.Stage();
            warm.setName(BanyanDBStorageConfig.StageName.warm);
            copyProperties(
                warm, (Properties) group.get(BanyanDBStorageConfig.StageName.warm.name()),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            groupResource.getAdditionalLifecycleStages().add(warm);
            groupResource.getDefaultQueryStages().add(BanyanDBStorageConfig.StageName.warm.name());
        }

        if (groupResource.isEnableColdStage()) {
            BanyanDBStorageConfig.Stage cold = new BanyanDBStorageConfig.Stage();
            cold.setName(BanyanDBStorageConfig.StageName.cold);
            cold.setClose(true);
            copyProperties(
                cold, (Properties) group.get(BanyanDBStorageConfig.StageName.cold.name()),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            groupResource.getAdditionalLifecycleStages().add(cold);
        }
    }
}
