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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.segment;

import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.Segment;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SegmentCoreInfo;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SpanDecorator;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.EntrySpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.FirstSpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.GlobalTraceIdsListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SpanListenerFactory;

/**
 * SegmentSpanListener forwards the segment raw data to the persistence layer with the query required conditions.
 */
@Slf4j
public class SegmentSpanListener implements FirstSpanListener, EntrySpanListener, GlobalTraceIdsListener {
    private final SourceReceiver sourceReceiver;
    private final TraceSegmentSampler sampler;
    private final Segment segment = new Segment();
    private SAMPLE_STATUS sampleStatus = SAMPLE_STATUS.UNKNOWN;
    private String endpointId = "";
    private String endpointName = "";

    private SegmentSpanListener(ModuleManager moduleManager, TraceSegmentSampler sampler) {
        this.sampler = sampler;
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
    }

    @Override
    public boolean containsPoint(Point point) {
        return Point.First.equals(point) || Point.Entry.equals(point) || Point.TraceIds.equals(point);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        if (sampleStatus.equals(SAMPLE_STATUS.IGNORE)) {
            return;
        }

        long timeBucket = TimeBucket.getRecordTimeBucket(segmentCoreInfo.getStartTime());

        segment.setSegmentId(segmentCoreInfo.getSegmentId());
        segment.setServiceId(segmentCoreInfo.getServiceId());
        segment.setServiceInstanceId(segmentCoreInfo.getServiceInstanceId());
        segment.setLatency((int) (segmentCoreInfo.getEndTime() - segmentCoreInfo.getStartTime()));
        segment.setStartTime(segmentCoreInfo.getStartTime());
        segment.setEndTime(segmentCoreInfo.getEndTime());
        segment.setIsError(BooleanUtils.booleanToValue(segmentCoreInfo.isError()));
        segment.setTimeBucket(timeBucket);
        segment.setDataBinary(segmentCoreInfo.getDataBinary());
        segment.setVersion(segmentCoreInfo.getVersion().number());

        endpointId = EndpointTraffic.buildId(segmentCoreInfo.getServiceId(), spanDecorator.getOperationName(),
                                             DetectPoint.fromSpanType(spanDecorator.getSpanType())
        );
        endpointName = spanDecorator.getOperationName();
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        endpointId = EndpointTraffic.buildId(segmentCoreInfo.getServiceId(), spanDecorator.getOperationName(),
                                             DetectPoint.fromSpanType(spanDecorator.getSpanType())
        );
        endpointName = spanDecorator.getOperationName();
    }

    @Override
    public void parseGlobalTraceId(UniqueId uniqueId, SegmentCoreInfo segmentCoreInfo) {
        if (sampleStatus.equals(SAMPLE_STATUS.UNKNOWN) || sampleStatus.equals(SAMPLE_STATUS.IGNORE)) {
            if (sampler.shouldSample(uniqueId)) {
                sampleStatus = SAMPLE_STATUS.SAMPLED;
            } else {
                sampleStatus = SAMPLE_STATUS.IGNORE;
            }
        }

        if (sampleStatus.equals(SAMPLE_STATUS.IGNORE)) {
            return;
        }

        final String traceId = uniqueId.getIdPartsList().stream().map(String::valueOf).collect(Collectors.joining("."));
        segment.setTraceId(traceId);
    }

    @Override
    public void build() {
        if (log.isDebugEnabled()) {
            log.debug("segment listener build, segment id: {}", segment.getSegmentId());
        }

        if (sampleStatus.equals(SAMPLE_STATUS.IGNORE)) {
            return;
        }

        segment.setEndpointId(endpointId);
        segment.setEndpointName(endpointName);

        sourceReceiver.receive(segment);
    }

    private enum SAMPLE_STATUS {
        UNKNOWN, SAMPLED, IGNORE
    }

    public static class Factory implements SpanListenerFactory {
        private final TraceSegmentSampler sampler;

        public Factory(int segmentSamplingRate) {
            this.sampler = new TraceSegmentSampler(segmentSamplingRate);
        }

        @Override
        public SpanListener create(ModuleManager moduleManager, TraceServiceModuleConfig config) {
            return new SegmentSpanListener(moduleManager, sampler);
        }
    }
}
