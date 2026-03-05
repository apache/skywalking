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

package org.apache.skywalking.oap.server.core.status;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * The server status service provides the indicators for the current server status.
 * Notice, this should not be treated as a kind of health checker or self telemetry.
 * For more, this helps modules to be aware of current OAP server status.
 *
 * @since 9.4.0
 */
@RequiredArgsConstructor
public class ServerStatusService implements Service {
    private final ModuleManager manager;
    @Getter
    private BootingStatus bootingStatus = new BootingStatus();
    @Getter
    private ClusterStatus clusterStatus = new ClusterStatus();

    private List<ServerStatusWatcher> statusWatchers = new CopyOnWriteArrayList<>();

    private List<ApplicationConfiguration.ModuleConfiguration> configurations;

    public void bootedNow(List<ApplicationConfiguration.ModuleConfiguration> configurations, long uptime) {
        bootingStatus.setBooted(true);
        bootingStatus.setUptime(uptime);
        manager.find(TelemetryModule.NAME)
               .provider()
               .getService(MetricsCreator.class)
               .createGauge("uptime", "oap server start up time", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE)
               // Set uptime to second
               .setValue(uptime / 1000d);
        this.statusWatchers.forEach(watcher -> watcher.onServerBooted(bootingStatus));
        this.configurations = configurations;
    }

    public void rebalancedCluster(long rebalancedTime) {
        clusterStatus.setRebalancedTime(rebalancedTime);
        manager.find(TelemetryModule.NAME)
               .provider()
               .getService(MetricsCreator.class)
               .createGauge(
                   "cluster_rebalanced_time", "oap cluster rebalanced time after scale", MetricsTag.EMPTY_KEY,
                   MetricsTag.EMPTY_VALUE
               )
               .setValue(rebalancedTime / 1000d);

        this.statusWatchers.forEach(watcher -> watcher.onClusterRebalanced(clusterStatus));
    }

    public void registerWatcher(ServerStatusWatcher watcher) {
        this.statusWatchers.add(watcher);
    }

    /**
     * @return a complete list of booting configurations with effected values.
     * @since 9.7.0
     * @since 10.3.0 return ConfigList instead of String, to support raw configurations.
     */
    public ConfigList dumpBootingConfigurations(String keywords4MaskingSecretsOfConfig) {
        ConfigList configList = new ConfigList();
        if (configurations == null || configurations.isEmpty()) {
            return configList;
        }
        final String[] keywords = keywords4MaskingSecretsOfConfig.split(",");
        for (ApplicationConfiguration.ModuleConfiguration configuration : configurations) {
            final String moduleName = configuration.getModuleName();
            if (configuration.getProviders().size() == 1) {
                configList.put(moduleName + ".provider", configuration.getProviders().keySet().iterator().next());
            }
            configuration.getProviders().forEach(
                (providerName, providerConfiguration) ->
                    providerConfiguration.getProperties().forEach(
                        (key, value) -> {
                            if (value instanceof Properties) {
                                Properties properties = (Properties) value;
                                properties.forEach((k, v) -> {
                                    String configKey = moduleName + "." + providerName + "." + key + "." + k;
                                    String configValue = maskConfigValue(configKey, v.toString(), keywords);
                                    configList.put(configKey, configValue);
                                });
                            } else {
                                String configKey = moduleName + "." + providerName + "." + key;
                                String configValue = maskConfigValue(key.toString(), value.toString(), keywords);
                                configList.put(configKey, configValue);
                            }
                        }
                    )
            );
        }
        return configList;
    }

    /**
     * Mask the configuration value if the key contains any masking keyword.
     *
     * @param configKey   the configuration key to check
     * @param configValue the configuration value to mask
     * @param keywords    the keywords for masking secrets
     * @return masked value "******" if key matches any keyword, otherwise return the original value
     */
    private String maskConfigValue(String configKey, String configValue, String[] keywords) {
        String lowerConfigKey = configKey.toLowerCase();
        for (String keyword : keywords) {
            if (lowerConfigKey.contains(keyword.toLowerCase())) {
                return "******";
            }
        }
        return configValue;
    }

    public static class ConfigList extends HashMap<String, String> {
        @Override
        public String toString() {
            StringBuilder configList = new StringBuilder();
            for (final var entry : this.entrySet()) {
                configList.append(entry.getKey())
                          .append("=")
                          .append(entry.getValue())
                          .append("\n");
            }
            return configList.toString();
        }
    }
}
