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
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.LogQueryService;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.LogQueryCondition;
import org.apache.skywalking.oap.server.core.query.input.LogQueryConditionByName;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static java.util.Objects.isNull;
import static org.apache.skywalking.oap.query.graphql.AsyncQueryUtils.queryAsync;
import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public class LogQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;
    private LogQueryService logQueryService;
    private TagAutoCompleteQueryService tagQueryService;

    public LogQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private LogQueryService getQueryService() {
        if (logQueryService == null) {
            this.logQueryService = moduleManager.find(CoreModule.NAME).provider().getService(LogQueryService.class);
        }
        return logQueryService;
    }

    private TagAutoCompleteQueryService getTagQueryService() {
        if (tagQueryService == null) {
            this.tagQueryService = moduleManager.find(CoreModule.NAME).provider().getService(TagAutoCompleteQueryService.class);
        }
        return tagQueryService;
    }

    public boolean supportQueryLogsByKeywords() {
        return getQueryService().supportQueryLogsByKeywords();
    }

    public CompletableFuture<Logs> queryLogs(LogQueryCondition condition, boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "LogQueryCondition: " + condition, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query logs");
            try {
                Logs logs = invokeQueryLogs(condition);
                if (debug) {
                    logs.setDebuggingTrace(traceContext.getExecTrace());
                }
                return logs;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    public CompletableFuture<Logs> queryLogsByName(LogQueryConditionByName condition, boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "LogQueryConditionByName: " + condition, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query logs");
            try {
                LogQueryCondition logQueryCondition = new LogQueryCondition();
                logQueryCondition.setServiceId(condition.getServiceId());
                logQueryCondition.setServiceInstanceId(condition.getServiceInstanceId());
                logQueryCondition.setEndpointId(condition.getEndpointId());
                logQueryCondition.setRelatedTrace(condition.getRelatedTrace());
                logQueryCondition.setQueryDuration(condition.getQueryDuration());
                logQueryCondition.setPaging(condition.getPaging());
                logQueryCondition.setTags(condition.getTags());
                logQueryCondition.setKeywordsOfContent(condition.getKeywordsOfContent());
                logQueryCondition.setExcludingKeywordsOfContent(condition.getExcludingKeywordsOfContent());
                logQueryCondition.setQueryOrder(condition.getQueryOrder());

                Logs logs = invokeQueryLogs(logQueryCondition);
                if (debug) {
                    logs.setDebuggingTrace(traceContext.getExecTrace());
                }
                return logs;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    private Logs invokeQueryLogs(LogQueryCondition condition) throws IOException {
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
        return getQueryService().queryLogs(
            condition.getServiceId(),
            condition.getServiceInstanceId(),
            condition.getEndpointId(),
            condition.getRelatedTrace(),
            condition.getPaging(),
            queryOrder,
            condition.getQueryDuration(),
            condition.getTags(),
            condition.getKeywordsOfContent(),
            condition.getExcludingKeywordsOfContent()
        );
    }

    public CompletableFuture<Set<String>> queryLogTagAutocompleteKeys(final Duration queryDuration) {
        return queryAsync(() -> getTagQueryService().queryTagAutocompleteKeys(TagType.LOG, queryDuration));
    }

    public CompletableFuture<Set<String>> queryLogTagAutocompleteValues(final String tagKey, final Duration queryDuration) {
        return queryAsync(() -> getTagQueryService().queryTagAutocompleteValues(TagType.LOG, tagKey, queryDuration));
    }
}
