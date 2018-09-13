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

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.module.Service;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class TopologyQueryService implements Service {

    private static final Logger logger = LoggerFactory.getLogger(TopologyQueryService.class);

    private final ModuleManager moduleManager;
    private ITopologyQueryDAO topologyQueryDAO;

    public TopologyQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ITopologyQueryDAO getTopologyQueryDAO() {
        if (topologyQueryDAO == null) {
            topologyQueryDAO = moduleManager.find(StorageModule.NAME).getService(ITopologyQueryDAO.class);
        }
        return topologyQueryDAO;
    }

    public Topology getGlobalTopology(final Step step, final long startTB, final long endTB) throws IOException {
        logger.debug("step: {}, startTimeBucket: {}, endTimeBucket: {}", step, startTB, endTB);
        List<ServiceComponent> serviceComponents = getTopologyQueryDAO().loadServiceComponents(step, startTB, endTB);
        List<ServiceMapping> serviceMappings = getTopologyQueryDAO().loadServiceMappings(step, startTB, endTB);

        List<Call> serviceRelationClientCalls = getTopologyQueryDAO().loadClientSideServiceRelations(step, startTB, endTB);
        List<Call> serviceRelationServerCalls = getTopologyQueryDAO().loadServerSideServiceRelations(step, startTB, endTB);

        TopologyBuilder builder = new TopologyBuilder(moduleManager);
        return builder.build(serviceComponents, serviceMappings, serviceRelationClientCalls, serviceRelationServerCalls);
    }

    public Topology getServiceTopology(final Step step, final long startTB, final long endTB,
        final int serviceId) throws IOException {
        List<ServiceComponent> serviceComponents = getTopologyQueryDAO().loadServiceComponents(step, startTB, endTB);
        List<ServiceMapping> serviceMappings = getTopologyQueryDAO().loadServiceMappings(step, startTB, endTB);

        Set<Integer> serviceIds = new HashSet<>();
        serviceIds.add(serviceId);
        serviceMappings.forEach(mapping -> {
            if (mapping.getServiceId() == serviceId) {
                serviceIds.add(mapping.getMappingServiceId());
            }
        });
        List<Integer> serviceIdList = new ArrayList<>(serviceIds);

        List<Call> serviceRelationClientCalls = getTopologyQueryDAO().loadSpecifiedClientSideServiceRelations(step, startTB, endTB, serviceIdList);
        List<Call> serviceRelationServerCalls = getTopologyQueryDAO().loadSpecifiedServerSideServiceRelations(step, startTB, endTB, serviceIdList);

        TopologyBuilder builder = new TopologyBuilder(moduleManager);
        Topology topology = builder.build(serviceComponents, serviceMappings, serviceRelationClientCalls, serviceRelationServerCalls);

        Set<Integer> nodeIds = new HashSet<>();
        topology.getCalls().forEach(call -> {
            nodeIds.add(call.getSource());
            nodeIds.add(call.getTarget());
        });

        for (int i = topology.getNodes().size() - 1; i >= 0; i--) {
            if (!nodeIds.contains(topology.getNodes().get(i).getId())) {
                topology.getNodes().remove(i);
            }
        }

        return topology;
    }
}
