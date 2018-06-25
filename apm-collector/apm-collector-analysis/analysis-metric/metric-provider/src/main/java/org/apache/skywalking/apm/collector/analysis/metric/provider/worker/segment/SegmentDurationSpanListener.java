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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.segment;

import java.util.*;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.*;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.*;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.graph.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.*;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDuration;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class SegmentDurationSpanListener implements FirstSpanListener, EntrySpanListener {

    private static final Logger logger = LoggerFactory.getLogger(SegmentDurationSpanListener.class);

    private final SegmentDuration segmentDuration;
    private final ServiceNameCacheService serviceNameCacheService;
    private Set<Integer> entryOperationNameIds;
    private int firstOperationNameId = 0;

    private SegmentDurationSpanListener(ModuleManager moduleManager) {
        this.segmentDuration = new SegmentDuration();
        this.entryOperationNameIds = new HashSet<>();
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.First.equals(point) || Point.Entry.equals(point);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        long timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(segmentCoreInfo.getStartTime());

        segmentDuration.setId(segmentCoreInfo.getSegmentId());
        segmentDuration.setTraceId(segmentCoreInfo.getTraceId());
        segmentDuration.setSegmentId(segmentCoreInfo.getSegmentId());
        segmentDuration.setApplicationId(segmentCoreInfo.getApplicationId());
        segmentDuration.setDuration(segmentCoreInfo.getEndTime() - segmentCoreInfo.getStartTime());
        segmentDuration.setStartTime(segmentCoreInfo.getStartTime());
        segmentDuration.setEndTime(segmentCoreInfo.getEndTime());
        segmentDuration.setIsError(BooleanUtils.booleanToValue(segmentCoreInfo.isError()));
        segmentDuration.setTimeBucket(timeBucket);

        firstOperationNameId = spanDecorator.getOperationNameId();
    }

    @Override public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        entryOperationNameIds.add(spanDecorator.getOperationNameId());
    }

    @Override public void build() {
        Graph<SegmentDuration> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.SEGMENT_DURATION_GRAPH_ID, SegmentDuration.class);

        if (logger.isDebugEnabled()) {
            logger.debug("segment duration listener build");
        }

        if (entryOperationNameIds.size() == 0) {
            segmentDuration.getServiceName().add(serviceNameCacheService.get(firstOperationNameId).getServiceName());
        } else {
            entryOperationNameIds.forEach(entryOperationNameId -> segmentDuration.getServiceName().add(serviceNameCacheService.get(entryOperationNameId).getServiceName()));
        }

        graph.start(segmentDuration);
    }

    public static class Factory implements SpanListenerFactory {

        @GraphComputingMetric(name = "/segment/parse/createSpanListeners/segmentDurationSpanListener")
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new SegmentDurationSpanListener(moduleManager);
        }
    }
}
