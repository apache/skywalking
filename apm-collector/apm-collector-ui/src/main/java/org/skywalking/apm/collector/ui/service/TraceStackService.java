/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.cache.ApplicationCache;
import org.skywalking.apm.collector.cache.ServiceNameCache;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.IGlobalTraceDAO;
import org.skywalking.apm.collector.ui.dao.ISegmentDAO;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;
import org.skywalking.apm.network.proto.UniqueId;

/**
 * @author peng-yongsheng
 */
public class TraceStackService {

    public JsonArray load(String globalTraceId) {
        IGlobalTraceDAO globalTraceDAO = (IGlobalTraceDAO)DAOContainer.INSTANCE.get(IGlobalTraceDAO.class.getName());
        ISegmentDAO segmentDAO = (ISegmentDAO)DAOContainer.INSTANCE.get(ISegmentDAO.class.getName());

        List<Span> spans = new ArrayList<>();
        List<String> segmentIds = globalTraceDAO.getSegmentIds(globalTraceId);
        if (CollectionUtils.isNotEmpty(segmentIds)) {
            for (String segmentId : segmentIds) {
                TraceSegmentObject segment = segmentDAO.load(segmentId);
                if (ObjectUtils.isNotEmpty(segment)) {
                    spans.addAll(buildSpanList(segmentId, segment));
                }
            }
        }

        List<Span> sortedSpans = new ArrayList<>();
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
        minStartTime(sortedSpans);

        return toJsonArray(sortedSpans);
    }

    private JsonArray toJsonArray(List<Span> sortedSpans) {
        JsonArray traceStackArray = new JsonArray();
        sortedSpans.forEach(span -> {
            JsonObject spanJson = new JsonObject();
            spanJson.addProperty("spanId", span.getSpanId());
            spanJson.addProperty("parentSpanId", span.getParentSpanId());
            spanJson.addProperty("segmentSpanId", span.getSegmentSpanId());
            spanJson.addProperty("segmentParentSpanId", span.getSegmentParentSpanId());
            spanJson.addProperty("startTime", span.getStartTime());
            spanJson.addProperty("operationName", span.getOperationName());
            spanJson.addProperty("applicationCode", span.getApplicationCode());
            spanJson.addProperty("cost", span.getCost());
            spanJson.addProperty("isRoot", span.isRoot());
            traceStackArray.add(spanJson);
        });
        return traceStackArray;
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

    private List<Span> buildSpanList(String segmentId, TraceSegmentObject segment) {
        List<Span> spans = new ArrayList<>();
        if (segment.getSpansCount() > 0) {
            for (SpanObject spanObject : segment.getSpansList()) {
                int spanId = spanObject.getSpanId();
                int parentSpanId = spanObject.getParentSpanId();
                String segmentSpanId = segmentId + Const.SEGMENT_SPAN_SPLIT + String.valueOf(spanId);
                String segmentParentSpanId = segmentId + Const.SEGMENT_SPAN_SPLIT + String.valueOf(parentSpanId);
                long startTime = spanObject.getStartTime();

                String operationName = spanObject.getOperationName();
                if (spanObject.getOperationNameId() != 0) {
                    String serviceName = ServiceNameCache.get(spanObject.getOperationNameId());
                    if (StringUtils.isNotEmpty(serviceName)) {
                        operationName = serviceName.split(Const.ID_SPLIT)[1];
                    } else {
                        operationName = Const.EMPTY_STRING;
                    }
                }
                String applicationCode = ApplicationCache.get(segment.getApplicationId());

                long cost = spanObject.getEndTime() - spanObject.getStartTime();
                if (cost == 0) {
                    cost = 1;
                }

                if (parentSpanId == -1 && segment.getRefsCount() > 0) {
                    for (TraceSegmentReference reference : segment.getRefsList()) {
                        parentSpanId = reference.getParentSpanId();
                        UniqueId uniqueId = reference.getParentTraceSegmentId();

                        StringBuilder segmentIdBuilder = new StringBuilder();
                        for (int i = 0; i < uniqueId.getIdPartsList().size(); i++) {
                            if (i == 0) {
                                segmentIdBuilder.append(String.valueOf(uniqueId.getIdPartsList().get(i)));
                            } else {
                                segmentIdBuilder.append(".").append(String.valueOf(uniqueId.getIdPartsList().get(i)));
                            }
                        }

                        String parentSegmentId = segmentIdBuilder.toString();
                        segmentParentSpanId = parentSegmentId + Const.SEGMENT_SPAN_SPLIT + String.valueOf(parentSpanId);

                        spans.add(new Span(spanId, parentSpanId, segmentSpanId, segmentParentSpanId, startTime, operationName, applicationCode, cost));
                    }
                } else {
                    spans.add(new Span(spanId, parentSpanId, segmentSpanId, segmentParentSpanId, startTime, operationName, applicationCode, cost));
                }
            }
        }
        return spans;
    }

    private List<Span> findRoot(List<Span> spans) {
        List<Span> rootSpans = new ArrayList<>();
        spans.forEach(span -> {
            String segmentParentSpanId = span.getSegmentParentSpanId();

            boolean hasParent = false;
            for (Span span1 : spans) {
                if (segmentParentSpanId.equals(span1.getSegmentSpanId())) {
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

    class Span {
        private int spanId;
        private int parentSpanId;
        private String segmentSpanId;
        private String segmentParentSpanId;
        private long startTime;
        private String operationName;
        private String applicationCode;
        private long cost;
        private boolean isRoot = false;

        Span(int spanId, int parentSpanId, String segmentSpanId, String segmentParentSpanId, long startTime,
            String operationName, String applicationCode, long cost) {
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.segmentSpanId = segmentSpanId;
            this.segmentParentSpanId = segmentParentSpanId;
            this.startTime = startTime;
            this.operationName = operationName;
            this.applicationCode = applicationCode;
            this.cost = cost;
        }

        int getSpanId() {
            return spanId;
        }

        int getParentSpanId() {
            return parentSpanId;
        }

        String getSegmentSpanId() {
            return segmentSpanId;
        }

        String getSegmentParentSpanId() {
            return segmentParentSpanId;
        }

        long getStartTime() {
            return startTime;
        }

        String getOperationName() {
            return operationName;
        }

        String getApplicationCode() {
            return applicationCode;
        }

        long getCost() {
            return cost;
        }

        public boolean isRoot() {
            return isRoot;
        }

        public void setRoot(boolean root) {
            isRoot = root;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }
    }
}
