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

package org.apache.skywalking.oap.server.configuration.consul;

import com.google.common.base.Splitter;
import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.cache.KVCache;
import com.orbitz.consul.model.kv.Value;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.FetchingConfigWatcherRegister;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;

@SuppressWarnings("UnstableApiUsage")
@Slf4j
public class ConsulConfigurationWatcherRegister extends FetchingConfigWatcherRegister {
    private static final int DEFAULT_PORT = 8500;

    private final KeyValueClient consul;
    private final Map<String, Optional<String>> configItemKeyedByName;
    private final Map<String, KVCache> cachesByKey;

    public ConsulConfigurationWatcherRegister(ConsulConfigurationCenterSettings settings) {
        super(settings.getPeriod());

        this.configItemKeyedByName = new ConcurrentHashMap<>();
        this.cachesByKey = new ConcurrentHashMap<>();

        List<HostAndPort> hostAndPorts = Splitter.on(",")
                                                 .splitToList(settings.getHostAndPorts())
                                                 .parallelStream()
                                                 .map(hostAndPort -> HostAndPort.fromString(hostAndPort)
                                                                                .withDefaultPort(DEFAULT_PORT))
                                                 .collect(Collectors.toList());

        Consul.Builder builder = Consul.builder().withConnectTimeoutMillis(3_000)
                                       .withReadTimeoutMillis(20_000);

        if (hostAndPorts.size() == 1) {
            builder.withHostAndPort(hostAndPorts.get(0));
        } else {
            builder.withMultipleHostAndPort(hostAndPorts, 5000);
        }

        if (StringUtils.isNotEmpty(settings.getAclToken())) {
            builder.withAclToken(settings.getAclToken());
        }

        consul = builder.build().keyValueClient();
    }

    @Override
    public Optional<ConfigTable> readConfig(Set<String> keys) {
        removeUninterestedKeys(keys);

        registerKeyListeners(keys);

        final ConfigTable table = new ConfigTable();

        configItemKeyedByName.forEach((key, value) -> {
            if (value.isPresent()) {
                table.add(new ConfigTable.ConfigItem(key, value.get()));
            } else {
                table.add(new ConfigTable.ConfigItem(key, null));
            }
        });

        return Optional.of(table);
    }

    @Override
    public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
        GroupConfigTable groupConfigTable = new GroupConfigTable();
        keys.forEach(key -> {
            GroupConfigTable.GroupConfigItems groupConfigItems = new GroupConfigTable.GroupConfigItems(key);
            groupConfigTable.addGroupConfigItems(groupConfigItems);
            String groupKey = key + "/";
            List<String> groupItemKeys = this.consul.getKeys(groupKey);
            if (groupItemKeys != null) {
                groupItemKeys.stream().filter(it -> !groupKey.equals(it)).forEach(groupItemKey -> {
                    Optional<String> itemValue = this.consul.getValueAsString(groupItemKey);
                    String itemName = groupItemKey.substring(groupKey.length());
                    groupConfigItems.add(
                        new ConfigTable.ConfigItem(itemName, itemValue.orElse(null)));
                });
            }
        });
        return Optional.of(groupConfigTable);
    }

    private void registerKeyListeners(final Set<String> keys) {
        final Set<String> unregisterKeys = new HashSet<>(keys);
        unregisterKeys.removeAll(cachesByKey.keySet());

        unregisterKeys.forEach(key -> {
            KVCache cache = KVCache.newCache(consul, key);
            cache.addListener(newValues -> {
                Optional<Value> value = newValues.values().stream().filter(it -> key.equals(it.getKey())).findAny();
                if (value.isPresent()) {
                    onKeyValueChanged(key, value.get().getValueAsString().orElse(null));
                } else {
                    onKeyValueChanged(key, null);
                }
            });
            cache.start();
            cachesByKey.put(key, cache);
        });
    }

    private void removeUninterestedKeys(final Set<String> interestedKeys) {
        final Set<String> uninterestedKeys = new HashSet<>(cachesByKey.keySet());
        uninterestedKeys.removeAll(interestedKeys);

        uninterestedKeys.forEach(k -> {
            KVCache cache = cachesByKey.remove(k);
            if (cache != null) {
                cache.stop();
            }
        });
    }

    private void onKeyValueChanged(String key, String value) {
        if (log.isInfoEnabled()) {
            log.info("Consul config changed: {}: {}", key, value);
        }

        configItemKeyedByName.put(key, Optional.ofNullable(value));
    }
}
