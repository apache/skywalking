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

package org.apache.skywalking.plugin.test.mockcollector.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.KeyWithStringValue;
import org.apache.skywalking.apm.network.language.agent.TraceSegmentReference;
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentReference;

import java.util.ArrayList;
import java.util.List;

@Builder
@ToString
@AllArgsConstructor
public class Span {
    private String operationName;
    private int operationId;
    private int parentSpanId;
    private int spanId;
    private String spanLayer;
    private long startTime;
    private long endTime;
    private int componentId;
    private String componentName;
    private boolean isError;
    private String spanType;
    private String peer;
    private int peerId;
    private List<KeyValuePair> tags = new ArrayList<>();
    private List<LogEvent> logs = new ArrayList<>();
    private List<SegmentRef> refs = new ArrayList<>();

    public static class LogEvent {
        private List<KeyValuePair> logEvent;

        public LogEvent() {
            this.logEvent = new ArrayList<>();
        }
    }

    public static class SpanBuilder {
        public SpanBuilder logEvent(List<KeyStringValuePair> eventMessage) {
            if (logs == null) {
                logs = new ArrayList<>();
            }

            LogEvent event = new LogEvent();
            for (KeyStringValuePair value : eventMessage) {
                event.logEvent.add(new KeyValuePair(value.getKey(), value.getValue()));
            }
            logs.add(event);
            return this;
        }

        public SpanBuilder tags(String key, String value) {
            if (tags == null) {
                tags = new ArrayList<>();
            }

            tags.add(new KeyValuePair(key, value));
            return this;
        }

        public SpanBuilder ref(SegmentRef segmentRefBuilder) {
            if (refs == null) {
                refs = new ArrayList<>();
            }

            refs.add(segmentRefBuilder);
            return this;
        }

        public SpanBuilder logEventV1(List<KeyWithStringValue> dataList) {
            if (logs == null) {
                logs = new ArrayList<>();
            }

            LogEvent event = new LogEvent();
            for (KeyWithStringValue value : dataList) {
                event.logEvent.add(new KeyValuePair(value.getKey(), value.getValue()));
            }
            logs.add(event);
            return this;
        }
    }

    public static class KeyValuePair {
        @Getter
        private String key;
        @Getter
        private String value;

        public KeyValuePair(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    @ToString
    public static class SegmentRef {
        @Getter
        private int parentEndpointId;
        @Getter
        private String parentEndpoint;
        @Getter
        private int networkAddressId;
        @Getter
        private int entryEndpointId;
        @Getter
        private String refType;
        @Getter
        private int parentSpanId;
        @Getter
        private String parentTraceSegmentId;
        @Getter
        private int parentServiceInstanceId;
        @Getter
        private String networkAddress;
        @Getter
        private String entryEndpoint;
        @Getter
        private int entryServiceInstanceId;

        public SegmentRef(SegmentReference ref) {
            UniqueId segmentUniqueId = ref.getParentTraceSegmentId();
            this.parentTraceSegmentId = String.join(".", Long.toString(segmentUniqueId.getIdParts(0)), Long.toString(segmentUniqueId.getIdParts(1)), Long.toString(segmentUniqueId.getIdParts(2)));
            this.refType = ref.getRefType().toString();
            this.parentSpanId = ref.getParentSpanId();
            this.entryEndpointId = ref.getEntryEndpointId();
            this.networkAddressId = ref.getNetworkAddressId();
            this.parentServiceInstanceId = ref.getParentServiceInstanceId();
            this.parentEndpointId = ref.getParentEndpointId();
            this.parentEndpoint = ref.getParentEndpoint();
            this.networkAddress = ref.getNetworkAddress();
            this.entryEndpoint = ref.getEntryEndpoint();
            this.entryServiceInstanceId = ref.getEntryServiceInstanceId();
        }

        public SegmentRef(TraceSegmentReference ref) {
            UniqueId segmentUniqueId = ref.getParentTraceSegmentId();
            this.parentTraceSegmentId = String.join(".", Long.toString(segmentUniqueId.getIdParts(0)), Long.toString(segmentUniqueId.getIdParts(1)), Long.toString(segmentUniqueId.getIdParts(2)));
            this.refType = ref.getRefType().toString();
            this.parentSpanId = ref.getParentSpanId();
            this.entryEndpointId = ref.getEntryServiceId();
            this.networkAddressId = ref.getNetworkAddressId();
            this.parentServiceInstanceId = ref.getParentApplicationInstanceId();
            this.parentEndpointId = ref.getParentServiceId();
            this.parentEndpoint = ref.getParentServiceName();
            this.networkAddress = ref.getNetworkAddress();
            this.entryEndpoint = ref.getEntryServiceName();
            this.entryServiceInstanceId = ref.getEntryApplicationInstanceId();
        }
    }
}
