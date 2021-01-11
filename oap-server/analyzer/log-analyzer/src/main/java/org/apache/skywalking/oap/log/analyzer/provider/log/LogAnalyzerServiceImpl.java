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
import org.apache.skywalking.oap.log.analyzer.provider.log.listener.LogAnalysisListenerFactory;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@RequiredArgsConstructor
public class LogAnalyzerServiceImpl implements ILogAnalyzerService, ILogAnalysisListenerFactoryManager {
    private final ModuleManager moduleManager;
    private final LogAnalyzerModuleConfig moduleConfig;
    private final List<LogAnalysisListenerFactory> factories = new ArrayList<>();

    @Override
    public void doAnalysis(final LogData.Builder log) {
        LogAnalyzer analyzer = new LogAnalyzer(moduleManager, moduleConfig, this);
        analyzer.doAnalysis(log);
    }

    @Override
    public void addListenerFactory(final LogAnalysisListenerFactory factory) {
        factories.add(factory);
    }

    @Override
    public List<LogAnalysisListenerFactory> getLogAnalysisListenerFactories() {
        return factories;
    }
}
