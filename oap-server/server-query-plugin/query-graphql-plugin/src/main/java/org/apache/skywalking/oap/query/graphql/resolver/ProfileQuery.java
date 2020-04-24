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

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.ProfileTaskQueryService;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.query.type.ProfiledSegment;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;
import java.util.List;

/**
 * profile query GraphQL resolver
 */
public class ProfileQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private ProfileTaskQueryService profileTaskQueryService;

    public ProfileQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ProfileTaskQueryService getProfileTaskQueryService() {
        if (profileTaskQueryService == null) {
            this.profileTaskQueryService = moduleManager.find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(ProfileTaskQueryService.class);
        }
        return profileTaskQueryService;
    }

    public List<ProfileTask> getProfileTaskList(final String serviceId, final String endpointName) throws IOException {
        return getProfileTaskQueryService().getTaskList(serviceId, endpointName);
    }

    public List<BasicTrace> getProfileTaskSegmentList(final String taskID) throws IOException {
        return getProfileTaskQueryService().getTaskTraces(taskID);
    }

    public ProfiledSegment getProfiledSegment(final String segmentId) throws IOException {
        return getProfileTaskQueryService().getProfiledSegment(segmentId);
    }

    public ProfileAnalyzation getProfileAnalyze(final String segmentId, final List<ProfileAnalyzeTimeRange> timeRanges) throws IOException {
        return getProfileTaskQueryService().getProfileAnalyze(segmentId, timeRanges);
    }

}
