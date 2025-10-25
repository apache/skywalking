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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.pprof.PprofMutationService;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskCreationResult;
import org.apache.skywalking.oap.server.core.query.input.PprofTaskCreationRequest;

import java.io.IOException;

@Slf4j
public class PprofMutation implements GraphQLMutationResolver {
    private final ModuleManager moduleManager;

    private PprofMutationService mutationService;

    public PprofMutation(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private PprofMutationService getPprofMutationService() {
        if (mutationService == null) {
            this.mutationService = moduleManager.find(CoreModule.NAME)
                    .provider()
                    .getService(PprofMutationService.class);
        }
        return mutationService;
    }

    public PprofTaskCreationResult createPprofTask(PprofTaskCreationRequest request) throws IOException {
        PprofMutationService pprofMutationService = getPprofMutationService();
        return pprofMutationService.createTask(request.getServiceId(), request.getServiceInstanceIds(),
                request.getDuration(), request.getEvents(), request.getDumpPeriod());
    }
}