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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance;

import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.decorators.BrowserPerfDecorator;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener.PerfDataAnalysisListener;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener.PerfDataListenerFactory;

import java.util.HashMap;
import java.util.Map;

public class PerfDataParserListenerManager {

    private final Map<Class<?>, PerfDataListenerFactory> factories = new HashMap<>();

    private final ModuleManager moduleManager;
    private final BrowserServiceModuleConfig config;

    public PerfDataParserListenerManager(ModuleManager moduleManager, BrowserServiceModuleConfig config) {
        this.moduleManager = moduleManager;
        this.config = config;
    }

    public <T extends BrowserPerfDecorator> void add(Class<T> decoratorClass, PerfDataListenerFactory<T> factory) {
        factories.put(decoratorClass, factory);
    }

    @SuppressWarnings("unchecked")
    public <T extends BrowserPerfDecorator> PerfDataAnalysisListener<T> create(Class<T> decorator) {
        return factories.get(decorator).create(moduleManager, config);
    }
}
