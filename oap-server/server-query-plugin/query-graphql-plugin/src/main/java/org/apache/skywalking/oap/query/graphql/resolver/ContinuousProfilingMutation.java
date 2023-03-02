package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLMutationResolver;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.continuous.ContinuousProfilingMutationService;
import org.apache.skywalking.oap.server.core.query.input.ContinuousProfilingPolicyCreation;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingSetResult;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;

public class ContinuousProfilingMutation implements GraphQLMutationResolver {

    private final ModuleManager moduleManager;
    private ContinuousProfilingMutationService mutationService;

    public ContinuousProfilingMutation(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ContinuousProfilingMutationService getMutationService() {
        if (mutationService == null) {
            this.mutationService = moduleManager.find(CoreModule.NAME)
                .provider()
                .getService(ContinuousProfilingMutationService.class);
        }
        return mutationService;
    }

    public ContinuousProfilingSetResult setContinuousProfilingPolicy(ContinuousProfilingPolicyCreation request) throws IOException {
        return getMutationService().setContinuousProfilingPolicy(request);
    }

}
