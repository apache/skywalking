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

package org.apache.skywalking.oap.server.receiver.zipkin.trace;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.EndpointMeta;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.handler.SpanEncode;
import org.apache.skywalking.oap.server.storage.plugin.zipkin.ZipkinSpan;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

@RequiredArgsConstructor
public class SpanForward {
    private static final String DEFAULT_SERVICE_INSTANCE_NAME = " unknown_instance";
    private final NamingControl namingControl;
    private final SourceReceiver receiver;
    private final ZipkinReceiverConfig config;

    public void send(List<Span> spanList) {
        spanList.forEach(span -> {
            ZipkinSpan zipkinSpan = new ZipkinSpan();
            zipkinSpan.setTraceId(span.traceId());
            zipkinSpan.setSpanId(span.id());
            String serviceName = span.localServiceName();
            if (StringUtil.isEmpty(serviceName)) {
                serviceName = "Unknown";
            }
            serviceName = namingControl.formatServiceName(serviceName);
            String serviceId = IDManager.ServiceID.buildId(serviceName, NodeType.Normal);
            zipkinSpan.setServiceId(serviceId);
            String serviceInstanceName = this.getServiceInstanceName(span);
            serviceInstanceName = namingControl.formatInstanceName(serviceInstanceName);
            zipkinSpan.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(serviceId, serviceInstanceName));

            long startTime = span.timestampAsLong() / 1000;
            zipkinSpan.setStartTime(startTime);
            long timeBucket = TimeBucket.getRecordTimeBucket(zipkinSpan.getStartTime());
            zipkinSpan.setTimeBucket(timeBucket);

            String spanName = span.name();
            if (!StringUtil.isEmpty(spanName)) {
                final String endpointName = namingControl.formatEndpointName(serviceName, spanName);
                zipkinSpan.setEndpointName(endpointName);
                zipkinSpan.setEndpointId(IDManager.EndpointID.buildId(zipkinSpan.getServiceId(), endpointName));

                //Create endpoint meta for the server side span
                EndpointMeta endpointMeta = new EndpointMeta();
                endpointMeta.setServiceName(serviceName);
                endpointMeta.setServiceNodeType(NodeType.Normal);
                endpointMeta.setEndpoint(endpointName);
                endpointMeta.setTimeBucket(timeBucket);
                receiver.receive(endpointMeta);
            }
            long latency = span.durationAsLong() / 1000;

            zipkinSpan.setEndTime(startTime + latency);
            zipkinSpan.setIsError(BooleanUtils.booleanToValue(false));
            zipkinSpan.setEncode(SpanEncode.PROTO3);
            zipkinSpan.setLatency((int) latency);
            zipkinSpan.setDataBinary(SpanBytesEncoder.PROTO3.encode(span));

            span.tags().forEach((key, value) -> {
                zipkinSpan.getTags().add(key + "=" + value);
            });

            receiver.receive(zipkinSpan);

            // Create the metadata source
            // No instance name is required in the Zipkin model.
            ServiceMeta serviceMeta = new ServiceMeta();
            serviceMeta.setName(serviceName);
            serviceMeta.setNodeType(NodeType.Normal);
            serviceMeta.setTimeBucket(timeBucket);
            receiver.receive(serviceMeta);
        });
    }

    private String getServiceInstanceName(Span span) {
        for (String tagName : config.getInstanceNameRule()) {
            String serviceInstanceName = span.tags().get(tagName);
            if (StringUtil.isNotEmpty(serviceInstanceName)) {
                return serviceInstanceName;
            }
        }
        return DEFAULT_SERVICE_INSTANCE_NAME;
    }
}
