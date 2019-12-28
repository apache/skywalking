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

import com.coxautodev.graphql.tools.GraphQLMutationResolver;
import org.apache.skywalking.oap.query.graphql.type.ThreadMonitorTaskCreationRequest;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.mutation.ThreadMonitorTaskMutationService;
import org.apache.skywalking.oap.server.core.mutation.entity.ThreadMonitorTaskCreationResult;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;

/**
 * profile mutation GraphQL resolver
 *
 * @author MrPro
 */
public class ProfileMutation implements GraphQLMutationResolver {

    private final ModuleManager moduleManager;
    private ThreadMonitorTaskMutationService threadMonitorTaskService;

    public ProfileMutation(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ThreadMonitorTaskMutationService getThreadMonitorTaskService() {
        if (threadMonitorTaskService == null) {
            this.threadMonitorTaskService = moduleManager.find(CoreModule.NAME).provider().getService(ThreadMonitorTaskMutationService.class);
        }
        return threadMonitorTaskService;
    }

    public ThreadMonitorTaskCreationResult createThreadMonitorTask(ThreadMonitorTaskCreationRequest creationRequest) throws IOException {
        return getThreadMonitorTaskService().createTask(
                creationRequest.getServiceId(),
                creationRequest.getEndpointName(),
                creationRequest.getStartTime() == null ? -1 : creationRequest.getStartTime(),
                Math.toIntExact(DurationUtils.INSTANCE.toSecond(creationRequest.getDurationUnit(), creationRequest.getDuration())),
                creationRequest.getMinDurationThreshold(),
                creationRequest.getDumpPeriod()
        );
    }
}
