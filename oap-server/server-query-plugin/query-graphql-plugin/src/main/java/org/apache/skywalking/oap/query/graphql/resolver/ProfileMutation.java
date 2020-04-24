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
import java.io.IOException;
import org.apache.skywalking.oap.server.core.query.input.ProfileTaskCreationRequest;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskMutationService;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskCreationResult;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * profile mutation GraphQL resolver
 */
public class ProfileMutation implements GraphQLMutationResolver {

    private final ModuleManager moduleManager;
    private ProfileTaskMutationService profileTaskService;

    public ProfileMutation(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ProfileTaskMutationService getProfileTaskService() {
        if (profileTaskService == null) {
            this.profileTaskService = moduleManager.find(CoreModule.NAME)
                                                   .provider()
                                                   .getService(ProfileTaskMutationService.class);
        }
        return profileTaskService;
    }

    public ProfileTaskCreationResult createProfileTask(ProfileTaskCreationRequest creationRequest) throws IOException {
        return getProfileTaskService().createTask(creationRequest.getServiceId(), creationRequest.getEndpointName() == null ? null : creationRequest
            .getEndpointName()
            .trim(), creationRequest.getStartTime() == null ? -1 : creationRequest.getStartTime(), creationRequest.getDuration(), creationRequest
            .getMinDurationThreshold(), creationRequest.getDumpPeriod(), creationRequest.getMaxSamplingCount());
    }
}
