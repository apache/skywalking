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

import groovy.util.logging.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.entity.Call;
import org.apache.skywalking.oap.server.core.query.entity.Node;
import org.apache.skywalking.oap.server.core.query.entity.Topology;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.util.Objects.isNull;

/**
 * @author zhangwei
 */
@Slf4j
public class TopologyInstanceBuilder {

    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    public TopologyInstanceBuilder(ModuleManager moduleManager) {
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
        this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME).provider().getService(IComponentLibraryCatalogService.class);
    }

    Topology build(List<Call.CallDetail> serviceInstanceRelationClientCalls, List<Call.CallDetail> serviceInstanceRelationServerCalls) {
        filterZeroSourceOrTargetReference(serviceInstanceRelationClientCalls);
        filterZeroSourceOrTargetReference(serviceInstanceRelationServerCalls);

        Map<Integer, Node> nodes = new HashMap<>();
        List<Call> calls = new LinkedList<>();
        HashMap<String, Call> callMap = new HashMap<>();

        for (Call.CallDetail clientCall : serviceInstanceRelationClientCalls) {
            ServiceInstanceInventory source = serviceInstanceInventoryCache.get(clientCall.getSource());
            ServiceInstanceInventory target = serviceInstanceInventoryCache.get(clientCall.getTarget());

            if (isNull(source) || isNull(target)) {
                continue;
            }

            if (target.getMappingServiceInstanceId() != Const.NONE) {
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
            if (!callMap.containsKey(callId)) {
                Call call = new Call();

                callMap.put(callId, call);

                call.setSource(clientCall.getSource());
                call.setTarget(clientCall.getTarget());
                call.setId(clientCall.getId());
                call.addDetectPoint(DetectPoint.CLIENT);
                call.addSourceComponent(componentLibraryCatalogService.getComponentName(clientCall.getComponentId()));
                calls.add(call);
            } else {
                Call call = callMap.get(callId);
                call.addDetectPoint(DetectPoint.CLIENT);
                call.addSourceComponent(componentLibraryCatalogService.getComponentName(clientCall.getComponentId()));
            }
        }

        for (Call.CallDetail serverCall : serviceInstanceRelationServerCalls) {
            ServiceInstanceInventory source = serviceInstanceInventoryCache.get(serverCall.getSource());
            ServiceInstanceInventory target = serviceInstanceInventoryCache.get(serverCall.getTarget());

            if (isNull(source) || isNull(target)) {
                continue;
            }

            if (source.getSequence() == Const.USER_INSTANCE_ID) {
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
            if (!callMap.containsKey(callId)) {
                Call call = new Call();
                callMap.put(callId, call);

                call.setSource(serverCall.getSource());
                call.setTarget(serverCall.getTarget());
                call.setId(callId);
                call.addDetectPoint(DetectPoint.SERVER);
                call.addTargetComponent(componentLibraryCatalogService.getComponentName(serverCall.getComponentId()));

                calls.add(call);
            } else {
                Call call = callMap.get(callId);

                call.addDetectPoint(DetectPoint.SERVER);
                call.addTargetComponent(componentLibraryCatalogService.getComponentName(serverCall.getComponentId()));
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

    private Node buildNode(ServiceInstanceInventory instanceInventory) {
        Node instanceNode = new Node();
        instanceNode.setId(instanceInventory.getSequence());
        instanceNode.setName(instanceInventory.getName());
        if (BooleanUtils.valueToBoolean(instanceInventory.getIsAddress())) {
            instanceNode.setReal(false);
        } else {
            instanceNode.setReal(true);
        }
        return instanceNode;
    }

    private void filterZeroSourceOrTargetReference(List<Call.CallDetail> serviceRelationClientCalls) {
        for (int i = serviceRelationClientCalls.size() - 1; i >= 0; i--) {
            Call.CallDetail call = serviceRelationClientCalls.get(i);
            if (call.getSource() == 0 || call.getTarget() == 0) {
                serviceRelationClientCalls.remove(i);
            }
        }
    }
}
