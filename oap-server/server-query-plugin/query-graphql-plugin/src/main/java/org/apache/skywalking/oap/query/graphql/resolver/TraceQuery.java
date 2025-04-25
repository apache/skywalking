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
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Trace;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static java.util.Objects.isNull;
import static org.apache.skywalking.oap.query.graphql.AsyncQueryUtils.queryAsync;
import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public class TraceQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private TraceQueryService queryService;
    private TagAutoCompleteQueryService tagQueryService;

    public TraceQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private TraceQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(TraceQueryService.class);
        }
        return queryService;
    }

    private TagAutoCompleteQueryService getTagQueryService() {
        if (tagQueryService == null) {
            this.tagQueryService = moduleManager.find(CoreModule.NAME).provider().getService(TagAutoCompleteQueryService.class);
        }
        return tagQueryService;
    }

    public CompletableFuture<TraceBrief> queryBasicTraces(final TraceQueryCondition condition, boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "TraceQueryCondition: " + condition, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query basic traces");
            try {
                TraceBrief traceBrief = invokeQueryBasicTraces(condition);
                if (debug) {
                    traceBrief.setDebuggingTrace(traceContext.getExecTrace());
                }
                return traceBrief;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    private TraceBrief invokeQueryBasicTraces(final TraceQueryCondition condition) throws IOException {
        String traceId = Const.EMPTY_STRING;

        if (!Strings.isNullOrEmpty(condition.getTraceId())) {
            traceId = condition.getTraceId();
        } else if (isNull(condition.getQueryDuration())) {
            throw new UnexpectedException("The condition must contains either queryDuration or traceId.");
        }

        int minDuration = condition.getMinTraceDuration();
        int maxDuration = condition.getMaxTraceDuration();
        String endpointId = condition.getEndpointId();
        TraceState traceState = condition.getTraceState();
        QueryOrder queryOrder = condition.getQueryOrder();
        Pagination pagination = condition.getPaging();

        return getQueryService().queryBasicTraces(
            condition.getServiceId(), condition.getServiceInstanceId(), endpointId, traceId, minDuration,
            maxDuration, traceState, queryOrder, pagination, condition.getQueryDuration(), condition.getTags()
        );
    }

    public CompletableFuture<Trace> queryTrace(final String traceId, boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "TraceId: " + traceId, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query trace");
            try {
                Trace trace = getQueryService().queryTrace(traceId, null);
                if (debug) {
                    trace.setDebuggingTrace(traceContext.getExecTrace());
                }
                return trace;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    public CompletableFuture<Trace> queryTraceFromColdStage(final String traceId, Duration duration, boolean debug) {
        duration.setColdStage(true);
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "TraceId: " + traceId, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query trace from cold stage");
            try {
                Trace trace = getQueryService().queryTrace(traceId, duration);
                if (debug) {
                    trace.setDebuggingTrace(traceContext.getExecTrace());
                }
                return trace;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    public CompletableFuture<Set<String>> queryTraceTagAutocompleteKeys(final Duration queryDuration) {
        return queryAsync(() -> getTagQueryService().queryTagAutocompleteKeys(TagType.TRACE, queryDuration));
    }

    public CompletableFuture<Set<String>> queryTraceTagAutocompleteValues(final String tagKey, final Duration queryDuration) {
        return queryAsync(() -> getTagQueryService().queryTagAutocompleteValues(TagType.TRACE, tagKey, queryDuration));
    }
}
