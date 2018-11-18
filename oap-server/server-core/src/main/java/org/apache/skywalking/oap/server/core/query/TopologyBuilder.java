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
import org.apache.skywalking.oap.server.core.source.DetectPoint;
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
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME).provider().getService(IComponentLibraryCatalogService.class);
    }

    Topology build(List<Call> serviceRelationClientCalls, List<Call> serviceRelationServerCalls) {
        filterZeroSourceOrTargetReference(serviceRelationClientCalls);
        filterZeroSourceOrTargetReference(serviceRelationServerCalls);

        Map<Integer, Node> nodes = new HashMap<>();
        List<Call> calls = new LinkedList<>();
        Set<String> callIds = new HashSet<>();

        for (Call clientCall : serviceRelationClientCalls) {
            ServiceInventory source = serviceInventoryCache.get(clientCall.getSource());
            ServiceInventory target = serviceInventoryCache.get(clientCall.getTarget());

            if (target.getMappingServiceId() != Const.NONE) {
                continue;
            }

            if (!nodes.containsKey(source.getSequence())) {
                nodes.put(source.getSequence(), buildNode(source));
            }

            if (!nodes.containsKey(target.getSequence())) {
                nodes.put(target.getSequence(), buildNode(target));
                if (BooleanUtils.valueToBoolean(target.getIsAddress())) {
                    nodes.get(target.getSequence()).setType(componentLibraryCatalogService.getServerNameBasedOnComponent(clientCall.getComponentId()));
                }
            }

            String callId = source.getSequence() + Const.ID_SPLIT + target.getSequence();
            if (!callIds.contains(callId)) {
                callIds.add(callId);

                Call call = new Call();
                call.setSource(clientCall.getSource());
                call.setTarget(clientCall.getTarget());
                call.setId(clientCall.getId());
                call.setDetectPoint(DetectPoint.CLIENT);
                call.setCallType(componentLibraryCatalogService.getComponentName(clientCall.getComponentId()));
                calls.add(call);
            }
        }

        for (Call serverCall : serviceRelationServerCalls) {
            ServiceInventory source = serviceInventoryCache.get(serverCall.getSource());
            ServiceInventory target = serviceInventoryCache.get(serverCall.getTarget());

            if (source.getSequence() == Const.USER_SERVICE_ID) {
                if (!nodes.containsKey(source.getSequence())) {
                    Node visualUserNode = new Node();
                    visualUserNode.setId(source.getSequence());
                    visualUserNode.setName(Const.USER_CODE);
                    visualUserNode.setType(Const.USER_CODE.toUpperCase());
                    visualUserNode.setReal(false);
                    nodes.put(source.getSequence(), visualUserNode);
                }
            }

            if (BooleanUtils.valueToBoolean(source.getIsAddress())) {
                if (!nodes.containsKey(source.getSequence())) {
                    Node conjecturalNode = new Node();
                    conjecturalNode.setId(source.getSequence());
                    conjecturalNode.setName(source.getName());
                    conjecturalNode.setType(componentLibraryCatalogService.getServerNameBasedOnComponent(serverCall.getComponentId()));
                    conjecturalNode.setReal(true);
                    nodes.put(source.getSequence(), conjecturalNode);
                }
            }

            String callId = source.getSequence() + Const.ID_SPLIT + target.getSequence();
            if (!callIds.contains(callId)) {
                callIds.add(callId);

                Call call = new Call();
                call.setSource(serverCall.getSource());
                call.setTarget(serverCall.getTarget());
                call.setId(serverCall.getId());
                call.setDetectPoint(DetectPoint.SERVER);
                calls.add(call);

                if (source.getSequence() == Const.USER_SERVICE_ID) {
                    call.setCallType(Const.EMPTY_STRING);
                } else {
                    call.setCallType(componentLibraryCatalogService.getComponentName(serverCall.getComponentId()));
                }
            }

            if (!nodes.containsKey(source.getSequence())) {
                nodes.put(source.getSequence(), buildNode(source));
            }

            if (!nodes.containsKey(target.getSequence())) {
                nodes.put(target.getSequence(), buildNode(target));
            }

            if (nodes.containsKey(target.getSequence())) {
                nodes.get(target.getSequence()).setType(componentLibraryCatalogService.getComponentName(serverCall.getComponentId()));
            }
        }

        Topology topology = new Topology();
        topology.getCalls().addAll(calls);
        topology.getNodes().addAll(nodes.values());
        return topology;
    }

    private Node buildNode(ServiceInventory serviceInventory) {
        Node serviceNode = new Node();
        serviceNode.setId(serviceInventory.getSequence());
        serviceNode.setName(serviceInventory.getName());
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
