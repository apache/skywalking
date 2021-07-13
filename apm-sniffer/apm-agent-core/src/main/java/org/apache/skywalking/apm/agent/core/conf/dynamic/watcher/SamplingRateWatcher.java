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
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;

public class SamplingRateWatcher extends AgentConfigChangeWatcher {
    private static final ILog LOGGER = LogManager.getLogger(SamplingRateWatcher.class);

    private final AtomicInteger samplingRate;
    private final SamplingService samplingService;

    public SamplingRateWatcher(final String propertyKey, SamplingService samplingService) {
        super(propertyKey);
        this.samplingRate = new AtomicInteger(getDefaultValue());
        this.samplingService = samplingService;
    }

    private void activeSetting(String config) {
        if (LOGGER.isDebugEnable()) {
            LOGGER.debug("Updating using new static config: {}", config);
        }
        try {
            this.samplingRate.set(Integer.parseInt(config));

            /*
             * We need to notify samplingService the samplingRate changed.
             */
            samplingService.handleSamplingRateChanged();
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
        return String.valueOf(samplingRate.get());
    }

    private int getDefaultValue() {
        return Config.Agent.SAMPLE_N_PER_3_SECS;
    }

    public int getSamplingRate() {
        return samplingRate.get();
    }
}
