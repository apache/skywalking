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
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.query.type.trace.v2.TraceList;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static org.apache.skywalking.oap.query.graphql.AsyncQueryUtils.queryAsync;
import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public class TraceQueryV2 implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private TraceQueryService queryService;

    public TraceQueryV2(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private TraceQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(TraceQueryService.class);
        }
        return queryService;
    }

    public CompletableFuture<TraceList> queryTraces(final TraceQueryCondition condition, boolean debug) throws IOException {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "TraceQueryCondition: " + condition, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query traces");
            try {
                TraceList traces = getQueryService().queryTraces(condition);
                if (debug) {
                    traces.setDebuggingTrace(traceContext.getExecTrace());
                }
                return traces;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    public CompletableFuture<Boolean> hasQueryTracesV2Support() {
        return queryAsync(() -> getQueryService().hasQueryTracesV2Support());
    }
}
