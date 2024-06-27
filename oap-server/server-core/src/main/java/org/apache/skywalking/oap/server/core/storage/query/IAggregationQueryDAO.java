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
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.DAO;

/**
 * Query ordered list, based on storage side aggregation. Most storage supports `groupby`/`aggregation` query.
 *
 * @since 8.0.0
 */
public interface IAggregationQueryDAO extends DAO {
    default List<SelectedRecord> sortMetricsDebuggable(final TopNCondition condition,
                                                       final String valueColumnName,
                                                       final Duration duration,
                                                       final List<KeyValue> additionalConditions) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: sortMetrics");
                span.setMsg("Condition: TopNCondition: " + condition + ", ValueColumnName: " + valueColumnName + ", Duration: " + duration + ", AdditionalConditions: " + additionalConditions);
            }
            return sortMetrics(condition, valueColumnName, duration, additionalConditions);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    List<SelectedRecord> sortMetrics(TopNCondition condition,
                                     String valueColumnName,
                                     Duration duration,
                                     List<KeyValue> additionalConditions) throws IOException;
}
