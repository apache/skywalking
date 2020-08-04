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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

public class DBLatencyThresholdsAndWatcher extends ConfigChangeWatcher {
    private AtomicReference<Map<String, Integer>> thresholds;
    private AtomicReference<String> settingsString;

    public DBLatencyThresholdsAndWatcher(String config, ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "slowDBAccessThreshold");
        thresholds = new AtomicReference<>(new HashMap<>());
        settingsString = new AtomicReference<>(Const.EMPTY_STRING);

        activeSetting(config);
    }

    private void activeSetting(String config) {
        Map<String, Integer> newThresholds = new HashMap<>();
        String[] settings = config.split(",");
        for (String setting : settings) {
            String[] typeValue = setting.split(":");
            if (typeValue.length == 2) {
                newThresholds.put(typeValue[0].trim().toLowerCase(), Integer.parseInt(typeValue[1].trim()));
            }
        }
        if (!newThresholds.containsKey("default")) {
            newThresholds.put("default", 10000);
        }

        thresholds.set(newThresholds);
        settingsString.set(config);
    }

    public int getThreshold(String type) {
        type = type.toLowerCase();
        if (thresholds.get().containsKey(type)) {
            return thresholds.get().get(type);
        } else {
            return thresholds.get().get("default");
        }
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            activeSetting("");
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return settingsString.get();
    }
}
