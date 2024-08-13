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

import graphql.kickstart.tools.GraphQLQueryResolver;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.query.graphql.AsyncQuery;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.profiling.continuous.ContinuousProfilingQueryService;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingTargetType;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingMonitoringInstance;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingPolicyTarget;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.util.List;

public class ContinuousProfilingQuery extends AsyncQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private ContinuousProfilingQueryService queryService;

    public ContinuousProfilingQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ContinuousProfilingQueryService getQueryService() {
        if (queryService == null) {
            queryService = this.moduleManager.find(CoreModule.NAME)
                .provider().getService(ContinuousProfilingQueryService.class);
        }
        return queryService;
    }

    public CompletableFuture<List<ContinuousProfilingPolicyTarget>> queryContinuousProfilingServiceTargets(String serviceId) {
        return queryAsync(() -> {
            try {
                return getQueryService().queryContinuousProfilingServiceTargets(serviceId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<ContinuousProfilingMonitoringInstance>> queryContinuousProfilingMonitoringInstances(String serviceId, ContinuousProfilingTargetType target) {
        return queryAsync(() -> {
            try {
                return getQueryService().queryContinuousProfilingMonitoringInstances(serviceId, target);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
