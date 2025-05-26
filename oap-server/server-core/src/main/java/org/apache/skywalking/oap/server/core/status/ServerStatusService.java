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

import com.google.gson.Gson;
import io.vavr.Tuple2;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
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
    private final CoreModuleConfig moduleConfig;
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
                configList.add(moduleName + ".provider", configuration.getProviders().keySet().iterator().next());
            }
            configuration.getProviders().forEach(
                (providerName, providerConfiguration) ->
                    providerConfiguration.getProperties().forEach(
                        (key, value) -> {
                            for (final String keyword : keywords) {
                                if (key.toString().toLowerCase().contains(keyword.toLowerCase())) {
                                    value = "******";
                                }
                            }
                            configList.add(moduleName + "." + providerName + "." + key, value.toString());
                        }
                    )
            );
        }
        return configList;
    }

    public static class ConfigList {
        private final static Gson GSON = new Gson();
        private List<Tuple2> configurations = new ArrayList<>(200);

        public void add(String key, String value) {
            configurations.add(new Tuple2<>(key, value));
        }

        @Override
        public String toString() {
            StringBuilder configList = new StringBuilder();
            for (Tuple2 tuple : configurations) {
                configList.append(tuple._1)
                          .append("=")
                          .append(tuple._2)
                          .append("\n");
            }
            return configList.toString();
        }

        public String toJsonString() {
            return GSON.toJson(configurations.stream()
                                             .collect(
                                                 java.util.stream.Collectors.toMap(
                                                     tuple -> tuple._1.toString(),
                                                     tuple -> tuple._2.toString()
                                                 )));
        }
    }
}
