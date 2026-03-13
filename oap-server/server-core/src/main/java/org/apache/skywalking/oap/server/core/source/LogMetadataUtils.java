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

import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;

/**
 * Utility for building {@link LogMetadata} from protobuf {@link LogData}.
 */
public final class LogMetadataUtils {

    private LogMetadataUtils() {
    }

    /**
     * Build LogMetadata from a LogData protobuf message.
     */
    public static LogMetadata fromLogData(final LogData logData) {
        final LogMetadata.TraceContext tc = buildTraceContext(logData.getTraceContext());
        return LogMetadata.builder()
                          .service(logData.getService())
                          .serviceInstance(logData.getServiceInstance())
                          .endpoint(logData.getEndpoint())
                          .layer(logData.getLayer())
                          .timestamp(logData.getTimestamp())
                          .traceContext(tc)
                          .build();
    }

    /**
     * Build LogMetadata from a LogData.Builder without building a full LogData.
     */
    public static LogMetadata fromLogData(final LogData.Builder builder) {
        final LogMetadata.TraceContext tc = buildTraceContext(builder.getTraceContext());
        return LogMetadata.builder()
                          .service(builder.getService())
                          .serviceInstance(builder.getServiceInstance())
                          .endpoint(builder.getEndpoint())
                          .layer(builder.getLayer())
                          .timestamp(builder.getTimestamp())
                          .traceContext(tc)
                          .build();
    }

    private static LogMetadata.TraceContext buildTraceContext(final TraceContext ctxProto) {
        if (ctxProto == null || ctxProto == TraceContext.getDefaultInstance()) {
            return LogMetadata.TraceContext.EMPTY;
        }
        return LogMetadata.TraceContext.builder()
                                      .traceId(ctxProto.getTraceId())
                                      .traceSegmentId(ctxProto.getTraceSegmentId())
                                      .spanId(ctxProto.getSpanId())
                                      .build();
    }
}
