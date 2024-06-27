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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.Service;

public interface ITraceQueryDAO extends Service {

    default TraceBrief queryBasicTracesDebuggable(Duration duration,
                                                  long minDuration,
                                                  long maxDuration,
                                                  String serviceId,
                                                  String serviceInstanceId,
                                                  String endpointId,
                                                  String traceId,
                                                  int limit,
                                                  int from,
                                                  TraceState traceState,
                                                  QueryOrder queryOrder,
                                                  final List<Tag> tags) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryBasicTraces");
                builder.append("Condition: Duration: ")
                       .append(duration)
                       .append(", MinDuration: ")
                       .append(minDuration)
                       .append(", MaxDuration: ")
                       .append(maxDuration)
                       .append(", ServiceId: ")
                       .append(serviceId)
                       .append(", ServiceInstanceId: ")
                       .append(serviceInstanceId)
                       .append(", EndpointId: ")
                       .append(endpointId)
                       .append(", TraceId: ")
                       .append(traceId)
                       .append(", Limit: ")
                       .append(limit)
                       .append(", From: ")
                       .append(from)
                       .append(", TraceState: ")
                       .append(traceState)
                       .append(", QueryOrder: ")
                       .append(queryOrder)
                       .append(", Tags: ")
                       .append(tags);
                span.setMsg(builder.toString());
            }
            return queryBasicTraces(
                duration, minDuration, maxDuration, serviceId, serviceInstanceId, endpointId, traceId, limit, from,
                traceState, queryOrder, tags
            );
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<SegmentRecord> queryByTraceIdDebuggable(String traceId) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryByTraceId");
                builder.append("Condition: TraceId: ")
                       .append(traceId);
                span.setMsg(builder.toString());
            }
            return queryByTraceId(traceId);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    TraceBrief queryBasicTraces(Duration duration,
                                long minDuration,
                                long maxDuration,
                                String serviceId,
                                String serviceInstanceId,
                                String endpointId,
                                String traceId,
                                int limit,
                                int from,
                                TraceState traceState,
                                QueryOrder queryOrder,
                                final List<Tag> tags) throws IOException;

    List<SegmentRecord> queryByTraceId(String traceId) throws IOException;

    List<SegmentRecord> queryBySegmentIdList(List<String> segmentIdList) throws IOException;

    List<SegmentRecord> queryByTraceIdWithInstanceId(List<String> traceIdList, List<String> instanceIdList) throws IOException;

    /**
     * This method gives more flexible for 3rd trace without segment concept, which can't search data through {@link #queryByTraceId(String)}
     */
    List<Span> doFlexibleTraceQuery(String traceId) throws IOException;
}
