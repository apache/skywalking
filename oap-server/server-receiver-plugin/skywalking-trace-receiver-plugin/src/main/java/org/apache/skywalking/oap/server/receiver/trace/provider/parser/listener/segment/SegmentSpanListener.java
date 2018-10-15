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

import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.EndpointInventoryCache;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class SegmentSpanListener implements FirstSpanListener, EntrySpanListener, GlobalTraceIdsListener {

    private static final Logger logger = LoggerFactory.getLogger(SegmentSpanListener.class);

    private final SourceReceiver sourceReceiver;
    private final Segment segment = new Segment();
    private final EndpointInventoryCache serviceNameCacheService;
    private int entryEndpointId = 0;
    private int firstEndpointId = 0;

    private SegmentSpanListener(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).getService(SourceReceiver.class);
        this.serviceNameCacheService = moduleManager.find(CoreModule.NAME).getService(EndpointInventoryCache.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.First.equals(point) || Point.Entry.equals(point) || Point.TraceIds.equals(point);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        long timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(segmentCoreInfo.getStartTime());

        segment.setSegmentId(segmentCoreInfo.getSegmentId());
        segment.setServiceId(segmentCoreInfo.getApplicationId());
        segment.setLatency((int)(segmentCoreInfo.getEndTime() - segmentCoreInfo.getStartTime()));
        segment.setStartTime(segmentCoreInfo.getStartTime());
        segment.setEndTime(segmentCoreInfo.getEndTime());
        segment.setIsError(BooleanUtils.booleanToValue(segmentCoreInfo.isError()));
        segment.setTimeBucket(timeBucket);
        segment.setDataBinary(segmentCoreInfo.getDataBinary());

        firstEndpointId = spanDecorator.getOperationNameId();
    }

    @Override public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        entryEndpointId = spanDecorator.getOperationNameId();
    }

    @Override public void parseGlobalTraceId(UniqueId uniqueId, SegmentCoreInfo segmentCoreInfo) {
        StringBuilder traceIdBuilder = new StringBuilder();
        for (int i = 0; i < uniqueId.getIdPartsList().size(); i++) {
            if (i == 0) {
                traceIdBuilder.append(uniqueId.getIdPartsList().get(i));
            } else {
                traceIdBuilder.append(".").append(uniqueId.getIdPartsList().get(i));
            }
        }
        segment.setTraceId(traceIdBuilder.toString());
    }

    @Override public void build() {
        if (logger.isDebugEnabled()) {
            logger.debug("segment duration listener build");
        }

        if (entryEndpointId == 0) {
            segment.setEndpointId(firstEndpointId);
            segment.setEndpointName(serviceNameCacheService.get(firstEndpointId).getName());
        } else {
            segment.setEndpointId(entryEndpointId);
            segment.setEndpointName(serviceNameCacheService.get(entryEndpointId).getName());
        }

        sourceReceiver.receive(segment);
    }

    public static class Factory implements SpanListenerFactory {

        @Override public SpanListener create(ModuleManager moduleManager) {
            return new SegmentSpanListener(moduleManager);
        }
    }
}
