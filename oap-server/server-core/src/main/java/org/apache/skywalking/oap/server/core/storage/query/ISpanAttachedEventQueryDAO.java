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

import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventTraceType;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.Service;

import java.io.IOException;
import java.util.List;

public interface ISpanAttachedEventQueryDAO extends Service {
    default List<SpanAttachedEventRecord> querySpanAttachedEventsDebuggable(SpanAttachedEventTraceType type, List<String> traceIds) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            StringBuilder builder = new StringBuilder();
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: querySpanAttachedEvents");
                builder.append("Condition: Type: ")
                       .append(type)
                       .append(", TraceIds: ")
                       .append(traceIds);
                span.setMsg(builder.toString());
            }
            return querySpanAttachedEvents(type, traceIds);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    List<SpanAttachedEventRecord> querySpanAttachedEvents(SpanAttachedEventTraceType type, List<String> traceIds) throws IOException;
}
