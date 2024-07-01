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

package org.apache.skywalking.oap.query.debug;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.server.annotation.Default;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.oap.query.debug.mqe.DebuggingMQERsp;
import org.apache.skywalking.oap.query.debug.trace.DebuggingQueryTraceBriefRsp;
import org.apache.skywalking.oap.query.debug.trace.DebuggingQueryTraceRsp;
import org.apache.skywalking.oap.query.debug.trace.zipkin.DebuggingZipkinQueryTraceRsp;
import org.apache.skywalking.oap.query.debug.trace.zipkin.DebuggingZipkinQueryTracesRsp;
import org.apache.skywalking.oap.query.graphql.resolver.MetricsExpressionQuery;
import org.apache.skywalking.oap.query.graphql.resolver.TraceQuery;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryConfig;
import org.apache.skywalking.oap.query.zipkin.handler.ZipkinQueryHandler;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Trace;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTrace;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.status.ServerStatusService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import zipkin2.Span;

@Slf4j
public class DebuggingHTTPHandler {
    private final ServerStatusService serverStatusService;
    private final MetricsExpressionQuery mqeQuery;
    private final TraceQuery traceQuery;
    private final ZipkinQueryHandler zipkinQueryHandler;
    final DebuggingQueryConfig config;

    public DebuggingHTTPHandler(final ModuleManager manager, final DebuggingQueryConfig config) {
        serverStatusService = manager.find(CoreModule.NAME)
                                     .provider()
                                     .getService(ServerStatusService.class);
        this.config = config;
        this.mqeQuery = new MetricsExpressionQuery(manager);
        this.traceQuery = new TraceQuery(manager);
        //use zipkin default config for debugging
        this.zipkinQueryHandler = new ZipkinQueryHandler(new ZipkinQueryConfig(), manager);
    }

    @Get("/debugging/config/dump")
    public String dumpConfigurations() {
        return serverStatusService.dumpBootingConfigurations(config.getKeywords4MaskingSecretsOfConfig());
    }

    @SneakyThrows
    @Get("/debugging/query/mqe")
    public String execExpression(@Param("dumpDBRsp") boolean dumpStorageRsp,
                                 @Param("expression") String expression,
                                 @Param("startTime") String startTime,
                                 @Param("endTime") String endTime,
                                 @Param("step") String step,
                                 @Param("service") String service,
                                 @Param("serviceLayer") String serviceLayer,
                                 @Param("serviceInstance") Optional<String> serviceInstance,
                                 @Param("endpoint") Optional<String> endpoint,
                                 @Param("process") Optional<String> process,
                                 @Param("destService") Optional<String> destService,
                                 @Param("destServiceLayer") Optional<String> destServiceLayer,
                                 @Param("destServiceInstance") Optional<String> destServiceInstance,
                                 @Param("destEndpoint") Optional<String> destEndpoint,
                                 @Param("destProcess") Optional<String> destProcess
    ) {
        Entity entity = new Entity();
        entity.setServiceName(service);
        entity.setServiceInstanceName(serviceInstance.orElse(null));
        entity.setEndpointName(endpoint.orElse(null));
        entity.setProcessName(process.orElse(null));
        entity.setDestServiceName(destService.orElse(null));
        entity.setDestServiceInstanceName(destServiceInstance.orElse(null));
        entity.setDestEndpointName(destEndpoint.orElse(null));
        entity.setDestProcessName(destProcess.orElse(null));
        entity.setNormal(Layer.nameOf(serviceLayer).isNormal());
        destServiceLayer.ifPresent(layer -> {
            entity.setDestNormal(Layer.nameOf(layer).isNormal());
        });
        Duration duration = new Duration();
        duration.setStart(startTime);
        duration.setEnd(endTime);
        duration.setStep(Step.valueOf(step));
        ExpressionResult expressionResult = mqeQuery.execExpression(expression, entity, duration, true, dumpStorageRsp);
        DebuggingTrace execTrace = expressionResult.getDebuggingTrace();
        DebuggingMQERsp result = new DebuggingMQERsp(
            expressionResult.getType(), expressionResult.getResults(), expressionResult.getError(),
            transformTrace(execTrace)
        );

        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/trace/queryBasicTraces")
    public String queryBasicTraces(@Param("service") String service,
                                   @Param("serviceLayer") String serviceLayer,
                                   @Param("serviceInstance") Optional<String> serviceInstance,
                                   @Param("endpoint") Optional<String> endpoint,
                                   @Param("traceId") Optional<String> traceId,
                                   @Param("startTime") String startTime,
                                   @Param("endTime") String endTime,
                                   @Param("step") String step,
                                   @Param("minTraceDuration") Optional<Integer> minDuration,
                                   @Param("maxTraceDuration") Optional<Integer> maxDuration,
                                   @Param("traceState") String traceState,
                                   @Param("queryOrder") String queryOrder,
                                   @Param("tags") Optional<String> tags,
                                   @Param("pageNum") int pageNum,
                                   @Param("pageSize") int pageSize
    ) {
        String serviceId = IDManager.ServiceID.buildId(service, Layer.nameOf(serviceLayer).isNormal());
        Optional<String> serviceInstanceId = serviceInstance.map(
            name -> IDManager.ServiceInstanceID.buildId(serviceId, name));
        Optional<String> endpointId = endpoint.map(name -> IDManager.EndpointID.buildId(serviceId, name));
        Duration duration = new Duration();
        duration.setStart(startTime);
        duration.setEnd(endTime);
        duration.setStep(Step.valueOf(step));
        Pagination pagination = new Pagination();
        pagination.setPageNum(pageNum);
        pagination.setPageSize(pageSize);
        TraceQueryCondition condition = new TraceQueryCondition();
        condition.setServiceId(serviceId);
        condition.setServiceInstanceId(serviceInstanceId.orElse(null));
        condition.setEndpointId(endpointId.orElse(null));
        condition.setTraceId(traceId.orElse(null));
        condition.setQueryDuration(duration);
        condition.setMinTraceDuration(minDuration.orElse(0));
        condition.setMaxTraceDuration(maxDuration.orElse(0));
        condition.setTraceState(TraceState.valueOf(traceState));
        condition.setQueryOrder(QueryOrder.valueOf(queryOrder));
        condition.setPaging(pagination);
        tags.ifPresent(ts -> {
            List<Tag> tagList = new ArrayList<>();
            Arrays.stream(ts.split(Const.COMMA)).forEach(t -> {
                Tag tag = new Tag();
                String[] tagArr = t.split(Const.EQUAL);
                tag.setKey(tagArr[0]);
                tag.setValue(tagArr[1]);
                tagList.add(tag);
            });
            condition.setTags(tagList);
        });

        TraceBrief traceBrief = traceQuery.queryBasicTraces(condition, true);
        DebuggingQueryTraceBriefRsp result = new DebuggingQueryTraceBriefRsp(
            traceBrief.getTraces(), transformTrace(traceBrief.getDebuggingTrace()));
        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/trace/queryTrace")
    public String queryTrace(@Param("traceId") String traceId) {
        Trace trace = traceQuery.queryTrace(traceId, true);
        DebuggingQueryTraceRsp result = new DebuggingQueryTraceRsp(
            trace.getSpans(), transformTrace(trace.getDebuggingTrace()));
        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/zipkin/api/v2/traces")
    public String queryZipkinTraces(@Param("serviceName") Optional<String> serviceName,
                                    @Param("remoteServiceName") Optional<String> remoteServiceName,
                                    @Param("spanName") Optional<String> spanName,
                                    @Param("annotationQuery") Optional<String> annotationQuery,
                                    @Param("minDuration") Optional<Long> minDuration,
                                    @Param("maxDuration") Optional<Long> maxDuration,
                                    @Param("endTs") Optional<Long> endTs,
                                    @Param("lookback") Optional<Long> lookback,
                                    @Default("10") @Param("limit") int limit) {
        final String condition = "serviceName: " + serviceName.orElse(null) +
            ", remoteServiceName: " + remoteServiceName.orElse(null) +
            ", spanName: " + spanName.orElse(null) +
            ", annotationQuery: " + annotationQuery.orElse(null) +
            ", minDuration: " + minDuration.orElse(null) +
            ", maxDuration: " + maxDuration.orElse(null) +
            ", endTs: " + endTs.orElse(null) +
            ", lookback: " + lookback.orElse(null) +
            ", limit: " + limit;
        DebuggingTraceContext traceContext = new DebuggingTraceContext(condition, true, false);
        DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
        try {
            AggregatedHttpResponse response = zipkinQueryHandler.getTraces(
                serviceName, remoteServiceName, spanName, annotationQuery, minDuration, maxDuration, endTs, lookback,
                limit
            );
            List<List<Span>> traces = new ArrayList<>();
            if (response.status().code() == 200) {
                traces = new Gson().fromJson(response.contentUtf8(), new TypeToken<ArrayList<ArrayList<Span>>>() {
                }.getType());
            }
            DebuggingZipkinQueryTracesRsp result = new DebuggingZipkinQueryTracesRsp(
                traces, transformTrace(traceContext.getExecTrace()));
            return transToYAMLStringZipkin(result);
        } finally {
            traceContext.stopTrace();
            DebuggingTraceContext.TRACE_CONTEXT.remove();
        }
    }

    @SneakyThrows
    @Get("/debugging/query/zipkin/api/v2/trace")
    public String getZipkinTraceById(@Param("traceId") String traceId) {
        DebuggingTraceContext traceContext = new DebuggingTraceContext("traceId: " + traceId, true, false);
        DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
        try {
            AggregatedHttpResponse response = zipkinQueryHandler.getTraceById(traceId);
            List<Span> trace = new ArrayList<>();
            if (response.status().code() == 200) {
                trace = new Gson().fromJson(response.contentUtf8(), new TypeToken<ArrayList<Span>>() {
                }.getType());
            }
            DebuggingZipkinQueryTraceRsp result = new DebuggingZipkinQueryTraceRsp(trace, transformTrace(
                traceContext.getExecTrace()));
            return transToYAMLStringZipkin(result);
        } finally {
            traceContext.stopTrace();
            DebuggingTraceContext.TRACE_CONTEXT.remove();
        }
    }

    private DebuggingTraceRsp transformTrace(DebuggingTrace trace) {
        Map<Integer, DebuggingSpanRsp> spanMap = trace.getSpans().stream()
                                                      .collect(Collectors.toMap(
                                                          DebuggingSpan::getSpanId,
                                                          this::transformSpan
                                                      ));
        trace.getSpans().forEach(span -> {
            if (span.getParentSpanId() != -1) {
                DebuggingSpanRsp parentSpan = spanMap.get(span.getParentSpanId());
                parentSpan.getChildSpans().add(spanMap.get(span.getSpanId()));
            }
        });

        return new DebuggingTraceRsp(
            trace.getTraceId(), trace.getCondition(), trace.getStartTime(), trace.getEndTime(), trace.getDuration(),
            spanMap.get(0)
        );
    }

    private DebuggingSpanRsp transformSpan(DebuggingSpan span) {
        return new DebuggingSpanRsp(
            span.getSpanId(), span.getParentSpanId(), span.getOperation(), span.getStartTime(), span.getEndTime(),
            span.getDuration(), span.getMsg(), span.getError()
        );
    }

    private String transToYAMLString(Object obj) {
        YAMLFactory yamlFactory = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(yamlFactory);
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to convert object to YAML String", e);
            return "Failed to convert object to YAML String";
        }
    }

    private String transToYAMLStringZipkin(Object obj) {
        YAMLFactory yamlFactory = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(yamlFactory).configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                                                           .setVisibility(
                                                               PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to convert object to YAML String", e);
            return "Failed to convert object to YAML String";
        }
    }
}
