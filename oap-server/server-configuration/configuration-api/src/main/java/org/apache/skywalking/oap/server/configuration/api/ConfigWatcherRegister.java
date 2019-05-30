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

import java.util.*;
import java.util.concurrent.*;
import lombok.Getter;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.slf4j.*;

/**
 * The default implementor of Config Watcher register.
 *
 * @author wusheng
 */
public class ConfigWatcherRegister implements DynamicConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigWatcherRegister.class);

    private Map<String, WatcherHolder> register = new HashMap<>();
    private volatile boolean isStarted = false;

    @Override synchronized public void registerConfigChangeWatcher(ConfigChangeWatcher watcher) {
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

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            new RunnableWithExceptionProtection(() -> sync(),
                t -> logger.error("Sync config center error.", t)), 1, 60, TimeUnit.SECONDS);
    }

    private void sync() {

    }

    @Getter
    private class WatcherHolder {
        private ConfigChangeWatcher watcher;
        private final String key;

        public WatcherHolder(ConfigChangeWatcher watcher) {
            this.watcher = watcher;
            this.key = String.join("-", watcher.getModule().name(), watcher.getProvider().name(), watcher.getItemName());
        }
    }
}
