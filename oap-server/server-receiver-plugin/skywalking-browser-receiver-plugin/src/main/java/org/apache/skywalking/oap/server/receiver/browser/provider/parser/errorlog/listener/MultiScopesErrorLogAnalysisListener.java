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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.listener;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppTrafficCategory;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.BrowserErrorLogDecorator;

/**
 * MultiScopesErrorLogAnalysisListener analysis error log, the kinds of error and error sum metrics.
 */
public class MultiScopesErrorLogAnalysisListener implements ErrorLogAnalysisListener {
    private final SourceReceiver sourceReceiver;

    private final SourceBuilder sourceBuilder;

    public MultiScopesErrorLogAnalysisListener(final SourceReceiver sourceReceiver, final NamingControl namingControl) {
        this.sourceReceiver = sourceReceiver;
        this.sourceBuilder = new SourceBuilder(namingControl);
    }

    /**
     * Send BrowserAppTraffic, BrowserAppSingleVersionTraffic, BrowserAppPageTraffic and BrowserAppPagePerf scope to the
     * receiver.
     */
    @Override
    public void build() {
        sourceReceiver.receive(sourceBuilder.toBrowserAppTraffic());
        sourceReceiver.receive(sourceBuilder.toBrowserAppSingleVersionTraffic());
        sourceReceiver.receive(sourceBuilder.toBrowserAppPageTraffic());
    }

    @Override
    public void parse(final BrowserErrorLogDecorator decorator) {
        sourceBuilder.setService(decorator.getService());
        sourceBuilder.setServiceVersion(decorator.getServiceVersion());
        sourceBuilder.setPatePath(decorator.getPagePath());

        // time bucket
        sourceBuilder.setTimeBucket(TimeBucket.getMinuteTimeBucket(decorator.getTime()));

        // category
        sourceBuilder.setTrafficCategory(
            decorator.isFirstReportedError() ? BrowserAppTrafficCategory.FIRST_ERROR : BrowserAppTrafficCategory.ERROR);
        sourceBuilder.setErrorCategory(decorator.getCategory());
    }

    public static class Factory implements ErrorLogListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;

        public Factory(ModuleManager moduleManager, BrowserServiceModuleConfig moduleConfig) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
            this.namingControl = moduleManager.find(CoreModule.NAME).provider().getService(NamingControl.class);
        }

        @Override
        public ErrorLogAnalysisListener create(final ModuleManager moduleManager,
                                               final BrowserServiceModuleConfig moduleConfig) {
            return new MultiScopesErrorLogAnalysisListener(sourceReceiver, namingControl);
        }
    }
}
