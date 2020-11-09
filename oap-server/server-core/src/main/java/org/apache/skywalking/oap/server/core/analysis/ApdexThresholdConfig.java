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

package org.apache.skywalking.oap.server.core.analysis;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Apdex threshold configuration dictionary adapter. Looking up a service apdex threshold from dynamic config service.
 */
@Slf4j
public class ApdexThresholdConfig extends ConfigChangeWatcher implements ConfigurationDictionary {

    private static final String CONFIG_FILE_NAME = "service-apdex-threshold.yml";

    private static final int SYSTEM_RESERVED_THRESHOLD = 500;

    private Map<String, Integer> dictionary = Collections.emptyMap();

    private String rawConfig = Const.EMPTY_STRING;

    public ApdexThresholdConfig(final CoreModuleProvider provider) {
        super(CoreModule.NAME, provider, "apdexThreshold");
        try {
            updateConfig(ResourceUtils.read(CONFIG_FILE_NAME));
        } catch (final FileNotFoundException e) {
            log.error("Cannot config from: {}", CONFIG_FILE_NAME, e);
        }
    }

    @Override
    public Number lookup(String name) {
        int t = dictionary.getOrDefault(name, -1);
        if (t < 0) {
            t = dictionary.getOrDefault("default", -1);
        }
        if (t < 0) {
            log.warn("Pick up system reserved threshold {}ms because of config missing", SYSTEM_RESERVED_THRESHOLD);
            return SYSTEM_RESERVED_THRESHOLD;
        }
        if (log.isDebugEnabled()) {
            log.debug("Apdex threshold of {} is {}ms", name, t);
        }
        return t;
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
        return rawConfig;
    }

    private synchronized void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("Updating using new static config: {}", config);
        }
        rawConfig = config;
        updateConfig(new StringReader(config));
    }

    @SuppressWarnings("unchecked")
    private void updateConfig(final Reader contentRender) {
        dictionary = (Map<String, Integer>) new Yaml(new SafeConstructor()).load(contentRender);
        if (dictionary == null) {
            dictionary = Collections.emptyMap();
        }
    }
}
