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
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppWebVitalsPerf;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.decorators.BrowserWebVitalsPerfDataDecorator;

public class BrowserWebVitalsPerfDataAnalysisListener implements PerfDataAnalysisListener<BrowserWebVitalsPerfDataDecorator> {
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    private BrowserAppWebVitalsPerf browserAppWebVitalsPerf;

    public BrowserWebVitalsPerfDataAnalysisListener(SourceReceiver sourceReceiver, NamingControl namingControl) {
        this.sourceReceiver = sourceReceiver;
        this.namingControl = namingControl;
    }

    @Override
    public void build() {
        sourceReceiver.receive(browserAppWebVitalsPerf);
    }

    @Override
    public void parse(BrowserWebVitalsPerfDataDecorator decorator) {
        browserAppWebVitalsPerf = new BrowserAppWebVitalsPerf();
        browserAppWebVitalsPerf.setTimeBucket(TimeBucket.getMinuteTimeBucket(decorator.getTime()));
        browserAppWebVitalsPerf.setServiceName(namingControl.formatServiceName(decorator.getService()));
        browserAppWebVitalsPerf.setPath(namingControl.formatEndpointName(browserAppWebVitalsPerf.getServiceName(), decorator.getPagePath()));
        browserAppWebVitalsPerf.setFmpTime(decorator.getFmpTime());
        // CLS values are typically between 0 and 1. Multiplying by 1000 allows storage as an integer
        // while preserving 3 decimal places of precision. When querying, divide by 1000 to restore the original value.
        browserAppWebVitalsPerf.setCls((int) Math.round(decorator.getCls() * 1000));
        browserAppWebVitalsPerf.setLcpTime(decorator.getLcpTime());
    }

    public static class Factory implements PerfDataListenerFactory<BrowserWebVitalsPerfDataDecorator> {

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
        public PerfDataAnalysisListener<BrowserWebVitalsPerfDataDecorator> create(ModuleManager moduleManager, BrowserServiceModuleConfig moduleConfig) {
            return new BrowserWebVitalsPerfDataAnalysisListener(sourceReceiver, namingControl);
        }
    }
}
