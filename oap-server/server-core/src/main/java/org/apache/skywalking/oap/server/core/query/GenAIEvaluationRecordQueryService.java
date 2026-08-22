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

import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationRecordSortBy;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAIEvaluationValueType;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.GenAITraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAIEvaluationRecords;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IGenAIEvaluationRecordQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

import java.io.IOException;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public class GenAIEvaluationRecordQueryService implements Service {

    private final ModuleManager moduleManager;
    private IGenAIEvaluationRecordQueryDAO genAIEvaluationRecordQueryDAO;

    public GenAIEvaluationRecordQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IGenAIEvaluationRecordQueryDAO getGenAIEvaluationRecordQueryDAO() {
        if (genAIEvaluationRecordQueryDAO == null) {
            this.genAIEvaluationRecordQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IGenAIEvaluationRecordQueryDAO.class);
        }
        return genAIEvaluationRecordQueryDAO;
    }

    public GenAIEvaluationRecords queryGenAIEvaluationRecord(String serviceId,
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
                                                             Pagination paging,
                                                             Order queryOrder,
                                                             final Duration duration) throws IOException {
        sortBy = sortBy == null ? GenAIEvaluationRecordSortBy.EVALUATION_TIME : sortBy;
        validateRelatedTrace(relatedTrace);
        if (booleanValue != null) {
            if (minScore != null || maxScore != null) {
                throw new IllegalArgumentException("booleanValue cannot be combined with score bounds");
            }
            if (valueType != null && valueType != GenAIEvaluationValueType.BOOLEAN) {
                throw new IllegalArgumentException("booleanValue requires valueType BOOLEAN");
            }
            valueType = GenAIEvaluationValueType.BOOLEAN;
        } else if (minScore != null || maxScore != null) {
            if (valueType != null && valueType != GenAIEvaluationValueType.SCORE) {
                throw new IllegalArgumentException("score bounds require valueType SCORE");
            }
            valueType = GenAIEvaluationValueType.SCORE;
        }
        if (GenAIEvaluationRecordSortBy.SCORE_VALUE.equals(sortBy) && valueType != GenAIEvaluationValueType.SCORE) {
            throw new IllegalArgumentException("sortBy SCORE_VALUE requires valueType SCORE");
        }
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                StringBuilder msg = new StringBuilder();
                span = traceContext.createSpan("Query Service: queryGenAIEvaluationRecord");
                msg.append("ServiceId: ").append(serviceId).append(", ");
                msg.append("ProviderId: ").append(providerId).append(", ");
                msg.append("ModelId: ").append(modelId).append(", ");
                msg.append("RelatedTrace: ").append(relatedTrace).append(", ");
                msg.append("Pagination: ").append(paging).append(", ");
                msg.append("QueryOrder: ").append(queryOrder).append(", ");
                msg.append("Duration: ").append(duration);
                span.setMsg(msg.toString());
            }
            return queryGenAIEvaluationRecordInternal(
                serviceId, providerId, modelId, valueType, minScore, maxScore, booleanValue, sortBy, taskName, evaluationLevel, judgeModel,
                relatedTrace, paging, queryOrder, duration
            );
        } finally {
            if (traceContext != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    private GenAIEvaluationRecords queryGenAIEvaluationRecordInternal(String serviceId,
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
                                                                      Pagination paging,
                                                                      Order queryOrder,
                                                                      final Duration duration) throws IOException {
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);

        return getGenAIEvaluationRecordQueryDAO().queryGenAIEvaluationRecordDebuggable(
                serviceId, providerId, modelId, valueType, minScore, maxScore, booleanValue, sortBy, taskName, evaluationLevel, judgeModel,
                relatedTrace, queryOrder, page.getFrom(), page.getLimit(), duration
        );
    }

    private static void validateRelatedTrace(final GenAITraceScopeCondition relatedTrace) {
        if (relatedTrace == null) {
            return;
        }
        if (relatedTrace.getType() == null) {
            throw new IllegalArgumentException("relatedTrace.type is required");
        }
        final boolean hasSegmentId = relatedTrace.getSegmentId() != null && !relatedTrace.getSegmentId().isEmpty();
        final boolean hasSpanId = relatedTrace.getSpanId() != null && !relatedTrace.getSpanId().isEmpty();
        if (GenAITraceRefType.SKYWALKING_NATIVE.equals(relatedTrace.getType())) {
            if (hasSpanId) {
                throw new IllegalArgumentException("Native relatedTrace cannot contain spanId");
            }
            if (relatedTrace.getSpanIndex() != null && !hasSegmentId) {
                throw new IllegalArgumentException("Native relatedTrace.spanIndex requires segmentId");
            }
        } else if (GenAITraceRefType.OTLP.equals(relatedTrace.getType())) {
            if (hasSegmentId || relatedTrace.getSpanIndex() != null) {
                throw new IllegalArgumentException("OTLP relatedTrace cannot contain segmentId or spanIndex");
            }
        }
    }

}
