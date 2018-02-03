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

package org.apache.skywalking.apm.collector.ui.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.ui.application.ApplicationNode;
import org.apache.skywalking.apm.collector.storage.ui.application.ConjecturalNode;
import org.apache.skywalking.apm.collector.storage.ui.common.Call;
import org.apache.skywalking.apm.collector.storage.ui.common.Node;
import org.apache.skywalking.apm.collector.storage.ui.common.Topology;
import org.apache.skywalking.apm.collector.storage.ui.common.VisualUserNode;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * @author peng-yongsheng
 */
class TopologyBuilder {

    private final ApplicationCacheService applicationCacheService;

    TopologyBuilder(ModuleManager moduleManager) {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    Topology build(List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents,
        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings, List<Call> callerCalls,
        List<Call> calleeCalls, long secondsBetween) {
        Map<Integer, String> components = changeNodeComp2Map(applicationComponents);
        Map<String, String> mappings = changeMapping2Map(applicationMappings);

        List<Call> calls = buildCalls(callerCalls, calleeCalls);

        Set<Integer> nodeIds = new HashSet<>();
        calls.forEach(call -> {
            String sourceName = applicationCacheService.getApplicationById(call.getSource()).getApplicationCode();
            String targetName = applicationCacheService.getApplicationById(call.getTarget()).getApplicationCode();

            call.setSourceName(sourceName);
            call.setTargetName(targetName);

            nodeIds.add(call.getSource());
            nodeIds.add(call.getTarget());
        });

        List<Node> nodes = new LinkedList<>();
        nodeIds.forEach(nodeId -> {
            Application application = applicationCacheService.getApplicationById(nodeId);
            if (BooleanUtils.valueToBoolean(application.getAddressId())) {
                ConjecturalNode conjecturalNode = new ConjecturalNode();
                conjecturalNode.setId(nodeId);
                conjecturalNode.setName(application.getApplicationCode());
                conjecturalNode.setType(components.getOrDefault(application.getApplicationId(), Const.UNKNOWN));
                nodes.add(conjecturalNode);
            } else {
                if (nodeId == Const.NONE_APPLICATION_ID) {
                    VisualUserNode node = new VisualUserNode();
                    node.setId(nodeId);
                    node.setName(Const.USER_CODE);
                    node.setType(Const.USER_CODE.toUpperCase());
                    nodes.add(node);
                } else {
                    ApplicationNode applicationNode = new ApplicationNode();
                    applicationNode.setId(nodeId);
                    applicationNode.setName(application.getApplicationCode());
                    applicationNode.setType(components.getOrDefault(application.getApplicationId(), Const.UNKNOWN));

                    calleeCalls.forEach(call -> {
                        if (call.getTarget() == nodeId) {
                            call.setCallsPerSec(call.getCalls() / secondsBetween);
                            call.setResponseTimePerSec(call.getResponseTimes() / secondsBetween);
                        }
                    });
                    applicationNode.setCallsPerSec(100L);
                    applicationNode.setResponseTimePerSec(100L);
                    nodes.add(applicationNode);
                }
            }
        });

        Topology topology = new Topology();
        topology.setCalls(calls);
        topology.setNodes(nodes);
        return topology;
    }

    private Map<String, String> changeMapping2Map(
        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings) {
        Map<String, String> mappings = new HashMap<>();
        applicationMappings.forEach(applicationMapping -> {
            String applicationCode = applicationCacheService.getApplicationById(applicationMapping.getApplicationId()).getApplicationCode();
            String address = applicationCacheService.getApplicationById(applicationMapping.getMappingApplicationId()).getApplicationCode();
            mappings.put(address, applicationCode);
        });
        return mappings;
    }

    private Map<Integer, String> changeNodeComp2Map(
        List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents) {
        Map<Integer, String> components = new HashMap<>();
        applicationComponents.forEach(applicationComponent -> {
            String componentName = ComponentsDefine.getInstance().getComponentName(applicationComponent.getComponentId());
            components.put(applicationComponent.getApplicationId(), componentName);
        });
        return components;
    }

    private List<Call> buildCalls(List<Call> callerCalls, List<Call> calleeCalls) {
        List<Call> calls = new LinkedList<>();

        Set<String> distinctCalls = new HashSet<>();
        callerCalls.forEach(callerCall -> {
            distinctCalls.add(callerCall.getSource() + Const.ID_SPLIT + callerCall.getTarget());
            calls.add(callerCall);
        });

        calleeCalls.forEach(calleeCall -> {
            String call = calleeCall.getSource() + Const.ID_SPLIT + calleeCall.getTarget();
            if (!distinctCalls.contains(call)) {
                distinctCalls.add(call);
                calls.add(calleeCall);
            }
        });

        return calls;
    }
}
