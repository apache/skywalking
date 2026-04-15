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
 */

package org.apache.skywalking.oap.server.core.source;

import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Uniform metadata carrier for log analysis. Receivers build this from their
 * native source (LogData proto for standard logs, ALS context for envoy, etc.).
 *
 * <p>For building from {@code LogData} protobuf, see {@link LogMetadataUtils}.
 */
@Data
@Builder
public class LogMetadata {
    private String service;
    private String serviceInstance;
    private String endpoint;
    private String layer;
    private long timestamp;
    @Builder.Default
    private TraceContext traceContext = TraceContext.EMPTY;

    /**
     * Non-persistent attributes from the log source (e.g., OTLP resource attributes,
     * ALS node context). Available to LAL scripts via {@code sourceAttribute()} but
     * NOT stored in tagsRawData.
     */
    @Builder.Default
    private Map<String, String> sourceAttributes = Collections.emptyMap();

    @Data
    @Builder
    public static class TraceContext {
        static final TraceContext EMPTY =
            TraceContext.builder().traceId("").traceSegmentId("").spanId(0).build();

        @Builder.Default
        private String traceId = "";
        @Builder.Default
        private String traceSegmentId = "";
        private int spanId;
    }
}
