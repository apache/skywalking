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

package org.apache.skywalking.oap.server.configuration.configmap;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.FetchingConfigWatcherRegister;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;

@Slf4j
public class ConfigmapConfigurationWatcherRegister extends FetchingConfigWatcherRegister {

    private final ConfigurationConfigmapInformer informer;

    public ConfigmapConfigurationWatcherRegister(ConfigmapConfigurationSettings settings,
                                                 ConfigurationConfigmapInformer informer) {
        super(settings.getPeriod());
        this.informer = informer;
    }

    @Override
    public Optional<ConfigTable> readConfig(Set<String> keys) {
        final ConfigTable configTable = new ConfigTable();
        Map<String, String> configMapData = informer.configMapData();
        for (final String name : keys) {
            final String value = configMapData.get(name);
            if (log.isDebugEnabled()) {
                log.debug("read config: name:{} ,value:{}", name, value);
            }
            configTable.add(new ConfigTable.ConfigItem(name, value));
        }
        return Optional.of(configTable);
    }

    @Override
    public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
        GroupConfigTable groupConfigTable = new GroupConfigTable();
        Map<String, String> configMapData = informer.configMapData();
        keys.forEach(key -> {
            GroupConfigTable.GroupConfigItems groupConfigItems = new GroupConfigTable.GroupConfigItems(key);
            groupConfigTable.addGroupConfigItems(groupConfigItems);
            configMapData.forEach((groupItemKey, itemValue) -> {
                if (groupItemKey.startsWith(key + ".")) {
                    String itemName = groupItemKey.substring(key.length() + 1);
                    groupConfigItems.add(new ConfigTable.ConfigItem(itemName, itemValue));
                }
            });
        });

        return Optional.of(groupConfigTable);
    }
}
