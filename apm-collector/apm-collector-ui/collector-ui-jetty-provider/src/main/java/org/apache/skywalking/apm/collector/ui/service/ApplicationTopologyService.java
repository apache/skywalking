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
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.ui.common.Call;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.common.Topology;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationTopologyService {

    private final Logger logger = LoggerFactory.getLogger(ApplicationTopologyService.class);

    private final IApplicationComponentUIDAO applicationComponentUIDAO;
    private final IApplicationMappingUIDAO applicationMappingUIDAO;
    private final IApplicationReferenceMetricUIDAO applicationReferenceMetricUIDAO;
    private final ModuleManager moduleManager;

    public ApplicationTopologyService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.applicationComponentUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationComponentUIDAO.class);
        this.applicationMappingUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationMappingUIDAO.class);
        this.applicationReferenceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceMetricUIDAO.class);
    }

    public Topology getApplicationTopology(Step step, int applicationId, long startTime,
        long endTime) throws ParseException {
        logger.debug("startTime: {}, endTime: {}", startTime, endTime);
        List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents = applicationComponentUIDAO.load(step, startTime, endTime);
        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings = applicationMappingUIDAO.load(step, startTime, endTime);

        Map<Integer, String> components = new HashMap<>();
        applicationComponents.forEach(component -> components.put(component.getApplicationId(), ComponentsDefine.getInstance().getComponentName(component.getComponentId())));

        List<Call> callerCalls = applicationReferenceMetricUIDAO.getFrontApplications(step, applicationId, startTime, endTime, MetricSource.Caller);
        callerCalls.addAll(applicationReferenceMetricUIDAO.getBehindApplications(step, applicationId, startTime, endTime, MetricSource.Caller));

        callerCalls.forEach(callerCall -> callerCall.setCallType(components.get(callerCall.getTarget())));

        List<Call> calleeCalls = applicationReferenceMetricUIDAO.getFrontApplications(step, applicationId, startTime, endTime, MetricSource.Callee);
        calleeCalls.addAll(applicationReferenceMetricUIDAO.getBehindApplications(step, applicationId, startTime, endTime, MetricSource.Callee));

        calleeCalls.forEach(calleeCall -> calleeCall.setCallType(components.get(calleeCall.getTarget())));

        Set<Integer> mappings = new HashSet<>();
        applicationMappings.forEach(mapping -> {
            if (applicationId == mapping.getApplicationId()) {
                mappings.add(mapping.getMappingApplicationId());
            }
        });

        mappings.forEach(mappingApplicationId -> {
            List<Call> frontCallerApplications = applicationReferenceMetricUIDAO.getFrontApplications(step, mappingApplicationId, startTime, endTime, MetricSource.Caller);
            frontCallerApplications.forEach(call -> {
                call.setCallType(components.get(call.getTarget()));
                call.setTarget(applicationId);
                callerCalls.add(call);
            });

            List<Call> behindCallerApplications = applicationReferenceMetricUIDAO.getBehindApplications(step, mappingApplicationId, startTime, endTime, MetricSource.Caller);
            behindCallerApplications.forEach(call -> {
                call.setCallType(components.get(call.getTarget()));
                call.setSource(applicationId);
                callerCalls.add(call);
            });

            List<Call> frontCalleeApplications = applicationReferenceMetricUIDAO.getFrontApplications(step, mappingApplicationId, startTime, endTime, MetricSource.Callee);
            frontCalleeApplications.forEach(call -> {
                call.setCallType(components.get(call.getTarget()));
                call.setTarget(applicationId);
                calleeCalls.add(call);
            });

            List<Call> behindCalleeApplications = applicationReferenceMetricUIDAO.getBehindApplications(step, mappingApplicationId, startTime, endTime, MetricSource.Callee);
            behindCalleeApplications.forEach(call -> {
                call.setCallType(components.get(call.getTarget()));
                call.setSource(applicationId);
                calleeCalls.add(call);
            });
        });

        TopologyBuilder builder = new TopologyBuilder(moduleManager);

        long secondsBetween = DurationUtils.INSTANCE.secondsBetween(step, startTime, endTime);
        Topology topology = builder.build(applicationComponents, applicationMappings, callerCalls, calleeCalls, secondsBetween);

        topology.getCalls().forEach(call -> {
            long calls = call.getCalls();
            long responseTimes = call.getResponseTimes();
            call.setCallsPerSec(calls / secondsBetween);
            call.setResponseTimePerSec(responseTimes / secondsBetween);
        });
        return topology;
    }
}
