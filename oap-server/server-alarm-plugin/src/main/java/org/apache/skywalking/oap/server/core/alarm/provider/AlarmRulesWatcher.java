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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

/**
 * @author kezhenxu94
 */
public class AlarmRulesWatcher extends ConfigChangeWatcher {
    @Getter
    private volatile Map<String, List<RunningRule>> runningContext;
    private volatile Rules rules;
    private volatile String settingsString;

    public AlarmRulesWatcher(Rules defaultRules, ModuleProvider provider) {
        super(AlarmModule.NAME, provider, "alarm-settings");
        this.runningContext = new HashMap<>();
        this.settingsString = Const.EMPTY_STRING;

        notify(defaultRules);
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (value.getEventType() == EventType.DELETE) {
            settingsString = Const.EMPTY_STRING;
            notify(new Rules());
        } else {
            settingsString = value.getNewValue();
            RulesReader rulesReader = new RulesReader(new StringReader(value.getNewValue()));
            Rules rules = rulesReader.readRules();
            notify(rules);
        }
    }

    private void notify(Rules newRules) {
        Map<String, List<RunningRule>> newRunningContext = new HashMap<>();

        newRules.getRules().forEach(rule -> {
            RunningRule runningRule = new RunningRule(rule);

            String metricsName = rule.getMetricsName();

            List<RunningRule> runningRules = newRunningContext.computeIfAbsent(metricsName, key -> new ArrayList<>());

            runningRules.add(runningRule);
        });

        this.rules = newRules;
        this.runningContext = newRunningContext;
    }

    @Override
    public String value() {
        return settingsString;
    }

    public List<AlarmRule> getRules() {
        return this.rules.getRules();
    }

    public List<String> getWebHooks() {
        return this.rules.getWebhooks();
    }
}
