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
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
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
                                                             TraceScopeCondition relatedTrace,
                                                             Pagination paging,
                                                             Order queryOrder,
                                                             final Duration duration) throws IOException {
        sortBy = sortBy == null ? GenAIEvaluationRecordSortBy.EVALUATION_TIME : sortBy;
        if (booleanValue != null) {
            valueType = GenAIEvaluationValueType.BOOLEAN;
        } else if (minScore != null || maxScore != null) {
            valueType = GenAIEvaluationValueType.SCORE;
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
                                                                      TraceScopeCondition relatedTrace,
                                                                      Pagination paging,
                                                                      Order queryOrder,
                                                                      final Duration duration) throws IOException {
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);

        return getGenAIEvaluationRecordQueryDAO().queryGenAIEvaluationRecordDebuggable(
                serviceId, providerId, modelId, valueType, minScore, maxScore, booleanValue, sortBy, taskName, evaluationLevel, judgeModel,
                relatedTrace, queryOrder, page.getFrom(), page.getLimit(), duration
        );
    }

}
