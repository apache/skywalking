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
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.ProtocolVersion;
import org.apache.skywalking.apm.network.language.agent.SpanType;
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.library.buffer.BufferData;
import org.apache.skywalking.oap.server.library.buffer.DataStreamReader;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.ReferenceDecorator;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SegmentCoreInfo;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SegmentDecorator;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SpanDecorator;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.EntrySpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.ExitSpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.FirstSpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.GlobalTraceIdsListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.LocalSpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization.ReferenceIdExchanger;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization.SegmentStandardization;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization.SegmentStandardizationWorker;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization.SpanExchanger;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * SegmentParseV2 replaced the SegmentParse(V1 is before 6.0.0) to drive the segment analysis. It includes the following
 * steps
 *
 * 1. Register data, name to ID register
 *
 * 2. If register unfinished, cache in the local buffer file. And back to (1).
 *
 * 3. If register finished, traverse the span and analysis by the given {@link SpanListener}s.
 *
 * 4. Notify the build event to all {@link SpanListener}s in order to forward all built sources into dispatchers.
 *
 * @since 6.0.0 In the 6.x, the V1 and V2 analysis both exist.
 * @since 7.0.0 SegmentParse(V1) has been removed permanently.
 */
@Slf4j
public class SegmentParseV2 {
    private final ModuleManager moduleManager;
    private final List<SpanListener> spanListeners;
    private final SegmentParserListenerManager listenerManager;
    private final SegmentCoreInfo segmentCoreInfo;
    private final TraceServiceModuleConfig config;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    @Setter
    private SegmentStandardizationWorker standardizationWorker;
    private volatile static CounterMetrics TRACE_BUFFER_FILE_RETRY;
    private volatile static CounterMetrics TRACE_BUFFER_FILE_OUT;
    private volatile static CounterMetrics TRACE_PARSE_ERROR;

    private SegmentParseV2(ModuleManager moduleManager, SegmentParserListenerManager listenerManager,
                           TraceServiceModuleConfig config) {
        this.moduleManager = moduleManager;
        this.listenerManager = listenerManager;
        this.spanListeners = new LinkedList<>();
        this.segmentCoreInfo = new SegmentCoreInfo();
        this.segmentCoreInfo.setStartTime(Long.MAX_VALUE);
        this.segmentCoreInfo.setEndTime(Long.MIN_VALUE);
        this.segmentCoreInfo.setVersion(ProtocolVersion.V2);
        this.config = config;

        if (TRACE_BUFFER_FILE_RETRY == null) {
            MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                         .provider()
                                                         .getService(MetricsCreator.class);
            TRACE_BUFFER_FILE_RETRY = metricsCreator.createCounter(
                "v6_trace_buffer_file_retry",
                "The number of retry trace segment from the buffer file, but haven't registered successfully.",
                MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE
            );
            TRACE_BUFFER_FILE_OUT = metricsCreator.createCounter(
                "v6_trace_buffer_file_out", "The number of trace segment out of the buffer file", MetricsTag.EMPTY_KEY,
                MetricsTag.EMPTY_VALUE
            );
            TRACE_PARSE_ERROR = metricsCreator.createCounter(
                "v6_trace_parse_error", "The number of trace segment out of the buffer file", MetricsTag.EMPTY_KEY,
                MetricsTag.EMPTY_VALUE
            );
        }

        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME)
                                                          .provider()
                                                          .getService(ServiceInstanceInventoryCache.class);
    }

    public boolean parse(BufferData<UpstreamSegment> bufferData, SegmentSource source) {
        createSpanListeners();

        try {
            UpstreamSegment upstreamSegment = bufferData.getMessageType();

            List<UniqueId> traceIds = upstreamSegment.getGlobalTraceIdsList();

            if (bufferData.getV2Segment() == null) {
                bufferData.setV2Segment(parseBinarySegment(upstreamSegment));
            }
            SegmentObject segmentObject = bufferData.getV2Segment();

            // Recheck in case that the segment comes from file buffer
            final int serviceInstanceId = segmentObject.getServiceInstanceId();
            if (serviceInstanceInventoryCache.get(serviceInstanceId) == null) {
                log.warn(
                    "Cannot recognize service instance id [{}] from cache, segment will be ignored", serviceInstanceId);
                return true; // to mark it "completed" thus won't be retried
            }

            SegmentDecorator segmentDecorator = new SegmentDecorator(segmentObject);

            if (!preBuild(traceIds, segmentDecorator)) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "This segment id exchange not success, write to buffer file, id: {}",
                        segmentCoreInfo.getSegmentId()
                    );
                }

                if (source.equals(SegmentSource.Agent)) {
                    writeToBufferFile(segmentCoreInfo.getSegmentId(), upstreamSegment);
                } else {
                    // from SegmentSource.Buffer
                    TRACE_BUFFER_FILE_RETRY.inc();
                }
                return false;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("This segment id exchange success, id: {}", segmentCoreInfo.getSegmentId());
                }

                notifyListenerToBuild();
                return true;
            }
        } catch (Throwable e) {
            TRACE_PARSE_ERROR.inc();
            log.error(e.getMessage(), e);
            return true;
        }
    }

    private SegmentObject parseBinarySegment(UpstreamSegment segment) throws InvalidProtocolBufferException {
        return SegmentObject.parseFrom(segment.getSegment());
    }

    private boolean preBuild(List<UniqueId> traceIds, SegmentDecorator segmentDecorator) {
        for (UniqueId uniqueId : traceIds) {
            notifyGlobalsListener(uniqueId);
        }

        final String segmentId = segmentDecorator.getTraceSegmentId()
                                                 .getIdPartsList()
                                                 .stream()
                                                 .map(String::valueOf)
                                                 .collect(Collectors.joining("."));
        segmentCoreInfo.setSegmentId(segmentId);
        segmentCoreInfo.setServiceId(segmentDecorator.getServiceId());
        segmentCoreInfo.setServiceInstanceId(segmentDecorator.getServiceInstanceId());
        segmentCoreInfo.setDataBinary(segmentDecorator.toByteArray());
        segmentCoreInfo.setVersion(ProtocolVersion.V2);

        boolean exchanged = true;

        for (int i = 0; i < segmentDecorator.getSpansCount(); i++) {
            SpanDecorator spanDecorator = segmentDecorator.getSpans(i);

            if (!SpanExchanger.getInstance(moduleManager).exchange(spanDecorator, segmentCoreInfo.getServiceId())) {
                exchanged = false;
            } else {
                for (int j = 0; j < spanDecorator.getRefsCount(); j++) {
                    ReferenceDecorator referenceDecorator = spanDecorator.getRefs(j);
                    if (!ReferenceIdExchanger.getInstance(moduleManager)
                                             .exchange(referenceDecorator, segmentCoreInfo.getServiceId())) {
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
            long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(segmentCoreInfo.getStartTime());
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
                    log.error("span type value was unexpected, span type name: {}", spanDecorator.getSpanType()
                                                                                                 .name());
                }
            }
        }

        return exchanged;
    }

    private void writeToBufferFile(String id, UpstreamSegment upstreamSegment) {
        if (log.isDebugEnabled()) {
            log.debug("push to segment buffer write worker, id: {}", id);
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
                ((ExitSpanListener) listener).parseExit(spanDecorator, segmentCoreInfo);
            }
        });
    }

    private void notifyEntryListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.Entry)) {
                ((EntrySpanListener) listener).parseEntry(spanDecorator, segmentCoreInfo);
            }
        });
    }

    private void notifyLocalListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.Local)) {
                ((LocalSpanListener) listener).parseLocal(spanDecorator, segmentCoreInfo);
            }
        });
    }

    private void notifyFirstListener(SpanDecorator spanDecorator) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.First)) {
                ((FirstSpanListener) listener).parseFirst(spanDecorator, segmentCoreInfo);
            }
        });
    }

    private void notifyGlobalsListener(UniqueId uniqueId) {
        spanListeners.forEach(listener -> {
            if (listener.containsPoint(SpanListener.Point.TraceIds)) {
                ((GlobalTraceIdsListener) listener).parseGlobalTraceId(uniqueId, segmentCoreInfo);
            }
        });
    }

    private void createSpanListeners() {
        listenerManager.getSpanListenerFactories()
                       .forEach(
                           spanListenerFactory -> spanListeners.add(spanListenerFactory.create(moduleManager, config)));
    }

    public static class Producer implements DataStreamReader.CallBack<UpstreamSegment> {

        @Setter
        private SegmentStandardizationWorker standardizationWorker;
        private final ModuleManager moduleManager;
        private final SegmentParserListenerManager listenerManager;
        private final TraceServiceModuleConfig config;

        public Producer(ModuleManager moduleManager, SegmentParserListenerManager listenerManager,
                        TraceServiceModuleConfig config) {
            this.moduleManager = moduleManager;
            this.listenerManager = listenerManager;
            this.config = config;
        }

        public void send(UpstreamSegment segment, SegmentSource source) {
            SegmentParseV2 segmentParse = new SegmentParseV2(moduleManager, listenerManager, config);
            segmentParse.setStandardizationWorker(standardizationWorker);
            segmentParse.parse(new BufferData<>(segment), source);
        }

        @Override
        public boolean call(BufferData<UpstreamSegment> bufferData) {
            SegmentParseV2 segmentParse = new SegmentParseV2(moduleManager, listenerManager, config);
            segmentParse.setStandardizationWorker(standardizationWorker);
            boolean parseResult = segmentParse.parse(bufferData, SegmentSource.Buffer);
            if (parseResult) {
                TRACE_BUFFER_FILE_OUT.inc();
            }

            return parseResult;
        }
    }
}
