/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.AsyncProfilerQueryService;
import org.apache.skywalking.oap.server.core.query.AsyncProfilerTaskLog;
import org.apache.skywalking.oap.server.core.query.input.AsyncProfilerAnalyzatonRequest;
import org.apache.skywalking.oap.server.core.query.input.AsyncProfilerTaskListRequest;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerStackTree;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskListResult;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskLogOperationType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskProgress;
import org.apache.skywalking.oap.server.library.jfr.parser.JFREventType;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AsyncProfilerQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;

    private AsyncProfilerQueryService queryService;

    public AsyncProfilerQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private AsyncProfilerQueryService getAsyncProfilerQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME)
                    .provider()
                    .getService(AsyncProfilerQueryService.class);
        }
        return queryService;
    }

    public AsyncProfilerTaskListResult queryAsyncProfilerTaskList(AsyncProfilerTaskListRequest request) throws IOException {
        List<AsyncProfilerTask> tasks = getAsyncProfilerQueryService().queryTask(
                request.getServiceId(), request.getStartTime(), request.getEndTime(), request.getLimit()
        );
        return new AsyncProfilerTaskListResult(null, tasks);
    }

    public AsyncProfilerAnalyzation queryAsyncProfilerAnalyze(AsyncProfilerAnalyzatonRequest request) throws IOException {
        /**
         * Due to the replacement of async-profiler-convert package, after JfrReader reads JFR events, it cannot distinguish lock events.
         * Therefore, JAVA_MONITOR_ENTER and THREAD_PARK events are merged into one during query and parsing.
         */
        if (JFREventType.isLockSample(request.getEventType())) {
            request.setEventType(JFREventType.LOCK);
        }
        AsyncProfilerStackTree eventFrameTrees = getAsyncProfilerQueryService().queryJFRData(
                request.getTaskId(), request.getInstanceIds(), request.getEventType()
        );
        return new AsyncProfilerAnalyzation(eventFrameTrees);
    }

    public AsyncProfilerTaskProgress queryAsyncProfilerTaskProgress(String taskId) throws IOException {
        AsyncProfilerTaskProgress asyncProfilerTaskProgress = new AsyncProfilerTaskProgress();
        List<AsyncProfilerTaskLog> logs = getAsyncProfilerQueryService().queryAsyncProfilerTaskLogs(taskId);
        asyncProfilerTaskProgress.setLogs(logs);
        List<String> errorInstances = new ArrayList<>();
        List<String> successInstances = new ArrayList<>();
        logs.forEach(log -> {
            if (AsyncProfilerTaskLogOperationType.EXECUTION_FINISHED.equals(log.getOperationType())) {
                successInstances.add(log.getInstanceId());
            } else if (AsyncProfilerTaskLogOperationType.EXECUTION_TASK_ERROR.equals(log.getOperationType())
                    || AsyncProfilerTaskLogOperationType.JFR_UPLOAD_FILE_TOO_LARGE_ERROR.equals(log.getOperationType())) {
                errorInstances.add(log.getInstanceId());
            }
        });
        asyncProfilerTaskProgress.setErrorInstanceIds(errorInstances);
        asyncProfilerTaskProgress.setSuccessInstanceIds(successInstances);
        return asyncProfilerTaskProgress;
    }
}
