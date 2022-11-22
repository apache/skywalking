/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.EndpointMeta;
import org.apache.skywalking.oap.server.core.source.MQAccess;
import org.apache.skywalking.oap.server.core.source.MQEndpointAccess;
import org.apache.skywalking.oap.server.core.source.MQOperation;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@RequiredArgsConstructor
public class VirtualMQProcessor implements VirtualServiceProcessor {

    private final NamingControl namingControl;
    private final List<Source> sourceList = new ArrayList<>();

    @Override
    public void prepareVSIfNecessary(final SpanObject span, final SegmentObject segmentObject) {
        if (span.getSpanLayer() != SpanLayer.MQ) {
            return;
        }
        if (!(span.getSpanType() == SpanType.Exit || span.getSpanType() == SpanType.Entry)) {
            return;
        }
        final String peer;
        final MQOperation mqOperation;
        if (span.getSpanType() == SpanType.Entry) {
            mqOperation = MQOperation.Consume;
            peer = span.getRefsList()
                       .stream()
                       .findFirst()
                       .map(SegmentReference::getNetworkAddressUsedAtPeer)
                       .filter(StringUtil::isNotBlank)
                       .orElse(span.getPeer());
        } else {
            mqOperation = MQOperation.Produce;
            peer = span.getPeer();
        }
        if (StringUtil.isBlank(peer)) {
            return;
        }
        MQTags mqTags = collectTags(span.getTagsList());
        String serviceName = namingControl.formatServiceName(peer);
        long timeBucket = TimeBucket.getMinuteTimeBucket(span.getStartTime());
        sourceList.add(toServiceMeta(serviceName, timeBucket));

        MQAccess access = new MQAccess();
        access.setTypeId(span.getComponentId());
        access.setTransmissionLatency(mqTags.transmissionLatency);
        access.setName(serviceName);
        access.setStatus(!span.getIsError());
        access.setTimeBucket(timeBucket);
        access.setOperation(mqOperation);
        sourceList.add(access);

        String endpoint = buildEndpointName(mqTags.topic, mqTags.queue);
        if (!endpoint.isEmpty()) {
            String endpointName = namingControl.formatEndpointName(serviceName, endpoint);
            sourceList.add(toEndpointMeta(serviceName, endpointName, timeBucket));
            MQEndpointAccess endpointAccess = new MQEndpointAccess();
            endpointAccess.setTypeId(span.getComponentId());
            endpointAccess.setTransmissionLatency(mqTags.transmissionLatency);
            endpointAccess.setStatus(!span.getIsError());
            endpointAccess.setTimeBucket(timeBucket);
            endpointAccess.setOperation(mqOperation);
            endpointAccess.setServiceName(serviceName);
            endpointAccess.setEndpoint(endpointName);
            sourceList.add(endpointAccess);
        }
    }

    private String buildEndpointName(String topic, String queue) {
        return Stream.of(topic, queue)
                     .filter(StringUtil::isNotBlank)
                     .reduce((a, b) -> a + "/" + b).orElse("");
    }

    private MQTags collectTags(final List<KeyStringValuePair> tagsList) {
        MQTags mqTags = new MQTags();
        for (KeyStringValuePair keyStringValuePair : tagsList) {
            if (SpanTags.MQ_TOPIC.equals(keyStringValuePair.getKey())) {
                mqTags.topic = keyStringValuePair.getValue();
            } else if (SpanTags.MQ_QUEUE.equals(keyStringValuePair.getKey())) {
                mqTags.queue = keyStringValuePair.getValue();
            } else if (SpanTags.TRANSMISSION_LATENCY.equals(keyStringValuePair.getKey())) {
                mqTags.transmissionLatency = StringUtil.isBlank(keyStringValuePair.getValue()) ? 0L : Long.parseLong(
                    keyStringValuePair.getValue());
            }
        }
        return mqTags;
    }

    private ServiceMeta toServiceMeta(String serviceName, Long timeBucket) {
        ServiceMeta service = new ServiceMeta();
        service.setName(serviceName);
        service.setLayer(Layer.VIRTUAL_MQ);
        service.setTimeBucket(timeBucket);
        return service;
    }

    private EndpointMeta toEndpointMeta(String serviceName, String endpoint, Long timeBucket) {
        EndpointMeta endpointMeta = new EndpointMeta();
        endpointMeta.setServiceName(serviceName);
        endpointMeta.setServiceNormal(false);
        endpointMeta.setEndpoint(endpoint);
        endpointMeta.setTimeBucket(timeBucket);
        return endpointMeta;
    }

    @Override
    public void emitTo(final Consumer<Source> consumer) {
        sourceList.forEach(consumer);
    }

    private static class MQTags {
        private String topic;
        private String queue;
        private long transmissionLatency;
    }

}
