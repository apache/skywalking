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

package org.apache.skywalking.oap.server.analyzer.provider.log;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogTag;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.source.Log;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;

/**
 * Process when receive the log data
 */
@RequiredArgsConstructor
public class LogProcessor {

    private final SourceReceiver sourceReceiver;

    private final NamingControl namingControl;

    private final List<String> searchableTagKeys;

    public void process(final LogData logData) {
        Log log = new Log();
        LogDataBody body = logData.getBody();
        // timestamp
        long timestamp;
        if (logData.getTimestamp() == 0) {
            timestamp = System.currentTimeMillis();
        } else {
            timestamp = logData.getTimestamp();
        }
        log.setTimestamp(timestamp);
        log.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));

        // service
        String serviceName = namingControl.formatServiceName(logData.getService());
        String serviceId = IDManager.ServiceID.buildId(serviceName, NodeType.Normal);
        log.setServiceId(serviceId);

        // service instance
        log.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(
            serviceId,
            namingControl.formatInstanceName(logData.getServiceInstance())
        ));

        // endpoint
        String endpointName = namingControl.formatEndpointName(serviceName, logData.getEndpoint());
        log.setEndpointId(IDManager.EndpointID.buildId(serviceId, endpointName));
        log.setEndpointName(endpointName);

        // trace context
        TraceContext traceContext = logData.getTraceContext();
        log.setTraceId(traceContext.getTraceId());
        log.setTraceSegmentId(traceContext.getTraceSegmentId());
        log.setSpanId(traceContext.getSpanId());

        // content
        if (body.hasText()) {
            log.setContentType(ContentType.TEXT);
            log.setContent(body.getText().getText());
        } else if (body.hasJson()) {
            log.setContentType(ContentType.JSON);
            log.setContent(body.getJson().getJson());
        } else if (body.hasYaml()) {
            log.setContentType(ContentType.YAML);
            log.setContent(body.getYaml().getYaml());
        }
        // tags
        log.getTags().addAll(appendSearchableTags(logData));

        // TODO log analysis
        sourceReceiver.receive(log);
    }

    private Collection<LogTag> appendSearchableTags(LogData data) {
        HashSet<LogTag> logTags = new HashSet<>();
        data.getTagsList().forEach(tag -> {
            if (searchableTagKeys.contains(tag.getKey())) {
                final LogTag logTag = new LogTag(tag.getKey(), tag.getValue());
                if (!logTags.contains(logTag)) {
                    logTags.add(logTag);
                }
            }
        });
        return logTags;
    }
}
