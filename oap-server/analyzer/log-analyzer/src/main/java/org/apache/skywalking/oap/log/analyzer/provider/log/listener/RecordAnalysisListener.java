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

package org.apache.skywalking.oap.log.analyzer.provider.log.listener;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.source.Log;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * RecordAnalysisListener forwards the log data to the persistence layer with the query required conditions.
 */
@RequiredArgsConstructor
public class RecordAnalysisListener implements LogAnalysisListener {
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    private final List<String> searchableTagKeys;
    private final Log log = new Log();

    @Override
    public void build() {
        sourceReceiver.receive(log);
    }

    @Override
    public LogAnalysisListener parse(final LogData.Builder logData) {
        LogDataBody body = logData.getBody();
        log.setUniqueId(UUID.randomUUID().toString().replace("-", ""));
        // timestamp
        log.setTimestamp(logData.getTimestamp());
        log.setTimeBucket(TimeBucket.getRecordTimeBucket(logData.getTimestamp()));

        // service
        String serviceName = namingControl.formatServiceName(logData.getService());
        String serviceId = IDManager.ServiceID.buildId(serviceName, NodeType.Normal);
        log.setServiceId(serviceId);
        // service instance
        if (StringUtil.isNotEmpty(logData.getServiceInstance())) {
            log.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(
                serviceId,
                namingControl.formatInstanceName(logData.getServiceInstance())
            ));
        }
        // endpoint
        if (StringUtil.isNotEmpty(logData.getEndpoint())) {
            String endpointName = namingControl.formatEndpointName(serviceName, logData.getEndpoint());
            log.setEndpointId(IDManager.EndpointID.buildId(serviceId, endpointName));
            log.setEndpointName(endpointName);
        }
        // trace
        TraceContext traceContext = logData.getTraceContext();
        if (StringUtil.isNotEmpty(traceContext.getTraceId())) {
            log.setTraceId(traceContext.getTraceId());
        }
        if (StringUtil.isNotEmpty(traceContext.getTraceSegmentId())) {
            log.setTraceSegmentId(traceContext.getTraceSegmentId());
            log.setSpanId(traceContext.getSpanId());
        }
        // content
        if (body.hasText()) {
            log.setContentType(ContentType.TEXT);
            log.setContent(body.getText().getText());
        } else if (body.hasYaml()) {
            log.setContentType(ContentType.YAML);
            log.setContent(body.getYaml().getYaml());
        } else if (body.hasJson()) {
            log.setContentType(ContentType.JSON);
            log.setContent(body.getJson().getJson());
        }
        if (logData.getTags().getDataCount() > 0) {
            log.setTagsRawData(logData.getTags().toByteArray());
        }
        log.getTags().addAll(appendSearchableTags(logData));
        return this;
    }

    private Collection<Tag> appendSearchableTags(LogData.Builder logData) {
        HashSet<Tag> logTags = new HashSet<>();
        logData.getTags().getDataList().forEach(tag -> {
            if (searchableTagKeys.contains(tag.getKey())) {
                final Tag logTag = new Tag(tag.getKey(), tag.getValue());
                logTags.add(logTag);
            }
        });
        return logTags;
    }

    public static class Factory implements LogAnalysisListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;
        private final List<String> searchableTagKeys;

        public Factory(ModuleManager moduleManager, LogAnalyzerModuleConfig moduleConfig) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(SourceReceiver.class);
            this.namingControl = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(NamingControl.class);
            ConfigService configService = moduleManager.find(CoreModule.NAME)
                                                       .provider()
                                                       .getService(ConfigService.class);
            this.searchableTagKeys = Arrays.asList(configService.getSearchableLogsTags().split(Const.COMMA));
        }

        @Override
        public LogAnalysisListener create() {
            return new RecordAnalysisListener(sourceReceiver, namingControl, searchableTagKeys);
        }
    }
}
