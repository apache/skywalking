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
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.DAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;

/**
 * Reads natively stored OTLP spans, {@code otlp_span}, and their name catalogs. Every storage returns the spans
 * as {@link SpanWrapper} carrying the stored single-span {@code ResourceSpans} bytes, so {@code server-core}
 * never decodes the OTLP protobuf.
 */
public interface IOTLPTraceQueryDAO extends DAO {
    /**
     * @param duration nullable: the catalog is then read without a time bound.
     */
    List<String> getServiceNames(@Nullable Duration duration) throws IOException;

    /**
     * @param duration nullable: the catalog is then read without a time bound.
     */
    List<String> getSpanNames(String serviceName, @Nullable Duration duration) throws IOException;

    /**
     * @param duration nullable: the catalog is then read without a time bound.
     */
    List<String> getPeerServiceNames(String serviceName, @Nullable Duration duration) throws IOException;

    /**
     * @param duration nullable: BanyanDB then searches everything the hot and warm stages retain; a non-null one
     *                 bounds the lookup, and its {@code coldStage} flag targets the BanyanDB cold stage instead.
     */
    List<SpanWrapper> queryTraceById(String traceId, @Nullable Duration duration) throws IOException;

    /**
     * @return the spans of every matched trace, grouped by trace, newest trace first for
     * {@code QueryOrder.BY_START_TIME} and longest first for {@code QueryOrder.BY_DURATION}.
     */
    List<List<SpanWrapper>> queryTraces(OTLPTraceQueryCondition condition) throws IOException;

    default List<SpanWrapper> queryTraceByIdDebuggable(final String traceId,
                                                       @Nullable final Duration duration) throws IOException {
        final DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryTraceById");
                span.setMsg("Condition: TraceId: " + traceId + ", Duration: " + duration);
            }
            return queryTraceById(traceId, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<List<SpanWrapper>> queryTracesDebuggable(final OTLPTraceQueryCondition condition) throws IOException {
        final DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryTraces");
                span.setMsg("Condition: " + condition);
            }
            return queryTraces(condition);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }
}
