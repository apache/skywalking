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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.Segment;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;

/**
 * SegmentSpanListener forwards the segment raw data to the persistence layer with the query required conditions.
 */
@Slf4j
@RequiredArgsConstructor
public class SegmentAnalysisListener implements FirstAnalysisListener, EntryAnalysisListener, SegmentListener {
    private final SourceReceiver sourceReceiver;
    private final TraceSegmentSampler sampler;
    private final NamingControl namingControl;

    private final Segment segment = new Segment();
    private SAMPLE_STATUS sampleStatus = SAMPLE_STATUS.UNKNOWN;
    private String serviceName = Const.EMPTY_STRING;
    private String serviceId = Const.EMPTY_STRING;
    private String endpointId = Const.EMPTY_STRING;
    private String endpointName = Const.EMPTY_STRING;
    private long startTimestamp;
    private long endTimestamp;
    private int duration;
    private boolean isError;

    @Override
    public boolean containsPoint(Point point) {
        return Point.First.equals(point) || Point.Entry.equals(point) || Point.Segment.equals(point);
    }

    @Override
    public void parseFirst(SpanObject span, SegmentObject segmentObject) {
        if (sampleStatus.equals(SAMPLE_STATUS.IGNORE)) {
            return;
        }

        if (StringUtil.isEmpty(serviceId)) {
            serviceName = namingControl.formatServiceName(segmentObject.getService());
            serviceId = IDManager.ServiceID.buildId(
                serviceName,
                NodeType.Normal
            );
        }

        long timeBucket = TimeBucket.getRecordTimeBucket(startTimestamp);

        segment.setSegmentId(segmentObject.getTraceSegmentId());
        segment.setServiceId(serviceId);
        segment.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(
            serviceId,
            namingControl.formatInstanceName(segmentObject.getServiceInstance())
        ));
        segment.setLatency(duration);
        segment.setStartTime(startTimestamp);
        segment.setTimeBucket(timeBucket);
        segment.setEndTime(endTimestamp);
        segment.setIsError(BooleanUtils.booleanToValue(isError));
        segment.setDataBinary(segmentObject.toByteArray());
        segment.setVersion(3);

        endpointName = namingControl.formatEndpointName(serviceName, span.getOperationName());
        endpointId = IDManager.EndpointID.buildId(
            serviceId,
            endpointName
        );

    }

    @Override
    public void parseEntry(SpanObject span, SegmentObject segmentObject) {
        if (StringUtil.isEmpty(serviceId)) {
            serviceName = namingControl.formatServiceName(segmentObject.getService());
            serviceId = IDManager.ServiceID.buildId(
                serviceName, NodeType.Normal);
        }

        endpointId = IDManager.EndpointID.buildId(
            serviceId,
            span.getOperationName()
        );

        endpointName = namingControl.formatEndpointName(serviceName, span.getOperationName());
        endpointId = IDManager.EndpointID.buildId(
            serviceId,
            endpointName
        );
    }

    @Override
    public void parseSegment(SegmentObject segmentObject) {
        if (sampleStatus.equals(SAMPLE_STATUS.UNKNOWN) || sampleStatus.equals(SAMPLE_STATUS.IGNORE)) {
            if (sampler.shouldSample(segmentObject.getTraceSegmentId())) {
                sampleStatus = SAMPLE_STATUS.SAMPLED;
            } else {
                sampleStatus = SAMPLE_STATUS.IGNORE;
            }
        }

        if (sampleStatus.equals(SAMPLE_STATUS.IGNORE)) {
            return;
        }

        segment.setTraceId(segmentObject.getTraceId());
        segmentObject.getSpansList().forEach(span -> {
            if (startTimestamp == 0 || startTimestamp > span.getStartTime()) {
                startTimestamp = span.getStartTime();
            }
            if (span.getEndTime() > endTimestamp) {
                endTimestamp = span.getEndTime();
            }
            if (!isError && span.getIsError()) {
                isError = true;
            }
        });
        final long accurateDuration = endTimestamp - startTimestamp;
        duration = accurateDuration > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) accurateDuration;
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

    public static class Factory implements AnalysisListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final TraceSegmentSampler sampler;
        private final NamingControl namingControl;

        public Factory(ModuleManager moduleManager, TraceServiceModuleConfig config) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
            this.sampler = new TraceSegmentSampler(config.getSampleRate());
            this.namingControl = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(NamingControl.class);
        }

        @Override
        public AnalysisListener create(ModuleManager moduleManager, TraceServiceModuleConfig config) {
            return new SegmentAnalysisListener(sourceReceiver, sampler, namingControl);
        }
    }
}
