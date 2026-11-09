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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser;

import java.util.ServiceLoader;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.AnalysisListenerFactory;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.EndpointDepFromCrossThreadAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.NetworkAddressAliasMappingListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.RPCAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SegmentAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.VirtualServiceAnalysisListener;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * The listeners {@code AnalyzerModuleProvider.listenerManager()} registered, in its order, as it was at
 * {@code 287b24fedaf91a75d33c9fcfce67569da5e05f35} before the native analysis moved to TAL rules.
 */
public final class ListenerManagerAssembly {
    private ListenerManagerAssembly() {
    }

    /**
     * @param moduleManager the manager the listener factories read their services from
     * @param moduleConfig  the agent analyzer's configuration
     * @return the listener manager the agent analyzer used to hand {@code SegmentParserServiceImpl}
     */
    public static SegmentParserListenerManager listenerManager(final ModuleManager moduleManager,
                                                               final AnalyzerModuleConfig moduleConfig) {
        SegmentParserListenerManager listenerManager = new SegmentParserListenerManager();
        if (moduleConfig.isTraceAnalysis()) {
            listenerManager.add(new RPCAnalysisListener.Factory(moduleManager));
            listenerManager.add(new EndpointDepFromCrossThreadAnalysisListener.Factory(moduleManager));
            listenerManager.add(new NetworkAddressAliasMappingListener.Factory(moduleManager));
        }
        listenerManager.add(new SegmentAnalysisListener.Factory(moduleManager, moduleConfig));
        listenerManager.add(new VirtualServiceAnalysisListener.Factory(moduleManager));
        ServiceLoader.load(AnalysisListenerFactory.class)
                     .forEach(listenerManager::add);

        return listenerManager;
    }
}
