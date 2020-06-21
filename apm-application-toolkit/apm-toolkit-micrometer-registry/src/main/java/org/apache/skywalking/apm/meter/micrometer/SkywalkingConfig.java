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

package org.apache.skywalking.apm.meter.micrometer;

import io.micrometer.core.instrument.config.MeterRegistryConfig;

import java.util.Collections;
import java.util.List;

/**
 * Skywalking config
 */
public class SkywalkingConfig implements MeterRegistryConfig {

    public static final SkywalkingConfig DEFAULT = new SkywalkingConfig(Collections.emptyList());

    /**
     * Supporting rate by agent side counter names
     */
    private final List<String> rateCounterNames;

    public SkywalkingConfig(List<String> rateCounterNames) {
        this.rateCounterNames = rateCounterNames;
    }

    /**
     * Is counter need rate by agent side
     */
    public boolean isRateCounter(String name) {
        return rateCounterNames == null ? false : rateCounterNames.contains(name);
    }

    @Override
    public String prefix() {
        return "";
    }

    @Override
    public String get(String key) {
        return null;
    }
}
