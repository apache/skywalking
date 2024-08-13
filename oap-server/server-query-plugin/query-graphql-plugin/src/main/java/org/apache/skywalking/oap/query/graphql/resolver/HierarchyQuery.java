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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.query.graphql.AsyncQuery;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.HierarchyQueryService;
import org.apache.skywalking.oap.server.core.query.type.InstanceHierarchy;
import org.apache.skywalking.oap.server.core.query.type.LayerLevel;
import org.apache.skywalking.oap.server.core.query.type.ServiceHierarchy;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class HierarchyQuery extends AsyncQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;
    private HierarchyQueryService hierarchyQueryService;

    public HierarchyQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private HierarchyQueryService getHierarchyQueryService() {
        if (hierarchyQueryService == null) {
            this.hierarchyQueryService = moduleManager.find(CoreModule.NAME)
                                                      .provider()
                                                      .getService(HierarchyQueryService.class);
        }
        return hierarchyQueryService;
    }

    public CompletableFuture<ServiceHierarchy> getServiceHierarchy(String serviceId, String layer) {
        return AsyncQuery.queryAsync(() -> {
            try {
                return getHierarchyQueryService().getServiceHierarchy(serviceId, layer);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<InstanceHierarchy> getInstanceHierarchy(String instanceId, String layer) {
        return AsyncQuery.queryAsync(() -> {
            try {
                return getHierarchyQueryService().getInstanceHierarchy(instanceId, layer);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<LayerLevel>> listLayerLevels() {
        return AsyncQuery.queryAsync(() -> {
            try {
                return getHierarchyQueryService().listLayerLevels();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
