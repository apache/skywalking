/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance;

import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener.PerfDataAnalysisListener;

@Slf4j
@RequiredArgsConstructor
public class PerfDataAnalyzer {
    private final ModuleManager moduleManager;
    private final PerfDataParserListenerManager listenerManager;
    private final BrowserServiceModuleConfig config;

    private final List<PerfDataAnalysisListener> analysisListeners = new LinkedList<>();

    public void doAnalysis(BrowserPerfData browserPerfData) {
        createAnalysisListeners();

        try {
            BrowserPerfDataDecorator decorator = new BrowserPerfDataDecorator(browserPerfData);
            // Use the server side current time.
            long nowMillis = System.currentTimeMillis();
            decorator.setTime(nowMillis);

            notifyListener(decorator);

            notifyListenerToBuild();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }

    private void notifyListener(BrowserPerfDataDecorator decorator) {
        analysisListeners.forEach(listener -> listener.parse(decorator));
    }

    private void notifyListenerToBuild() {
        analysisListeners.forEach(PerfDataAnalysisListener::build);
    }

    private void createAnalysisListeners() {
        listenerManager.getPerfDataListenerFactories()
                       .forEach(factory -> analysisListeners.add(factory.create(moduleManager, config)));
    }
}
