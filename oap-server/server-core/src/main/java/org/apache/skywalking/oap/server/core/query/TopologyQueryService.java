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
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.*;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.elasticsearch.common.Strings;
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
            metadataQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    private ITopologyQueryDAO getTopologyQueryDAO() {
        if (topologyQueryDAO == null) {
            topologyQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ITopologyQueryDAO.class);
        }
        return topologyQueryDAO;
    }

    private IComponentLibraryCatalogService getComponentLibraryCatalogService() {
        if (componentLibraryCatalogService == null) {
            componentLibraryCatalogService = moduleManager.find(CoreModule.NAME).provider().getService(IComponentLibraryCatalogService.class);
        }
        return componentLibraryCatalogService;
    }

    private EndpointInventoryCache getEndpointInventoryCache() {
        if (endpointInventoryCache == null) {
            endpointInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(EndpointInventoryCache.class);
        }
        return endpointInventoryCache;
    }

    public Topology getGlobalTopology(final Step step, final long startTB, final long endTB, final long startTimestamp,
        final long endTimestamp) throws IOException {
        logger.debug("step: {}, startTimeBucket: {}, endTimeBucket: {}", step, startTB, endTB);
        List<Call.CallDetail> serviceRelationServerCalls = getTopologyQueryDAO().loadServerSideServiceRelations(step, startTB, endTB);
        List<Call.CallDetail> serviceRelationClientCalls = getTopologyQueryDAO().loadClientSideServiceRelations(step, startTB, endTB);

        TopologyBuilder builder = new TopologyBuilder(moduleManager);
        Topology topology = builder.build(serviceRelationClientCalls, serviceRelationServerCalls);

        return topology;
    }

    public Topology getServiceTopology(final Step step, final long startTB, final long endTB,
        final int serviceId) throws IOException {
        List<Integer> serviceIds = new ArrayList<>();
        serviceIds.add(serviceId);

        List<Call.CallDetail> serviceRelationClientCalls = getTopologyQueryDAO().loadSpecifiedClientSideServiceRelations(step, startTB, endTB, serviceIds);
        List<Call.CallDetail> serviceRelationServerCalls = getTopologyQueryDAO().loadSpecifiedServerSideServiceRelations(step, startTB, endTB, serviceIds);

        TopologyBuilder builder = new TopologyBuilder(moduleManager);
        Topology topology = builder.build(serviceRelationClientCalls, serviceRelationServerCalls);

        List<Integer> sourceServiceIds = new ArrayList<>();
        serviceRelationClientCalls.forEach(call -> sourceServiceIds.add(call.getSource()));
        if (CollectionUtils.isNotEmpty(sourceServiceIds)) {
            List<Call.CallDetail> sourceCalls = getTopologyQueryDAO().loadSpecifiedServerSideServiceRelations(step, startTB, endTB, sourceServiceIds);
            topology.getNodes().forEach(node -> {
                if (Strings.isNullOrEmpty(node.getType())) {
                    for (Call.CallDetail call : sourceCalls) {
                        if (node.getId() == call.getTarget()) {
                            node.setType(getComponentLibraryCatalogService().getComponentName(call.getComponentId()));
                            break;
                        }
                    }
                }
            });
        }

        return topology;
    }

    public Topology getEndpointTopology(final Step step, final long startTB, final long endTB,
        final int endpointId) throws IOException {
        List<Call.CallDetail> serverSideCalls = getTopologyQueryDAO().loadSpecifiedDestOfServerSideEndpointRelations(step, startTB, endTB, endpointId);

        Topology topology = new Topology();
        serverSideCalls.forEach(callDetail -> {
            Call call = new Call();
            call.setId(callDetail.getId());
            call.setSource(callDetail.getSource());
            call.setTarget(callDetail.getTarget());
            call.addDetectPoint(DetectPoint.SERVER);
            topology.getCalls().add(call);
        });

        Set<Integer> nodeIds = new HashSet<>();
        serverSideCalls.forEach(call -> {
            if (!nodeIds.contains(call.getSource())) {
                topology.getNodes().add(buildEndpointNode(call.getSource()));
                nodeIds.add(call.getSource());
            }
            if (!nodeIds.contains(call.getTarget())) {
                topology.getNodes().add(buildEndpointNode(call.getTarget()));
                nodeIds.add(call.getTarget());
            }
        });

        return topology;
    }

    private Node buildEndpointNode(int endpointId) {
        Node node = new Node();
        node.setId(endpointId);
        node.setName(getEndpointInventoryCache().get(endpointId).getName());
        node.setType(Const.EMPTY_STRING);
        node.setReal(true);
        return node;
    }
}
