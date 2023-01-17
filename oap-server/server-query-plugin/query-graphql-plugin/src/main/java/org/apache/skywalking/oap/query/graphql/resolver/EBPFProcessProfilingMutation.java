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

import graphql.kickstart.tools.GraphQLMutationResolver;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.ebpf.EBPFProfilingMutationService;
import org.apache.skywalking.oap.server.core.query.input.EBPFProfilingNetworkTaskRequest;
import org.apache.skywalking.oap.server.core.query.input.EBPFProfilingTaskFixedTimeCreationRequest;
import org.apache.skywalking.oap.server.core.query.type.EBPFNetworkKeepProfilingResult;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskCreationResult;
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

    public EBPFProfilingTaskCreationResult createEBPFProfilingFixedTimeTask(EBPFProfilingTaskFixedTimeCreationRequest request) throws IOException {
        return getMutationService().createTask(request);
    }

    public EBPFProfilingTaskCreationResult createEBPFNetworkProfiling(EBPFProfilingNetworkTaskRequest request) throws IOException {
        return getMutationService().createTask(request);
    }

    public EBPFNetworkKeepProfilingResult keepEBPFNetworkProfiling(String taskId) throws IOException {
        return getMutationService().keepEBPFNetworkProfiling(taskId);
    }
}