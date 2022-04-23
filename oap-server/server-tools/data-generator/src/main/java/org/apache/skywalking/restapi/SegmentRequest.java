/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.restapi;

import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.generator.Generator;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.source.Segment;
import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public final class SegmentRequest implements Generator<Segment> {
    private Generator<String> segmentId;
    private Generator<String> traceId;
    private Generator<String> serviceName;
    private Generator<String> serviceInstanceName;
    private Generator<String> endpointName;
    private Generator<Long> startTime;
    private Generator<Long> latency;
    private Generator<Long> error;
    private Generator<List<TagGenerator>> tags;
    private Generator<List<SpanGenerator>> spans;

    @Override
    public Segment next() {
        final String traceId = getTraceId().next();
        final String serviceName = getServiceName().next();
        final String serviceInstanceName = getServiceInstanceName().next();
        final String endpointName = getEndpointName().next();
        final String segmentId = getSegmentId().next();

        final SegmentObject segmentObj = SegmentObject
            .newBuilder()
            .setTraceId(traceId)
            .setTraceSegmentId(segmentId)
            .addAllSpans(
                getSpans()
                    .next()
                    .stream()
                    .map(SpanGenerator::next)
                    .collect(Collectors.<SpanObject>toList()))
            .setService(serviceName)
            .setServiceInstance(serviceInstanceName)
            .build();

        // Reset the span generator to generate the span id from 0
        getSpans().reset();

        final Segment segment = new Segment();
        segment.setSegmentId(segmentId);
        segment.setTraceId(traceId);
        segment.setServiceId(
            IDManager.ServiceID.buildId(serviceName, true));
        segment.setServiceInstanceId(
            IDManager.ServiceInstanceID.buildId(
                segment.getServiceId(),
                serviceInstanceName));
        segment.setEndpointId(
            IDManager.EndpointID.buildId(
                segment.getServiceId(),
                endpointName));
        segment.setStartTime(getStartTime().next());
        segment.setLatency(getLatency().next().intValue());
        segment.setIsError(getError().next().intValue());
        segment.setTimeBucket(TimeBucket.getRecordTimeBucket(segment.getStartTime()));
        segment.setTags(
            getTags()
                .next()
                .stream()
                .map(TagGenerator::next)
                .collect(Collectors.<Tag>toList()));

        segment.setDataBinary(segmentObj.toByteArray());
        return segment;
    }
}
