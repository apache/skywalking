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
import com.linecorp.armeria.server.annotation.ExceptionHandler;
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
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.query.debug.log.DebuggingQueryLogsRsp;
import org.apache.skywalking.oap.query.debug.mqe.DebuggingMQERsp;
import org.apache.skywalking.oap.query.debug.topology.DebuggingQueryEndpointTopologyRsp;
import org.apache.skywalking.oap.query.debug.topology.DebuggingQueryInstanceTopologyRsp;
import org.apache.skywalking.oap.query.debug.topology.DebuggingQueryProcessTopologyRsp;
import org.apache.skywalking.oap.query.debug.topology.DebuggingQueryServiceTopologyRsp;
import org.apache.skywalking.oap.query.debug.trace.DebuggingQueryTraceBriefRsp;
import org.apache.skywalking.oap.query.debug.trace.DebuggingQueryTraceRsp;
import org.apache.skywalking.oap.query.debug.trace.zipkin.DebuggingZipkinQueryTraceRsp;
import org.apache.skywalking.oap.query.debug.trace.zipkin.DebuggingZipkinQueryTracesRsp;
import org.apache.skywalking.oap.query.graphql.resolver.LogQuery;
import org.apache.skywalking.oap.query.graphql.resolver.MetricsExpressionQuery;
import org.apache.skywalking.oap.query.graphql.resolver.TopologyQuery;
import org.apache.skywalking.oap.query.graphql.resolver.TraceQuery;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryConfig;
import org.apache.skywalking.oap.query.zipkin.handler.ZipkinQueryHandler;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.LogQueryCondition;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.EndpointTopology;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.ProcessTopology;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstanceTopology;
import org.apache.skywalking.oap.server.core.query.type.Topology;
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
@ExceptionHandler(StatusQueryExceptionHandler.class)
public class DebuggingHTTPHandler {
    private final ServerStatusService serverStatusService;
    private final MetricsExpressionQuery mqeQuery;
    private final TraceQuery traceQuery;
    private final ZipkinQueryHandler zipkinQueryHandler;
    private final TopologyQuery topologyQuery;
    private final LogQuery logQuery;
    final StatusQueryConfig config;

    public DebuggingHTTPHandler(final ModuleManager manager, final StatusQueryConfig config) {
        serverStatusService = manager.find(CoreModule.NAME)
                                     .provider()
                                     .getService(ServerStatusService.class);
        this.config = config;
        this.mqeQuery = new MetricsExpressionQuery(manager);
        this.traceQuery = new TraceQuery(manager);
        //use zipkin default config for debugging
        this.zipkinQueryHandler = new ZipkinQueryHandler(new ZipkinQueryConfig(), manager);
        this.topologyQuery = new TopologyQuery(manager);
        this.logQuery = new LogQuery(manager);
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
                                 @Param("coldStage") Optional<Boolean> coldStage,
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
        coldStage.ifPresent(duration::setColdStage);
        ExpressionResult expressionResult = mqeQuery.execExpression(expression, entity, duration, true, dumpStorageRsp).join();
        DebuggingTrace execTrace = expressionResult.getDebuggingTrace();
        DebuggingMQERsp result = new DebuggingMQERsp(
            expressionResult.getType(), expressionResult.getResults(), expressionResult.getError(),
            transformTrace(execTrace)
        );

        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/trace/queryBasicTraces")
    public String queryBasicTraces(@Param("service") Optional<String> service,
                                   @Param("serviceLayer") Optional<String> serviceLayer,
                                   @Param("serviceInstance") Optional<String> serviceInstance,
                                   @Param("endpoint") Optional<String> endpoint,
                                   @Param("traceId") Optional<String> traceId,
                                   @Param("startTime") String startTime,
                                   @Param("endTime") String endTime,
                                   @Param("step") String step,
                                   @Param("coldStage") Optional<Boolean> coldStage,
                                   @Param("minTraceDuration") Optional<Integer> minDuration,
                                   @Param("maxTraceDuration") Optional<Integer> maxDuration,
                                   @Param("traceState") String traceState,
                                   @Param("queryOrder") String queryOrder,
                                   @Param("tags") Optional<String> tags,
                                   @Param("pageNum") int pageNum,
                                   @Param("pageSize") int pageSize
    ) {
        Optional<String> serviceId = service.map(
            name -> IDManager.ServiceID.buildId(name, Layer.nameOf(serviceLayer.orElseThrow()).isNormal()));
        Optional<String> serviceInstanceId = serviceInstance.map(
            name -> IDManager.ServiceInstanceID.buildId(serviceId.orElseThrow(), name));
        Optional<String> endpointId = endpoint.map(name -> IDManager.EndpointID.buildId(serviceId.orElseThrow(), name));
        Duration duration = new Duration();
        duration.setStart(startTime);
        duration.setEnd(endTime);
        duration.setStep(Step.valueOf(step));
        coldStage.ifPresent(duration::setColdStage);
        Pagination pagination = new Pagination();
        pagination.setPageNum(pageNum);
        pagination.setPageSize(pageSize);
        TraceQueryCondition condition = new TraceQueryCondition();
        condition.setServiceId(serviceId.orElse(null));
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

        TraceBrief traceBrief = traceQuery.queryBasicTraces(condition, true).join();
        DebuggingQueryTraceBriefRsp result = new DebuggingQueryTraceBriefRsp(
            traceBrief.getTraces(), transformTrace(traceBrief.getDebuggingTrace()));
        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/trace/queryTrace")
    public String queryTrace(@Param("traceId") String traceId) {
        Trace trace = traceQuery.queryTrace(traceId, true).join();
        DebuggingQueryTraceRsp result = new DebuggingQueryTraceRsp(
            trace.getSpans(), transformTrace(trace.getDebuggingTrace()));
        return transToYAMLString(result);
    }

    /**
     * Only for BanyanDB, can be used to query the trace in the cold stage.
     */
    @SneakyThrows
    @Get("/debugging/query/trace/queryTraceFromColdStage")
    public String queryTraceFromColdStage(@Param("traceId") String traceId,
                                          @Param("startTime") String startTime,
                                          @Param("endTime") String endTime,
                                          @Param("step") String step) {
        Duration duration = new Duration();
        duration.setStart(startTime);
        duration.setEnd(endTime);
        duration.setStep(Step.valueOf(step));
        duration.setColdStage(true);
        Trace trace = traceQuery.queryTraceFromColdStage(traceId, duration, true).join();
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

    @SneakyThrows
    @Get("/debugging/query/topology/getGlobalTopology")
    public String getGlobalTopology(@Param("startTime") String startTime,
                                 @Param("endTime") String endTime,
                                 @Param("step") String step,
                                 @Param("coldStage") Optional<Boolean> coldStage,
                                 @Param("serviceLayer") Optional<String> serviceLayer) {
        Duration duration = new Duration();
        duration.setStart(startTime);
        duration.setEnd(endTime);
        duration.setStep(Step.valueOf(step));
        coldStage.ifPresent(duration::setColdStage);
        Topology topology = topologyQuery.getGlobalTopology(duration, serviceLayer.orElse(null), true).join();
        DebuggingQueryServiceTopologyRsp result = new DebuggingQueryServiceTopologyRsp(
            topology.getNodes(), topology.getCalls(), transformTrace(topology.getDebuggingTrace()));
        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/topology/getServicesTopology")
    public String getServicesTopology(@Param("startTime") String startTime,
                                    @Param("endTime") String endTime,
                                    @Param("step") String step,
                                    @Param("coldStage") Optional<Boolean> coldStage,
                                    @Param("serviceLayer") String serviceLayer,
                                    @Param("services") String services) {
        Duration duration = new Duration();
        duration.setStart(startTime);
        duration.setEnd(endTime);
        duration.setStep(Step.valueOf(step));
        coldStage.ifPresent(duration::setColdStage);
        List<String> ids = Arrays.stream(services.split(Const.COMMA))
                                 .map(name -> IDManager.ServiceID.buildId(name, Layer.nameOf(serviceLayer).isNormal()))
                                 .collect(Collectors.toList());
        Topology topology = topologyQuery.getServicesTopology(ids, duration, true).join();
        DebuggingQueryServiceTopologyRsp result = new DebuggingQueryServiceTopologyRsp(
            topology.getNodes(), topology.getCalls(), transformTrace(topology.getDebuggingTrace()));
        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/topology/getServiceInstanceTopology")
    public String getServiceInstanceTopology(@Param("startTime") String startTime,
                                             @Param("endTime") String endTime,
                                             @Param("step") String step,
                                             @Param("coldStage") Optional<Boolean> coldStage,
                                             @Param("clientService") String clientService,
                                             @Param("serverService") String serverService,
                                             @Param("clientServiceLayer") String clientServiceLayer,
                                             @Param("serverServiceLayer") String serverServiceLayer) {
        Duration duration = new Duration();
        duration.setStart(startTime);
        duration.setEnd(endTime);
        duration.setStep(Step.valueOf(step));
        coldStage.ifPresent(duration::setColdStage);
        String clientServiceId = IDManager.ServiceID.buildId(clientService, Layer.nameOf(clientServiceLayer).isNormal());
        String serverServiceId = IDManager.ServiceID.buildId(serverService, Layer.nameOf(serverServiceLayer).isNormal());
        ServiceInstanceTopology topology = topologyQuery.getServiceInstanceTopology(clientServiceId, serverServiceId, duration, true).join();
        DebuggingQueryInstanceTopologyRsp result = new DebuggingQueryInstanceTopologyRsp(
            topology.getNodes(), topology.getCalls(), transformTrace(topology.getDebuggingTrace()));
        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/topology/getEndpointDependencies")
    public String getEndpointDependencies(@Param("startTime") String startTime,
                                          @Param("endTime") String endTime,
                                          @Param("step") String step,
                                          @Param("coldStage") Optional<Boolean> coldStage,
                                          @Param("service") String service,
                                          @Param("serviceLayer") String serviceLayer,
                                          @Param("endpoint") String endpoint) {
        Duration duration = new Duration();
        duration.setStart(startTime);
        duration.setEnd(endTime);
        duration.setStep(Step.valueOf(step));
        coldStage.ifPresent(duration::setColdStage);
        String endpointId = IDManager.EndpointID.buildId(
            IDManager.ServiceID.buildId(service, Layer.nameOf(serviceLayer).isNormal()), endpoint);
        EndpointTopology topology = topologyQuery.getEndpointDependencies(endpointId, duration, true).join();
        DebuggingQueryEndpointTopologyRsp result = new DebuggingQueryEndpointTopologyRsp(
            topology.getNodes(), topology.getCalls(), transformTrace(topology.getDebuggingTrace()));
        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/topology/getProcessTopology")
    public String getProcessTopology(@Param("startTime") String startTime,
                                     @Param("endTime") String endTime,
                                     @Param("step") String step,
                                     @Param("coldStage") Optional<Boolean> coldStage,
                                     @Param("service") String service,
                                     @Param("serviceLayer") String serviceLayer,
                                     @Param("instance") String process) {
        Duration duration = new Duration();
        duration.setStart(startTime);
        duration.setEnd(endTime);
        duration.setStep(Step.valueOf(step));
        coldStage.ifPresent(duration::setColdStage);
        String instanceId = IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId(service, Layer.nameOf(serviceLayer).isNormal()), process);
        ProcessTopology topology = topologyQuery.getProcessTopology(instanceId, duration, true).join();
        DebuggingQueryProcessTopologyRsp result = new DebuggingQueryProcessTopologyRsp(
            topology.getNodes(), topology.getCalls(), transformTrace(topology.getDebuggingTrace()));
        return transToYAMLString(result);
    }

    @SneakyThrows
    @Get("/debugging/query/log/queryLogs")
    public String queryLogs(@Param("service") Optional<String> service,
                            @Param("serviceLayer") Optional<String> serviceLayer,
                            @Param("serviceInstance") Optional<String> serviceInstance,
                            @Param("endpoint") Optional<String> endpoint,
                            @Param("startTime") Optional<String> startTime,
                            @Param("endTime") Optional<String> endTime,
                            @Param("step") Optional<String> step,
                            @Param("coldStage") Optional<Boolean> coldStage,
                            @Param("traceId") Optional<String> traceId,
                            @Param("segmentId") Optional<String> segmentId,
                            @Param("spanId") Optional<Integer> spanId,
                            @Param("tags") Optional<String> tags,
                            @Param("pageNum") int pageNum,
                            @Param("pageSize") int pageSize,
                            @Param("keywordsOfContent") Optional<String> keywordsOfContent,
                            @Param("excludingKeywordsOfContent") Optional<String> excludingKeywordsOfContent,
                            @Param("queryOrder") Optional<String> queryOrder) {
        LogQueryCondition condition = new LogQueryCondition();
        condition.setPaging(new Pagination(pageNum, pageSize));
        if (traceId.isPresent()) {
            TraceScopeCondition traceScopeCondition = new TraceScopeCondition();
            traceScopeCondition.setTraceId(traceId.get());
            segmentId.ifPresent(traceScopeCondition::setSegmentId);
            spanId.ifPresent(traceScopeCondition::setSpanId);
            condition.setRelatedTrace(traceScopeCondition);
        } else {
            if (startTime.isEmpty() || endTime.isEmpty() || step.isEmpty()) {
                return "startTime, endTime and step are required";
            }
            Duration duration = new Duration();
            duration.setStart(startTime.get());
            duration.setEnd(endTime.get());
            duration.setStep(Step.valueOf(step.get()));
            coldStage.ifPresent(duration::setColdStage);
            condition.setQueryDuration(duration);
        }

        Optional<String> serviceId = service.map(
            name -> IDManager.ServiceID.buildId(name, Layer.nameOf(serviceLayer.orElseThrow()).isNormal()));
        Optional<String> serviceInstanceId = serviceInstance.map(
            name -> IDManager.ServiceInstanceID.buildId(serviceId.orElseThrow(), name));
        Optional<String> endpointId = endpoint.map(name -> IDManager.EndpointID.buildId(serviceId.orElseThrow(), name));
        serviceId.ifPresent(condition::setServiceId);
        serviceInstanceId.ifPresent(condition::setServiceInstanceId);
        endpointId.ifPresent(condition::setEndpointId);
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
        queryOrder.ifPresent(s -> condition.setQueryOrder(Order.valueOf(s)));
        keywordsOfContent.ifPresent(
            k -> condition.setKeywordsOfContent(Arrays.stream(k.split(Const.COMMA)).collect(Collectors.toList())));
        excludingKeywordsOfContent.ifPresent(e -> condition.setExcludingKeywordsOfContent(
            Arrays.stream(e.split(Const.COMMA)).collect(Collectors.toList())));

        Logs logs = logQuery.queryLogs(condition, true).join();
        DebuggingQueryLogsRsp result = new DebuggingQueryLogsRsp(
            logs.getLogs(), logs.getErrorReason(), transformTrace(logs.getDebuggingTrace()));
        return transToYAMLString(result);
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
