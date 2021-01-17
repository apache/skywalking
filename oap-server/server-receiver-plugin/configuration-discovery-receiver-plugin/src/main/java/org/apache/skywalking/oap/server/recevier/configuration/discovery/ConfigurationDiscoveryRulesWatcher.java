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

package org.apache.skywalking.oap.server.recevier.configuration.discovery;

import java.io.StringReader;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

/**
 * ConfigurationDiscoveryRulesWatcher used to handle dynamic configuration changes, and convert the configuration of the
 * character type to {@link ConfigurationDiscoveryRules}
 */
public class ConfigurationDiscoveryRulesWatcher extends ConfigChangeWatcher {
    private volatile String settingsString;
    private volatile ConfigurationDiscoveryRules activeConfigurationDiscoveryRules;

    public ConfigurationDiscoveryRulesWatcher(ConfigurationDiscoveryRules configurationDiscoveryRules,
                                              ModuleProvider provider) {
        super(ConfigurationDiscoveryModule.NAME, provider, "configurationRules");
        this.settingsString = Const.EMPTY_STRING;
        this.activeConfigurationDiscoveryRules = configurationDiscoveryRules;
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (value.getEventType().equals(EventType.DELETE)) {
            settingsString = Const.EMPTY_STRING;
            this.activeConfigurationDiscoveryRules = new ConfigurationDiscoveryRules();
        } else {
            settingsString = value.getNewValue();
            ConfigurationDiscoveryRulesReader configurationDiscoveryRulesReader =
                new ConfigurationDiscoveryRulesReader(new StringReader(value.getNewValue()));
            this.activeConfigurationDiscoveryRules = configurationDiscoveryRulesReader.readRules();
        }
    }

    @Override
    public String value() {
        return settingsString;
    }

    public ConfigurationDiscoveryRules getActiveConfigRules() {
        return activeConfigurationDiscoveryRules;
    }
}
