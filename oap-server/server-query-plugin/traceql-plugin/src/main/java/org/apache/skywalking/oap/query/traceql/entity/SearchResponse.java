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

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class SearchResponse extends QueryResponse {
    private List<Trace> traces = new ArrayList<>();

    @Data
    public static class Trace {
        private String traceID;
        private String rootServiceName;
        private String rootTraceName;
        private String startTimeUnixNano;
        private Integer durationMs;
        private List<SpanSet> spanSets = new ArrayList<>();
    }

    @Data
    public static class SpanSet {
        private List<Span> spans = new ArrayList<>();
        private Integer matched;
    }

    @Data
    public static class Span {
        private String spanID;
        private String startTimeUnixNano;
        private String durationNanos;
        private List<Attribute> attributes = new ArrayList<>();
    }

    @Data
    public static class Attribute {
        private String key;
        private Value value;
    }

    @Data
    public static class Value {
        private String stringValue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceStat {
        private Integer spanCount;
        private Integer errorCount;
    }
}