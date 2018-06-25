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
import java.util.*;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IComponentLibraryCatalogService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.*;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.ui.application.*;
import org.apache.skywalking.apm.collector.storage.ui.common.*;
import org.apache.skywalking.apm.collector.ui.utils.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class TopologyBuilder {

    private static final Logger logger = LoggerFactory.getLogger(TopologyBuilder.class);

    private final ApplicationCacheService applicationCacheService;
    private final ServerService serverService;
    private final DateBetweenService dateBetweenService;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    TopologyBuilder(ModuleManager moduleManager) {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.serverService = new ServerService(moduleManager);
        this.dateBetweenService = new DateBetweenService(moduleManager);
        this.componentLibraryCatalogService = moduleManager.find(ConfigurationModule.NAME).getService(IComponentLibraryCatalogService.class);
    }

    Topology build(List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents,
        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings,
        List<IApplicationMetricUIDAO.ApplicationMetric> applicationMetrics,
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> callerReferenceMetric,
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> calleeReferenceMetric,
        long startSecondTimeBucket, long endSecondTimeBucket) {
        Map<Integer, String> nodeCompMap = buildNodeCompMap(applicationComponents);
        Map<Integer, String> conjecturalNodeCompMap = buildConjecturalNodeCompMap(applicationComponents);
        Map<Integer, Integer> mappings = changeMapping2Map(applicationMappings);
        filterZeroSourceOrTargetReference(callerReferenceMetric);
        filterZeroSourceOrTargetReference(calleeReferenceMetric);
        calleeReferenceMetric = calleeReferenceMetricFilter(calleeReferenceMetric);

        List<Node> nodes = new LinkedList<>();
        Map<Integer, Integer> applicationMinuteBetweenMap = new HashMap<>();
        Map<Integer, Integer> numberOfServer = new HashMap<>();
        applicationMetrics.forEach(applicationMetric -> {
            int applicationId = applicationMetric.getId();
            Application application = applicationCacheService.getApplicationById(applicationId);
            ApplicationNode applicationNode = new ApplicationNode();
            applicationNode.setId(applicationId);
            applicationNode.setName(application.getApplicationCode());
            applicationNode.setType(nodeCompMap.getOrDefault(application.getApplicationId(), Const.UNKNOWN));

            applicationNode.setSla(SLACalculator.INSTANCE.calculate(applicationMetric.getErrorCalls(), applicationMetric.getCalls()));
            try {
                applicationNode.setCpm(applicationMetric.getCalls() / getApplicationMinuteBetween(applicationMinuteBetweenMap, applicationId, startSecondTimeBucket, endSecondTimeBucket));
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
            applicationNode.setAvgResponseTime(applicationMetric.getDurations() / applicationMetric.getCalls());
            applicationNode.setApdex(ApdexCalculator.INSTANCE.calculate(applicationMetric.getSatisfiedCount(), applicationMetric.getToleratingCount(), applicationMetric.getFrustratedCount()));
            applicationNode.setAlarm(false);
            applicationNode.setNumOfServer(getNumberOfServer(numberOfServer, applicationId, startSecondTimeBucket, endSecondTimeBucket));
            nodes.add(applicationNode);
        });

        List<Call> calls = new LinkedList<>();
        Set<Integer> nodeIds = new HashSet<>();
        callerReferenceMetric.forEach(referenceMetric -> {
            Application source = applicationCacheService.getApplicationById(referenceMetric.getSource());
            Application target = applicationCacheService.getApplicationById(referenceMetric.getTarget());

            if (BooleanUtils.valueToBoolean(target.getIsAddress()) && !mappings.containsKey(target.getApplicationId())) {
                if (!nodeIds.contains(target.getApplicationId())) {
                    ConjecturalNode conjecturalNode = new ConjecturalNode();
                    conjecturalNode.setId(target.getApplicationId());
                    conjecturalNode.setName(target.getApplicationCode());
                    conjecturalNode.setType(conjecturalNodeCompMap.getOrDefault(target.getApplicationId(), Const.UNKNOWN));
                    nodes.add(conjecturalNode);
                    nodeIds.add(target.getApplicationId());
                }
            }

            Set<Integer> applicationNodeIds = buildNodeIds(nodes);
            if (!applicationNodeIds.contains(source.getApplicationId())) {
                ApplicationNode applicationNode = new ApplicationNode();
                applicationNode.setId(source.getApplicationId());
                applicationNode.setName(source.getApplicationCode());
                applicationNode.setType(nodeCompMap.getOrDefault(source.getApplicationId(), Const.UNKNOWN));
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
            call.setCallType(nodeCompMap.get(referenceMetric.getTarget()));
            try {
                call.setCpm(referenceMetric.getCalls() / getApplicationMinuteBetween(applicationMinuteBetweenMap, source.getApplicationId(), startSecondTimeBucket, endSecondTimeBucket));
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
            call.setAvgResponseTime(referenceMetric.getDurations() / referenceMetric.getCalls());
            calls.add(call);
        });

        calleeReferenceMetric.forEach(referenceMetric -> {
            Application source = applicationCacheService.getApplicationById(referenceMetric.getSource());
            Application target = applicationCacheService.getApplicationById(referenceMetric.getTarget());

            if (source.getApplicationId() == Const.NONE_APPLICATION_ID) {
                if (!nodeIds.contains(source.getApplicationId())) {
                    VisualUserNode visualUserNode = new VisualUserNode();
                    visualUserNode.setId(source.getApplicationId());
                    visualUserNode.setName(Const.USER_CODE);
                    visualUserNode.setType(Const.USER_CODE.toUpperCase());
                    nodes.add(visualUserNode);
                    nodeIds.add(source.getApplicationId());
                }
            }

            if (BooleanUtils.valueToBoolean(source.getIsAddress())) {
                if (!nodeIds.contains(source.getApplicationId())) {
                    ConjecturalNode conjecturalNode = new ConjecturalNode();
                    conjecturalNode.setId(source.getApplicationId());
                    conjecturalNode.setName(source.getApplicationCode());
                    conjecturalNode.setType(conjecturalNodeCompMap.getOrDefault(target.getApplicationId(), Const.UNKNOWN));
                    nodeIds.add(source.getApplicationId());
                    nodes.add(conjecturalNode);
                }
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
                call.setCallType(nodeCompMap.get(referenceMetric.getTarget()));
            }
            try {
                call.setCpm(referenceMetric.getCalls() / getApplicationMinuteBetween(applicationMinuteBetweenMap, target.getApplicationId(), startSecondTimeBucket, endSecondTimeBucket));
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
            call.setAvgResponseTime(referenceMetric.getDurations() / referenceMetric.getCalls());
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

    private Map<Integer, String> buildConjecturalNodeCompMap(
        List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents) {
        Map<Integer, String> components = new HashMap<>();
        applicationComponents.forEach(applicationComponent -> {
            int componentServerId = this.componentLibraryCatalogService.getServerIdBasedOnComponent(applicationComponent.getComponentId());
            String componentName = this.componentLibraryCatalogService.getServerName(componentServerId);
            components.put(applicationComponent.getApplicationId(), componentName);
        });
        return components;
    }

    private Map<Integer, String> buildNodeCompMap(
        List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents) {
        Map<Integer, String> components = new HashMap<>();
        applicationComponents.forEach(applicationComponent -> {
            String componentName = this.componentLibraryCatalogService.getComponentName(applicationComponent.getComponentId());
            components.put(applicationComponent.getApplicationId(), componentName);
        });
        return components;
    }

    private void filterZeroSourceOrTargetReference(
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> referenceMetric) {
        for (int i = referenceMetric.size() - 1; i >= 0; i--) {
            IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric applicationReferenceMetric = referenceMetric.get(i);
            if (applicationReferenceMetric.getSource() == 0 || applicationReferenceMetric.getTarget() == 0) {
                referenceMetric.remove(i);
            }
        }
    }

    private int getApplicationMinuteBetween(Map<Integer, Integer> applicationMinuteBetweenMap, int applicationId,
        long startSecondTimeBucket,
        long endSecondTimeBucket) throws ParseException {
        if (applicationMinuteBetweenMap.containsKey(applicationId)) {
            return applicationMinuteBetweenMap.get(applicationId);
        } else {
            int applicationMinuteBetween = dateBetweenService.minutesBetween(applicationId, startSecondTimeBucket, endSecondTimeBucket);
            applicationMinuteBetweenMap.put(applicationId, applicationMinuteBetween);
            return applicationMinuteBetween;
        }
    }

    private int getNumberOfServer(Map<Integer, Integer> numberOfServerMap, int applicationId,
        long startSecondTimeBucket,
        long endSecondTimeBucket) {
        if (numberOfServerMap.containsKey(applicationId)) {
            return numberOfServerMap.get(applicationId);
        } else {
            int numberOfServer = serverService.getAllServer(applicationId, startSecondTimeBucket, endSecondTimeBucket).size();
            numberOfServerMap.put(applicationId, numberOfServer);
            return numberOfServer;
        }
    }
}
