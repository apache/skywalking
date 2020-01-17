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
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v2.SpanObjectV2;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.cache.EndpointInventoryCache;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.entity.KeyValue;
import org.apache.skywalking.oap.server.core.query.entity.LogEntity;
import org.apache.skywalking.oap.server.core.query.entity.Pagination;
import org.apache.skywalking.oap.server.core.query.entity.QueryOrder;
import org.apache.skywalking.oap.server.core.query.entity.Ref;
import org.apache.skywalking.oap.server.core.query.entity.RefType;
import org.apache.skywalking.oap.server.core.query.entity.Span;
import org.apache.skywalking.oap.server.core.query.entity.Trace;
import org.apache.skywalking.oap.server.core.query.entity.TraceBrief;
import org.apache.skywalking.oap.server.core.query.entity.TraceState;
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public class TraceQueryService implements Service {

    private final ModuleManager moduleManager;
    private ITraceQueryDAO traceQueryDAO;
    private ServiceInventoryCache serviceInventoryCache;
    private EndpointInventoryCache endpointInventoryCache;
    private NetworkAddressInventoryCache networkAddressInventoryCache;
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

    private ServiceInventoryCache getServiceInventoryCache() {
        if (serviceInventoryCache == null) {
            this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        }
        return serviceInventoryCache;
    }

    private EndpointInventoryCache getEndpointInventoryCache() {
        if (endpointInventoryCache == null) {
            this.endpointInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(EndpointInventoryCache.class);
        }
        return endpointInventoryCache;
    }

    private NetworkAddressInventoryCache getNetworkAddressInventoryCache() {
        if (networkAddressInventoryCache == null) {
            this.networkAddressInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(NetworkAddressInventoryCache.class);
        }
        return networkAddressInventoryCache;
    }

    private IComponentLibraryCatalogService getComponentLibraryCatalogService() {
        if (componentLibraryCatalogService == null) {
            this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME).provider().getService(IComponentLibraryCatalogService.class);
        }
        return componentLibraryCatalogService;
    }

    public TraceBrief queryBasicTraces(final int serviceId, final int serviceInstanceId, final int endpointId,
        final String traceId, final String endpointName, final int minTraceDuration, int maxTraceDuration,
        final TraceState traceState, final QueryOrder queryOrder,
        final Pagination paging, final long startTB, final long endTB) throws IOException {
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);

        return getTraceQueryDAO().queryBasicTraces(startTB, endTB, minTraceDuration, maxTraceDuration, endpointName,
            serviceId, serviceInstanceId, endpointId, traceId, page.getLimit(), page.getFrom(), traceState, queryOrder);
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
                    trace.getSpans().addAll(buildSpanV2List(traceId, segment.getSegmentId(), segment.getServiceId(), segmentObject.getSpansList()));
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

        trace.getSpans().clear();
        trace.getSpans().addAll(sortedSpans);
        return trace;
    }

    private List<Span> buildSpanV2List(String traceId, String segmentId, int serviceId,
        List<SpanObjectV2> spanObjects) {
        List<Span> spans = new ArrayList<>();

        spanObjects.forEach(spanObject -> {
            Span span = new Span();
            span.setTraceId(traceId);
            span.setSegmentId(segmentId);
            span.setSpanId(spanObject.getSpanId());
            span.setParentSpanId(spanObject.getParentSpanId());
            span.setStartTime(spanObject.getStartTime());
            span.setEndTime(spanObject.getEndTime());
            span.setError(spanObject.getIsError());
            span.setLayer(spanObject.getSpanLayer().name());
            span.setType(spanObject.getSpanType().name());

            String segmentSpanId = segmentId + Const.SEGMENT_SPAN_SPLIT + spanObject.getSpanId();
            span.setSegmentSpanId(segmentSpanId);

            String segmentParentSpanId = segmentId + Const.SEGMENT_SPAN_SPLIT + spanObject.getParentSpanId();
            span.setSegmentParentSpanId(segmentParentSpanId);

            if (spanObject.getPeerId() == 0) {
                span.setPeer(spanObject.getPeer());
            } else {
                span.setPeer(getNetworkAddressInventoryCache().get(spanObject.getPeerId()).getName());
            }

            String endpointName = spanObject.getOperationName();
            if (spanObject.getOperationNameId() != 0) {
                EndpointInventory endpointInventory = getEndpointInventoryCache().get(spanObject.getOperationNameId());
                if (nonNull(endpointInventory)) {
                    endpointName = endpointInventory.getName();
                } else {
                    endpointName = Const.EMPTY_STRING;
                }
            }
            span.setEndpointName(endpointName);

            final ServiceInventory serviceInventory = getServiceInventoryCache().get(serviceId);
            if (serviceInventory != null) {
                span.setServiceCode(serviceInventory.getName());
            } else {
                span.setServiceCode("unknown");
            }

            if (spanObject.getComponentId() == 0) {
                span.setComponent(spanObject.getComponent());
            } else {
                span.setComponent(getComponentLibraryCatalogService().getComponentName(spanObject.getComponentId()));
            }

            spanObject.getRefsList().forEach(reference -> {
                Ref ref = new Ref();
                ref.setTraceId(traceId);

                switch (reference.getRefType()) {
                    case CrossThread:
                        ref.setType(RefType.CROSS_THREAD);
                        break;
                    case CrossProcess:
                        ref.setType(RefType.CROSS_PROCESS);
                        break;
                }
                ref.setParentSpanId(reference.getParentSpanId());

                final UniqueId uniqueId = reference.getParentTraceSegmentId();
                final String parentSegmentId = uniqueId.getIdPartsList().stream().map(String::valueOf).collect(Collectors.joining("."));
                ref.setParentSegmentId(parentSegmentId);

                span.setSegmentParentSpanId(ref.getParentSegmentId() + Const.SEGMENT_SPAN_SPLIT + ref.getParentSpanId());

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
}
