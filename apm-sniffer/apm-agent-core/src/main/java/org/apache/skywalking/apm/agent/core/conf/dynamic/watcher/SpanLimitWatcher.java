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

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.dynamic.AgentConfigChangeWatcher;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

public class SpanLimitWatcher extends AgentConfigChangeWatcher {
    private static final ILog LOGGER = LogManager.getLogger(SpanLimitWatcher.class);

    private final AtomicInteger spanLimit;

    public SpanLimitWatcher(final String propertyKey) {
        super(propertyKey);
        this.spanLimit = new AtomicInteger(getDefaultValue());
    }

    private void activeSetting(String config) {
        if (LOGGER.isDebugEnable()) {
            LOGGER.debug("Updating using new static config: {}", config);
        }
        try {
            this.spanLimit.set(Integer.parseInt(config));
        } catch (NumberFormatException ex) {
            LOGGER.error(ex, "Cannot load {} from: {}", getPropertyKey(), config);
        }
    }

    @Override
    public void notify(final ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            activeSetting(String.valueOf(getDefaultValue()));
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return String.valueOf(spanLimit.get());
    }

    private int getDefaultValue() {
        return Config.Agent.SPAN_LIMIT_PER_SEGMENT;
    }

    public int getSpanLimit() {
        return spanLimit.get();
    }
}
