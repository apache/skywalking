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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.generator.Generator;
import org.apache.skywalking.generator.StringGenerator;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.source.Segment;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@Slf4j
public class SegmentGenerator implements Generator<SegmentGenerator.SegmentContext, SegmentGenerator.SegmentResult> {

    private Generator<String, String> segmentId;
    private Generator<String, String> endpointName;
    private Generator<Object, Long> error;
    private Generator<Object, List<TagGenerator>> tags;
    private Generator<Object, List<SpanGenerator>> spans;
    private Generator<Object, Long> now;

    @Override
    public SegmentResult next(SegmentContext ctx) {
        long n = now.next(null);
        final String serviceName = ctx.serviceName;
        final String serviceInstanceName = ctx.serviceInstanceName;
        final String endpointName = getEndpointName().next(null);
        if (segmentId == null) {
            StringGenerator.Builder segmentIdBuilder = new StringGenerator.Builder();
            segmentIdBuilder.setLength(20);
            segmentIdBuilder.setNumbers(true);
            segmentIdBuilder.setLetters(true);
            segmentId = segmentIdBuilder.build();
        }

        final SegmentReference sr = Optional.ofNullable(ctx.parentSegment).flatMap(parentSegment -> parentSegment.segmentObject.getSpansList().stream()
                        .filter(span -> !Strings.isNullOrEmpty(span.getPeer()))
                        .findFirst().map(span -> SegmentReference
                                .newBuilder()
                                .setTraceId(ctx.traceId)
                                .setParentServiceInstance(parentSegment.segmentObject.getServiceInstance())
                                .setParentService(parentSegment.segmentObject.getService())
                                .setParentSpanId(span.getSpanId())
                                .setParentTraceSegmentId(parentSegment.segment.getSegmentId())
                                .setParentEndpoint(IDManager.EndpointID.analysisId(parentSegment.segment.getEndpointId()).getEndpointName())
                                .setNetworkAddressUsedAtPeer(serviceInstanceName)
                                .build()))
                .orElse(null);
        final String segmentId = getSegmentId().next(null);

        final List<SpanGenerator> spanGenerators = getSpans().next(null);
        int size = spanGenerators.size();
        final SegmentObject segmentObj = SegmentObject
                .newBuilder()
                .setTraceId(ctx.traceId)
                .setTraceSegmentId(segmentId)
                .addAllSpans(
                        IntStream.range(0, size)
                                .mapToObj(i -> {
                                    SpanGenerator sg = spanGenerators.get(i);
                                    return sg.next(new SpanGenerator.SpanGeneratorContext(i, size, sr, ctx.peer, n));
                                })
                                .collect(Collectors.<SpanObject>toList()))
                .setService(serviceName)
                .setServiceInstance(serviceInstanceName)
                .build();

        // Reset the span generator to generate the span id from 0
        getSpans().reset();

        Long latency = segmentObj.getSpansList().stream().reduce(0L, (l, span) -> l + (span.getEndTime() - span.getStartTime()), Long::sum);

        final Segment segment = new Segment();
        segment.setSegmentId(segmentId);
        segment.setTraceId(ctx.traceId);
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
        segment.setStartTime(n - latency);
        segment.setLatency(latency.intValue());
        segment.setIsError(getError().next(null).intValue());
        segment.setTimeBucket(TimeBucket.getRecordTimeBucket(segment.getStartTime()));
        segment.setTags(
                getTags()
                        .next(null)
                        .stream()
                        .map(tg -> tg.next(null))
                        .collect(Collectors.<Tag>toList()));

        return new SegmentResult(segment, segmentObj);
    }

    @RequiredArgsConstructor
    public static class SegmentContext {
        final String traceId;
        final String serviceName;
        final String serviceInstanceName;
        String peer;
        SegmentResult parentSegment;
    }

    @RequiredArgsConstructor
    public static class SegmentResult {
        final Segment segment;
        final SegmentObject segmentObject;
    }
}
