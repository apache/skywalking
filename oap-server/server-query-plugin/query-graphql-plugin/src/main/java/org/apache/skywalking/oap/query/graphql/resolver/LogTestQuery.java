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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.dsl.Binding;
import org.apache.skywalking.oap.log.analyzer.dsl.DSL;
import org.apache.skywalking.oap.log.analyzer.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.query.graphql.GraphQLQueryConfig;
import org.apache.skywalking.oap.query.graphql.type.LogTestRequest;
import org.apache.skywalking.oap.query.graphql.type.LogTestResponse;
import org.apache.skywalking.oap.query.graphql.type.Metrics;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotBlank;

@RequiredArgsConstructor
public class LogTestQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;

    private final GraphQLQueryConfig config;

    public LogTestResponse test(LogTestRequest request) throws Exception {
        if (!config.isEnableLogTestTool()) {
            throw new IllegalAccessException(
                "LAL debug tool is not enabled. To enable, please set SW_QUERY_GRAPHQL_ENABLE_LOG_TEST_TOOL=true," +
                    "for more details, refer to https://skywalking.apache.org/docs/main/next/en/setup/backend/configuration-vocabulary/");
        }

        requireNonNull(request, "request");
        checkArgument(isNotBlank(request.getLog()), "request.log cannot be blank");
        checkArgument(isNotBlank(request.getDsl()), "request.dsl cannot be blank");

        final LogAnalyzerModuleProvider provider =
            (LogAnalyzerModuleProvider) moduleManager.find(LogAnalyzerModule.NAME)
                                                     .provider();
        final LogAnalyzerModuleConfig config = provider.getModuleConfig();
        final DSL dsl = DSL.of(moduleManager, config, request.getDsl());
        final Binding binding = new Binding();

        final LogData.Builder log = LogData.newBuilder();
        ProtoBufJsonUtils.fromJSON(request.getLog(), log);
        binding.log(log);

        binding.logContainer(new AtomicReference<>());
        binding.metricsContainer(new ArrayList<>());

        dsl.bind(binding);
        dsl.evaluate();

        final LogTestResponse.LogTestResponseBuilder builder = LogTestResponse.builder();
        binding.logContainer().map(AtomicReference::get).ifPresent(it -> {
            final Log l = new Log();

            if (isNotBlank(it.getServiceId())) {
                l.setServiceName(IDManager.ServiceID.analysisId(it.getServiceId()).getName());
            }
            l.setServiceId(it.getServiceId());
            if (isNotBlank(it.getServiceInstanceId())) {
                String name = IDManager.ServiceInstanceID.analysisId(it.getServiceId()).getName();
                l.setServiceInstanceName(name);
            }
            l.setServiceInstanceId(it.getServiceInstanceId());
            l.setEndpointId(it.getEndpointId());
            if (isNotBlank(it.getEndpointId())) {
                String name = IDManager.EndpointID.analysisId(it.getEndpointId()).getEndpointName();
                l.setEndpointName(name);
            }
            l.setTraceId(it.getTraceId());
            l.setTimestamp(it.getTimestamp());
            l.setContentType(it.getContentType());
            l.setContent(it.getContent());
            final List<KeyValue> tags = it.getTags()
                                          .stream()
                                          .map(tag -> new KeyValue(tag.getKey(), tag.getValue()))
                                          .collect(Collectors.toList());
            l.getTags().addAll(tags);

            builder.log(l);
        });
        binding.metricsContainer().ifPresent(it -> {
            final List<Metrics> samples =
                it.stream()
                  .flatMap(s -> Arrays.stream(s.samples))
                  .map(s -> new Metrics(
                      s.getName(),
                      s.getLabels().entrySet()
                       .stream().map(kv -> new KeyValue(kv.getKey(), kv.getValue()))
                       .collect(Collectors.toList()),
                      (long) s.getValue(),
                      s.getTimestamp()
                  ))
                  .collect(Collectors.toList());
            builder.metrics(samples);
        });
        return builder.build();
    }
}
