package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLMutationResolver;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.ebpf.EBPFProfilingMutationService;
import org.apache.skywalking.oap.server.core.query.input.EBPFProfilingTaskFixedTimeCreationRequest;
import org.apache.skywalking.oap.server.core.query.type.EBPFProcessProfilingTaskCreationResult;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;

public class EBPFProcessProfilingMutation implements GraphQLMutationResolver {

    private final ModuleManager moduleManager;
    private EBPFProfilingMutationService mutationService;

    public EBPFProcessProfilingMutation(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public EBPFProfilingMutationService getMutationService() {
        if (mutationService == null) {
            this.mutationService = moduleManager.find(CoreModule.NAME)
                    .provider()
                    .getService(EBPFProfilingMutationService.class);
        }
        return mutationService;
    }

    public EBPFProcessProfilingTaskCreationResult createEBPFProfilingFixedTimeTask(EBPFProfilingTaskFixedTimeCreationRequest request) throws IOException {
        return getMutationService().createTask(request);
    }
}