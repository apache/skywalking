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

package org.apache.skywalking.oap.server.analyzer.provider.trace;

import com.google.common.base.Splitter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

public class CacheReadLatencyThresholdsAndWatcher extends ConfigChangeWatcher {
    private AtomicReference<Map<String, Integer>> thresholds;
    private final String initialSettingsString;
    private volatile String dynamicSettingsString;

    public CacheReadLatencyThresholdsAndWatcher(String config, ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "slowCacheReadThreshold");
        thresholds = new AtomicReference<>(new HashMap<>());
        initialSettingsString = config;

        activeSetting(config);
    }

    private void activeSetting(String config) {
        Map<String, Integer> newThresholds = new HashMap<>();
        List<String> settings = Splitter.on(',').splitToList(config);
        for (String setting : settings) {
            List<String> typeValue = Splitter.on(":").splitToList(setting);
            if (typeValue.size() == 2) {
                newThresholds.put(typeValue.get(0).trim().toLowerCase(), Integer.parseInt(typeValue.get(1).trim()));
            }
        }
        thresholds.set(newThresholds);
    }

    public int getThreshold(String type) {
        type = type.toLowerCase();
        if (thresholds.get().containsKey(type)) {
            return thresholds.get().get(type);
        } else {
            return Optional.ofNullable(thresholds.get().get("default")).orElse(Integer.MAX_VALUE);
        }
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            dynamicSettingsString = null;
            activeSetting(initialSettingsString);
        } else {
            dynamicSettingsString = value.getNewValue();
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return dynamicSettingsString;
    }
}
