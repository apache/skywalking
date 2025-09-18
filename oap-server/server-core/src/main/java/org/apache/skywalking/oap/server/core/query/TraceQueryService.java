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

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.google.protobuf.InvalidProtocolBufferException;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.common.v3.KeyIntValuePair;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventTraceType;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyNumericValue;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.LogEntity;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Ref;
import org.apache.skywalking.oap.server.core.query.type.RefType;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.Trace;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceList;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceV2;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TracesQueryResult;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryV2DAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.Source;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public class TraceQueryService implements Service {

    private final ModuleManager moduleManager;
    private ITraceQueryDAO traceQueryDAO;
    private ISpanAttachedEventQueryDAO spanAttachedEventQueryDAO;
    private IComponentLibraryCatalogService componentLibraryCatalogService;
    @Getter
    private boolean supportTraceV2 = false;

    public TraceQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    protected ITraceQueryDAO getTraceQueryDAO() {
        if (traceQueryDAO == null) {
            this.traceQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ITraceQueryDAO.class);
            if (ITraceQueryV2DAO.class.isAssignableFrom(traceQueryDAO.getClass())) {
                this.supportTraceV2 = true;
            }
        }
        return traceQueryDAO;
    }

    private ISpanAttachedEventQueryDAO getSpanAttachedEventQueryDAO() {
        if (spanAttachedEventQueryDAO == null) {
            this.spanAttachedEventQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ISpanAttachedEventQueryDAO.class);
        }
        return spanAttachedEventQueryDAO;
    }

    protected IComponentLibraryCatalogService getComponentLibraryCatalogService() {
        if (componentLibraryCatalogService == null) {
            this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME)
                                                               .provider()
                                                               .getService(IComponentLibraryCatalogService.class);
        }
        return componentLibraryCatalogService;
    }

    public TraceBrief queryBasicTraces(final String serviceId,
                                       final String serviceInstanceId,
                                       final String endpointId,
                                       final String traceId,
                                       final int minTraceDuration,
                                       int maxTraceDuration,
                                       final TraceState traceState,
                                       final QueryOrder queryOrder,
                                       final Pagination paging,
                                       final Duration duration,
                                       final List<Tag> tags) throws IOException {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                StringBuilder msg = new StringBuilder();
                span = traceContext.createSpan("Query Service: queryBasicTraces");
                msg.append("Condition: ServiceId: ").append(serviceId)
                   .append(", ServiceInstanceId: ").append(serviceInstanceId)
                   .append(", EndpointId: ").append(endpointId)
                   .append(", TraceId: ").append(traceId)
                   .append(", MinTraceDuration: ").append(minTraceDuration)
                   .append(", MaxTraceDuration: ").append(maxTraceDuration)
                   .append(", TraceState: ").append(traceState)
                   .append(", QueryOrder: ").append(queryOrder)
                   .append(", Pagination: ").append(paging)
                   .append(", Duration: ").append(duration)
                   .append(", Tags: ").append(tags);
                span.setMsg(msg.toString());
            }
            PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);

            return getTraceQueryDAO().queryBasicTracesDebuggable(
                duration, minTraceDuration, maxTraceDuration, serviceId, serviceInstanceId, endpointId,
                traceId, page.getLimit(), page.getFrom(), traceState, queryOrder, tags
            );
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    /**
     * @param duration nullable unless for BanyanDB query from cold stage
     */
    public Trace queryTrace(final String traceId, @Nullable final Duration duration) throws IOException {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                StringBuilder msg = new StringBuilder();
                span = traceContext.createSpan("Query Service: queryTrace");
                msg.append("Condition: TraceId: ").append(traceId);
                span.setMsg(msg.toString());
            }
            getTraceQueryDAO();
            if (supportTraceV2) {
                return invokeQueryTraceV2(traceId, duration);
            } else {
                return invokeQueryTrace(traceId, duration);
            }
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    public TraceList queryTraces(final TraceQueryCondition condition) throws IOException {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                StringBuilder msg = new StringBuilder();
                span = traceContext.createSpan("Query Service: queryTraces");
                msg.append("Condition: TraceQueryCondition: ").append(condition);
                span.setMsg(msg.toString());
            }
            getTraceQueryDAO();
            if (supportTraceV2) {
                return invokeQueryTraces(condition);
            } else {
                throw new UnsupportedOperationException("Only BanyanDB storage support Trace V2 query now.");
            }
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    public boolean hasQueryTracesV2Support() {
        getTraceQueryDAO();
        return isSupportTraceV2();
    }

    private Trace invokeQueryTrace(final String traceId, @Nullable final Duration duration) throws IOException {
        Trace trace = new Trace();

        List<SegmentRecord> segmentRecords = getTraceQueryDAO().queryByTraceIdDebuggable(traceId, duration);
        if (segmentRecords.isEmpty()) {
            trace.getSpans().addAll(getTraceQueryDAO().doFlexibleTraceQuery(traceId));
        } else {
            for (SegmentRecord segment : segmentRecords) {
                if (nonNull(segment)) {
                    SegmentObject segmentObject = SegmentObject.parseFrom(segment.getDataBinary());
                    trace.getSpans()
                         .addAll(buildSpanList(segmentObject));
                }
            }
        }

        List<Span> sortedSpans = sortSpans(trace.getSpans());

        if (CollectionUtils.isNotEmpty(sortedSpans)) {
            final List<SWSpanAttachedEventRecord> spanAttachedEvents = getSpanAttachedEventQueryDAO().
                querySWSpanAttachedEventsDebuggable(SpanAttachedEventTraceType.SKYWALKING, Arrays.asList(traceId), duration);
            List<SpanAttachedEvent> events = new ArrayList<>();
            for (SWSpanAttachedEventRecord record : spanAttachedEvents) {
                events.add(SpanAttachedEvent.parseFrom(record.getDataBinary()));
            }
            appendAttachedEventsToSpanDebuggable(sortedSpans, events);
        }

        trace.getSpans().clear();
        trace.getSpans().addAll(sortedSpans);
        return trace;
    }

    private Trace invokeQueryTraceV2(final String traceId, @Nullable final Duration duration) throws IOException {
        Trace trace = new Trace();
        List<SpanWrapper> spanWrappers = ((ITraceQueryV2DAO) getTraceQueryDAO()).queryByTraceIdV2(traceId, duration);
        if (CollectionUtils.isNotEmpty(spanWrappers)) {
            List<SpanAttachedEvent> events = new ArrayList<>();
            for (SpanWrapper spanWrapper : spanWrappers) {
                if (spanWrapper.getSource().equals(Source.SKYWALKING)) {
                    SegmentObject segmentObject = SegmentObject.parseFrom(spanWrapper.getSpan());
                    trace.getSpans().addAll(buildSpanList(segmentObject));
                } else if (spanWrapper.getSource().equals(Source.SKYWALKING_EVENT)) {
                    SpanAttachedEvent event = SpanAttachedEvent.parseFrom(spanWrapper.getSpan());
                    events.add(event);
                }
            }
            List<Span> sortedSpans = sortSpans(trace.getSpans());
            appendAttachedEventsToSpanDebuggable(sortedSpans, events);
            trace.getSpans().clear();
            trace.getSpans().addAll(sortedSpans);
        }
        return trace;
    }

    private TraceList invokeQueryTraces(final TraceQueryCondition condition) throws IOException {
        TracesQueryResult tracesResult = ((ITraceQueryV2DAO) getTraceQueryDAO()).queryTracesDebuggable(condition);
        TraceList traceList = new TraceList(tracesResult.getRetrievedTimeRange());
        List<TraceV2> traces = new ArrayList<>();
        for (List<SpanWrapper> spans : tracesResult.getTraces()) {
            TraceV2 trace = new TraceV2();
            List<SpanAttachedEvent> events = new ArrayList<>();
            for (SpanWrapper spanWrapper : spans) {
                if (spanWrapper.getSource().equals(Source.SKYWALKING)) {
                    SegmentObject segmentObject = SegmentObject.parseFrom(spanWrapper.getSpan());
                    trace.getSpans().addAll(buildSpanList(segmentObject));
                } else if (spanWrapper.getSource().equals(Source.SKYWALKING_EVENT)) {
                    SpanAttachedEvent event = SpanAttachedEvent.parseFrom(spanWrapper.getSpan());
                    events.add(event);
                }
            }
            List<Span> sortedSpans = sortSpans(trace.getSpans());
            trace.getSpans().clear();
            trace.getSpans().addAll(sortedSpans);
            appendAttachedEventsToSpanDebuggable(sortedSpans, events);
            traces.add(trace);
        }
        traceList.getTraces().addAll(traces);
        return traceList;
    }

    private List<Span> buildSpanList(SegmentObject segmentObject) {
        List<Span> spans = new ArrayList<>();

        segmentObject.getSpansList().forEach(spanObject -> {
            Span span = new Span();
            span.setTraceId(segmentObject.getTraceId());
            span.setSegmentId(segmentObject.getTraceSegmentId());
            span.setSpanId(spanObject.getSpanId());
            span.setParentSpanId(spanObject.getParentSpanId());
            span.setStartTime(spanObject.getStartTime());
            span.setEndTime(spanObject.getEndTime());
            span.setError(spanObject.getIsError());
            span.setLayer(spanObject.getSpanLayer().name());
            span.setType(spanObject.getSpanType().name());

            String segmentSpanId = segmentObject.getTraceSegmentId() + Const.SEGMENT_SPAN_SPLIT + spanObject.getSpanId();
            span.setSegmentSpanId(segmentSpanId);

            String segmentParentSpanId = segmentObject.getTraceSegmentId() + Const.SEGMENT_SPAN_SPLIT + spanObject.getParentSpanId();
            span.setSegmentParentSpanId(segmentParentSpanId);

            span.setPeer(spanObject.getPeer());

            span.setEndpointName(spanObject.getOperationName());

            span.setServiceCode(segmentObject.getService());
            span.setServiceInstanceName(segmentObject.getServiceInstance());

            span.setComponent(getComponentLibraryCatalogService().getComponentName(spanObject.getComponentId()));

            spanObject.getRefsList().forEach(reference -> {
                Ref ref = new Ref();
                ref.setTraceId(reference.getTraceId());
                ref.setParentSegmentId(reference.getParentTraceSegmentId());

                switch (reference.getRefType()) {
                    case CrossThread:
                        ref.setType(RefType.CROSS_THREAD);
                        break;
                    case CrossProcess:
                        ref.setType(RefType.CROSS_PROCESS);
                        break;
                }
                ref.setParentSpanId(reference.getParentSpanId());

                span.setSegmentParentSpanId(
                    ref.getParentSegmentId() + Const.SEGMENT_SPAN_SPLIT + ref.getParentSpanId());

                span.getRefs().add(ref);
            });

            spanObject.getTagsList().forEach(tag -> {
                KeyValue keyValue = new KeyValue();
                keyValue.setKey(tag.getKey());
                keyValue.setValue(tag.getValue());
                span.getTags().add(keyValue);
            });

            spanObject.getLogsList().forEach(log -> {
                LogEntity logEntity = new LogEntity();
                logEntity.setTime(log.getTime());

                log.getDataList().forEach(data -> {
                    KeyValue keyValue = new KeyValue();
                    keyValue.setKey(data.getKey());
                    keyValue.setValue(data.getValue());
                    logEntity.getData().add(keyValue);
                });

                span.getLogs().add(logEntity);
            });

            spans.add(span);
        });

        return spans;
    }

    private List<Span> sortSpans(List<Span> spans) {
        List<Span> sortedSpans = new LinkedList<>();
        if (CollectionUtils.isNotEmpty(spans)) {
            List<Span> rootSpans = findRoot(spans);

            if (CollectionUtils.isNotEmpty(rootSpans)) {
                rootSpans.forEach(span -> {
                    List<Span> childrenSpan = new ArrayList<>();
                    childrenSpan.add(span);
                    findChildren(spans, span, childrenSpan);
                    sortedSpans.addAll(childrenSpan);
                });
            }
        }
        return  sortedSpans;
    }

    private List<Span> findRoot(List<Span> spans) {
        List<Span> rootSpans = new ArrayList<>();
        spans.forEach(span -> {
            String segmentParentSpanId = span.getSegmentParentSpanId();

            boolean hasParent = false;
            for (Span subSpan : spans) {
                if (segmentParentSpanId.equals(subSpan.getSegmentSpanId())) {
                    hasParent = true;
                    // if find parent, quick exit
                    break;
                }
            }

            if (!hasParent) {
                span.setRoot(true);
                rootSpans.add(span);
            }
        });
        /*
         * In some cases, there are segment fragments, which could not be linked by Ref,
         * because of two kinds of reasons.
         * 1. Multiple leaf segments have no particular order in the storage.
         * 2. Lost in sampling, agent fail safe, segment lost, even bug.
         * Sorting the segments makes the trace view more readable.
         */
        rootSpans.sort(Comparator.comparing(Span::getStartTime));
        return rootSpans;
    }

    private void findChildren(List<Span> spans, Span parentSpan, List<Span> childrenSpan) {
        spans.forEach(span -> {
            if (span.getSegmentParentSpanId().equals(parentSpan.getSegmentSpanId())) {
                childrenSpan.add(span);
                findChildren(spans, span, childrenSpan);
            }
        });
    }

    private void appendAttachedEventsToSpanDebuggable(List<Span> spans, List<SpanAttachedEvent> events) throws InvalidProtocolBufferException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan debuggingSpan = null;
        try {
            if (traceContext != null) {
                debuggingSpan = traceContext.createSpan("Query Service : appendAttachedEventsToSpan");
            }
            appendAttachedEventsToSpan(spans, events);
        } finally {
            if (traceContext != null && debuggingSpan != null) {
                traceContext.stopSpan(debuggingSpan);

            }
        }
    }

    private void appendAttachedEventsToSpan(List<Span> spans, List<SpanAttachedEvent> events) throws InvalidProtocolBufferException {
        if (CollectionUtils.isEmpty(events)) {
            return;
        }

        // sort by start time
        events.sort((e1, e2) -> {
            final int second = Long.compare(e1.getStartTime().getSeconds(), e2.getStartTime().getSeconds());
            if (second == 0) {
                return Long.compare(e1.getStartTime().getNanos(), e2.getStartTime().getNanos());
            }
            return second;
        });

        final HashMap<String, Span> spanMatcher = new HashMap<>();
        for (SpanAttachedEvent event : events) {
            if (!StringUtils.isNumeric(event.getTraceContext().getSpanId())) {
                continue;
            }
            SpanAttachedEvent.SpanReference spanReference = event.getTraceContext();
            final String spanMatcherKey = spanReference.getTraceSegmentId() + "_" + spanReference.getSpanId();
            Span span = spanMatcher.get(spanMatcherKey);
            if (span == null) {
                // find the matches span
                final int eventSpanId = Integer.parseInt(spanReference.getSpanId());
                span = spans.stream().filter(s -> Objects.equals(s.getSegmentId(), spanReference.getTraceSegmentId()) &&
                    (s.getSpanId() == eventSpanId)).findFirst().orElse(null);
                if (span == null) {
                    continue;
                }

                // if the event is server side, then needs to change to the upstream span
                final String direction = getSpanAttachedEventTagValue(event.getTagsList(), "data_direction");
                final String type = getSpanAttachedEventTagValue(event.getTagsList(), "data_type");

                if (("request".equals(type) && "inbound".equals(direction)) || ("response".equals(type) && "outbound".equals(direction))) {
                    final String parentSpanId = span.getSegmentSpanId();
                    span = spans.stream().filter(s -> s.getSegmentParentSpanId().equals(parentSpanId)
                        && Objects.equals(s.getType(), SpanType.Entry.name())).findFirst().orElse(span);
                }

                spanMatcher.put(spanMatcherKey, span);
            }

            span.getAttachedEvents().add(parseEvent(event));
        }
    }

    private String getSpanAttachedEventTagValue(List<KeyStringValuePair> values, String tagKey) {
        for (KeyStringValuePair pair : values) {
            if (Objects.equals(pair.getKey(), tagKey)) {
                return pair.getValue();
            }
        }
        return null;
    }

    private org.apache.skywalking.oap.server.core.query.type.SpanAttachedEvent parseEvent(SpanAttachedEvent event) {
        final org.apache.skywalking.oap.server.core.query.type.SpanAttachedEvent result =
            new org.apache.skywalking.oap.server.core.query.type.SpanAttachedEvent();
        result.getStartTime().setSeconds(event.getStartTime().getSeconds());
        result.getStartTime().setNanos(event.getStartTime().getNanos());
        result.getEndTime().setSeconds(event.getEndTime().getSeconds());
        result.getEndTime().setNanos(event.getEndTime().getNanos());
        result.setEvent(event.getEvent());
        for (KeyStringValuePair tag : event.getTagsList()) {
            result.getTags().add(new KeyValue(tag.getKey(), tag.getValue()));
        }
        for (KeyIntValuePair pair : event.getSummaryList()) {
            result.getSummary().add(new KeyNumericValue(pair.getKey(), pair.getValue()));
        }
        return result;
    }
}
