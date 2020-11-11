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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog;

import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.listener.ErrorLogAnalysisListener;

@Slf4j
@RequiredArgsConstructor
public class ErrorLogAnalyzer {
    private final ModuleManager moduleManager;
    private final ErrorLogParserListenerManager listenerManager;
    private final BrowserServiceModuleConfig moduleConfig;

    private final List<ErrorLogAnalysisListener> analysisListeners = new LinkedList<>();

    public void doAnalysis(BrowserErrorLog errorLog) {
        createAnalysisListeners();

        try {
            BrowserErrorLogDecorator decorator = new BrowserErrorLogDecorator(errorLog);
            // Use the server side current time.
            long nowMillis = System.currentTimeMillis();
            decorator.setTime(nowMillis);

            notifyListener(decorator);

            notifyListenerToBuild();
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }

    private void notifyListener(BrowserErrorLogDecorator decorator) {
        analysisListeners.forEach(listener -> listener.parse(decorator));
    }

    private void notifyListenerToBuild() {
        analysisListeners.forEach(ErrorLogAnalysisListener::build);
    }

    private void createAnalysisListeners() {
        listenerManager.getErrorLogAnalysisListeners()
                       .forEach(factory -> analysisListeners.add(factory.create(moduleManager, moduleConfig)));
    }
}
