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
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.EndpointInventoryCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.*;
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
    private IMetadataQueryDAO metadataQueryDAO;
    private EndpointInventoryCache endpointInventoryCache;
    private IComponentLibraryCatalogService componentLibraryCatalogService;

    public TopologyQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            metadataQueryDAO = moduleManager.find(StorageModule.NAME).getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    private ITopologyQueryDAO getTopologyQueryDAO() {
        if (topologyQueryDAO == null) {
            topologyQueryDAO = moduleManager.find(StorageModule.NAME).getService(ITopologyQueryDAO.class);
        }
        return topologyQueryDAO;
    }

    private IComponentLibraryCatalogService getComponentLibraryCatalogService() {
        if (componentLibraryCatalogService == null) {
            componentLibraryCatalogService = moduleManager.find(CoreModule.NAME).getService(IComponentLibraryCatalogService.class);
        }
        return componentLibraryCatalogService;
    }

    private EndpointInventoryCache getEndpointInventoryCache() {
        if (endpointInventoryCache == null) {
            endpointInventoryCache = moduleManager.find(CoreModule.NAME).getService(EndpointInventoryCache.class);
        }
        return endpointInventoryCache;
    }

    public Topology getGlobalTopology(final Step step, final long startTB, final long endTB, final long startTimestamp,
        final long endTimestamp) throws IOException {
        logger.debug("step: {}, startTimeBucket: {}, endTimeBucket: {}", step, startTB, endTB);
        List<ServiceComponent> serviceComponents = getTopologyQueryDAO().loadServiceComponents(step, startTB, endTB);
        List<ServiceMapping> serviceMappings = getTopologyQueryDAO().loadServiceMappings(step, startTB, endTB);

        List<Call> serviceRelationClientCalls = getTopologyQueryDAO().loadClientSideServiceRelations(step, startTB, endTB);
        List<Call> serviceRelationServerCalls = getTopologyQueryDAO().loadServerSideServiceRelations(step, startTB, endTB);

        List<org.apache.skywalking.oap.server.core.query.entity.Service> serviceList = getMetadataQueryDAO().searchServices(startTimestamp, endTimestamp, null);

        TopologyBuilder builder = new TopologyBuilder(moduleManager);
        Topology topology = builder.build(serviceComponents, serviceMappings, serviceRelationClientCalls, serviceRelationServerCalls);

        serviceList.forEach(service -> {
            boolean contains = false;
            for (Node node : topology.getNodes()) {
                if (service.getId() == node.getId()) {
                    contains = true;
                    break;
                }
            }

            if (!contains) {
                Node newNode = new Node();
                newNode.setId(service.getId());
                newNode.setName(service.getName());
                newNode.setReal(true);
                newNode.setType(Const.UNKNOWN);
                topology.getNodes().add(newNode);
            }
        });

        return topology;
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
        return builder.build(serviceComponents, serviceMappings, serviceRelationClientCalls, serviceRelationServerCalls);
    }

    public Topology getEndpointTopology(final Step step, final long startTB, final long endTB,
        final int endpointId) throws IOException {
        List<ServiceComponent> serviceComponents = getTopologyQueryDAO().loadServiceComponents(step, startTB, endTB);

        Map<Integer, String> components = new HashMap<>();
        serviceComponents.forEach(component -> components.put(component.getServiceId(), getComponentLibraryCatalogService().getComponentName(component.getComponentId())));

        List<Call> serverSideCalls = getTopologyQueryDAO().loadSpecifiedDestOfServerSideEndpointRelations(step, startTB, endTB, endpointId);
        serverSideCalls.forEach(call -> call.setDetectPoint(DetectPoint.SERVER));

        serverSideCalls.forEach(call -> call.setCallType(components.getOrDefault(getEndpointInventoryCache().get(call.getTarget()).getServiceId(), Const.UNKNOWN)));

        Topology topology = new Topology();
        topology.getCalls().addAll(serverSideCalls);

        Set<Integer> nodeIds = new HashSet<>();
        serverSideCalls.forEach(call -> {
            if (!nodeIds.contains(call.getSource())) {
                topology.getNodes().add(buildEndpointNode(components, call.getSource()));
                nodeIds.add(call.getSource());
            }
            if (!nodeIds.contains(call.getTarget())) {
                topology.getNodes().add(buildEndpointNode(components, call.getTarget()));
                nodeIds.add(call.getTarget());
            }
        });

        return topology;
    }

    private Node buildEndpointNode(Map<Integer, String> components, int endpointId) {
        Node node = new Node();
        node.setId(endpointId);
        node.setName(getEndpointInventoryCache().get(endpointId).getName());
        node.setType(components.getOrDefault(getEndpointInventoryCache().get(endpointId).getServiceId(), Const.UNKNOWN));
        node.setReal(true);
        return node;
    }
}
