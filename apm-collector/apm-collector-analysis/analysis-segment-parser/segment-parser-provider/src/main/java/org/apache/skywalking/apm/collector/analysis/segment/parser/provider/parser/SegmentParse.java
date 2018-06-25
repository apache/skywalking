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

package org.apache.skywalking.apm.collector.analysis.segment.parser.provider.parser;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.*;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.*;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.graph.GraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.*;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParseService;
import org.apache.skywalking.apm.collector.analysis.segment.parser.provider.parser.standardization.*;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.graph.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.segment.Segment;
import org.apache.skywalking.apm.network.proto.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class SegmentParse {

    private static final Logger logger = LoggerFactory.getLogger(SegmentParse.class);

    private final ModuleManager moduleManager;
    private final List<SpanListener> spanListeners;
    private final SegmentParserListenerManager listenerManager;
    private final SegmentCoreInfo segmentCoreInfo;

    public SegmentParse(ModuleManager moduleManager, SegmentParserListenerManager listenerManager) {
        this.moduleManager = moduleManager;
        this.listenerManager = listenerManager;
        this.spanListeners = new LinkedList<>();
        this.segmentCoreInfo = new SegmentCoreInfo();
        this.segmentCoreInfo.setStartTime(Long.MAX_VALUE);
        this.segmentCoreInfo.setEndTime(Long.MIN_VALUE);
    }

    @GraphComputingMetric(name = "/segment/parse")
    public boolean parse(UpstreamSegment segment, ISegmentParseService.Source source) {
        createSpanListeners();

        try {
            List<UniqueId> traceIds = segment.getGlobalTraceIdsList();
            TraceSegmentObject segmentObject = parseBinarySegment(segment);

            SegmentDecorator segmentDecorator = new SegmentDecorator(segmentObject);

            if (!preBuild(traceIds, segmentDecorator)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("This segment id exchange not success, write to buffer file, id: {}", segmentCoreInfo.getSegmentId());
                }

                if (source.equals(ISegmentParseService.Source.Agent)) {
                    writeToBufferFile(segmentCoreInfo.getSegmentId(), segment);
                }
                return false;
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("This segment id exchange success, id: {}", segmentCoreInfo.getSegmentId());
                }

                notifyListenerToBuild();
                buildSegment(segmentCoreInfo.getSegmentId(), segmentDecorator.toByteArray());
                return true;
            }
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            return true;
        }
    }

    @GraphComputingMetric(name = "/segment/parse/parseBinarySegment")
    private TraceSegmentObject parseBinarySegment(UpstreamSegment segment) throws InvalidProtocolBufferException {
        return TraceSegmentObject.parseFrom(segment.getSegment());
    }

    @GraphComputingMetric(name = "/segment/parse/preBuild")
    private boolean preBuild(List<UniqueId> traceIds, SegmentDecorator segmentDecorator) {
        StringBuilder segmentIdBuilder = new StringBuilder();

        for (int i = 0; i < segmentDecorator.getTraceSegmentId().getIdPartsList().size(); i++) {
            if (i == 0) {
                segmentIdBuilder.append(segmentDecorator.getTraceSegmentId().getIdPartsList().get(i));
            } else {
                segmentIdBuilder.append(".").append(segmentDecorator.getTraceSegmentId().getIdPartsList().get(i));
            }
        }

        for (UniqueId uniqueId : traceIds) {
            notifyGlobalsListener(uniqueId);
        }

        segmentCoreInfo.setSegmentId(segmentIdBuilder.toString());
        segmentCoreInfo.setApplicationId(segmentDecorator.getApplicationId());
        segmentCoreInfo.setApplicationInstanceId(segmentDecorator.getApplicationInstanceId());

        for (int i = 0; i < segmentDecorator.getSpansCount(); i++) {
            SpanDecorator spanDecorator = segmentDecorator.getSpans(i);

            if (!SpanIdExchanger.getInstance(moduleManager).exchange(spanDecorator, segmentCoreInfo.getApplicationId())) {
                return false;
            } else {
                for (int j = 0; j < spanDecorator.getRefsCount(); j++) {
                    ReferenceDecorator referenceDecorator = spanDecorator.getRefs(j);
                    if (!ReferenceIdExchanger.getInstance(moduleManager).exchange(referenceDecorator, segmentCoreInfo.getApplicationId())) {
                        return false;
                    }
                }
            }

            if (segmentCoreInfo.getStartTime() > spanDecorator.getStartTime()) {
                segmentCoreInfo.setStartTime(spanDecorator.getStartTime());
            }
            if (segmentCoreInfo.getEndTime() < spanDecorator.getEndTime()) {
                segmentCoreInfo.setEndTime(spanDecorator.getEndTime());
            }
            segmentCoreInfo.setError(spanDecorator.getIsError() || segmentCoreInfo.isError());
        }

        long minuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(segmentCoreInfo.getStartTime());
        segmentCoreInfo.setMinuteTimeBucket(minuteTimeBucket);

        for (int i = 0; i < segmentDecorator.getSpansCount(); i++) {
            SpanDecorator spanDecorator = segmentDecorator.getSpans(i);

            if (spanDecorator.getSpanId() == 0) {
                notifyFirstListener(spanDecorator);
            }

            if (SpanType.Exit.equals(spanDecorator.getSpanType())) {
                notifyExitListener(spanDecorator);
            } else if (SpanType.Entry.equals(spanDecorator.getSpanType())) {
                notifyEntryListener(spanDecorator);
            } else if (SpanType.Local.equals(spanDecorator.getSpanType())) {
                notifyLocalListener(spanDecorator);
            } else {
                logger.error("span type value was unexpected, span type name: {}", spanDecorator.getSpanType().name());
            }
        }

        return true;
    }

    @GraphComputingMetric(name = "/segment/parse/buildSegment")
    private void buildSegment(String id, byte[] dataBinary) {
        Segment segment = new Segment();
        segment.setId(id);
        segment.setDataBinary(dataBinary);
        segment.setTimeBucket(segmentCoreInfo.getMinuteTimeBucket());
        Graph<Segment> graph = GraphManager.INSTANCE.findGraph(GraphIdDefine.SEGMENT_PERSISTENCE_GRAPH_ID, Segment.class);
        graph.start(segment);
    }

    @GraphComputingMetric(name = "/segment/parse/bufferFile/write")
    private void writeToBufferFile(String id, UpstreamSegment upstreamSegment) {
        if (logger.isDebugEnabled()) {
            logger.debug("push to segment buffer write worker, id: {}", id);
        }

        SegmentStandardization standardization = new SegmentStandardization(id);
        standardization.setUpstreamSegment(upstreamSegment);
        Graph<SegmentStandardization> graph = GraphManager.INSTANCE.findGraph(GraphIdDefine.SEGMENT_STANDARDIZATION_GRAPH_ID, SegmentStandardization.class);
        graph.start(standardization);
    }

    @GraphComputingMetric(name = "/segment/parse/notifyListenerToBuild")
    private void notifyListenerToBuild() {
        spanListeners.forEach(SpanListener::build);
    }

    @GraphComputingMetric(name = "/segment/parse/notifyExitListener")
    private void notifyExitListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.Exit)) {
                ((ExitSpanListener)listener).parseExit(spanDecorator, segmentCoreInfo);
            }
        });
    }

    @GraphComputingMetric(name = "/segment/parse/notifyEntryListener")
    private void notifyEntryListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.Entry)) {
                ((EntrySpanListener)listener).parseEntry(spanDecorator, segmentCoreInfo);
            }
        });
    }

    @GraphComputingMetric(name = "/segment/parse/notifyLocalListener")
    private void notifyLocalListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.Local)) {
                ((LocalSpanListener)listener).parseLocal(spanDecorator, segmentCoreInfo);
            }
        });
    }

    @GraphComputingMetric(name = "/segment/parse/notifyFirstListener")
    private void notifyFirstListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.First)) {
                ((FirstSpanListener)listener).parseFirst(spanDecorator, segmentCoreInfo);
            }
        });
    }

    @GraphComputingMetric(name = "/segment/parse/notifyGlobalsListener")
    private void notifyGlobalsListener(UniqueId uniqueId) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.GlobalTraceIds)) {
                ((GlobalTraceIdsListener)listener).parseGlobalTraceId(uniqueId, segmentCoreInfo);
            }
        });
    }

    @GraphComputingMetric(name = "/segment/parse/createSpanListeners")
    private void createSpanListeners() {
        listenerManager.getSpanListenerFactories().forEach(spanListenerFactory -> spanListeners.add(spanListenerFactory.create(moduleManager)));
    }
}
