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
import org.apache.skywalking.oap.query.graphql.type.Duration;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.StepToDownsampling;
import org.apache.skywalking.oap.server.core.query.TopologyQueryService;
import org.apache.skywalking.oap.server.core.query.entity.ServiceInstanceTopology;
import org.apache.skywalking.oap.server.core.query.entity.Topology;
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
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getGlobalTopology(
            StepToDownsampling.transform(duration.getStep()), startTimeBucket, endTimeBucket);
    }

    public Topology getServiceTopology(final int serviceId, final Duration duration) throws IOException {
        List<Integer> selectedServiceList = new ArrayList<>(1);
        selectedServiceList.add(serviceId);
        return this.getServicesTopology(selectedServiceList, duration);
    }

    public Topology getServicesTopology(final List<Integer> serviceIds, final Duration duration) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getServiceTopology(
            StepToDownsampling.transform(duration.getStep()), startTimeBucket, endTimeBucket, serviceIds);
    }

    public ServiceInstanceTopology getServiceInstanceTopology(final int clientServiceId, final int serverServiceId,
                                                              final Duration duration) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getServiceInstanceTopology(
            clientServiceId, serverServiceId, StepToDownsampling.transform(duration
                                                                               .getStep()), startTimeBucket,
            endTimeBucket
        );
    }

    public Topology getEndpointTopology(final String endpointId, final Duration duration) throws IOException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        return getQueryService().getEndpointTopology(
            StepToDownsampling.transform(duration.getStep()), startTimeBucket, endTimeBucket, endpointId);
    }
}
