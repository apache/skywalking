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
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public class LogQueryService implements Service {

    private final ModuleManager moduleManager;
    private ILogQueryDAO logQueryDAO;

    public LogQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ILogQueryDAO getLogQueryDAO() {
        if (logQueryDAO == null) {
            this.logQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ILogQueryDAO.class);
        }
        return logQueryDAO;
    }

    public boolean supportQueryLogsByKeywords() {
        return getLogQueryDAO().supportQueryLogsByKeywords();
    }

    public Logs queryLogs(String serviceId,
                          String serviceInstanceId,
                          String endpointId,
                          TraceScopeCondition relatedTrace,
                          Pagination paging,
                          Order queryOrder,
                          final Duration duration,
                          final List<Tag> tags,
                          List<String> keywordsOfContent,
                          List<String> excludingKeywordsOfContent) throws IOException {
        validateRelatedTrace(relatedTrace);
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                StringBuilder msg = new StringBuilder();
                span = traceContext.createSpan("Query Service: queryLogs");
                msg.append("ServiceId: ").append(serviceId).append(", ");
                msg.append("ServiceInstanceId: ").append(serviceInstanceId).append(", ");
                msg.append("EndpointId: ").append(endpointId).append(", ");
                msg.append("RelatedTrace: ").append(relatedTrace).append(", ");
                msg.append("Pagination: ").append(paging).append(", ");
                msg.append("QueryOrder: ").append(queryOrder).append(", ");
                msg.append("Duration: ").append(duration).append(", ");
                msg.append("Tags: ").append(tags).append(", ");
                msg.append("KeywordsOfContent: ").append(keywordsOfContent).append(", ");
                msg.append("ExcludingKeywordsOfContent: ").append(excludingKeywordsOfContent);
                span.setMsg(msg.toString());
            }
            return invokeQueryLogs(
                serviceId, serviceInstanceId, endpointId, relatedTrace, paging, queryOrder, duration, tags,
                keywordsOfContent, excludingKeywordsOfContent
            );
        } finally {
            if (traceContext != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    /**
     * A related trace is one of three forms: {@code traceId} alone for the whole trace, {@code traceId} +
     * {@code segmentId} + {@code spanId} for a SkyWalking native span, or {@code traceId} + {@code otlpSpanId} for an
     * OTLP or Zipkin span. Any other mix names a partial or contradictory span, which would answer "no logs", or a
     * different set of logs, rather than report the mistake. A condition with nothing set, as the LogQL handler passes
     * without a trace ID label, is no condition.
     */
    static void validateRelatedTrace(final TraceScopeCondition relatedTrace) {
        if (relatedTrace == null) {
            return;
        }
        final boolean segment = StringUtil.isNotEmpty(relatedTrace.getSegmentId());
        final boolean span = nonNull(relatedTrace.getSpanId());
        final boolean otlpSpan = StringUtil.isNotEmpty(relatedTrace.getOtlpSpanId());
        if (!segment && !span && !otlpSpan) {
            return;
        }
        final boolean nativeSpan = segment && span && !otlpSpan;
        final boolean otlpSpanOnly = otlpSpan && !segment && !span;
        if (StringUtil.isEmpty(relatedTrace.getTraceId()) || !(nativeSpan || otlpSpanOnly)) {
            throw new IllegalArgumentException(
                "relatedTrace takes traceId alone, traceId with segmentId and spanId for a SkyWalking native span, "
                    + "or traceId with otlpSpanId for an OTLP or Zipkin span");
        }
    }

    private Logs invokeQueryLogs(String serviceId,
                          String serviceInstanceId,
                          String endpointId,
                          TraceScopeCondition relatedTrace,
                          Pagination paging,
                          Order queryOrder,
                          final Duration duration,
                          final List<Tag> tags,
                          List<String> keywordsOfContent,
                          List<String> excludingKeywordsOfContent) throws IOException {
        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(paging);

        if (nonNull(keywordsOfContent)) {
            keywordsOfContent = keywordsOfContent.stream()
                                                 .filter(StringUtil::isNotEmpty)
                                                 .collect(Collectors.toList());
        }
        if (nonNull(excludingKeywordsOfContent)) {
            excludingKeywordsOfContent = excludingKeywordsOfContent.stream()
                                                                   .filter(StringUtil::isNotEmpty)
                                                                   .collect(Collectors.toList());
        }

        Logs logs = getLogQueryDAO().queryLogsDebuggable(serviceId,
                                               serviceInstanceId,
                                               endpointId,
                                               relatedTrace,
                                               queryOrder,
                                               page.getFrom(), page.getLimit(),
                                               duration, tags,
                                               keywordsOfContent, excludingKeywordsOfContent
        );
        logs.getLogs().forEach(log -> {
            if (StringUtil.isNotEmpty(log.getServiceId())) {
                final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
                    log.getServiceId());
                log.setServiceName(serviceIDDefinition.getName());
            }
            if (StringUtil.isNotEmpty(log.getServiceInstanceId())) {
                final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID
                    .analysisId(log.getServiceInstanceId());
                log.setServiceInstanceName(instanceIDDefinition.getName());
            }
        });
        return logs;
    }
}
