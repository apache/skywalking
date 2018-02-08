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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.ui.common.Call;
import org.apache.skywalking.apm.collector.storage.ui.common.Node;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.common.Topology;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceTopologyService {

    private final Logger logger = LoggerFactory.getLogger(ServiceTopologyService.class);

    private final IApplicationComponentUIDAO applicationComponentUIDAO;
    private final IServiceMetricUIDAO serviceMetricUIDAO;
    private final IServiceReferenceMetricUIDAO serviceReferenceMetricUIDAO;
    private final ServiceNameCacheService serviceNameCacheService;

    public ServiceTopologyService(ModuleManager moduleManager) {
        this.serviceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IServiceMetricUIDAO.class);
        this.serviceReferenceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IServiceReferenceMetricUIDAO.class);
        this.applicationComponentUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationComponentUIDAO.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
    }

    public Topology getServiceTopology(Step step, int serviceId, long start, long end) throws ParseException {
        logger.debug("start: {}, end: {}", start, end);
        List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents = applicationComponentUIDAO.load(step, start, end);

        Map<Integer, String> components = new HashMap<>();
        applicationComponents.forEach(component -> components.put(component.getApplicationId(), ComponentsDefine.getInstance().getComponentName(component.getComponentId())));

        List<Call> calleeCalls = serviceReferenceMetricUIDAO.getFrontServices(step, start, end, MetricSource.Callee, serviceId);
        calleeCalls.addAll(serviceReferenceMetricUIDAO.getBehindServices(step, start, end, MetricSource.Caller, serviceId));

        Set<Integer> nodeIds = new HashSet<>();
        calleeCalls.forEach(call -> {
            call.setCallType(Const.EMPTY_STRING);
            nodeIds.add(call.getSource());
            nodeIds.add(call.getTarget());
        });

        List<Node> serviceNodes = serviceMetricUIDAO.getServicesMetric(step, start, end, MetricSource.Callee, nodeIds);

        serviceNodes.forEach(serviceNode -> {
            ServiceName serviceName = serviceNameCacheService.get(serviceNode.getId());
            serviceNode.setName(serviceName.getServiceName());
        });

        Topology topology = new Topology();
        topology.setCalls(calleeCalls);
        topology.setNodes(serviceNodes);
        return topology;
    }
}
