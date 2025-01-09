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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppResourcePerf;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.decorators.BrowserResourcePerfDataDecorator;

public class BrowserWebResourcePerfDataAnalysisListener implements PerfDataAnalysisListener<BrowserResourcePerfDataDecorator>  {
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    private BrowserAppResourcePerf browserAppResourcePerf;

    public BrowserWebResourcePerfDataAnalysisListener(SourceReceiver sourceReceiver, NamingControl namingControl) {
        this.sourceReceiver = sourceReceiver;
        this.namingControl = namingControl;
    }

    @Override
    public void build() {
        sourceReceiver.receive(browserAppResourcePerf);
    }

    @Override
    public void parse(BrowserResourcePerfDataDecorator decorator) {
        browserAppResourcePerf = new BrowserAppResourcePerf();
        browserAppResourcePerf.setTimeBucket(TimeBucket.getMinuteTimeBucket(decorator.getTime()));
        browserAppResourcePerf.setServiceName(namingControl.formatServiceName(decorator.getService()));
        browserAppResourcePerf.setPath(namingControl.formatEndpointName(browserAppResourcePerf.getServiceName(), decorator.getPagePath()));
        browserAppResourcePerf.setName(decorator.getName());
        browserAppResourcePerf.setDuration(decorator.getDuration());
        browserAppResourcePerf.setSize(decorator.getSize());
        browserAppResourcePerf.setProtocol(decorator.getProtocol());
        browserAppResourcePerf.setType(decorator.getType());
    }

    public static class Factory implements PerfDataListenerFactory<BrowserResourcePerfDataDecorator> {

        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;

        public Factory(ModuleManager moduleManager, BrowserServiceModuleConfig moduleConfig) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME)
                .provider()
                .getService(SourceReceiver.class);

            this.namingControl = moduleManager.find(CoreModule.NAME)
                .provider()
                .getService(NamingControl.class);
        }

        @Override
        public PerfDataAnalysisListener<BrowserResourcePerfDataDecorator> create(ModuleManager moduleManager, BrowserServiceModuleConfig moduleConfig) {
            return new BrowserWebResourcePerfDataAnalysisListener(sourceReceiver, namingControl);
        }
    }
}
