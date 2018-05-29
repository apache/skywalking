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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.RegisterServices;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import zipkin2.Span;

/**
 * @author wusheng
 */
public class SegmentBuilder {
    private Context context;
    private LinkedList<TraceSegmentObject.Builder> segments;

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
            String applicationCode = rootSpan.localServiceName();
            // If root span doesn't include applicationCode, a.k.a local service name,
            // Segment can't be built
            // Ignore the whole trace.
            // :P Hope anyone could provide better solution.
            // Wu Sheng.
            if (StringUtils.isNotEmpty(applicationCode)) {
                builder.context.addApp(applicationCode, registerServices);
                builder.scanSpansFromRoot(rootSpan, parentId2SpanListMap, registerServices);
            }
        }

        return builder.segments;
    }

    private void scanSpansFromRoot(Span parent, Map<String, List<Span>> parentId2SpanListMap, RegisterServices registerServices) {
        String parentId = parent.id();
        List<Span> spanList = parentId2SpanListMap.get(parentId);
        for (Span childSpan : spanList) {

        }
    }

    /**
     * Context holds the values in build process.
     */
    private class Context {
        private LinkedList<AppIDAndInstanceID> appContextStack = new LinkedList<>();
        private LinkedList<TraceSegmentObject.Builder> segments = new LinkedList<>();

        private boolean isAppIdChanged(String applicationCode) {
            return StringUtils.isNotEmpty(applicationCode) && !applicationCode.equals(currentAppId().applicationCode);
        }

        private TraceSegmentObject.Builder addApp(String applicationCode,
            RegisterServices registerServices) throws Exception {
            int applicationId = waitForExchange(() ->
                    registerServices.getApplicationIDService().getOrCreateForApplicationCode(applicationCode),
                10
            );

            int appInstanceId = waitForExchange(() ->
                    registerServices.getOrCreateApplicationInstanceId(applicationId, applicationCode),
                10
            );

            appContextStack.add(new AppIDAndInstanceID(applicationCode, applicationId, appInstanceId));
            TraceSegmentObject.Builder builder = TraceSegmentObject.newBuilder();
            segments.add(builder);
            return builder;
        }

        private AppIDAndInstanceID currentAppId() {
            return appContextStack.getLast();
        }

        private TraceSegmentObject.Builder removeApp() {
            appContextStack.removeLast();
            return segments.removeLast();
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

    private class AppIDAndInstanceID {
        private String applicationCode;
        private int appId;
        private int instanceId;

        public AppIDAndInstanceID(String applicationCode, int appId, int instanceId) {
            this.applicationCode = applicationCode;
            this.appId = appId;
            this.instanceId = instanceId;
        }
    }

}
