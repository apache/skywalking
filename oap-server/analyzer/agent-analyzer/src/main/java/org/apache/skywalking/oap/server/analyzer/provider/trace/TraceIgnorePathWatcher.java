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

import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

@Slf4j
public class TraceIgnorePathWatcher extends ConfigChangeWatcher {
    private AtomicReference<String> traceIgnorePathPatterns;

    public TraceIgnorePathWatcher(ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "traceIgnorePathPatterns");
        traceIgnorePathPatterns = new AtomicReference<>();
        traceIgnorePathPatterns.set(getDefaultValue());
    }

    private void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("Updating using new static config: {}", config);
        }
        try {
            traceIgnorePathPatterns.set(config);
        } catch (NumberFormatException ex) {
            log.error("Cannot load traceIgnorePathPatterns from: {}", config, ex);
        }
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            activeSetting(getDefaultValue());
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return String.valueOf(traceIgnorePathPatterns.get());
    }

    private String getDefaultValue() {
        return ((AnalyzerModuleConfig) this.getProvider().createConfigBeanIfAbsent()).getTraceIgnorePathPatterns();
    }

    public String getTraceIgnorePathPatterns() {
        return traceIgnorePathPatterns.get();
    }
}
