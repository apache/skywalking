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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default implementor of Config Watcher register.
 */
public abstract class ConfigWatcherRegister implements DynamicConfigurationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigWatcherRegister.class);
    public static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");

    private Register register = new Register();
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
        if (register.containsKey(holder.getKey())) {
            throw new IllegalStateException("Duplicate register, watcher=" + watcher);
        }
        register.put(holder.getKey(), holder);
    }

    public void start() {
        isStarted = true;

        LOGGER.info("Current configurations after the bootstrap sync." + LINE_SEPARATOR + register.toString());

        Executors.newSingleThreadScheduledExecutor()
                 .scheduleAtFixedRate(
                     new RunnableWithExceptionProtection(
                         this::configSync,
                         t -> LOGGER.error("Sync config center error.", t)
                     ), 0, syncPeriod, TimeUnit.SECONDS);
    }

    void configSync() {
        Optional<ConfigTable> configTable = readConfig(register.keys());

        // Config table would be null if no change detected from the implementation.
        configTable.ifPresent(config -> {
            config.getItems().forEach(item -> {
                String itemName = item.getName();
                WatcherHolder holder = register.get(itemName);
                if (holder != null) {
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
                } else {
                    LOGGER.warn("Config {} from configuration center, doesn't match any watcher, ignore.", itemName);
                }
            });

            LOGGER.trace("Current configurations after the sync." + LINE_SEPARATOR + register.toString());
        });

        GroupConfigsSync(configTable);
    }

    private void GroupConfigsSync(Optional<ConfigTable> configTable) {
        configTable.ifPresent(config -> {
            config.getGroupItems().forEach(groupConfigItems -> {
                String groupConfigItemName = groupConfigItems.getName();
                WatcherHolder holder = register.get(groupConfigItemName);
                if (holder != null) {
                    if (holder.getWatcher().watchType == ConfigChangeWatcher.WatchType.GROUP) {
                        GroupConfigChangeWatcher watcher = (GroupConfigChangeWatcher) holder.getWatcher();
                        Map<String, ConfigTable.ConfigItem> groupItems = groupConfigItems.getItems();
                        groupItems.forEach((groupItemName, groupItem) -> {
                            String newItemValue = groupItem.getValue();
                            if (newItemValue == null) {
                                if (watcher.groupItems().get(groupItemName) != null) {
                                    // Notify watcher, the new value is null with delete event type.
                                    watcher.notify(
                                        new ConfigChangeWatcher.ConfigChangeEvent(groupItemName,
                                                                                  null,
                                                                                  ConfigChangeWatcher.EventType.DELETE
                                        ));
                                } else {
                                    // Don't need to notify, stay in null.
                                }
                            } else { //add and modify
                                if (!newItemValue.equals(watcher.groupItems().get(groupItemName))) {
                                    watcher.notify(new ConfigChangeWatcher.ConfigChangeEvent(
                                        groupItemName,
                                        newItemValue,
                                        ConfigChangeWatcher.EventType.MODIFY
                                    ));
                                } else {
                                    // Don't need to notify, stay in the same config value.
                                }
                            }
                        });

                        watcher.groupItems().forEach((oldGroupItemName, oldGroupItemValue) -> {
                            //delete item
                            if (null == groupItems.get(oldGroupItemName)) {
                                // Notify watcher, the item is deleted with delete event type.
                                watcher.notify(
                                    new ConfigChangeWatcher.ConfigChangeEvent(oldGroupItemName,
                                                                              null, ConfigChangeWatcher.EventType.DELETE
                                    ));
                            }
                        });

                    } else {
                        LOGGER.warn(
                            "Config {} from configuration center, doesn't match any watcher, ignore.",
                            groupConfigItemName
                        );
                    }
                }
            });

            LOGGER.trace("Current configurations after the sync." + LINE_SEPARATOR + register.toString());
        });
    }

    public abstract Optional<ConfigTable> readConfig(Set<String> keys);

    public class Register {
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
                if (watcher.watchType == ConfigChangeWatcher.WatchType.GROUP) {
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
    private class WatcherHolder {
        private ConfigChangeWatcher watcher;
        private final String key;

        public WatcherHolder(ConfigChangeWatcher watcher) {
            this.watcher = watcher;
            if (watcher.getWatchType() == ConfigChangeWatcher.WatchType.GROUP) {
                this.key = String.join(
                    ".", watcher.watchType.name(), watcher.getModule(), watcher.getProvider().name(),
                    watcher.getItemName()
                );
            } else {
                this.key = String.join(
                    ".", watcher.getModule(), watcher.getProvider().name(),
                    watcher.getItemName()
                );
            }
        }
    }
}
