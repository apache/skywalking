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

package org.apache.skywalking.oap.log.analyzer.provider.log;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.provider.log.listener.LogAnalysisListener;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@RequiredArgsConstructor
public class LogAnalyzer {
    private final ModuleManager moduleManager;
    private final LogAnalyzerModuleConfig moduleConfig;
    private final ILogAnalysisListenerFactoryManager factoryManager;

    private final List<LogAnalysisListener> listeners = new ArrayList<>();

    public void doAnalysis(LogData logData) {
        createListeners();

        notifyListener(logData);
        notifyListenerToBuild();
    }

    private void notifyListener(LogData logData) {
        listeners.forEach(listener -> listener.parse(logData));
    }

    private void notifyListenerToBuild() {
        listeners.forEach(LogAnalysisListener::build);
    }

    private void createListeners() {
        factoryManager.getLogAnalysisListenerFactories()
                      .forEach(factory -> listeners.add(factory.create(moduleManager, moduleConfig)));
    }
}
