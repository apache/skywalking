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

import lombok.extern.slf4j.Slf4j;

/**
 * Implement Config Watcher register using client listening.
 */
@Slf4j
public abstract class ListeningConfigWatcherRegister extends ConfigWatcherRegister {

    @Override
    synchronized public void registerConfigChangeWatcher(ConfigChangeWatcher watcher) {
        startListening(new WatcherHolder(watcher), new ConfigChangeCallback() {
            @Override
            public synchronized void onSingleValueChanged(final WatcherHolder holder, final ConfigTable.ConfigItem configItem) {
                notifySingleValue(holder.getWatcher(), configItem);
            }

            @Override
            public synchronized void onGroupValuesChanged(final WatcherHolder holder,
                                             final GroupConfigTable.GroupConfigItems groupConfigItems) {
                notifyGroupValues((GroupConfigChangeWatcher) holder.getWatcher(), groupConfigItems);
            }
        });
    }

    @Override
    public void start() {
        // do nothing
    }

    /**
     * listen key value defined by watcherHolder, callback should be executed if key value changed
     */
    protected abstract void startListening(WatcherHolder watcherHolder, ConfigChangeCallback configChangeCallback);

    protected interface ConfigChangeCallback {
        void onSingleValueChanged(WatcherHolder holder, ConfigTable.ConfigItem configItem);

        void onGroupValuesChanged(WatcherHolder holder, GroupConfigTable.GroupConfigItems groupConfigItems);
    }
}
