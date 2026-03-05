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

package org.apache.skywalking.oap.query.traceql.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class OtlpTraceResponse extends QueryResponse {
    private TraceData trace;

    @Data
    public static class TraceData {
        private List<ResourceSpans> resourceSpans = new ArrayList<>();
    }

    @Data
    public static class ResourceSpans {
        private Resource resource;
        private List<ScopeSpans> scopeSpans = new ArrayList<>();
    }

    @Data
    public static class Resource {
        private List<KeyValue> attributes = new ArrayList<>();
    }

    @Data
    public static class ScopeSpans {
        private Scope scope;
        private List<Span> spans = new ArrayList<>();
    }

    @Data
    public static class Scope {
        private String name;
        private String version;
    }

    @Data
    public static class Span {
        private String traceId;
        private String spanId;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String parentSpanId;
        private String name;
        private String kind;
        private String startTimeUnixNano;
        private String endTimeUnixNano;
        private List<KeyValue> attributes = new ArrayList<>();
        private List<Event> events = new ArrayList<>();
        private Status status = new Status();
    }

    @Data
    public static class Event {
        private String timeUnixNano;
        private String name;
        private List<KeyValue> attributes = new ArrayList<>();
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Status {
        private String code;
        private String message;
    }

    @Data
    public static class KeyValue {
        private String key;
        private AnyValue value;
    }

    @Data
    public static class AnyValue {
        private String stringValue;
    }
}