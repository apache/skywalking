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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The default implementor of Config Watcher register.
 */
@Slf4j
public abstract class ConfigWatcherRegister implements DynamicConfigurationService {

    public abstract void start();

    protected void notifySingleValue(final ConfigChangeWatcher watcher, ConfigTable.ConfigItem configItem) {
        String newItemValue = configItem.getValue();
        if (newItemValue == null) {
            if (watcher.value() != null) {
                // Notify watcher, the new value is null with delete event type.
                try {
                    watcher.notify(
                        new ConfigChangeWatcher.ConfigChangeEvent(null, ConfigChangeWatcher.EventType.DELETE));
                } catch (Exception e) {
                    log.error("notify config change watcher {} failed", watcher, e);
                }
            } else {
                // Don't need to notify, stay in null.
            }
        } else {
            if (!newItemValue.equals(watcher.value())) {
                try {
                    watcher.notify(new ConfigChangeWatcher.ConfigChangeEvent(
                        newItemValue,
                        ConfigChangeWatcher.EventType.MODIFY
                    ));
                } catch (Exception e) {
                    log.error("notify config change watcher {} failed", watcher, e);
                }
            } else {
                // Don't need to notify, stay in the same config value.
            }
        }
    }

    protected void notifyGroupValues(final GroupConfigChangeWatcher watcher,
                                     final GroupConfigTable.GroupConfigItems groupConfigItems) {
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
            try {
                watcher.notifyGroup(changedGroupItems);
            } catch (Exception e) {
                log.error("notify config change watcher {} failed", watcher, e);
            }
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
