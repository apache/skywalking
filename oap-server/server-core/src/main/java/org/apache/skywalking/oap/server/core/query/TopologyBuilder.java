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
    //    private final DateBetweenService dateBetweenService;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    TopologyBuilder(ModuleManager moduleManager) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInventoryCache.class);
//        this.dateBetweenService = new DateBetweenService(moduleManager);
        this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME).getService(IComponentLibraryCatalogService.class);
    }

    Topology build(List<ServiceComponent> serviceComponents, List<ServiceMapping> serviceMappings,
        List<Call> serviceRelationClientCalls, List<Call> serviceRelationServerCalls) {
        Map<Integer, String> nodeCompMap = buildNodeCompMap(serviceComponents);
        Map<Integer, String> conjecturalNodeCompMap = buildConjecturalNodeCompMap(serviceComponents);
        Map<Integer, Integer> mappings = changeMapping2Map(serviceMappings);
        filterZeroSourceOrTargetReference(serviceRelationClientCalls);
        filterZeroSourceOrTargetReference(serviceRelationServerCalls);
        serviceRelationServerCalls = serverCallsFilter(serviceRelationServerCalls);

        List<Node> nodes = new LinkedList<>();
        Map<Integer, Integer> applicationMinuteBetweenMap = new HashMap<>();

        List<Call> calls = new LinkedList<>();
        Set<Integer> nodeIds = new HashSet<>();
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

            Set<Integer> serviceNodeIds = buildNodeIds(nodes);
            if (!serviceNodeIds.contains(source.getSequence())) {
                Node serviceNode = new Node();
                serviceNode.setId(source.getSequence());
                serviceNode.setName(source.getName());
                serviceNode.setType(nodeCompMap.getOrDefault(source.getSequence(), Const.UNKNOWN));
                nodes.add(serviceNode);
            }

            Call call = new Call();
            call.setSource(source.getSequence());

            int actualTargetId = mappings.getOrDefault(target.getSequence(), target.getSequence());
            call.setTarget(actualTargetId);
            call.setCallType(nodeCompMap.get(clientCall.getTarget()));
//            try {
//                call.setCpm(clientCall.getCalls() / getApplicationMinuteBetween(applicationMinuteBetweenMap, source.getSequence(), startSecondTimeBucket, endSecondTimeBucket));
//            } catch (ParseException e) {
//                logger.error(e.getMessage(), e);
//            }
            calls.add(call);
        });

        serviceRelationServerCalls.forEach(referenceMetric -> {
            ServiceInventory source = serviceInventoryCache.get(referenceMetric.getSource());
            ServiceInventory target = serviceInventoryCache.get(referenceMetric.getTarget());

            if (source.getSequence() == Const.NONE_SERVICE_ID) {
                if (!nodeIds.contains(source.getSequence())) {
                    Node visualUserNode = new Node();
                    visualUserNode.setId(source.getSequence());
                    visualUserNode.setName(Const.USER_CODE);
                    visualUserNode.setType(Const.USER_CODE.toUpperCase());
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
                    nodeIds.add(source.getSequence());
                    nodes.add(conjecturalNode);
                }
            }

            Call call = new Call();
            call.setSource(source.getSequence());
            call.setTarget(target.getSequence());

            if (source.getSequence() == Const.NONE_SERVICE_ID) {
                call.setCallType(Const.EMPTY_STRING);
            } else {
                call.setCallType(nodeCompMap.get(referenceMetric.getTarget()));
            }
//            try {
//                call.setCpm(referenceMetric.getCalls() / getApplicationMinuteBetween(applicationMinuteBetweenMap, target.getSequence(), startSecondTimeBucket, endSecondTimeBucket));
//            } catch (ParseException e) {
//                logger.error(e.getMessage(), e);
//            }
            calls.add(call);
        });

        Topology topology = new Topology();
        topology.getCalls().addAll(calls);
        topology.getNodes().addAll(nodes);
        return topology;
    }

    private Set<Integer> buildNodeIds(List<Node> nodes) {
        Set<Integer> nodeIds = new HashSet<>();
        nodes.forEach(node -> nodeIds.add(node.getId()));
        return nodeIds;
    }

    private List<Call> serverCallsFilter(List<Call> serviceRelationServerCalls) {
        List<Call> filteredCalls = new LinkedList<>();

        serviceRelationServerCalls.forEach(serverCall -> {
            ServiceInventory source = serviceInventoryCache.get(serverCall.getSource());
            if (BooleanUtils.valueToBoolean(source.getIsAddress()) || source.getSequence() == Const.NONE_SERVICE_ID) {
                filteredCalls.add(serverCall);
            }
        });

        return filteredCalls;
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

    private void filterZeroSourceOrTargetReference(List<Call> serviceRelationClientCalls) {
        for (int i = serviceRelationClientCalls.size() - 1; i >= 0; i--) {
            Call call = serviceRelationClientCalls.get(i);
            if (call.getSource() == 0 || call.getTarget() == 0) {
                serviceRelationClientCalls.remove(i);
            }
        }
    }
}
