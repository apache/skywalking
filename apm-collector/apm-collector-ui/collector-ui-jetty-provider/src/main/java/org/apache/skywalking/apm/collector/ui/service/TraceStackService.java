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

import java.util.List;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IComponentLibraryCatalogService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.ui.trace.KeyValue;
import org.apache.skywalking.apm.collector.storage.ui.trace.LogEntity;
import org.apache.skywalking.apm.collector.storage.ui.trace.Ref;
import org.apache.skywalking.apm.collector.storage.ui.trace.RefType;
import org.apache.skywalking.apm.collector.storage.ui.trace.Segment;
import org.apache.skywalking.apm.collector.storage.ui.trace.SegmentBrief;
import org.apache.skywalking.apm.collector.storage.ui.trace.SegmentRef;
import org.apache.skywalking.apm.collector.storage.ui.trace.Span;
import org.apache.skywalking.apm.collector.storage.ui.trace.Trace;
import org.apache.skywalking.apm.network.proto.SpanLayer;
import org.apache.skywalking.apm.network.proto.SpanType;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.apache.skywalking.apm.network.proto.UniqueId;

/**
 * @author peng-yongsheng
 */
public class TraceStackService {

    private final IGlobalTraceUIDAO globalTraceDAO;
    private final ISegmentUIDAO segmentDAO;
    private final ApplicationCacheService applicationCacheService;
    private final ServiceNameCacheService serviceNameCacheService;
    private final NetworkAddressCacheService networkAddressCacheService;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    public TraceStackService(ModuleManager moduleManager) {
        this.globalTraceDAO = moduleManager.find(StorageModule.NAME).getService(IGlobalTraceUIDAO.class);
        this.segmentDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentUIDAO.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
        this.networkAddressCacheService = moduleManager.find(CacheModule.NAME).getService(NetworkAddressCacheService.class);
        this.componentLibraryCatalogService = moduleManager.find(ConfigurationModule.NAME).getService(IComponentLibraryCatalogService.class);
    }

    public Trace load(String traceId) {
        final Trace trace = new Trace();
        List<String> segmentIds = globalTraceDAO.getSegmentIds(traceId);

        if (CollectionUtils.isNotEmpty(segmentIds)) {
            for (String segmentId : segmentIds) {
                TraceSegmentObject segObj = segmentDAO.load(segmentId);
                if (ObjectUtils.isNotEmpty(segObj)) {
                    Segment segment = new Segment();
                    String applicationCode = applicationCacheService.getApplicationById(segObj.getApplicationId()).getApplicationCode();

                    segment.setApplicationCode(applicationCode);
                    segment.setSegmentId(segmentId);

                    SegmentBrief brief = new SegmentBrief();
                    segment.setBrief(brief);

                    DurationAssist durationAssist = new DurationAssist();

                    segObj.getSpansList().forEach(spanObject -> {
                        Span span = new Span();
                        span.setSpanId(spanObject.getSpanId());
                        span.setParentSpanId(spanObject.getParentSpanId());
                        span.setStartTime(spanObject.getStartTime());
                        span.setEndTime(spanObject.getEndTime());
                        long duration = span.getEndTime() - span.getStartTime();
                        durationAssist.setStartTime(span.getStartTime());
                        durationAssist.setEndTime(span.getEndTime());
                        span.setError(spanObject.getIsError());
                        if (span.getError()) {
                            segment.setError(true);
                        }

                        SpanLayer layer = spanObject.getSpanLayer();
                        switch (layer) {
                            case MQ:
                                brief.getMq().addOnce(duration);
                                durationAssist.addExcluded(duration);
                                break;
                            case Http:
                                brief.getHttp().addOnce(duration);
                                durationAssist.addExcluded(duration);
                                break;
                            case Cache:
                                brief.getCache().addOnce(duration);
                                durationAssist.addExcluded(duration);
                                break;
                            case Database:
                                brief.getDb().addOnce(duration);
                                durationAssist.addExcluded(duration);
                                break;
                            case RPCFramework:
                                brief.getRpc().addOnce(duration);
                                durationAssist.addExcluded(duration);
                                break;
                        }
                        span.setLayer(layer.name());
                        span.setType(spanObject.getSpanType().name());

                        if (spanObject.getPeerId() == 0) {
                            span.setPeer(spanObject.getPeer());
                        } else {
                            span.setPeer(networkAddressCacheService.getAddress(spanObject.getPeerId()).getNetworkAddress());
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
                        if (SpanType.Entry.equals(spanObject.getSpanType())) {
                            segment.setName(span.getOperationName());
                        }

                        if (spanObject.getComponentId() == 0) {
                            span.setComponent(spanObject.getComponent());
                        } else {
                            span.setComponent(this.componentLibraryCatalogService.getComponentName(spanObject.getComponentId()));
                        }

                        spanObject.getRefsList().forEach(reference -> {
                            Ref ref = new Ref();
                            ref.setTraceId(traceId);

                            SegmentRef segmentRef = new SegmentRef();
                            segmentRef.setSourceSegment(segmentId);
                            trace.addRefs(segmentRef);

                            switch (reference.getRefType()) {
                                case CrossThread:
                                    ref.setType(RefType.CROSS_THREAD);
                                    segmentRef.setCallType("Cross Thread");
                                    break;
                                case CrossProcess:
                                    ref.setType(RefType.CROSS_PROCESS);

                                    segmentRef.setCallType(span.getLayer());
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
                            segmentRef.setTargetSegment(ref.getParentSegmentId());

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

                        segment.addSpan(span);
                    });

                    // If there is no entry span, use the first span operation as segment name.
                    if (StringUtils.isEmpty(segment.getName())) {
                        segment.getSpans().forEach(span -> {
                            if (span.getSpanId() == 0) {
                                segment.setName(span.getOperationName());
                            }

                        });
                    }

                    segment.setDuration(durationAssist.getDuration());

                    trace.addSegment(segment);
                }
            }
        }

        return trace;
    }

    private class DurationAssist {
        private long startTime = 0;
        private long endTime = 0;
        private long excluded = 0;

        private void setStartTime(long startTime) {
            if (startTime < this.startTime) {
                this.startTime = startTime;
            }
        }

        private void setEndTime(long endTime) {
            if (endTime > this.endTime) {
                this.endTime = endTime;
            }
        }

        private void addExcluded(long excluded) {
            this.excluded += excluded;
        }

        private long getDuration() {
            return this.endTime - this.startTime - this.excluded;
        }
    }
}
