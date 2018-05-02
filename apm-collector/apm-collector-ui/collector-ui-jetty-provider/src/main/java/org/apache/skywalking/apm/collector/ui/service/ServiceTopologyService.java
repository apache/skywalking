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
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IComponentLibraryCatalogService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.ui.common.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceTopologyService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceTopologyService.class);

    private final IApplicationComponentUIDAO applicationComponentUIDAO;
    private final IServiceMetricUIDAO serviceMetricUIDAO;
    private final IServiceReferenceMetricUIDAO serviceReferenceMetricUIDAO;
    private final ServiceNameCacheService serviceNameCacheService;
    private final DateBetweenService dateBetweenService;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    public ServiceTopologyService(ModuleManager moduleManager) {
        this.serviceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IServiceMetricUIDAO.class);
        this.serviceReferenceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IServiceReferenceMetricUIDAO.class);
        this.applicationComponentUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationComponentUIDAO.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
        this.dateBetweenService = new DateBetweenService(moduleManager);
        this.componentLibraryCatalogService = moduleManager.find(ConfigurationModule.NAME).getService(IComponentLibraryCatalogService.class);
    }

    public Topology getServiceTopology(Step step, int serviceId, long startTimeBucket,
        long endTimeBucket, long startSecondTimeBucket, long endSecondTimeBucket) {
        logger.debug("startTimeBucket: {}, endTimeBucket: {}", startTimeBucket, endTimeBucket);
        List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents = applicationComponentUIDAO.load(step, startTimeBucket, endTimeBucket);

        Map<Integer, String> components = new HashMap<>();
        applicationComponents.forEach(component -> components.put(component.getApplicationId(), this.componentLibraryCatalogService.getComponentName(component.getComponentId())));

        List<IServiceReferenceMetricUIDAO.ServiceReferenceMetric> referenceMetrics = serviceReferenceMetricUIDAO.getFrontServices(step, startTimeBucket, endTimeBucket, MetricSource.Callee, serviceId);
        referenceMetrics.addAll(serviceReferenceMetricUIDAO.getBehindServices(step, startTimeBucket, endTimeBucket, MetricSource.Caller, serviceId));

        Set<Integer> nodeIds = new HashSet<>();

        List<Call> calls = new LinkedList<>();
        referenceMetrics.forEach(referenceMetric -> {
            nodeIds.add(referenceMetric.getSource());
            nodeIds.add(referenceMetric.getTarget());

            Call call = new Call();
            call.setSource(referenceMetric.getSource());
            call.setTarget(referenceMetric.getTarget());
            call.setAvgResponseTime(referenceMetric.getDurations() / referenceMetric.getCalls());
            call.setCallType(components.getOrDefault(serviceNameCacheService.get(referenceMetric.getTarget()).getApplicationId(), Const.UNKNOWN));
            try {
                int applicationId = serviceNameCacheService.get(referenceMetric.getTarget()).getApplicationId();
                call.setCpm(referenceMetric.getCalls() / dateBetweenService.minutesBetween(applicationId, startSecondTimeBucket, endSecondTimeBucket));
            } catch (ParseException e) {
                logger.error(e.getMessage(), e);
            }
            call.setAlert(false);
            calls.add(call);
        });

        List<Node> serviceNodes = serviceMetricUIDAO.getServicesMetric(step, startTimeBucket, endTimeBucket, MetricSource.Callee, nodeIds);

        Set<Integer> gotNodes = new HashSet<>();
        serviceNodes.forEach(serviceNode -> gotNodes.add(serviceNode.getId()));

        Set<Integer> callerNodeIds = new HashSet<>();
        nodeIds.forEach(nodeId -> {
            if (!gotNodes.contains(nodeId)) {
                callerNodeIds.add(nodeId);
            }
        });

        serviceNodes.addAll(serviceMetricUIDAO.getServicesMetric(step, startTimeBucket, endTimeBucket, MetricSource.Caller, callerNodeIds));

        serviceNodes.forEach(serviceNode -> {
            ServiceName serviceName = serviceNameCacheService.get(serviceNode.getId());
            serviceNode.setName(serviceName.getServiceName());
        });

        if (callerNodeIds.contains(Const.NONE_SERVICE_ID)) {
            VisualUserNode userNode = new VisualUserNode();
            userNode.setId(Const.NONE_SERVICE_ID);
            userNode.setName(Const.USER_CODE);
            userNode.setType(Const.USER_CODE.toUpperCase());
            serviceNodes.add(userNode);
        }

        Topology topology = new Topology();
        topology.setCalls(calls);
        topology.setNodes(serviceNodes);
        return topology;
    }
}
