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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.oap.query.debug.mqe.DebuggingMQERsp;
import org.apache.skywalking.oap.query.graphql.resolver.MetricsExpressionQuery;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTrace;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.status.ServerStatusService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
public class DebuggingHTTPHandler {
    private final ServerStatusService serverStatusService;
    private final MetricsExpressionQuery mqeQuery;
    final DebuggingQueryConfig config;

    public DebuggingHTTPHandler(final ModuleManager manager, final DebuggingQueryConfig config) {
        serverStatusService = manager.find(CoreModule.NAME)
                                     .provider()
                                     .getService(ServerStatusService.class);
        this.config = config;
        this.mqeQuery = new MetricsExpressionQuery(manager);
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

        Map<Integer, DebuggingSpanRsp> spanMap = execTrace.getSpans().stream()
                                                          .collect(Collectors.toMap(
                                                              DebuggingSpan::getSpanId,
                                                              this::transformSpan
                                                          ));

        execTrace.getSpans().forEach(span -> {
            if (span.getParentSpanId() != -1) {
                DebuggingSpanRsp parentSpan = spanMap.get(span.getParentSpanId());
                parentSpan.getChildSpans().add(spanMap.get(span.getSpanId()));
            }
        });
        DebuggingMQERsp result = new DebuggingMQERsp(
            expressionResult.getType(), expressionResult.getResults(), expressionResult.getError(),
            new DebuggingTraceRsp(
                execTrace.getTraceId(), execTrace.getCondition(), execTrace.getStartTime(), execTrace.getEndTime(),
                execTrace.getDuration(), spanMap.get(0)
            )
        );

        YAMLFactory yamlFactory = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(yamlFactory);

        return mapper.writeValueAsString(result);
    }

    private DebuggingSpanRsp transformSpan(DebuggingSpan span) {
        return new DebuggingSpanRsp(
            span.getSpanId(), span.getParentSpanId(), span.getOperation(), span.getStartTime(), span.getEndTime(),
            span.getDuration(), span.getMsg(), span.getError()
        );
    }
}
