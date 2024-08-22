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

package org.apache.skywalking.oap.server.configuration.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.FetchingConfigWatcherRegister;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
public class NacosConfigWatcherRegister extends FetchingConfigWatcherRegister {
    private final NacosServerSettings settings;
    private final ConfigService configService;
    private final Map<String, Optional<String>> configItemKeyedByName;
    private final Map<String, Listener> listenersByKey;

    public NacosConfigWatcherRegister(NacosServerSettings settings) throws NacosException {
        super(settings.getPeriod());

        this.settings = settings;
        this.configItemKeyedByName = new ConcurrentHashMap<>();
        this.listenersByKey = new ConcurrentHashMap<>();

        final int port = this.settings.getPort();
        final String serverAddr = this.settings.getServerAddr();

        final Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr + ":" + port);
        properties.put(PropertyKeyConst.NAMESPACE, settings.getNamespace());
        if (StringUtil.isNotEmpty(settings.getContextPath())) {
            properties.put(PropertyKeyConst.CONTEXT_PATH, settings.getContextPath());
        }
        if (StringUtil.isNotEmpty(settings.getUsername())) {
            properties.put(PropertyKeyConst.USERNAME, settings.getUsername());
            properties.put(PropertyKeyConst.PASSWORD, settings.getPassword());
        } else if (StringUtil.isNotEmpty(settings.getAccessKey())) {
            properties.put(PropertyKeyConst.ACCESS_KEY, settings.getAccessKey());
            properties.put(PropertyKeyConst.SECRET_KEY, settings.getSecretKey());
        }
        this.configService = NacosFactory.createConfigService(properties);
    }

    @Override
    public Optional<ConfigTable> readConfig(Set<String> keys) {
        removeUninterestedKeys(keys);
        registerKeyListeners(keys);

        final ConfigTable table = new ConfigTable();

        for (Map.Entry<String, Optional<String>> entry : configItemKeyedByName.entrySet()) {
            final String key = entry.getKey();
            final Optional<String> value = entry.getValue();

            if (value.isPresent()) {
                table.add(new ConfigTable.ConfigItem(key, value.get()));
            } else {
                table.add(new ConfigTable.ConfigItem(key, null));
            }
        }

        return Optional.of(table);
    }

    @Override
    public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
        GroupConfigTable groupConfigTable = new GroupConfigTable();
        keys.forEach(key -> {
            GroupConfigTable.GroupConfigItems groupConfigItems = new GroupConfigTable.GroupConfigItems(key);
            groupConfigTable.addGroupConfigItems(groupConfigItems);
            String config = null;
            try {
                config = configService.getConfig(key, settings.getGroup(), 1000);
                if (StringUtil.isNotEmpty(config)) {
                    String[] itemNames = config.split("\\n|\\r\\n");
                    Arrays.stream(itemNames).map(String::trim).forEach(itemName -> {
                        String itemValue = null;
                        try {
                            itemValue = configService.getConfig(itemName, settings.getGroup(), 1000);
                        } catch (NacosException e) {
                            log.error("Failed to register Nacos listener for dataId: {}", itemName, e);
                        }
                        groupConfigItems.add(
                            new ConfigTable.ConfigItem(itemName, itemValue));
                    });
                }
            } catch (NacosException e) {
                log.error("Failed to register Nacos listener for dataId: {}", key, e);
            }
        });

        return Optional.of(groupConfigTable);
    }

    private void registerKeyListeners(final Set<String> keys) {
        final String group = settings.getGroup();

        for (final String dataId : keys) {
            if (listenersByKey.containsKey(dataId)) {
                continue;
            }
            try {
                listenersByKey.putIfAbsent(dataId, new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        onDataIdValueChanged(dataId, configInfo);
                    }
                });
                configService.addListener(dataId, group, listenersByKey.get(dataId));

                // the key is newly added, read the config for the first time
                final String config = configService.getConfig(dataId, group, 1000);
                onDataIdValueChanged(dataId, config);
            } catch (NacosException e) {
                log.warn("Failed to register Nacos listener for dataId: {}", dataId);
            }
        }
    }

    private void removeUninterestedKeys(final Set<String> interestedKeys) {
        final String group = settings.getGroup();

        final Set<String> uninterestedKeys = new HashSet<>(listenersByKey.keySet());
        uninterestedKeys.removeAll(interestedKeys);

        uninterestedKeys.forEach(k -> {
            final Listener listener = listenersByKey.remove(k);
            if (listener != null) {
                configService.removeListener(k, group, listener);
            }
        });
    }

    void onDataIdValueChanged(String dataId, String configInfo) {
        if (log.isInfoEnabled()) {
            log.info("Nacos config changed: {}: {}", dataId, configInfo);
        }

        configItemKeyedByName.put(dataId, Optional.ofNullable(configInfo));
    }
}
