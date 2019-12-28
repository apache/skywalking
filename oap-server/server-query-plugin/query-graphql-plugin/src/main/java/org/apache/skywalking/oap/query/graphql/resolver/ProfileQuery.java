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
import org.apache.skywalking.oap.query.graphql.type.Duration;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.ThreadMonitorTaskQueryService;
import org.apache.skywalking.oap.server.core.query.entity.ThreadMonitorTask;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;
import java.util.List;

/**
 * profile query GraphQL resolver
 *
 * @author MrPro
 */
public class ProfileQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private ThreadMonitorTaskQueryService threadMonitorTaskQueryService;

    public ProfileQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ThreadMonitorTaskQueryService getThreadMonitorTaskQueryService() {
        if (threadMonitorTaskQueryService == null) {
            this.threadMonitorTaskQueryService = moduleManager.find(CoreModule.NAME).provider().getService(ThreadMonitorTaskQueryService.class);
        }
        return threadMonitorTaskQueryService;
    }

    public List<ThreadMonitorTask> getThreadMonitorTaskList(final Integer serviceId, final String endpointName, final Duration duration) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getThreadMonitorTaskQueryService().getTaskList(serviceId, endpointName, startTimeBucket, endTimeBucket);
    }


}
