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

import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.OTLPTraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IOTLPTraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

/**
 * Reads natively stored OTLP spans for the query plugins. Constructed by its callers, the way
 * {@code ZipkinQueryService} is, rather than registered as a core service: it is a thin validation and
 * debugging-trace layer over {@link IOTLPTraceQueryDAO} and decodes nothing, so the OTLP protobuf stays out of
 * {@code server-core}.
 */
public class OTLPTraceQueryService {
    private final ModuleManager moduleManager;
    private IOTLPTraceQueryDAO queryDAO;

    public OTLPTraceQueryService(final ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IOTLPTraceQueryDAO getQueryDAO() {
        if (queryDAO == null) {
            queryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IOTLPTraceQueryDAO.class);
        }
        return queryDAO;
    }

    public List<String> getServiceNames() throws IOException {
        return getQueryDAO().getServiceNames();
    }

    public List<String> getSpanNames(final String serviceName) throws IOException {
        return getQueryDAO().getSpanNames(serviceName);
    }

    public List<String> getPeerServiceNames(final String serviceName) throws IOException {
        return getQueryDAO().getPeerServiceNames(serviceName);
    }

    /**
     * @param traceId  32 lowercase hex characters
     * @param duration nullable: the storage then searches everything it retains; a non-null one bounds the lookup
     *                 and its {@code coldStage} flag targets the BanyanDB cold stage.
     */
    public List<SpanWrapper> queryTraceById(final String traceId, @Nullable final Duration duration) throws IOException {
        final DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Service: queryOTLPTraceById");
                span.setMsg("Condition: TraceId: " + traceId + ", Duration: " + duration);
            }
            return getQueryDAO().queryTraceByIdDebuggable(traceId, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    /**
     * @throws IllegalArgumentException when the condition carries neither a trace id nor a time range.
     */
    public List<List<SpanWrapper>> queryTraces(final OTLPTraceQueryCondition condition) throws IOException {
        if (StringUtil.isEmpty(condition.getTraceId()) && condition.getQueryDuration() == null) {
            throw new IllegalArgumentException("The OTLP trace query requires a traceId or a queryDuration.");
        }
        final DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Service: queryOTLPTraces");
                span.setMsg("Condition: " + condition);
            }
            return getQueryDAO().queryTracesDebuggable(condition);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }
}
