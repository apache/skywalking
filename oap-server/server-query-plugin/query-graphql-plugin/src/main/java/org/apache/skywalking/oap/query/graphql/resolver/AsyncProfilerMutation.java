package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLMutationResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.AsyncProfilerMutationService;
import org.apache.skywalking.oap.server.core.query.input.AsyncProfilerTaskCreationRequest;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskCreationResult;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;

@Slf4j
public class AsyncProfilerMutation implements GraphQLMutationResolver {
    private final ModuleManager moduleManager;

    private AsyncProfilerMutationService mutationService;

    public AsyncProfilerMutation(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private AsyncProfilerMutationService getAsyncProfilerMutationService() {
        if (mutationService == null) {
            this.mutationService = moduleManager.find(CoreModule.NAME)
                    .provider()
                    .getService(AsyncProfilerMutationService.class);
        }
        return mutationService;
    }

    public AsyncProfilerTaskCreationResult createAsyncProfilerTask(AsyncProfilerTaskCreationRequest request) throws IOException {
        AsyncProfilerMutationService asyncProfilerMutationService = getAsyncProfilerMutationService();
        return asyncProfilerMutationService.createTask(request.getServiceId(), request.getServiceInstanceIds(),
                request.getDuration(), request.getEvents(), request.getExecArgs());
    }
}
