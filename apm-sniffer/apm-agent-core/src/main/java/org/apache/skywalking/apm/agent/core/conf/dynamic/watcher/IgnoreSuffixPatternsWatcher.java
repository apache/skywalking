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

package org.apache.skywalking.apm.agent.core.conf.dynamic.watcher;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.dynamic.AgentConfigChangeWatcher;
import org.apache.skywalking.apm.agent.core.context.ContextManagerExtendService;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import java.util.concurrent.atomic.AtomicReference;

public class IgnoreSuffixPatternsWatcher extends AgentConfigChangeWatcher {

    private static final ILog LOGGER = LogManager.getLogger(IgnoreSuffixPatternsWatcher.class);

    private final AtomicReference<String> ignoreSuffixPatterns;
    private final ContextManagerExtendService contextManagerExtendService;

    public IgnoreSuffixPatternsWatcher(final String propertyKey, ContextManagerExtendService contextManagerExtendService) {
        super(propertyKey);
        this.ignoreSuffixPatterns = new AtomicReference(getDefaultValue());
        this.contextManagerExtendService = contextManagerExtendService;
    }

    private void activeSetting(String config) {
        if (LOGGER.isDebugEnable()) {
            LOGGER.debug("Updating using new static config: {}", config);
        }

        this.ignoreSuffixPatterns.set(config);
        contextManagerExtendService.handleIgnoreSuffixPatternsChanged();
    }

    @Override
    public void notify(final ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            activeSetting(getDefaultValue());
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return ignoreSuffixPatterns.get();
    }

    private String getDefaultValue() {
        return Config.Agent.IGNORE_SUFFIX;
    }

    public String getIgnoreSuffixPatterns() {
        return ignoreSuffixPatterns.get();
    }

}
