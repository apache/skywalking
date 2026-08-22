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

import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationRecordSortBy;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationValueType;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.GenAITraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAIEvaluationRecords;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.Service;

import java.io.IOException;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public interface IGenAIEvaluationRecordQueryDAO extends Service {

    default GenAIEvaluationRecords queryGenAIEvaluationRecordDebuggable(String serviceId,
                                                      String providerId,
                                                      String modelId,
                                                      GenAIEvaluationValueType valueType,
                                                      Long minScore,
                                                      Long maxScore,
                                                      Boolean booleanValue,
                                                      GenAIEvaluationRecordSortBy sortBy,
                                                      String taskName,
                                                      String evaluationLevel,
                                                      String judgeModel,
                                                      GenAITraceScopeCondition relatedTrace,
                                                      Order queryOrder,
                                                      int from,
                                                      int limit,
                                                      final Duration duration) throws IOException {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryGenAIEvaluationRecord");
                StringBuilder msg = new StringBuilder();
                msg.append("ServiceId: ").append(serviceId)
                   .append(", ProviderId: ").append(providerId)
                   .append(", ModelId: ").append(modelId)
                   .append(", RelatedTrace: ").append(relatedTrace)
                   .append(", QueryOrder: ").append(queryOrder)
                   .append(", From: ").append(from)
                   .append(", Limit: ").append(limit)
                   .append(", Duration: ").append(duration);
                span.setMsg(msg.toString());
            }
            return queryGenAIEvaluationRecord(
                serviceId, providerId, modelId, valueType, minScore, maxScore, booleanValue, sortBy, taskName, evaluationLevel, judgeModel,
                relatedTrace, queryOrder, from, limit, duration
            );
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    GenAIEvaluationRecords queryGenAIEvaluationRecord(String serviceId,
                                                      String providerId,
                                                      String modelId,
                                                      GenAIEvaluationValueType valueType,
                                                      Long minScore,
                                                      Long maxScore,
                                                      Boolean booleanValue,
                                                      GenAIEvaluationRecordSortBy sortBy,
                                                      String taskName,
                                                      String evaluationLevel,
                                                      String judgeModel,
                                                      GenAITraceScopeCondition relatedTrace,
                                                      Order queryOrder,
                                                      int from,
                                                      int limit,
                                                      final Duration duration) throws IOException;
}
