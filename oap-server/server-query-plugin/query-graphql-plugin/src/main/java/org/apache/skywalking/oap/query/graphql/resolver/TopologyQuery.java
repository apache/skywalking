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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.TopologyQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.EndpointTopology;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstanceTopology;
import org.apache.skywalking.oap.server.core.query.type.Topology;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class TopologyQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private TopologyQueryService queryService;

    public TopologyQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private TopologyQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(TopologyQueryService.class);
        }
        return queryService;
    }

    public Topology getGlobalTopology(final Duration duration) throws IOException {
        return getQueryService().getGlobalTopology(duration.getStartTimeBucket(), duration.getEndTimeBucket());
    }

    public Topology getServiceTopology(final String serviceId, final Duration duration) throws IOException {
        List<String> selectedServiceList = new ArrayList<>(1);
        selectedServiceList.add(serviceId);
        return this.getServicesTopology(selectedServiceList, duration);
    }

    public Topology getServicesTopology(final List<String> serviceIds, final Duration duration) throws IOException {
        return getQueryService().getServiceTopology(
            duration.getStartTimeBucket(), duration.getEndTimeBucket(), serviceIds);
    }

    public ServiceInstanceTopology getServiceInstanceTopology(final String clientServiceId,
                                                              final String serverServiceId,
                                                              final Duration duration) throws IOException {
        return getQueryService().getServiceInstanceTopology(
            clientServiceId, serverServiceId,
            duration.getStartTimeBucket(), duration.getEndTimeBucket()
        );
    }

    /**
     * Replaced by {@link #getEndpointDependencies(String, Duration)}
     */
    @Deprecated
    public Topology getEndpointTopology(final String endpointId, final Duration duration) throws IOException {
        return getQueryService().getEndpointTopology(
            duration.getStartTimeBucket(), duration.getEndTimeBucket(), endpointId);
    }

    public EndpointTopology getEndpointDependencies(final String endpointId,
                                                    final Duration duration) throws IOException {
        return getQueryService().getEndpointDependencies(
            duration.getStartTimeBucket(), duration.getEndTimeBucket(), endpointId);
    }
}
