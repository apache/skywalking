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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.google.protobuf.InvalidProtocolBufferException;
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
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventTraceType;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
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
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static java.util.Objects.nonNull;

public class TraceQueryService implements Service {

    private final ModuleManager moduleManager;
    private ITraceQueryDAO traceQueryDAO;
    private ISpanAttachedEventQueryDAO spanAttachedEventQueryDAO;
    private IComponentLibraryCatalogService componentLibraryCatalogService;

    public TraceQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ITraceQueryDAO getTraceQueryDAO() {
        if (traceQueryDAO == null) {
            this.traceQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ITraceQueryDAO.class);
        }
        return traceQueryDAO;
    }

    private ISpanAttachedEventQueryDAO getSpanAttachedEventQueryDAO() {
        if (spanAttachedEventQueryDAO == null) {
            this.spanAttachedEventQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ISpanAttachedEventQueryDAO.class);
        }
        return spanAttachedEventQueryDAO;
    }

    private IComponentLibraryCatalogService getComponentLibraryCatalogService() {
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
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);

        return getTraceQueryDAO().queryBasicTraces(
            duration, minTraceDuration, maxTraceDuration, serviceId, serviceInstanceId, endpointId,
            traceId, page.getLimit(), page.getFrom(), traceState, queryOrder, tags
        );
    }

    public Trace queryTrace(final String traceId) throws IOException {
        Trace trace = new Trace();

        List<SegmentRecord> segmentRecords = getTraceQueryDAO().queryByTraceId(traceId);
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

        List<Span> sortedSpans = new LinkedList<>();
        if (CollectionUtils.isNotEmpty(trace.getSpans())) {
            List<Span> rootSpans = findRoot(trace.getSpans());

            if (CollectionUtils.isNotEmpty(rootSpans)) {
                rootSpans.forEach(span -> {
                    List<Span> childrenSpan = new ArrayList<>();
                    childrenSpan.add(span);
                    findChildren(trace.getSpans(), span, childrenSpan);
                    sortedSpans.addAll(childrenSpan);
                });
            }
        }

        if (CollectionUtils.isNotEmpty(sortedSpans)) {
            final List<SpanAttachedEventRecord> spanAttachedEvents = getSpanAttachedEventQueryDAO().
                querySpanAttachedEvents(SpanAttachedEventTraceType.SKYWALKING, traceId);
            appendAttachedEventsToSpan(sortedSpans, spanAttachedEvents);
        }

        trace.getSpans().clear();
        trace.getSpans().addAll(sortedSpans);
        return trace;
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

    private void appendAttachedEventsToSpan(List<Span> spans, List<SpanAttachedEventRecord> events) throws InvalidProtocolBufferException {
        if (CollectionUtils.isEmpty(events)) {
            return;
        }

        // sort by start time
        events.sort((e1, e2) -> {
            final int second = Long.compare(e1.getStartTimeSecond(), e2.getStartTimeSecond());
            if (second == 0) {
                return Long.compare(e1.getStartTimeNanos(), e2.getStartTimeNanos());
            }
            return second;
        });

        final HashMap<String, Span> spanMatcher = new HashMap<>();
        for (SpanAttachedEventRecord record : events) {
            if (!StringUtils.isNumeric(record.getTraceSpanId())) {
                continue;
            }
            SpanAttachedEvent event = SpanAttachedEvent.parseFrom(record.getDataBinary());
            final String spanMatcherKey = record.getTraceSegmentId() + "_" + record.getTraceSpanId();
            Span span = spanMatcher.get(spanMatcherKey);
            if (span == null) {
                // find the matches span
                final int eventSpanId = Integer.parseInt(record.getTraceSpanId());
                span = spans.stream().filter(s -> Objects.equals(s.getSegmentId(), record.getTraceSegmentId()) &&
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
