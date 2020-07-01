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

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.EndpointNode;
import org.apache.skywalking.oap.server.core.query.type.EndpointTopology;
import org.apache.skywalking.oap.server.core.query.type.Node;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstanceTopology;
import org.apache.skywalking.oap.server.core.query.type.Topology;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@Slf4j
public class TopologyQueryService implements Service {
    private final ModuleManager moduleManager;
    private ITopologyQueryDAO topologyQueryDAO;
    private IComponentLibraryCatalogService componentLibraryCatalogService;

    public TopologyQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ITopologyQueryDAO getTopologyQueryDAO() {
        if (topologyQueryDAO == null) {
            topologyQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(ITopologyQueryDAO.class);
        }
        return topologyQueryDAO;
    }

    private IComponentLibraryCatalogService getComponentLibraryCatalogService() {
        if (componentLibraryCatalogService == null) {
            componentLibraryCatalogService = moduleManager.find(CoreModule.NAME)
                                                          .provider()
                                                          .getService(IComponentLibraryCatalogService.class);
        }
        return componentLibraryCatalogService;
    }

    public Topology getGlobalTopology(final long startTB,
                                      final long endTB) throws IOException {
        List<Call.CallDetail> serviceRelationServerCalls = getTopologyQueryDAO().loadServiceRelationsDetectedAtServerSide(
            startTB, endTB);
        List<Call.CallDetail> serviceRelationClientCalls = getTopologyQueryDAO().loadServiceRelationDetectedAtClientSide(
            startTB, endTB);

        ServiceTopologyBuilder builder = new ServiceTopologyBuilder(moduleManager);
        return builder.build(serviceRelationClientCalls, serviceRelationServerCalls);
    }

    public Topology getServiceTopology(final long startTB, final long endTB,
                                       final List<String> serviceIds) throws IOException {
        List<Call.CallDetail> serviceRelationClientCalls = getTopologyQueryDAO().loadServiceRelationDetectedAtClientSide(
            startTB, endTB, serviceIds);
        List<Call.CallDetail> serviceRelationServerCalls = getTopologyQueryDAO().loadServiceRelationsDetectedAtServerSide(
            startTB, endTB, serviceIds);

        ServiceTopologyBuilder builder = new ServiceTopologyBuilder(moduleManager);
        Topology topology = builder.build(serviceRelationClientCalls, serviceRelationServerCalls);

        /**
         * The topology built above is complete.
         * There is a special case, there may be a node of the `serviceIds` call these services as and only as a client, so it is included in the topology,
         * its component name could be missed as not being queried before. We add another query about this.
         */
        List<String> outScopeSourceServiceIds = new ArrayList<>();
        serviceRelationClientCalls.forEach(call -> {
            // Client side relationships exclude the given services(#serviceIds)
            // The given services(#serviceIds)'s component names have been included inside `serviceRelationServerCalls`
            if (!serviceIds.contains(call.getSource())) {
                outScopeSourceServiceIds.add(call.getSource());
            }
        });
        if (CollectionUtils.isNotEmpty(outScopeSourceServiceIds)) {
            // If exist, query them as the server side to get the target's component.
            List<Call.CallDetail> sourceCalls = getTopologyQueryDAO().loadServiceRelationsDetectedAtServerSide(
                startTB, endTB, outScopeSourceServiceIds);
            topology.getNodes().forEach(node -> {
                if (Strings.isNullOrEmpty(node.getType())) {
                    for (Call.CallDetail call : sourceCalls) {
                        if (node.getId().equals(call.getTarget())) {
                            node.setType(getComponentLibraryCatalogService().getComponentName(call.getComponentId()));
                            break;
                        }
                    }
                }
            });
        }

        return topology;
    }

    public ServiceInstanceTopology getServiceInstanceTopology(final String clientServiceId,
                                                              final String serverServiceId,
                                                              final long startTB,
                                                              final long endTB) throws IOException {
        List<Call.CallDetail> serviceInstanceRelationClientCalls = getTopologyQueryDAO().loadInstanceRelationDetectedAtClientSide(
            clientServiceId, serverServiceId, startTB, endTB);
        List<Call.CallDetail> serviceInstanceRelationServerCalls = getTopologyQueryDAO().loadInstanceRelationDetectedAtServerSide(
            clientServiceId, serverServiceId, startTB, endTB);

        ServiceInstanceTopologyBuilder builder = new ServiceInstanceTopologyBuilder(moduleManager);
        return builder.build(serviceInstanceRelationClientCalls, serviceInstanceRelationServerCalls);
    }

    @Deprecated
    public Topology getEndpointTopology(final long startTB, final long endTB,
                                        final String endpointId) throws IOException {
        List<Call.CallDetail> serverSideCalls = getTopologyQueryDAO().loadEndpointRelation(
            startTB, endTB, endpointId);

        Topology topology = new Topology();
        serverSideCalls.forEach(callDetail -> {
            Call call = new Call();
            call.setId(callDetail.getId());
            call.setSource(callDetail.getSource());
            call.setTarget(callDetail.getTarget());
            call.addDetectPoint(DetectPoint.SERVER);
            topology.getCalls().add(call);
        });

        Set<String> nodeIds = new HashSet<>();
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

    public EndpointTopology getEndpointDependencies(final long startTB, final long endTB,
                                                    final String endpointId) throws IOException {
        List<Call.CallDetail> serverSideCalls = getTopologyQueryDAO().loadEndpointRelation(
            startTB, endTB, endpointId);

        EndpointTopology topology = new EndpointTopology();
        serverSideCalls.forEach(callDetail -> {
            Call call = new Call();
            call.setId(callDetail.getId());
            call.setSource(callDetail.getSource());
            call.setTarget(callDetail.getTarget());
            call.addDetectPoint(DetectPoint.SERVER);
            topology.getCalls().add(call);
        });

        Set<String> nodeIds = new HashSet<>();
        serverSideCalls.forEach(call -> {
            if (!nodeIds.contains(call.getSource())) {
                topology.getNodes().add(buildEndpointDependencyNode(call.getSource()));
                nodeIds.add(call.getSource());
            }
            if (!nodeIds.contains(call.getTarget())) {
                topology.getNodes().add(buildEndpointDependencyNode(call.getTarget()));
                nodeIds.add(call.getTarget());
            }
        });

        return topology;
    }

    @Deprecated
    private Node buildEndpointNode(String endpointId) {
        Node node = new Node();
        node.setId(endpointId);
        final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
            endpointId);
        node.setName(endpointIDDefinition.getEndpointName());
        node.setType(Const.EMPTY_STRING);
        node.setReal(true);
        return node;
    }

    private EndpointNode buildEndpointDependencyNode(String endpointId) {
        final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
            endpointId);
        EndpointNode instanceNode = new EndpointNode();
        instanceNode.setId(endpointId);
        instanceNode.setName(endpointIDDefinition.getEndpointName());
        instanceNode.setServiceId(endpointIDDefinition.getServiceId());
        final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
            endpointIDDefinition.getServiceId());
        instanceNode.setServiceName(serviceIDDefinition.getName());
        instanceNode.setReal(serviceIDDefinition.isReal());
        return instanceNode;
    }
}
