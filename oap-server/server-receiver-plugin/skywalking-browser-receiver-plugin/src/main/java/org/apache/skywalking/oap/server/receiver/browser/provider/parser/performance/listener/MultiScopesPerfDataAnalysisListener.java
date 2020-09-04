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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.BrowserPerfDataDecorator;

/**
 * Browser traffic and page Performance related metrics.
 */
@Slf4j
public class MultiScopesPerfDataAnalysisListener implements PerfDataAnalysisListener {
    private final SourceReceiver sourceReceiver;

    private final SourceBuilder sourceBuilder;

    public MultiScopesPerfDataAnalysisListener(final SourceReceiver sourceReceiver,
                                               final NamingControl namingControl) {
        this.sourceReceiver = sourceReceiver;
        this.sourceBuilder = new SourceBuilder(namingControl);
    }

    /**
     * Send BrowserAppTraffic, BrowserAppSingleVersionTraffic, BrowserAppPageTraffic and BrowserAppPagePerf scope to the
     * receiver.
     */
    @Override
    public void build() {
        // traffic
        sourceReceiver.receive(sourceBuilder.toBrowserAppTraffic());
        sourceReceiver.receive(sourceBuilder.toBrowserAppSingleVersionTraffic());
        sourceReceiver.receive(sourceBuilder.toBrowserAppPageTraffic());

        // performance (currently only page level performance data is analyzed)
        sourceReceiver.receive(sourceBuilder.toBrowserAppPagePerf());
    }

    /**
     * Parse raw data
     */
    @Override
    public void parse(final BrowserPerfDataDecorator decorator) {
        sourceBuilder.setService(decorator.getService());
        sourceBuilder.setServiceVersion(decorator.getServiceVersion());
        sourceBuilder.setPatePath(decorator.getPagePath());

        // time
        sourceBuilder.setTimeBucket(TimeBucket.getMinuteTimeBucket(decorator.getTime()));

        // performance related
        sourceBuilder.setRedirectTime(decorator.getRedirectTime());
        sourceBuilder.setDnsTime(decorator.getDnsTime());
        sourceBuilder.setTtfbTime(decorator.getTtfbTime());
        sourceBuilder.setTcpTime(decorator.getTcpTime());
        sourceBuilder.setTransTime(decorator.getTransTime());
        sourceBuilder.setDomAnalysisTime(decorator.getDomAnalysisTime());
        sourceBuilder.setFptTime(decorator.getFptTime());
        sourceBuilder.setDomReadyTime(decorator.getDomReadyTime());
        sourceBuilder.setLoadPageTime(decorator.getLoadPageTime());
        sourceBuilder.setResTime(decorator.getResTime());
        sourceBuilder.setSslTime(decorator.getSslTime());
        sourceBuilder.setTtlTime(decorator.getTtlTime());
        sourceBuilder.setFirstPackTime(decorator.getFirstPackTime());
        sourceBuilder.setFmpTime(decorator.getFmpTime());
    }

    public static class Factory implements PerfDataListenerFactory {

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
        public PerfDataAnalysisListener create(final ModuleManager moduleManager,
                                               final BrowserServiceModuleConfig moduleConfig) {
            return new MultiScopesPerfDataAnalysisListener(sourceReceiver, namingControl);
        }
    }
}
