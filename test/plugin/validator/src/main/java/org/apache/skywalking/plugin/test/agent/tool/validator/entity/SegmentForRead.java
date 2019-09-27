/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SegmentForRead implements Segment {
    private String segmentId;
    private List<SpanForRead> spans;

    @Override
    public String segmentId() {
        return segmentId;
    }

    @Override public List<Span> spans() {
        if (spans == null) {
            return null;
        }
        return new ArrayList<>(spans);
    }

    public static class SegmentRefForRead implements SegmentRef {
        private String parentEndpointId;
        private String parentEndpoint;
        private String networkAddressId;
        private String entryEndpointId;
        private String refType;
        private String parentSpanId;
        private String parentTraceSegmentId;
        private String parentServiceInstanceId;
        private String networkAddress;
        private String entryEndpoint;
        private String entryServiceInstanceId;

        public SegmentRefForRead() {
        }

        public SegmentRefForRead(Map<String, Object> ref) {
            this.networkAddress = ref.get("networkAddress").toString();
            this.entryEndpoint = ref.get("entryEndpoint").toString();
            this.parentEndpoint = ref.get("parentEndpoint").toString();
            this.parentTraceSegmentId = ref.get("parentTraceSegmentId").toString();
            this.entryServiceInstanceId = ref.get("entryServiceInstanceId").toString();
            this.refType = ref.get("refType") == null ? null : ref.get("refType").toString();
            this.parentSpanId = ref.get("parentSpanId") == null ? null : ref.get("parentSpanId").toString();
            this.entryEndpointId = ref.get("entryEndpointId") == null ? null : ref.get("entryEndpointId").toString();
            this.parentEndpointId = ref.get("parentEndpointId") == null ? null : ref.get("parentEndpointId").toString();
            this.networkAddressId = ref.get("networkAddressId") == null ? null : ref.get("networkAddressId").toString();
            this.parentServiceInstanceId = ref.get("parentServiceInstanceId") == null ? null : ref.get("parentServiceInstanceId").toString();
        }

        public void setParentEndpointId(String parentEndpointId) {
            this.parentEndpointId = parentEndpointId;
        }

        public void setParentEndpoint(String parentEndpoint) {
            this.parentEndpoint = parentEndpoint;
        }

        public void setNetworkAddressId(String networkAddressId) {
            this.networkAddressId = networkAddressId;
        }

        public void setEntryEndpointId(String entryEndpointId) {
            this.entryEndpointId = entryEndpointId;
        }

        public void setRefType(String refType) {
            this.refType = refType;
        }

        public void setParentSpanId(String parentSpanId) {
            this.parentSpanId = parentSpanId;
        }

        public void setParentTraceSegmentId(String parentTraceSegmentId) {
            this.parentTraceSegmentId = parentTraceSegmentId;
        }

        public void setParentServiceInstanceId(String parentServiceInstanceId) {
            this.parentServiceInstanceId = parentServiceInstanceId;
        }

        public void setNetworkAddress(String networkAddress) {
            this.networkAddress = networkAddress;
        }

        public void setEntryEndpoint(String entryEndpoint) {
            this.entryEndpoint = entryEndpoint;
        }

        public void setEntryServiceInstanceId(String entryServiceInstanceId) {
            this.entryServiceInstanceId = entryServiceInstanceId;
        }

        @Override public String parentServiceId() {
            return parentEndpointId;
        }

        @Override public String parentServiceName() {
            return parentEndpoint;
        }

        @Override public String networkAddressId() {
            return networkAddressId;
        }

        @Override public String entryServiceId() {
            return entryEndpointId;
        }

        @Override public String refType() {
            return refType;
        }

        @Override public String parentSpanId() {
            return parentSpanId;
        }

        @Override public String parentTraceSegmentId() {
            return parentTraceSegmentId;
        }

        @Override public String parentApplicationInstanceId() {
            return parentServiceInstanceId;
        }

        @Override public String networkAddress() {
            return networkAddress;
        }

        @Override public String entryServiceName() {
            return entryEndpoint;
        }

        @Override public void parentTraceSegmentId(String parentTraceSegmentId) {
            this.parentTraceSegmentId = parentTraceSegmentId;
        }

        @Override public String entryApplicationInstanceId() {
            return entryServiceInstanceId;
        }

        @Override public String toString() {
            StringBuilder actualSegmentRef = new StringBuilder("\nSegmentRef:\n");
            return actualSegmentRef.append(String.format(" - entryServiceName:\t\t%s\n", entryServiceName()))
                .append(String.format(" - networkAddress:\t\t\t%s\n", networkAddress()))
                .append(String.format(" - parentServiceName:\t\t%s\n", parentServiceName()))
                .append(String.format(" - parentSpanId:\t\t\t%s\n", parentSpanId()))
                .append(String.format(" - parentTraceSegmentId:\t%s\n", parentTraceSegmentId()))
                .append(String.format(" - refType:\t\t\t\t\t%s", refType())).toString();
        }
    }

    public static class SpanForRead implements Span {
        private String operationName;
        private String operationId;
        private String parentSpanId;
        private String spanId;
        private String spanLayer;
        private List<Map<String, String>> tags;
        private List<Map<String, List<Map<String, String>>>> logs;
        private List<Map<String, Object>> refs;
        private List<SegmentRef> formatedRefs;
        private List<SegmentRef> actualRefs;
        private String startTime;
        private String endTime;
        private String componentId;
        private String componentName;
        private boolean isError;
        private String spanType;
        private String peer;
        private String peerId;

        public void setOperationName(String operationName) {
            this.operationName = operationName;
        }

        public void setOperationId(String operationId) {
            this.operationId = operationId;
        }

        public void setParentSpanId(String parentSpanId) {
            this.parentSpanId = parentSpanId;
        }

        public void setSpanId(String spanId) {
            this.spanId = spanId;
        }

        public void setSpanLayer(String spanLayer) {
            this.spanLayer = spanLayer;
        }

        public void setTags(List<Map<String, String>> tags) {
            this.tags = tags;
        }

        public void setLogs(List<Map<String, List<Map<String, String>>>> logs) {
            this.logs = logs;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public void setComponentId(String componentId) {
            this.componentId = componentId;
        }

        public void setComponentName(String componentName) {
            this.componentName = componentName;
        }

        public void setError(boolean error) {
            isError = error;
        }

        public void setSpanType(String spanType) {
            this.spanType = spanType;
        }

        public void setPeer(String peer) {
            this.peer = peer;
        }

        public void setPeerId(String peerId) {
            this.peerId = peerId;
        }

        public void setRefs(
            List<Map<String, Object>> refs) {
            this.refs = refs;
        }

        @Override public String operationName() {
            return operationName;
        }

        @Override public String operationId() {
            return operationId;
        }

        @Override public String parentSpanId() {
            return parentSpanId;
        }

        @Override public String spanId() {
            return spanId;
        }

        @Override public String spanLayer() {
            return spanLayer;
        }

        @Override public List<KeyValuePair> tags() {
            if (tags == null) {
                return new ArrayList<>();
            }
            List<KeyValuePair> result = new ArrayList<>();
            for (Map<String, String> tag : tags) {
                result.add(new KeyValuePair.Impl(tag.get("key"), tag.get("value")));
            }
            return result;
        }

        @Override public List<LogEvent> logs() {
            if (logs == null) {
                return new ArrayList<>();
            }
            List<LogEvent> result = new ArrayList<>();
            for (Map<String, List<Map<String, String>>> log : logs) {
                List<Map<String, String>> events = log.get("logEvent");
                LogEvent.Impl logEvent = new LogEvent.Impl();
                for (Map<String, String> event : events) {
                    logEvent.add(event.get("key"), event.get("value"));
                }
                result.add(logEvent);
            }

            return result;
        }

        @Override public String startTime() {
            return startTime;
        }

        @Override public String endTime() {
            return endTime;
        }

        @Override public String componentId() {
            return componentId;
        }

        @Override public String componentName() {
            return componentName;
        }

        @Override public boolean error() {
            return isError;
        }

        @Override public String spanType() {
            return spanType;
        }

        @Override public String peer() {
            return peer;
        }

        @Override public String peerId() {
            return peerId;
        }

        @Override public List<SegmentRef> refs() {
            if (formatedRefs == null && refs != null) {
                List<SegmentRef> segmentRefs = new ArrayList<>();
                for (Map<String, Object> ref : refs) {
                    segmentRefs.add(new SegmentRefForRead(ref));
                }

                this.formatedRefs = segmentRefs;
            }
            return formatedRefs;
        }

        @Override public void setActualRefs(List<SegmentRef> refs) {
            this.actualRefs = refs;
        }

        @Override public List<SegmentRef> actualRefs() {
            return actualRefs;
        }
    }

    public static class LogEventForRead {
        private List<Map<String, String>> logEvent;

        public List<Map<String, String>> getLogEvent() {
            return logEvent;
        }

        public void setLogEvent(List<Map<String, String>> logEvent) {
            this.logEvent = logEvent;
        }
    }

    public String getSegmentId() {
        return segmentId;
    }

    public List<SpanForRead> getSpans() {
        return spans;
    }

    @Override public void setSegmentId(String segmentId) {
        this.segmentId = segmentId;
    }

    public void setSpans(List<SpanForRead> spans) {
        this.spans = spans;
    }
}
