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

package org.apache.skywalking.apm.collector.ui.service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.ui.trace.KeyValue;
import org.apache.skywalking.apm.collector.storage.ui.trace.LogEntity;
import org.apache.skywalking.apm.collector.storage.ui.trace.Ref;
import org.apache.skywalking.apm.collector.storage.ui.trace.RefType;
import org.apache.skywalking.apm.collector.storage.ui.trace.Span;
import org.apache.skywalking.apm.collector.storage.ui.trace.Trace;
import org.apache.skywalking.apm.network.proto.SpanObject;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.apache.skywalking.apm.network.proto.UniqueId;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * @author peng-yongsheng
 */
public class TraceStackService {

    private final IGlobalTraceUIDAO globalTraceDAO;
    private final ISegmentUIDAO segmentDAO;
    private final ApplicationCacheService applicationCacheService;
    private final ServiceNameCacheService serviceNameCacheService;
    private final NetworkAddressCacheService networkAddressCacheService;

    public TraceStackService(ModuleManager moduleManager) {
        this.globalTraceDAO = moduleManager.find(StorageModule.NAME).getService(IGlobalTraceUIDAO.class);
        this.segmentDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentUIDAO.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
        this.networkAddressCacheService = moduleManager.find(CacheModule.NAME).getService(NetworkAddressCacheService.class);
    }

    public Trace load(String traceId) {
        Trace trace = new Trace();
        List<String> segmentIds = globalTraceDAO.getSegmentIds(traceId);
        if (CollectionUtils.isNotEmpty(segmentIds)) {
            for (String segmentId : segmentIds) {
                TraceSegmentObject segment = segmentDAO.load(segmentId);
                if (ObjectUtils.isNotEmpty(segment)) {
                    trace.getSpans().addAll(buildSpanList(traceId, segmentId, segment.getApplicationId(), segment.getSpansList()));
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
//        minStartTime(sortedSpans);

        trace.setSpans(sortedSpans);
        return trace;
    }

    private void minStartTime(List<Span> spans) {
        long minStartTime = Long.MAX_VALUE;
        for (Span span : spans) {
            if (span.getStartTime() < minStartTime) {
                minStartTime = span.getStartTime();
            }
        }

        for (Span span : spans) {
            span.setStartTime(span.getStartTime() - minStartTime);
        }
    }

    private List<Span> buildSpanList(String traceId, String segmentId, int applicationId,
        List<SpanObject> spanObjects) {
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

            String segmentSpanId = segmentId + Const.SEGMENT_SPAN_SPLIT + String.valueOf(spanObject.getSpanId());
            span.setSegmentSpanId(segmentSpanId);

            String segmentParentSpanId = segmentId + Const.SEGMENT_SPAN_SPLIT + String.valueOf(spanObject.getParentSpanId());
            span.setSegmentParentSpanId(segmentParentSpanId);

            if (spanObject.getPeerId() == 0) {
                span.setPeer(spanObject.getPeer());
            } else {
                span.setPeer(networkAddressCacheService.getAddress(spanObject.getPeerId()));
            }

            String operationName = spanObject.getOperationName();
            if (spanObject.getOperationNameId() != 0) {
                ServiceName serviceName = serviceNameCacheService.get(spanObject.getOperationNameId());
                if (ObjectUtils.isNotEmpty(serviceName)) {
                    operationName = serviceName.getServiceName();
                } else {
                    operationName = Const.EMPTY_STRING;
                }
            }
            span.setOperationName(operationName);

            String applicationCode = applicationCacheService.getApplicationById(applicationId).getApplicationCode();
            span.setApplicationCode(applicationCode);

            if (spanObject.getComponentId() == 0) {
                span.setComponent(spanObject.getComponent());
            } else {
                span.setComponent(ComponentsDefine.getInstance().getComponentName(spanObject.getComponentId()));
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

                UniqueId uniqueId = reference.getParentTraceSegmentId();
                StringBuilder segmentIdBuilder = new StringBuilder();
                for (int i = 0; i < uniqueId.getIdPartsList().size(); i++) {
                    if (i == 0) {
                        segmentIdBuilder.append(String.valueOf(uniqueId.getIdPartsList().get(i)));
                    } else {
                        segmentIdBuilder.append(".").append(String.valueOf(uniqueId.getIdPartsList().get(i)));
                    }
                }
                ref.setParentSegmentId(segmentIdBuilder.toString());

                span.setSegmentParentSpanId(ref.getParentSegmentId() + Const.SEGMENT_SPAN_SPLIT + String.valueOf(ref.getParentSpanId()));

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
                }
            }

            if (!hasParent) {
                span.setRoot(true);
                rootSpans.add(span);
            }
        });
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
