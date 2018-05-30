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

package org.apache.skywalking.apm.collector.receiver.zipkin.provider.transform;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.RegisterServices;
import org.apache.skywalking.apm.network.proto.SpanObject;
import org.apache.skywalking.apm.network.proto.SpanType;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.apache.skywalking.apm.network.proto.UniqueId;
import org.eclipse.jetty.util.StringUtil;
import zipkin2.Span;

/**
 * @author wusheng
 */
public class SegmentBuilder {
    private Context context;
    private LinkedList<TraceSegmentObject.Builder> segments;
    private UniqueId traceId;

    private SegmentBuilder() {
        segments = new LinkedList<>();
        context = new Context();
    }

    public static List<TraceSegmentObject.Builder> build(List<Span> traceSpans,
        RegisterServices registerServices) throws Exception {
        SegmentBuilder builder = new SegmentBuilder();
        // This map groups the spans by their parent id, in order to assist to build tree.
        // key: parentId
        // value: span
        Map<String, List<Span>> parentId2SpanListMap = new HashMap<>();
        AtomicReference<Span> root = new AtomicReference<>();
        traceSpans.forEach(span -> {
            // parent id is null, it is the root span of the trace
            if (span.parentId() == null) {
                root.set(span);
            } else {
                List<Span> spanList = parentId2SpanListMap.get(span.parentId());
                if (spanList == null) {
                    spanList = new LinkedList<>();
                    spanList.add(span);
                }
            }
        });

        Span rootSpan = root.get();
        if (rootSpan != null) {
            builder.traceId = builder.generateTraceOrSegmentId();
            String applicationCode = rootSpan.localServiceName();
            // If root span doesn't include applicationCode, a.k.a local service name,
            // Segment can't be built
            // Ignore the whole trace.
            // :P Hope anyone could provide better solution.
            // Wu Sheng.
            if (StringUtils.isNotEmpty(applicationCode)) {
                try {
                    builder.context.addApp(applicationCode, registerServices);
                    SpanObject.Builder rootSpanBuilder = builder.initSpan(null, rootSpan, true);
                    builder.scanSpansFromRoot(rootSpanBuilder, rootSpan, parentId2SpanListMap, registerServices);

                    builder.context.currentSegment().builder().addSpans(rootSpanBuilder);
                } finally {
                    builder.context.removeApp();
                }
            }
        }

        return builder.segments;
    }

    private void scanSpansFromRoot(SpanObject.Builder parentSegmentSpan, Span parent,
        Map<String, List<Span>> parentId2SpanListMap,
        RegisterServices registerServices) throws Exception {
        String parentId = parent.id();
        List<Span> spanList = parentId2SpanListMap.get(parentId);
        for (Span childSpan : spanList) {
            String localServiceName = childSpan.localServiceName();
            boolean isNewApp = false;
            if (StringUtil.isNotBlank(localServiceName)) {
                if (context.isAppChanged(localServiceName)) {
                    isNewApp = true;
                }
            }

            try {
                if (isNewApp) {
                    context.addApp(localServiceName, registerServices);
                }
                SpanObject.Builder childSpanBuilder = initSpan(parentSegmentSpan, childSpan, isNewApp);

                scanSpansFromRoot(childSpanBuilder, childSpan, parentId2SpanListMap, registerServices);

                context.currentSegment().builder().addSpans(childSpanBuilder);
            } finally {
                if (isNewApp) {
                    context.removeApp();
                }
            }
        }
    }

    private SpanObject.Builder initSpan(SpanObject.Builder parentSegmentSpan, Span span, boolean isSegmentRoot) {
        SpanObject.Builder spanBuilder = SpanObject.newBuilder();
        spanBuilder.setSpanId(context.currentIDs().nextSpanId());
        if (!isSegmentRoot && parentSegmentSpan != null) {
            spanBuilder.setParentSpanId(parentSegmentSpan.getSpanId());
        }
        Span.Kind kind = span.kind();
        spanBuilder.setOperationName(span.name());
        switch (kind) {
            case CLIENT:
                spanBuilder.setSpanType(SpanType.Exit);
                break;
            case SERVER:
                spanBuilder.setSpanType(SpanType.Entry);
                break;
            case CONSUMER:
                spanBuilder.setSpanType(SpanType.Entry);
                break;
            case PRODUCER:
                spanBuilder.setSpanType(SpanType.Exit);
                break;
            default:
                spanBuilder.setSpanType(SpanType.Local);
        }
        // microseconds in Zipkin -> milliseconds in SkyWalking
        long startTime = span.timestamp() / 1000;
        long duration = span.duration() / 1000;
        spanBuilder.setStartTime(startTime);
        spanBuilder.setEndTime(startTime + duration);

        return spanBuilder;
    }

    /**
     * Context holds the values in build process.
     */
    private class Context {
        private LinkedList<Segment> segmentsStack = new LinkedList<>();

        private boolean isAppChanged(String applicationCode) {
            return StringUtils.isNotEmpty(applicationCode) && !applicationCode.equals(currentIDs().applicationCode);
        }

        private Segment addApp(String applicationCode,
            RegisterServices registerServices) throws Exception {
            int applicationId = waitForExchange(() ->
                    registerServices.getApplicationIDService().getOrCreateForApplicationCode(applicationCode),
                10
            );

            int appInstanceId = waitForExchange(() ->
                    registerServices.getOrCreateApplicationInstanceId(applicationId, applicationCode),
                10
            );

            Segment segment = new Segment(applicationCode, applicationId, appInstanceId);
            segmentsStack.add(segment);
            return segment;
        }

        private IDCollection currentIDs() {
            return segmentsStack.getLast().ids;
        }

        private Segment currentSegment() {
            return segmentsStack.getLast();
        }

        private Segment removeApp() {
            return segmentsStack.removeLast();
        }

        private int waitForExchange(Callable<Integer> callable, int retry) throws Exception {
            for (int i = 0; i < retry; i++) {
                Integer id = callable.call();
                if (id == 0) {
                    Thread.sleep(1000L);
                } else {
                    return id;
                }
            }
            throw new TimeoutException("ID exchange costs more than expected.");
        }
    }

    private class Segment {
        private TraceSegmentObject.Builder segmentBuilder;
        private IDCollection ids;

        private Segment(String applicationCode, int applicationId, int appInstanceId) {
            ids = new IDCollection(applicationCode, applicationId, appInstanceId);
            segmentBuilder = TraceSegmentObject.newBuilder();
            segmentBuilder.setApplicationId(applicationId);
            segmentBuilder.setApplicationInstanceId(appInstanceId);
            segmentBuilder.setTraceSegmentId(generateTraceOrSegmentId());
        }

        private TraceSegmentObject.Builder builder() {
            return segmentBuilder;
        }

        private IDCollection ids() {
            return ids;
        }
    }

    private class IDCollection {
        private String applicationCode;
        private int appId;
        private int instanceId;
        private int spanIdSeq;

        private IDCollection(String applicationCode, int appId, int instanceId) {
            this.applicationCode = applicationCode;
            this.appId = appId;
            this.instanceId = instanceId;
            this.spanIdSeq = 1;
        }

        private int nextSpanId() {
            return spanIdSeq++;
        }
    }

    private UniqueId generateTraceOrSegmentId() {
        return UniqueId.newBuilder()
            .addIdParts(ThreadLocalRandom.current().nextLong())
            .addIdParts(ThreadLocalRandom.current().nextLong())
            .addIdParts(ThreadLocalRandom.current().nextLong())
            .build();
    }
}
