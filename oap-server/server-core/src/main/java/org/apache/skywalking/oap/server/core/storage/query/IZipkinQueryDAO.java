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

import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.DAO;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface IZipkinQueryDAO extends DAO {
    default List<List<Span>> getTracesDebuggable(final QueryRequest request,
                                                 final Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: getTraces");
                builder.append("Condition: Request: ")
                       .append(request)
                       .append(", Duration: ")
                       .append(duration);
                span.setMsg(builder.toString());
            }
            return getTraces(request, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    /**
     * @param duration nullable unless for BanyanDB query from cold stage
     */
    default List<Span> getTraceDebuggable(final String traceId, @Nullable final Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: getTrace");
                span.setMsg("Condition: TraceId: " + traceId);
            }
            return getTrace(traceId, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    List<String> getServiceNames() throws IOException;

    List<String> getRemoteServiceNames(final String serviceName) throws IOException;

    List<String> getSpanNames(final String serviceName) throws IOException;

    /**
     * @param duration nullable unless for BanyanDB query from cold stage
     */
    List<Span> getTrace(final String traceId, @Nullable final Duration duration) throws IOException;

    /**
     * @param duration nullable unless for BanyanDB query from cold stage
     */
    List<List<Span>> getTraces(final QueryRequest request, final Duration duration) throws IOException;

    /**
     * @param duration nullable unless for BanyanDB query from cold stage
     */
    List<List<Span>> getTraces(final Set<String> traceIds, @Nullable final Duration duration) throws IOException;
}
