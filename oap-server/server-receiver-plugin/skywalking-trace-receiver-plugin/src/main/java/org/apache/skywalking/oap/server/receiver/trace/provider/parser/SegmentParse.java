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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.*;
import lombok.Setter;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.oap.server.library.buffer.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.TimeBucketUtils;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization.*;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
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
    private final TraceServiceModuleConfig config;
    @Setter private SegmentStandardizationWorker standardizationWorker;
    private volatile static CounterMetric TRACE_BUFFER_FILE_RETRY;
    private volatile static CounterMetric TRACE_BUFFER_FILE_OUT;
    private volatile static CounterMetric TRACE_PARSE_ERROR;

    private SegmentParse(ModuleManager moduleManager, SegmentParserListenerManager listenerManager,
        TraceServiceModuleConfig config) {
        this.moduleManager = moduleManager;
        this.listenerManager = listenerManager;
        this.spanListeners = new LinkedList<>();
        this.segmentCoreInfo = new SegmentCoreInfo();
        this.segmentCoreInfo.setStartTime(Long.MAX_VALUE);
        this.segmentCoreInfo.setEndTime(Long.MIN_VALUE);
        this.segmentCoreInfo.setV2(false);
        this.config = config;

        MetricCreator metricCreator = moduleManager.find(TelemetryModule.NAME).provider().getService(MetricCreator.class);
        TRACE_BUFFER_FILE_RETRY = metricCreator.createCounter("v5_trace_buffer_file_retry", "The number of retry trace segment from the buffer file, but haven't registered successfully.",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
        TRACE_BUFFER_FILE_OUT = metricCreator.createCounter("v5_trace_buffer_file_out", "The number of trace segment out of the buffer file",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
        TRACE_PARSE_ERROR = metricCreator.createCounter("v5_trace_parse_error", "The number of trace segment out of the buffer file",
            MetricTag.EMPTY_KEY, MetricTag.EMPTY_VALUE);
    }

    public boolean parse(BufferData<UpstreamSegment> bufferData, Source source) {
        createSpanListeners();

        try {
            UpstreamSegment upstreamSegment = bufferData.getMessageType();
            List<UniqueId> traceIds = upstreamSegment.getGlobalTraceIdsList();

            if (bufferData.getV1Segment() == null) {
                bufferData.setV1Segment(parseBinarySegment(upstreamSegment));
            }
            TraceSegmentObject segmentObject = bufferData.getV1Segment();

            SegmentDecorator segmentDecorator = new SegmentDecorator(segmentObject);

            if (!preBuild(traceIds, segmentDecorator)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("This segment id exchange not success, write to buffer file, id: {}", segmentCoreInfo.getSegmentId());
                }

                if (source.equals(Source.Agent)) {
                    writeToBufferFile(segmentCoreInfo.getSegmentId(), upstreamSegment);
                } else {
                    // from SegmentSource.Buffer
                    TRACE_BUFFER_FILE_RETRY.inc();
                }
                return false;
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("This segment id exchange success, id: {}", segmentCoreInfo.getSegmentId());
                }
                TRACE_BUFFER_FILE_OUT.inc();
                notifyListenerToBuild();
                return true;
            }
        } catch (Throwable e) {
            TRACE_PARSE_ERROR.inc();
            logger.error(e.getMessage(), e);
            return true;
        }
    }

    private TraceSegmentObject parseBinarySegment(UpstreamSegment segment) throws InvalidProtocolBufferException {
        return TraceSegmentObject.parseFrom(segment.getSegment());
    }

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
        segmentCoreInfo.setServiceId(segmentDecorator.getServiceId());
        segmentCoreInfo.setServiceInstanceId(segmentDecorator.getServiceInstanceId());
        segmentCoreInfo.setDataBinary(segmentDecorator.toByteArray());
        segmentCoreInfo.setV2(false);

        boolean exchanged = true;

        for (int i = 0; i < segmentDecorator.getSpansCount(); i++) {
            SpanDecorator spanDecorator = segmentDecorator.getSpans(i);

            if (!SpanIdExchanger.getInstance(moduleManager).exchange(spanDecorator, segmentCoreInfo.getServiceId())) {
                exchanged = false;
            } else {
                for (int j = 0; j < spanDecorator.getRefsCount(); j++) {
                    ReferenceDecorator referenceDecorator = spanDecorator.getRefs(j);
                    if (!ReferenceIdExchanger.getInstance(moduleManager).exchange(referenceDecorator, segmentCoreInfo.getServiceId())) {
                        exchanged = false;
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

        if (exchanged) {
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
        }

        return exchanged;
    }

    private void writeToBufferFile(String id, UpstreamSegment upstreamSegment) {
        if (logger.isDebugEnabled()) {
            logger.debug("push to segment buffer write worker, id: {}", id);
        }

        SegmentStandardization standardization = new SegmentStandardization(id);
        standardization.setUpstreamSegment(upstreamSegment);

        standardizationWorker.in(standardization);
    }

    private void notifyListenerToBuild() {
        spanListeners.forEach(SpanListener::build);
    }

    private void notifyExitListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.Exit)) {
                ((ExitSpanListener)listener).parseExit(spanDecorator, segmentCoreInfo);
            }
        });
    }

    private void notifyEntryListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.Entry)) {
                ((EntrySpanListener)listener).parseEntry(spanDecorator, segmentCoreInfo);
            }
        });
    }

    private void notifyLocalListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.Local)) {
                ((LocalSpanListener)listener).parseLocal(spanDecorator, segmentCoreInfo);
            }
        });
    }

    private void notifyFirstListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.First)) {
                ((FirstSpanListener)listener).parseFirst(spanDecorator, segmentCoreInfo);
            }
        });
    }

    private void notifyGlobalsListener(UniqueId uniqueId) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.TraceIds)) {
                ((GlobalTraceIdsListener)listener).parseGlobalTraceId(uniqueId, segmentCoreInfo);
            }
        });
    }

    private void createSpanListeners() {
        listenerManager.getSpanListenerFactories().forEach(spanListenerFactory -> spanListeners.add(spanListenerFactory.create(moduleManager, config)));
    }

    public enum Source {
        Agent, Buffer
    }

    public static class Producer implements DataStreamReader.CallBack<UpstreamSegment> {

        @Setter private SegmentStandardizationWorker standardizationWorker;
        private final ModuleManager moduleManager;
        private final SegmentParserListenerManager listenerManager;
        private final TraceServiceModuleConfig config;

        public Producer(ModuleManager moduleManager, SegmentParserListenerManager listenerManager,
            TraceServiceModuleConfig config) {
            this.moduleManager = moduleManager;
            this.listenerManager = listenerManager;
            this.config = config;
        }

        public void send(UpstreamSegment segment, Source source) {
            SegmentParse segmentParse = new SegmentParse(moduleManager, listenerManager, config);
            segmentParse.setStandardizationWorker(standardizationWorker);
            segmentParse.parse(new BufferData<>(segment), source);
        }

        @Override public boolean call(BufferData<UpstreamSegment> bufferData) {
            SegmentParse segmentParse = new SegmentParse(moduleManager, listenerManager, config);
            segmentParse.setStandardizationWorker(standardizationWorker);
            boolean parseResult = segmentParse.parse(bufferData, Source.Buffer);
            if (parseResult) {
                segmentParse.TRACE_BUFFER_FILE_OUT.inc();
            }

            return parseResult;
        }
    }
}
