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

package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLQueryResolver;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.query.GenAIEvaluationRecordQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.GenAIEvaluationRecordQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAIEvaluationRecords;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.isNull;
import static org.apache.skywalking.oap.query.graphql.AsyncQueryUtils.queryAsync;
import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public class GenAIEvaluationRecordQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;
    private GenAIEvaluationRecordQueryService genAIEvaluationRecordQueryService;

    public GenAIEvaluationRecordQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public boolean supportGenAIEvaluationRecordQueryByKeywords() {
        return getQueryService().supportQueryGenAIEvaluationRecordByKeywords();
    }

    private GenAIEvaluationRecordQueryService getQueryService() {
        if (genAIEvaluationRecordQueryService == null) {
            this.genAIEvaluationRecordQueryService = moduleManager.find(CoreModule.NAME).provider().getService(GenAIEvaluationRecordQueryService.class);
        }
        return genAIEvaluationRecordQueryService;
    }

    public CompletableFuture<GenAIEvaluationRecords> queryGenAIEvaluationRecord(
            GenAIEvaluationRecordQueryCondition condition, boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                    "GenAIEvaluationRecordCondition: " + condition, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query gen AI evaluation records");
            try {
                GenAIEvaluationRecords evaluationRecords = queryGenAIEvaluationRecord(condition);
                if (debug) {
                    evaluationRecords.setDebuggingTrace(traceContext.getExecTrace());
                }
                return evaluationRecords;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    private GenAIEvaluationRecords queryGenAIEvaluationRecord(
            GenAIEvaluationRecordQueryCondition condition) throws IOException {
        if (isNull(condition.getQueryDuration()) && isNull(condition.getRelatedTrace())) {
            throw new UnexpectedException("The condition must contains either queryDuration or relatedTrace.");
        }

        Order queryOrder = isNull(condition.getQueryOrder()) ? Order.DES : condition.getQueryOrder();
        if (CollectionUtils.isNotEmpty(condition.getTags())) {
            condition.getTags().forEach(tag -> {
                if (tag != null) {
                    if (StringUtil.isNotEmpty(tag.getKey())) {
                        tag.setKey(tag.getKey().trim());
                    }
                    if (StringUtil.isNotEmpty(tag.getValue())) {
                        tag.setValue(tag.getValue().trim());
                    }
                }
            });
        }
        return getQueryService().queryGenAIEvaluationRecord(
                condition.getServiceId(),
                condition.getServiceInstanceId(),
                condition.getRelatedTrace(),
                condition.getPaging(),
                queryOrder,
                condition.getQueryDuration(),
                condition.getTags()
        );
    }
}
