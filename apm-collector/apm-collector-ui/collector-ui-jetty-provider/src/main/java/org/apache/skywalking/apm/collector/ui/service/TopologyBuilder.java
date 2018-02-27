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
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationReferenceMetricUIDAO;
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
        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings,
        List<IApplicationMetricUIDAO.ApplicationMetric> applicationMetrics,
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> callerReferenceMetric,
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> calleeReferenceMetric, long secondsBetween) {
        Map<Integer, String> components = changeNodeComp2Map(applicationComponents);
        Map<Integer, Integer> mappings = changeMapping2Map(applicationMappings);

        calleeReferenceMetric = calleeReferenceMetricFilter(calleeReferenceMetric);

        List<Node> nodes = new LinkedList<>();
        applicationMetrics.forEach(node -> {
            int id = node.getId();
            Application application = applicationCacheService.getApplicationById(id);
            ApplicationNode applicationNode = new ApplicationNode();
            applicationNode.setId(id);
            applicationNode.setName(application.getApplicationCode());
            applicationNode.setType(components.getOrDefault(application.getApplicationId(), Const.UNKNOWN));

            applicationNode.setSla(10);
            applicationNode.setCallsPerSec(100L);
            applicationNode.setResponseTimePerSec(100L);
            applicationNode.setApdex(10);
            applicationNode.setAlarm(false);
            applicationNode.setNumOfServer(1);
            applicationNode.setNumOfServerAlarm(1);
            applicationNode.setNumOfServiceAlarm(1);
            nodes.add(applicationNode);
        });

        List<Call> calls = new LinkedList<>();
        callerReferenceMetric.forEach(referenceMetric -> {
            Application source = applicationCacheService.getApplicationById(referenceMetric.getSource());
            Application target = applicationCacheService.getApplicationById(referenceMetric.getTarget());

            if (BooleanUtils.valueToBoolean(target.getIsAddress()) && !mappings.containsKey(target.getApplicationId())) {
                ConjecturalNode conjecturalNode = new ConjecturalNode();
                conjecturalNode.setId(target.getApplicationId());
                conjecturalNode.setName(target.getApplicationCode());
                conjecturalNode.setType(components.getOrDefault(target.getApplicationId(), Const.UNKNOWN));
                nodes.add(conjecturalNode);
            }

            Call call = new Call();
            call.setSource(source.getApplicationId());
            call.setSourceName(source.getApplicationCode());

            int actualTargetId = mappings.getOrDefault(target.getApplicationId(), target.getApplicationId());
            call.setTarget(actualTargetId);
            call.setTargetName(applicationCacheService.getApplicationById(actualTargetId).getApplicationCode());
            call.setAlert(true);
            call.setCallType("aaa");
            call.setCallsPerSec(1);
            call.setResponseTimePerSec(1);
            calls.add(call);
        });

        calleeReferenceMetric.forEach(referenceMetric -> {
            Application source = applicationCacheService.getApplicationById(referenceMetric.getSource());
            Application target = applicationCacheService.getApplicationById(referenceMetric.getTarget());

            if (source.getApplicationId() == Const.NONE_APPLICATION_ID) {
                VisualUserNode visualUserNode = new VisualUserNode();
                visualUserNode.setId(source.getApplicationId());
                visualUserNode.setName(Const.USER_CODE);
                visualUserNode.setType(Const.USER_CODE.toUpperCase());
                nodes.add(visualUserNode);
            }

            if (BooleanUtils.valueToBoolean(source.getIsAddress())) {
                ConjecturalNode conjecturalNode = new ConjecturalNode();
                conjecturalNode.setId(source.getApplicationId());
                conjecturalNode.setName(source.getApplicationCode());
                conjecturalNode.setType(components.getOrDefault(source.getApplicationId(), Const.UNKNOWN));
                nodes.add(conjecturalNode);
            }

            Call call = new Call();
            call.setSource(source.getApplicationId());
            call.setSourceName(source.getApplicationCode());
            call.setTarget(target.getApplicationId());
            call.setTargetName(target.getApplicationCode());
            call.setAlert(true);
            call.setCallType("aaa");
            call.setCallsPerSec(1);
            call.setResponseTimePerSec(1);
            calls.add(call);
        });

        Topology topology = new Topology();
        topology.setCalls(calls);
        topology.setNodes(nodes);
        return topology;
    }

    private List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> calleeReferenceMetricFilter(
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> calleeReferenceMetric) {
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> filteredMetrics = new LinkedList<>();

        calleeReferenceMetric.forEach(referenceMetric -> {
            Application source = applicationCacheService.getApplicationById(referenceMetric.getSource());
            if (BooleanUtils.valueToBoolean(source.getIsAddress()) || source.getApplicationId() == Const.NONE_APPLICATION_ID) {
                filteredMetrics.add(referenceMetric);
            }
        });

        return filteredMetrics;
    }

    private Map<Integer, Integer> changeMapping2Map(
        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings) {
        Map<Integer, Integer> mappings = new HashMap<>();
        applicationMappings.forEach(applicationMapping -> mappings.put(applicationMapping.getMappingApplicationId(), applicationMapping.getApplicationId()));
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
