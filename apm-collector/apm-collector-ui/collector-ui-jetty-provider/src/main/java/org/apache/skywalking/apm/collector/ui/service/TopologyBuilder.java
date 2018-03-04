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

import java.text.ParseException;
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
import org.apache.skywalking.apm.collector.storage.ui.alarm.Alarm;
import org.apache.skywalking.apm.collector.storage.ui.application.ApplicationNode;
import org.apache.skywalking.apm.collector.storage.ui.application.ConjecturalNode;
import org.apache.skywalking.apm.collector.storage.ui.common.Call;
import org.apache.skywalking.apm.collector.storage.ui.common.Node;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.common.Topology;
import org.apache.skywalking.apm.collector.storage.ui.common.VisualUserNode;
import org.apache.skywalking.apm.collector.ui.utils.ApdexCalculator;
import org.apache.skywalking.apm.collector.ui.utils.SLACalculator;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
class TopologyBuilder {

    private final Logger logger = LoggerFactory.getLogger(TopologyBuilder.class);

    private final ApplicationCacheService applicationCacheService;
    private final ServerService serverService;
    private final SecondBetweenService secondBetweenService;
    private final AlarmService alarmService;

    TopologyBuilder(ModuleManager moduleManager) {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.serverService = new ServerService(moduleManager);
        this.secondBetweenService = new SecondBetweenService(moduleManager);
        this.alarmService = new AlarmService(moduleManager);
    }

    Topology build(List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents,
        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings,
        List<IApplicationMetricUIDAO.ApplicationMetric> applicationMetrics,
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> callerReferenceMetric,
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> calleeReferenceMetric,
        Step step, long startTimeBucket, long endTimeBucket, long startSecondTimeBucket, long endSecondTimeBucket) {
        Map<Integer, String> components = changeNodeComp2Map(applicationComponents);
        Map<Integer, Integer> mappings = changeMapping2Map(applicationMappings);

        calleeReferenceMetric = calleeReferenceMetricFilter(calleeReferenceMetric);

        List<Node> nodes = new LinkedList<>();
        applicationMetrics.forEach(applicationMetric -> {
            int applicationId = applicationMetric.getId();
            Application application = applicationCacheService.getApplicationById(applicationId);
            ApplicationNode applicationNode = new ApplicationNode();
            applicationNode.setId(applicationId);
            applicationNode.setName(application.getApplicationCode());
            applicationNode.setType(components.getOrDefault(application.getApplicationId(), Const.UNKNOWN));

            applicationNode.setSla(SLACalculator.INSTANCE.calculate(applicationMetric.getErrorCalls(), applicationMetric.getCalls()));
            try {
                applicationNode.setCallsPerSec(applicationMetric.getCalls() / secondBetweenService.calculate(applicationId, startSecondTimeBucket, endSecondTimeBucket));
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
            applicationNode.setAvgResponseTime((applicationMetric.getDurations() - applicationMetric.getErrorDurations()) / (applicationMetric.getCalls() - applicationMetric.getErrorCalls()));
            applicationNode.setApdex(ApdexCalculator.INSTANCE.calculate(applicationMetric.getSatisfiedCount(), applicationMetric.getToleratingCount(), applicationMetric.getFrustratedCount()));
            applicationNode.setAlarm(false);
            try {
                Alarm alarm = alarmService.loadApplicationAlarmList(Const.EMPTY_STRING, step, startTimeBucket, endTimeBucket, 1, 0);
                if (alarm.getItems().size() > 0) {
                    applicationNode.setAlarm(true);
                }
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }

            applicationNode.setNumOfServer(serverService.getAllServer(applicationId, startSecondTimeBucket, endSecondTimeBucket).size());
            try {
                Alarm alarm = alarmService.loadInstanceAlarmList(Const.EMPTY_STRING, step, startTimeBucket, endTimeBucket, 1000, 0);
                applicationNode.setNumOfServerAlarm(alarm.getItems().size());
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }

            try {
                Alarm alarm = alarmService.loadServiceAlarmList(Const.EMPTY_STRING, step, startTimeBucket, endTimeBucket, 1000, 0);
                applicationNode.setNumOfServiceAlarm(alarm.getItems().size());
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
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

            Set<Integer> nodeIds = buildNodeIds(nodes);
            if (!nodeIds.contains(source.getApplicationId())) {
                ApplicationNode applicationNode = new ApplicationNode();
                applicationNode.setId(source.getApplicationId());
                applicationNode.setName(source.getApplicationCode());
                applicationNode.setType(components.getOrDefault(source.getApplicationId(), Const.UNKNOWN));
                applicationNode.setApdex(100);
                applicationNode.setSla(100);
                nodes.add(applicationNode);
            }

            Call call = new Call();
            call.setSource(source.getApplicationId());
            call.setSourceName(source.getApplicationCode());

            int actualTargetId = mappings.getOrDefault(target.getApplicationId(), target.getApplicationId());
            call.setTarget(actualTargetId);
            call.setTargetName(applicationCacheService.getApplicationById(actualTargetId).getApplicationCode());
            call.setAlert(false);
            call.setCallType(components.get(referenceMetric.getTarget()));
            try {
                call.setCallsPerSec(referenceMetric.getCalls() / secondBetweenService.calculate(source.getApplicationId(), startSecondTimeBucket, endSecondTimeBucket));
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
            call.setAvgResponseTime((referenceMetric.getDurations() - referenceMetric.getErrorDurations()) / (referenceMetric.getCalls() - referenceMetric.getErrorCalls()));
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
            call.setAlert(false);

            if (source.getApplicationId() == Const.NONE_APPLICATION_ID) {
                call.setCallType(Const.EMPTY_STRING);
            } else {
                call.setCallType(components.get(referenceMetric.getTarget()));
            }
            try {
                call.setCallsPerSec(referenceMetric.getCalls() / secondBetweenService.calculate(target.getApplicationId(), startSecondTimeBucket, endSecondTimeBucket));
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
            call.setAvgResponseTime((referenceMetric.getDurations() - referenceMetric.getErrorDurations()) / (referenceMetric.getCalls() - referenceMetric.getErrorCalls()));
            calls.add(call);
        });

        Topology topology = new Topology();
        topology.setCalls(calls);
        topology.setNodes(nodes);
        return topology;
    }

    private Set<Integer> buildNodeIds(List<Node> nodes) {
        Set<Integer> nodeIds = new HashSet<>();
        nodes.forEach(node -> nodeIds.add(node.getId()));
        return nodeIds;
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
}
