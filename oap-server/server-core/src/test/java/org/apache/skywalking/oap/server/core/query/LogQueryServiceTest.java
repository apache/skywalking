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

package org.apache.skywalking.oap.server.core.query;

import java.lang.reflect.Field;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A related trace is the whole trace, a SkyWalking native span by segment ID and span ID, or an OTLP or Zipkin span by
 * its hex ID, each with the trace ID.
 */
class LogQueryServiceTest {
    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String OTLP_SPAN = "b7ad6b7169203331";

    @Test
    void theAcceptedFormsAreRead() throws Exception {
        for (final TraceScopeCondition accepted : new TraceScopeCondition[] {
            condition(TRACE_ID, null, null, null),
            condition(TRACE_ID, "segment-1", 0, null),
            condition(TRACE_ID, null, null, OTLP_SPAN),
            // The LogQL handler passes an empty condition when the query names no trace ID.
            condition(null, null, null, null),
            condition("", "", null, "")
        }) {
            final ILogQueryDAO dao = dao();

            query(dao, accepted);

            verify(dao).queryLogsDebuggable(any(), any(), any(), eq(accepted), any(), anyInt(), anyInt(), any(), any(),
                                            any(), any());
        }
    }

    @Test
    void anyOtherMixIsRefusedBeforeStorageIsAsked() throws Exception {
        for (final TraceScopeCondition refused : new TraceScopeCondition[] {
            condition(TRACE_ID, "segment-1", 0, OTLP_SPAN),
            condition(TRACE_ID, "segment-1", null, OTLP_SPAN),
            condition(TRACE_ID, null, 0, OTLP_SPAN),
            condition(TRACE_ID, "segment-1", null, null),
            condition(TRACE_ID, null, 0, null),
            condition(null, "segment-1", 0, null),
            condition("", null, null, OTLP_SPAN),
            condition(null, null, 0, null)
        }) {
            final ILogQueryDAO dao = dao();
            assertThrows(IllegalArgumentException.class, () -> query(dao, refused), refused.toString());
            verify(dao, never()).queryLogsDebuggable(any(), any(), any(), any(), any(), anyInt(), anyInt(), any(),
                                                     any(), any(), any());
        }
    }

    private static TraceScopeCondition condition(final String traceId, final String segmentId, final Integer spanId,
                                                 final String otlpSpanId) {
        final TraceScopeCondition condition = new TraceScopeCondition();
        condition.setTraceId(traceId);
        condition.setSegmentId(segmentId);
        condition.setSpanId(spanId);
        condition.setOtlpSpanId(otlpSpanId);
        return condition;
    }

    private static ILogQueryDAO dao() throws Exception {
        final ILogQueryDAO dao = mock(ILogQueryDAO.class);
        when(dao.queryLogsDebuggable(any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any(),
                                     any())).thenReturn(new Logs());
        return dao;
    }

    private static void query(final ILogQueryDAO dao, final TraceScopeCondition condition) throws Exception {
        final LogQueryService service = new LogQueryService(null);
        final Field field = LogQueryService.class.getDeclaredField("logQueryDAO");
        field.setAccessible(true);
        field.set(service, dao);
        service.queryLogs(null, null, null, condition, new Pagination(1, 10), Order.DES, null, null, null, null);
    }
}
