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
import org.apache.skywalking.oap.server.core.profile.entity.ThreadMonitorTaskCreateResult;
import org.apache.skywalking.oap.server.core.profile.ThreadMonitorTaskService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * thread monitor task GraphQL handler
 *
 * @author MrPro
 */
public class ThreadMonitorTaskQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private ThreadMonitorTaskService threadMonitorTaskService;

    public ThreadMonitorTaskQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ThreadMonitorTaskService getThreadMonitorTaskService() {
        if (threadMonitorTaskService == null) {
            this.threadMonitorTaskService = moduleManager.find(CoreModule.NAME).provider().getService(ThreadMonitorTaskService.class);
        }
        return threadMonitorTaskService;
    }

    public ThreadMonitorTaskCreateResult createTask(final int serviceId, final String endpointName, final long monitorStartTime, final int monitorDuration,
                                                    final int minDurationThreshold, final int dumpPeriod) {
        return getThreadMonitorTaskService().createTask(serviceId, endpointName, monitorStartTime, monitorDuration, minDurationThreshold, dumpPeriod);
    }
}
