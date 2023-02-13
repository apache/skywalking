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

package org.apache.skywalking.oap.server.configuration.api;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.RunnableWithExceptionProtection;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The default implementor of Config Watcher register.
 */
@Slf4j
public abstract class ConfigWatcherRegister implements DynamicConfigurationService {
    public static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");
    private final Register singleConfigChangeWatcherRegister = new Register();
    @Getter
    private final Register groupConfigChangeWatcherRegister = new Register();
    private volatile boolean isStarted = false;
    private final long syncPeriod;

    public ConfigWatcherRegister() {
        this(60);
    }

    public ConfigWatcherRegister(long syncPeriod) {
        this.syncPeriod = syncPeriod;
    }

    @Override
    synchronized public void registerConfigChangeWatcher(ConfigChangeWatcher watcher) {
        if (isStarted) {
            throw new IllegalStateException("Config Register has been started. Can't register new watcher.");
        }

        WatcherHolder holder = new WatcherHolder(watcher);
        if (singleConfigChangeWatcherRegister.containsKey(
            holder.getKey()) || groupConfigChangeWatcherRegister.containsKey(holder.getKey())) {
            throw new IllegalStateException("Duplicate register, watcher=" + watcher);
        }

        switch (holder.getWatcher().getWatchType()) {
            case SINGLE:
                singleConfigChangeWatcherRegister.put(holder.getKey(), holder);
                break;
            case GROUP:
                groupConfigChangeWatcherRegister.put(holder.getKey(), holder);
                break;
            default:
                throw new IllegalArgumentException(
                    "Unexpected watch type of ConfigChangeWatcher " + watcher.toString());
        }
    }

    public void start() {
        isStarted = true;

        log.info("Current configurations after the bootstrap sync." + LINE_SEPARATOR + singleConfigChangeWatcherRegister.toString());

        Executors.newSingleThreadScheduledExecutor()
                 .scheduleAtFixedRate(
                     new RunnableWithExceptionProtection(
                         this::configSync,
                         t -> log.error("Sync config center error.", t)
                     ), 0, syncPeriod, TimeUnit.SECONDS);
    }

    void configSync() {
        singleConfigsSync();
        groupConfigsSync();
    }

    private void singleConfigsSync() {
        Optional<ConfigTable> configTable = readConfig(singleConfigChangeWatcherRegister.keys());

        // Config table would be null if no change detected from the implementation.
        configTable.ifPresent(config -> {
            config.getItems().forEach(item -> {
                String itemName = item.getName();
                WatcherHolder holder = singleConfigChangeWatcherRegister.get(itemName);
                if (holder == null) {
                    log.warn(
                        "Config {} from configuration center, doesn't match any WatchType.SINGLE watcher, ignore.",
                        itemName
                    );
                    return;
                }
                ConfigChangeWatcher watcher = holder.getWatcher();
                String newItemValue = item.getValue();
                if (newItemValue == null) {
                    if (watcher.value() != null) {
                        // Notify watcher, the new value is null with delete event type.
                        watcher.notify(
                            new ConfigChangeWatcher.ConfigChangeEvent(null, ConfigChangeWatcher.EventType.DELETE));
                    } else {
                        // Don't need to notify, stay in null.
                    }
                } else {
                    if (!newItemValue.equals(watcher.value())) {
                        watcher.notify(new ConfigChangeWatcher.ConfigChangeEvent(
                            newItemValue,
                            ConfigChangeWatcher.EventType.MODIFY
                        ));
                    } else {
                        // Don't need to notify, stay in the same config value.
                    }
                }
            });
            if (log.isTraceEnabled()) {
                log.trace(
                    "Current configurations after the sync." + LINE_SEPARATOR + singleConfigChangeWatcherRegister.toString());
            }
        });
    }

    private void groupConfigsSync() {
        Optional<GroupConfigTable> groupConfigTable = readGroupConfig(groupConfigChangeWatcherRegister.keys());
        // Config table would be null if no change detected from the implementation.
        groupConfigTable.ifPresent(config -> {
            config.getGroupItems().forEach(groupConfigItems -> {
                String groupConfigItemName = groupConfigItems.getName();
                WatcherHolder holder = groupConfigChangeWatcherRegister.get(groupConfigItemName);

                if (holder == null) {
                    log.warn(
                        "Config {} from configuration center, doesn't match any WatchType.GROUP watcher, ignore.",
                        groupConfigItemName
                    );
                    return;
                }

                GroupConfigChangeWatcher watcher = (GroupConfigChangeWatcher) holder.getWatcher();
                Map<String, ConfigTable.ConfigItem> groupItems = groupConfigItems.getItems();
                Map<String, ConfigChangeWatcher.ConfigChangeEvent> changedGroupItems = new HashMap<>();
                Map<String, String> currentGroupItems = Optional.ofNullable(watcher.groupItems())
                                                                .orElse(new HashMap<>());

                groupItems.forEach((groupItemName, groupItem) -> {
                    String newItemValue = groupItem.getValue();
                    if (newItemValue == null) {
                        if (currentGroupItems.get(groupItemName) != null) {
                            // Notify watcher, the new value is null with delete event type.
                            changedGroupItems.put(groupItemName, new ConfigChangeWatcher.ConfigChangeEvent(
                                null,
                                ConfigChangeWatcher.EventType.DELETE
                            ));

                        } else {
                            // Don't need to notify, stay in null.
                        }
                    } else { //add and modify
                        if (!newItemValue.equals(currentGroupItems.get(groupItemName))) {
                            changedGroupItems.put(groupItemName, new ConfigChangeWatcher.ConfigChangeEvent(
                                newItemValue,
                                ConfigChangeWatcher.EventType.MODIFY
                            ));

                        } else {
                            // Don't need to notify, stay in the same config value.
                        }
                    }
                });

                currentGroupItems.forEach((oldGroupItemName, oldGroupItemValue) -> {
                    //delete item
                    if (null == groupItems.get(oldGroupItemName)) {
                        // Notify watcher, the item is deleted with delete event type.
                        changedGroupItems.put(oldGroupItemName, new ConfigChangeWatcher.ConfigChangeEvent(
                            null,
                            ConfigChangeWatcher.EventType.DELETE
                        ));
                    }
                });

                if (changedGroupItems.size() > 0) {
                    watcher.notifyGroup(changedGroupItems);
                }
            });
            if (log.isTraceEnabled()) {
                log.trace(
                    "Current configurations after the sync." + LINE_SEPARATOR + groupConfigChangeWatcherRegister.toString());
            }
        });
    }

    public abstract Optional<ConfigTable> readConfig(Set<String> keys);

    public abstract Optional<GroupConfigTable> readGroupConfig(Set<String> keys);

    static class Register {
        private Map<String, WatcherHolder> register = new HashMap<>();

        private boolean containsKey(String key) {
            return register.containsKey(key);
        }

        private void put(String key, WatcherHolder holder) {
            register.put(key, holder);
        }

        public WatcherHolder get(String name) {
            return register.get(name);
        }

        public Set<String> keys() {
            return register.keySet();
        }

        @Override
        public String toString() {
            StringBuilder registerTableDescription = new StringBuilder();
            registerTableDescription.append("Following dynamic config items are available.").append(LINE_SEPARATOR);
            registerTableDescription.append("---------------------------------------------").append(LINE_SEPARATOR);
            register.forEach((key, holder) -> {
                ConfigChangeWatcher watcher = holder.getWatcher();
                registerTableDescription.append("key:")
                                        .append(key)
                                        .append("    module:")
                                        .append(watcher.getModule())
                                        .append("    provider:")
                                        .append(watcher.getProvider().name());
                if (watcher.watchType.equals(ConfigChangeWatcher.WatchType.GROUP)) {
                    GroupConfigChangeWatcher groupWatcher = (GroupConfigChangeWatcher) watcher;
                    registerTableDescription.append("    groupItems(current):")
                                            .append(groupWatcher.groupItems());
                } else {
                    registerTableDescription.append("    value(current):")
                                            .append(watcher.value());
                }
                registerTableDescription.append(LINE_SEPARATOR);
            });
            return registerTableDescription.toString();
        }
    }

    @Getter
    protected static class WatcherHolder {
        private ConfigChangeWatcher watcher;
        private final String key;

        public WatcherHolder(ConfigChangeWatcher watcher) {
            this.watcher = watcher;
            this.key = String.join(
                ".", watcher.getModule(), watcher.getProvider().name(),
                watcher.getItemName()
            );
        }
    }
}
