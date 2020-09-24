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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.BrowserServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.BrowserErrorLogDecorator;

/**
 * ErrorLogRecordListener forwards the error log raw data to the persistence layer with the query required conditions.
 */
@Slf4j
@RequiredArgsConstructor
public class ErrorLogRecordListener implements ErrorLogAnalysisListener {
    private final NamingControl namingControl;
    private final SourceReceiver sourceReceiver;
    private final BrowserErrorLog errorLog = new BrowserErrorLog();
    private final ErrorLogRecordSampler sampler;
    private SampleStatus sampleStatus = SampleStatus.UNKNOWN;

    /**
     * Send BrowserErrorLog to the oreceiver.
     */
    @Override
    public void build() {
        if (sampleStatus.equals(SampleStatus.SAMPLED)) {
            sourceReceiver.receive(errorLog);
        }
    }

    @Override
    public void parse(final BrowserErrorLogDecorator decorator) {
        // sample
        if (StringUtil.isEmpty(decorator.getUniqueId())) {
            if (log.isDebugEnabled()) {
                log.debug("Because uniqueId is empty BrowserErrorLog is ignored.");
            }
            sampleStatus = SampleStatus.IGNORED;
            return;
        }

        if (!sampler.shouldSample(decorator.getUniqueId().hashCode())) {
            sampleStatus = SampleStatus.IGNORED;
            return;
        }
        sampleStatus = SampleStatus.SAMPLED;

        // error log
        errorLog.setUniqueId(decorator.getUniqueId());
        errorLog.setTimeBucket(TimeBucket.getRecordTimeBucket(decorator.getTime()));
        errorLog.setTimestamp(decorator.getTime());

        // service
        String serviceName = namingControl.formatServiceName(decorator.getService());
        String serviceId = IDManager.ServiceID.buildId(serviceName, NodeType.Browser);
        errorLog.setServiceId(serviceId);

        // service version
        errorLog.setServiceVersionId(IDManager.ServiceInstanceID.buildId(serviceId, namingControl.formatInstanceName(
            decorator.getServiceVersion())));

        // page
        String pagePath = namingControl.formatEndpointName(serviceName, decorator.getPagePath());
        errorLog.setPagePath(pagePath);
        errorLog.setPagePathId(IDManager.EndpointID.buildId(serviceId, pagePath));

        // raw data
        errorLog.setErrorCategory(BrowserErrorCategory.fromErrorCategory(decorator.getCategory()));
        errorLog.setDataBinary(decorator.toByteArray());
    }

    private enum SampleStatus {
        UNKNOWN, SAMPLED, IGNORED
    }

    public static class Factory implements ErrorLogListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;
        private final ErrorLogRecordSampler sampler;

        public Factory(ModuleManager moduleManager, BrowserServiceModuleConfig moduleConfig) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
            this.namingControl = moduleManager.find(CoreModule.NAME).provider().getService(NamingControl.class);
            this.sampler = new ErrorLogRecordSampler(moduleConfig.getSampleRate());
        }

        @Override
        public ErrorLogAnalysisListener create(final ModuleManager moduleManager,
                                               final BrowserServiceModuleConfig moduleConfig) {
            return new ErrorLogRecordListener(namingControl, sourceReceiver, sampler);
        }
    }
}
