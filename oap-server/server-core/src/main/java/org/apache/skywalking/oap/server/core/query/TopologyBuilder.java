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

import java.util.*;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class TopologyBuilder {

    private static final Logger logger = LoggerFactory.getLogger(TopologyBuilder.class);

    private final ServiceInventoryCache serviceInventoryCache;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    TopologyBuilder(ModuleManager moduleManager) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInventoryCache.class);
        this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME).getService(IComponentLibraryCatalogService.class);
    }

    Topology build(List<ServiceComponent> serviceComponents, List<ServiceMapping> serviceMappings,
        List<Call> serviceRelationClientCalls, List<Call> serviceRelationServerCalls) {
        Map<Integer, String> nodeCompMap = buildNodeCompMap(serviceComponents);
        Map<Integer, String> conjecturalNodeCompMap = buildConjecturalNodeCompMap(serviceComponents);
        Map<Integer, Integer> mappings = changeMapping2Map(serviceMappings);
        filterZeroSourceOrTargetReference(serviceRelationClientCalls);
        filterZeroSourceOrTargetReference(serviceRelationServerCalls);
        mappingIdExchange(mappings, serviceRelationClientCalls);
        mappingIdExchange(mappings, serviceRelationServerCalls);

        List<Node> nodes = new LinkedList<>();
        List<Call> calls = new LinkedList<>();
        Set<Integer> nodeIds = new HashSet<>();
        Set<String> callIds = new HashSet<>();

        serviceRelationClientCalls.forEach(clientCall -> {
            ServiceInventory source = serviceInventoryCache.get(clientCall.getSource());
            ServiceInventory target = serviceInventoryCache.get(clientCall.getTarget());

            if (BooleanUtils.valueToBoolean(target.getIsAddress()) && !mappings.containsKey(target.getSequence())) {
                if (!nodeIds.contains(target.getSequence())) {
                    Node conjecturalNode = new Node();
                    conjecturalNode.setId(target.getSequence());
                    conjecturalNode.setName(target.getName());
                    conjecturalNode.setType(conjecturalNodeCompMap.getOrDefault(target.getSequence(), Const.UNKNOWN));
                    conjecturalNode.setReal(false);
                    nodes.add(conjecturalNode);
                    nodeIds.add(target.getSequence());
                }
            }

            if (!nodeIds.contains(source.getSequence())) {
                nodes.add(buildNode(nodeCompMap, source));
                nodeIds.add(source.getSequence());
            }

            if (!nodeIds.contains(target.getSequence())) {
                nodes.add(buildNode(nodeCompMap, target));
                nodeIds.add(target.getSequence());
            }

            String callId = source.getSequence() + Const.ID_SPLIT + target.getSequence();
            if (!callIds.contains(callId)) {
                callIds.add(callId);

                Call call = new Call();
                call.setSource(source.getSequence());
                call.setTarget(target.getSequence());
                call.setCallType(nodeCompMap.get(clientCall.getTarget()));
                call.setId(clientCall.getId());
                call.setDetectPoint(DetectPoint.CLIENT);
                calls.add(call);
            }
        });

        serviceRelationServerCalls.forEach(serverCall -> {
            ServiceInventory source = serviceInventoryCache.get(serverCall.getSource());
            ServiceInventory target = serviceInventoryCache.get(serverCall.getTarget());

            if (source.getSequence() == Const.USER_SERVICE_ID) {
                if (!nodeIds.contains(source.getSequence())) {
                    Node visualUserNode = new Node();
                    visualUserNode.setId(source.getSequence());
                    visualUserNode.setName(Const.USER_CODE);
                    visualUserNode.setType(Const.USER_CODE.toUpperCase());
                    visualUserNode.setReal(false);
                    nodes.add(visualUserNode);
                    nodeIds.add(source.getSequence());
                }
            }

            if (BooleanUtils.valueToBoolean(source.getIsAddress())) {
                if (!nodeIds.contains(source.getSequence())) {
                    Node conjecturalNode = new Node();
                    conjecturalNode.setId(source.getSequence());
                    conjecturalNode.setName(source.getName());
                    conjecturalNode.setType(conjecturalNodeCompMap.getOrDefault(target.getSequence(), Const.UNKNOWN));
                    conjecturalNode.setReal(true);
                    nodeIds.add(source.getSequence());
                    nodes.add(conjecturalNode);
                }
            }

            if (!nodeIds.contains(source.getSequence())) {
                nodes.add(buildNode(nodeCompMap, source));
                nodeIds.add(source.getSequence());
            }

            if (!nodeIds.contains(target.getSequence())) {
                nodes.add(buildNode(nodeCompMap, target));
                nodeIds.add(target.getSequence());
            }

            String callId = source.getSequence() + Const.ID_SPLIT + target.getSequence();
            if (!callIds.contains(callId)) {
                callIds.add(callId);

                Call call = new Call();
                call.setSource(source.getSequence());
                call.setTarget(target.getSequence());
                call.setId(serverCall.getId());
                call.setDetectPoint(DetectPoint.SERVER);

                if (source.getSequence() == Const.USER_SERVICE_ID) {
                    call.setCallType(Const.EMPTY_STRING);
                } else {
                    call.setCallType(nodeCompMap.get(serverCall.getTarget()));
                }
                calls.add(call);
            }
        });

        Topology topology = new Topology();
        topology.getCalls().addAll(calls);
        topology.getNodes().addAll(nodes);
        return topology;
    }

    private void mappingIdExchange(Map<Integer, Integer> mappings, List<Call> serviceRelationCalls) {
        serviceRelationCalls.forEach(relationCall -> {
            relationCall.setSource(mappings.getOrDefault(relationCall.getSource(), relationCall.getSource()));
            relationCall.setTarget(mappings.getOrDefault(relationCall.getTarget(), relationCall.getTarget()));
        });
    }

    private Map<Integer, Integer> changeMapping2Map(List<ServiceMapping> serviceMappings) {
        Map<Integer, Integer> mappings = new HashMap<>();
        serviceMappings.forEach(serviceMapping -> mappings.put(serviceMapping.getMappingServiceId(), serviceMapping.getServiceId()));
        return mappings;
    }

    private Map<Integer, String> buildConjecturalNodeCompMap(List<ServiceComponent> serviceComponents) {
        Map<Integer, String> components = new HashMap<>();
        serviceComponents.forEach(serviceComponent -> {
            int componentServerId = this.componentLibraryCatalogService.getServerIdBasedOnComponent(serviceComponent.getComponentId());
            String componentName = this.componentLibraryCatalogService.getServerName(componentServerId);
            components.put(serviceComponent.getServiceId(), componentName);
        });
        return components;
    }

    private Map<Integer, String> buildNodeCompMap(List<ServiceComponent> serviceComponents) {
        Map<Integer, String> components = new HashMap<>();
        serviceComponents.forEach(serviceComponent -> {
            String componentName = this.componentLibraryCatalogService.getComponentName(serviceComponent.getComponentId());
            components.put(serviceComponent.getServiceId(), componentName);
        });
        return components;
    }

    private Node buildNode(Map<Integer, String> nodeCompMap, ServiceInventory serviceInventory) {
        Node serviceNode = new Node();
        serviceNode.setId(serviceInventory.getSequence());
        serviceNode.setName(serviceInventory.getName());
        serviceNode.setType(nodeCompMap.getOrDefault(serviceInventory.getSequence(), Const.UNKNOWN));
        if (BooleanUtils.valueToBoolean(serviceInventory.getIsAddress())) {
            serviceNode.setReal(false);
        } else {
            serviceNode.setReal(true);
        }
        return serviceNode;
    }

    private void filterZeroSourceOrTargetReference(List<Call> serviceRelationClientCalls) {
        for (int i = serviceRelationClientCalls.size() - 1; i >= 0; i--) {
            Call call = serviceRelationClientCalls.get(i);
            if (call.getSource() == 0 || call.getTarget() == 0) {
                serviceRelationClientCalls.remove(i);
            }
        }
    }
}
