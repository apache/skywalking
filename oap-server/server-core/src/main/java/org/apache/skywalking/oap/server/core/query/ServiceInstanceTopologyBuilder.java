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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.entity.Call;
import org.apache.skywalking.oap.server.core.query.entity.ServiceInstanceNode;
import org.apache.skywalking.oap.server.core.query.entity.ServiceInstanceTopology;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

import static java.util.Objects.isNull;

@Slf4j
public class ServiceInstanceTopologyBuilder {

    private final ServiceInventoryCache serviceInventoryCache;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    public ServiceInstanceTopologyBuilder(ModuleManager moduleManager) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME)
                                                  .provider()
                                                  .getService(ServiceInventoryCache.class);
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME)
                                                          .provider()
                                                          .getService(ServiceInstanceInventoryCache.class);
        this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME)
                                                           .provider()
                                                           .getService(IComponentLibraryCatalogService.class);
    }

    ServiceInstanceTopology build(List<Call.CallDetail> serviceInstanceRelationClientCalls,
        List<Call.CallDetail> serviceInstanceRelationServerCalls) {

        Map<Integer, ServiceInstanceNode> nodes = new HashMap<>();
        List<Call> calls = new LinkedList<>();
        HashMap<String, Call> callMap = new HashMap<>();

        for (Call.CallDetail clientCall : serviceInstanceRelationClientCalls) {
            ServiceInstanceInventory sourceInstance = serviceInstanceInventoryCache.get(Integer.parseInt(clientCall.getSource()));
            ServiceInstanceInventory targetInstance = serviceInstanceInventoryCache.get(Integer.parseInt(clientCall.getTarget()));

            if (isNull(sourceInstance) || isNull(targetInstance)) {
                continue;
            }

            if (targetInstance.getMappingServiceInstanceId() != Const.NONE) {
                continue;
            }

            if (!nodes.containsKey(sourceInstance.getSequence())) {
                ServiceInventory sourceService = serviceInventoryCache.get(sourceInstance.getServiceId());
                nodes.put(sourceInstance.getSequence(), buildNode(sourceService, sourceInstance));
            }

            if (!nodes.containsKey(targetInstance.getSequence())) {
                ServiceInventory targetService = serviceInventoryCache.get(targetInstance.getServiceId());
                nodes.put(targetInstance.getSequence(), buildNode(targetService, targetInstance));
                if (BooleanUtils.valueToBoolean(targetInstance.getIsAddress())) {
                    nodes.get(targetInstance.getSequence())
                         .setType(componentLibraryCatalogService.getServerNameBasedOnComponent(clientCall.getComponentId()));
                }
            }

            String callId = sourceInstance.getSequence() + Const.ID_SPLIT + targetInstance.getSequence();
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
            ServiceInstanceInventory sourceInstance = serviceInstanceInventoryCache.get(Integer.parseInt(serverCall.getSource()));
            ServiceInstanceInventory targetInstance = serviceInstanceInventoryCache.get(Integer.parseInt(serverCall.getTarget()));

            if (isNull(sourceInstance) || isNull(targetInstance)) {
                continue;
            }

            if (sourceInstance.getSequence() == Const.USER_INSTANCE_ID) {
                if (!nodes.containsKey(sourceInstance.getSequence())) {
                    ServiceInstanceNode visualUserNode = new ServiceInstanceNode();
                    visualUserNode.setId(sourceInstance.getSequence());
                    visualUserNode.setName(Const.USER_CODE);
                    visualUserNode.setServiceId(Const.USER_SERVICE_ID);
                    visualUserNode.setServiceName(Const.USER_CODE);
                    visualUserNode.setType(Const.USER_CODE.toUpperCase());
                    visualUserNode.setReal(false);
                    nodes.put(sourceInstance.getSequence(), visualUserNode);
                }
            }

            if (BooleanUtils.valueToBoolean(sourceInstance.getIsAddress())) {
                if (!nodes.containsKey(sourceInstance.getSequence())) {
                    ServiceInventory sourceService = serviceInventoryCache.get(sourceInstance.getServiceId());
                    ServiceInstanceNode conjecturalNode = new ServiceInstanceNode();
                    conjecturalNode.setId(sourceInstance.getSequence());
                    conjecturalNode.setName(sourceInstance.getName());
                    conjecturalNode.setServiceId(sourceService.getSequence());
                    conjecturalNode.setServiceName(sourceService.getName());
                    conjecturalNode.setType(componentLibraryCatalogService.getServerNameBasedOnComponent(serverCall.getComponentId()));
                    conjecturalNode.setReal(true);
                    nodes.put(sourceInstance.getSequence(), conjecturalNode);
                }
            }

            String callId = sourceInstance.getSequence() + Const.ID_SPLIT + targetInstance.getSequence();
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

            if (!nodes.containsKey(sourceInstance.getSequence())) {
                ServiceInventory sourceService = serviceInventoryCache.get(sourceInstance.getServiceId());
                nodes.put(sourceInstance.getSequence(), buildNode(sourceService, sourceInstance));
            }

            if (!nodes.containsKey(targetInstance.getSequence())) {
                ServiceInventory targetService = serviceInventoryCache.get(targetInstance.getServiceId());
                nodes.put(targetInstance.getSequence(), buildNode(targetService, targetInstance));
            }

            if (nodes.containsKey(targetInstance.getSequence())) {
                nodes.get(targetInstance.getSequence())
                     .setType(componentLibraryCatalogService.getComponentName(serverCall.getComponentId()));
            }
        }

        ServiceInstanceTopology topology = new ServiceInstanceTopology();
        topology.getCalls().addAll(calls);
        topology.getNodes().addAll(nodes.values());
        return topology;
    }

    private ServiceInstanceNode buildNode(ServiceInventory serviceInventory,
        ServiceInstanceInventory instanceInventory) {
        ServiceInstanceNode instanceNode = new ServiceInstanceNode();
        instanceNode.setId(instanceInventory.getSequence());
        instanceNode.setName(instanceInventory.getName());
        instanceNode.setServiceId(serviceInventory.getSequence());
        instanceNode.setServiceName(serviceInventory.getName());
        if (BooleanUtils.valueToBoolean(instanceInventory.getIsAddress())) {
            instanceNode.setReal(false);
        } else {
            instanceNode.setReal(true);
        }
        return instanceNode;
    }
}
