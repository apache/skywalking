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
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.EndpointInventoryCache;
import org.apache.skywalking.oap.server.core.source.Segment;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SegmentCoreInfo;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SpanDecorator;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.EntrySpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.FirstSpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.GlobalTraceIdsListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SpanListenerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SegmentSpanListener implements FirstSpanListener, EntrySpanListener, GlobalTraceIdsListener {

    private static final Logger logger = LoggerFactory.getLogger(SegmentSpanListener.class);

    private final SourceReceiver sourceReceiver;
    private final TraceSegmentSampler sampler;
    private final Segment segment = new Segment();
    private final EndpointInventoryCache serviceNameCacheService;
    private SAMPLE_STATUS sampleStatus = SAMPLE_STATUS.UNKNOWN;
    private int entryEndpointId = 0;
    private int firstEndpointId = 0;
    private String firstEndpointName = "";

    private SegmentSpanListener(ModuleManager moduleManager, TraceSegmentSampler sampler) {
        this.sampler = sampler;
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.serviceNameCacheService = moduleManager.find(CoreModule.NAME).provider().getService(EndpointInventoryCache.class);
    }

    @Override public boolean containsPoint(Point point) {
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
        segment.setLatency((int)(segmentCoreInfo.getEndTime() - segmentCoreInfo.getStartTime()));
        segment.setStartTime(segmentCoreInfo.getStartTime());
        segment.setEndTime(segmentCoreInfo.getEndTime());
        segment.setIsError(BooleanUtils.booleanToValue(segmentCoreInfo.isError()));
        segment.setTimeBucket(timeBucket);
        segment.setDataBinary(segmentCoreInfo.getDataBinary());
        segment.setVersion(segmentCoreInfo.getVersion().number());

        firstEndpointId = spanDecorator.getOperationNameId();
        firstEndpointName = spanDecorator.getOperationName();
    }

    @Override public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        entryEndpointId = spanDecorator.getOperationNameId();
    }

    @Override public void parseGlobalTraceId(UniqueId uniqueId, SegmentCoreInfo segmentCoreInfo) {
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

    @Override public void build() {
        if (logger.isDebugEnabled()) {
            logger.debug("segment listener build, segment id: {}", segment.getSegmentId());
        }

        if (sampleStatus.equals(SAMPLE_STATUS.IGNORE)) {
            return;
        }

        if (entryEndpointId == Const.NONE) {
            if (firstEndpointId != Const.NONE) {
                /*
                 * Since 6.6.0, only entry span is treated as an endpoint. Other span's endpoint id == 0.
                 */
                segment.setEndpointId(firstEndpointId);
                segment.setEndpointName(serviceNameCacheService.get(firstEndpointId).getName());
            } else {
                /*
                 * Only fill first operation name for the trace list query, as no endpoint id.
                 */
                segment.setEndpointName(firstEndpointName);
            }
        } else {
            segment.setEndpointId(entryEndpointId);
            segment.setEndpointName(serviceNameCacheService.get(entryEndpointId).getName());
        }

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

        @Override public SpanListener create(ModuleManager moduleManager, TraceServiceModuleConfig config) {
            return new SegmentSpanListener(moduleManager, sampler);
        }
    }
}
