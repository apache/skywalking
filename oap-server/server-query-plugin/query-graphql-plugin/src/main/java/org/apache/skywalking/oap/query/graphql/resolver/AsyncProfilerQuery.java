package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.AsyncProfilerQueryService;
import org.apache.skywalking.oap.server.core.query.input.AsyncProfilerAnalyzatonRequest;
import org.apache.skywalking.oap.server.core.query.input.AsyncProfilerTaskListRequest;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerStackTree;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskListResult;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;
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
        AsyncProfilerStackTree eventFrameTrees = getAsyncProfilerQueryService().queryJfrData(
                request.getTaskId(), request.getInstanceIds(), request.getEventType()
        );
        return new AsyncProfilerAnalyzation(null, eventFrameTrees);
    }

    public List<ProfileTaskLog> queryAsyncProfilerTaskLogs(String taskId) throws IOException {
        return getAsyncProfilerQueryService().queryAsyncProfilerTaskLogs(taskId);
    }
}
