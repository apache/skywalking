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
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.TopNStreamProcessor;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Query the records sampled by {@link Stream} = {@link TopNStreamProcessor}
 *
 * @since 8.0.0
 */
public interface IRecordsQueryDAO extends Service {
    default List<Record> readRecordsDebuggable(final RecordCondition condition,
                                               final String valueColumnName,
                                               final Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: readRecords");
                span.setMsg("Condition: RecordCondition: " + condition + ", ValueColumnName: " + valueColumnName + ", Duration: " + duration);
            }
            return readRecords(condition, valueColumnName, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    List<Record> readRecords(RecordCondition condition,
                             final String valueColumnName,
                             Duration duration) throws IOException;
}
