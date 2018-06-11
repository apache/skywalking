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

import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.RegisterServices;
import org.apache.skywalking.apm.network.proto.*;
import org.eclipse.jetty.util.StringUtil;
import zipkin2.Endpoint;
import zipkin2.Span;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
                    SpanObject.Builder rootSpanBuilder = builder.initSpan(null, null, rootSpan, true);
                    builder.scanSpansFromRoot(rootSpanBuilder, rootSpan, parentId2SpanListMap, registerServices);

                    builder.context.currentSegment().addSpan(rootSpanBuilder);
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
                SpanObject.Builder childSpanBuilder = initSpan(parentSegmentSpan, parent, childSpan, isNewApp);

                scanSpansFromRoot(childSpanBuilder, childSpan, parentId2SpanListMap, registerServices);

                context.currentSegment().addSpan(childSpanBuilder);
            } finally {
                if (isNewApp) {
                    context.removeApp();
                }
            }
        }
    }

    private SpanObject.Builder initSpan(SpanObject.Builder parentSegmentSpan, Span parentSpan, Span span,
                                        boolean isSegmentRoot) {
        SpanObject.Builder spanBuilder = SpanObject.newBuilder();
        spanBuilder.setSpanId(context.currentIDs().nextSpanId());
        if (!isSegmentRoot && parentSegmentSpan != null) {
            spanBuilder.setParentSpanId(parentSegmentSpan.getSpanId());
        }
        Span.Kind kind = span.kind();
        spanBuilder.setOperationName(span.name());
        switch (kind) {
            case CLIENT:
                String peer = endpoint2Peer(span.remoteEndpoint());
                if (peer != null) {
                    spanBuilder.setPeer(peer);
                }
                break;
            case SERVER:
                spanBuilder.setSpanType(SpanType.Entry);
                this.buildRef(spanBuilder, span, parentSegmentSpan, parentSpan);
                break;
            case CONSUMER:
                spanBuilder.setSpanType(SpanType.Entry);
                this.buildRef(spanBuilder, span, parentSegmentSpan, parentSpan);
                break;
            case PRODUCER:
                spanBuilder.setSpanType(SpanType.Exit);
                peer = endpoint2Peer(span.remoteEndpoint());
                if (peer != null) {
                    spanBuilder.setPeer(peer);
                }
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

    private String endpoint2Peer(Endpoint endpoint) {
        if (endpoint == null) {
            return null;
        }
        String ip = null;
        if (StringUtils.isNotEmpty(endpoint.ipv4())) {
            ip = endpoint.ipv4();
        } else if (StringUtils.isNotEmpty(endpoint.ipv6())) {
            ip = endpoint.ipv6();
        }
        if (StringUtils.isEmpty(ip)) {
            return null;
        }
        int port = endpoint.port();
        return port == 0 ? ip : ip + ":" + port;
    }

    private void buildRef(SpanObject.Builder spanBuilder, Span span, SpanObject.Builder parentSegmentSpan,
                          Span parentSpan) {
        Segment parentSegment = context.parentSegment();
        if (parentSegment == null) {
            return;
        }
        Segment rootSegment = context.rootSegment();
        if (rootSegment == null) {
            return;
        }

        String ip = null;
        int port = 0;
        Endpoint serverEndpoint = span.localEndpoint();
        Endpoint clientEndpoint = parentSpan.remoteEndpoint();
        if (serverEndpoint != null) {
            if (StringUtils.isNotEmpty(serverEndpoint.ipv4())) {
                ip = serverEndpoint.ipv4();
            } else if (StringUtils.isNotEmpty(serverEndpoint.ipv6())) {
                ip = serverEndpoint.ipv6();
            }
        }

        if (clientEndpoint != null) {
            if (StringUtil.isBlank(ip)) {
                if (StringUtils.isNotEmpty(clientEndpoint.ipv4())) {
                    ip = clientEndpoint.ipv4();
                } else if (StringUtils.isNotEmpty(clientEndpoint.ipv6())) {
                    ip = clientEndpoint.ipv6();
                }
                port = clientEndpoint.port();
            }
        }
        if (StringUtil.isBlank(ip)) {
            //The IP is the most important for building the ref at both sides.
            return;
        }

        TraceSegmentReference.Builder refBuilder = TraceSegmentReference.newBuilder();
        refBuilder.setEntryApplicationInstanceId(rootSegment.builder().getApplicationInstanceId());
        int serviceId = rootSegment.getEntryServiceId();
        if (serviceId == 0) {
            refBuilder.setEntryServiceName(rootSegment.getEntryServiceName());
        } else {
            refBuilder.setEntryServiceId(serviceId);
        }
        refBuilder.setEntryApplicationInstanceId(rootSegment.builder().getApplicationInstanceId());

        // parent ref info
        refBuilder.setNetworkAddress(port == 0 ? ip : ip + ":" + port);
        parentSegmentSpan.setPeer(refBuilder.getNetworkAddress());
        refBuilder.setParentApplicationInstanceId(parentSegment.builder().getApplicationInstanceId());
        refBuilder.setParentSpanId(parentSegmentSpan.getSpanId());
        refBuilder.setParentTraceSegmentId(parentSegment.builder().getTraceSegmentId());
        int parentServiceId = parentSegment.getEntryServiceId();
        if (parentServiceId == 0) {
            refBuilder.setParentServiceId(parentServiceId);
        } else {
            refBuilder.setParentServiceName(parentSegment.getEntryServiceName());
        }
        refBuilder.setRefType(RefType.CrossProcess);

        spanBuilder.addRefs(refBuilder);
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

        private Segment parentSegment() {
            if (segmentsStack.size() < 2) {
                return null;
            } else {
                return segmentsStack.get(segmentsStack.size() - 2);
            }

        }

        private Segment rootSegment() {
            if (segmentsStack.size() < 2) {
                return null;
            } else {
                return segmentsStack.getFirst();
            }
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
        private int entryServiceId = 0;
        private String entryServiceName = null;
        private List<SpanObject.Builder> spans;

        private Segment(String applicationCode, int applicationId, int appInstanceId) {
            ids = new IDCollection(applicationCode, applicationId, appInstanceId);
            spans = new LinkedList<>();
            segmentBuilder = TraceSegmentObject.newBuilder();
            segmentBuilder.setApplicationId(applicationId);
            segmentBuilder.setApplicationInstanceId(appInstanceId);
            segmentBuilder.setTraceSegmentId(generateTraceOrSegmentId());
        }

        private TraceSegmentObject.Builder builder() {
            return segmentBuilder;
        }

        private void addSpan(SpanObject.Builder spanBuilder) {
            if (entryServiceId == 0 && StringUtils.isEmpty(entryServiceName)) {
                if (SpanType.Entry == spanBuilder.getSpanType()) {
                    entryServiceId = spanBuilder.getOperationNameId();
                    entryServiceName = spanBuilder.getOperationName();
                }
            }

            // init by root span
            if (spanBuilder.getSpanId() == 1 && entryServiceId == 0 && StringUtils.isEmpty(entryServiceName)) {
                entryServiceId = spanBuilder.getOperationNameId();
                entryServiceName = spanBuilder.getOperationName();
            }

            spans.add(spanBuilder);
        }

        public int getEntryServiceId() {
            return entryServiceId;
        }

        public String getEntryServiceName() {
            return entryServiceName;
        }

        private IDCollection ids() {
            return ids;
        }

        public TraceSegmentObject.Builder freeze() {
            for (SpanObject.Builder span : spans) {
                segmentBuilder.addSpans(span);
            }
            return segmentBuilder;
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
