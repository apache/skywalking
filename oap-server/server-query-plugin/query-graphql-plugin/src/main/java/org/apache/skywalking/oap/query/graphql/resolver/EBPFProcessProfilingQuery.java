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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.ebpf.EBPFProfilingQueryService;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeAggregateType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskPrepare;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import java.util.List;

import static org.apache.skywalking.oap.query.graphql.resolver.AsyncQueryUtils.queryAsync;

public class EBPFProcessProfilingQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private EBPFProfilingQueryService queryService;

    public EBPFProcessProfilingQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public EBPFProfilingQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME)
                    .provider()
                    .getService(EBPFProfilingQueryService.class);
        }
        return queryService;
    }

    public CompletableFuture<EBPFProfilingTaskPrepare> queryPrepareCreateEBPFProfilingTaskData(String serviceId) {
        if (StringUtil.isEmpty(serviceId)) {
            throw new IllegalArgumentException("please provide the service id");
        }
        return queryAsync(() -> getQueryService().queryPrepareCreateEBPFProfilingTaskData(serviceId));
    }

    public CompletableFuture<List<EBPFProfilingTask>> queryEBPFProfilingTasks(String serviceId, String serviceInstanceId, List<EBPFProfilingTargetType> targets, EBPFProfilingTriggerType triggerType, Duration duration) {
        if (StringUtil.isEmpty(serviceId) && StringUtil.isEmpty(serviceInstanceId)) {
            throw new IllegalArgumentException("please provide the service id or instance id");
        }

        return queryAsync(() -> getQueryService().queryEBPFProfilingTasks(serviceId, serviceInstanceId, targets,
                                                                          Objects.requireNonNullElse(
                                                                              triggerType,
                                                                              EBPFProfilingTriggerType.FIXED_TIME
                                                                          ),
                                                                          duration
        ));
    }

    public CompletableFuture<List<EBPFProfilingSchedule>> queryEBPFProfilingSchedules(String taskId) {
        return queryAsync(() -> getQueryService().queryEBPFProfilingSchedules(taskId));
    }

    public CompletableFuture<EBPFProfilingAnalyzation> analysisEBPFProfilingResult(List<String> scheduleIdList,
                                                                List<EBPFProfilingAnalyzeTimeRange> timeRanges,
                                                                EBPFProfilingAnalyzeAggregateType aggregateType) {
        return queryAsync(() -> getQueryService().getEBPFProfilingAnalyzation(scheduleIdList, timeRanges, aggregateType));
    }
}
