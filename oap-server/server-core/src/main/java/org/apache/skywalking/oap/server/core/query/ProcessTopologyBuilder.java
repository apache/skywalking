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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.ProcessNode;
import org.apache.skywalking.oap.server.core.query.type.ProcessTopology;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ProcessTopologyBuilder {
    private final IComponentLibraryCatalogService componentLibraryCatalogService;
    private final IMetricsDAO metricsDAO;
    private Model processTrafficModel;

    public ProcessTopologyBuilder(ModuleManager moduleManager, StorageModels storageModels) {
        final StorageDAO storageDAO = moduleManager.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        this.metricsDAO = storageDAO.newMetricsDao(new ProcessTraffic.Builder());
        for (Model model : storageModels.allModels()) {
            if (Objects.equals(model.getName(), ProcessTraffic.INDEX_NAME)) {
                this.processTrafficModel = model;
                break;
            }
        }
        if (this.processTrafficModel == null) {
            throw new IllegalStateException("could not found the process traffic model");
        }
        this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME)
            .provider()
            .getService(IComponentLibraryCatalogService.class);
    }

    ProcessTopology buildDebuggable(List<Call.CallDetail> clientCalls,
                                    List<Call.CallDetail> serverCalls) throws Exception {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Build process topology");
            }
            return build(clientCalls, serverCalls);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    ProcessTopology build(List<Call.CallDetail> clientCalls,
                          List<Call.CallDetail> serverCalls) throws Exception {
        log.debug("building process topology, total found client calls: {}, total found server calls: {}",
            clientCalls.size(), serverCalls.size());
        if (CollectionUtils.isEmpty(clientCalls) && CollectionUtils.isEmpty(serverCalls)) {
            return new ProcessTopology();
        }
        List<Call> calls = new LinkedList<>();
        HashMap<String, Call> callMap = new HashMap<>();

        final Set<String> sourceProcessIdList = Stream.concat(clientCalls.stream(), serverCalls.stream())
            .map(Call.CallDetail::getSource).collect(Collectors.toSet());
        final Set<String> destProcessIdList = Stream.concat(clientCalls.stream(), serverCalls.stream())
            .map(Call.CallDetail::getTarget).collect(Collectors.toSet());
        sourceProcessIdList.addAll(destProcessIdList);

        // query all traffic data
        final Map<String, ProcessNode> nodes = this.metricsDAO.multiGet(this.processTrafficModel, Stream.concat(sourceProcessIdList.stream(), destProcessIdList.stream())
                .distinct().map(processId -> {
                    final ProcessTraffic p = new ProcessTraffic();
                    p.setProcessId(processId);
                    return p;
                }).collect(Collectors.toList())).stream()
            .map(t -> (ProcessTraffic) t)
            .collect(Collectors.toMap(m -> m.id().build(), this::buildNode, (t1, t2) -> t1));

        int appendCallCount = 0;
        for (Call.CallDetail clientCall : clientCalls) {
            if (!callMap.containsKey(clientCall.getId())) {
                Call call = new Call();

                callMap.put(clientCall.getId(), call);
                call.setSource(clientCall.getSource());
                call.setTarget(clientCall.getTarget());
                call.setId(clientCall.getId());
                call.addDetectPoint(DetectPoint.CLIENT);
                call.addSourceComponent(componentLibraryCatalogService.getComponentName(clientCall.getComponentId()));
                calls.add(call);
                appendCallCount++;
            }
        }

        // adding server side call
        for (Call.CallDetail serverCall : serverCalls) {
            Call call = callMap.get(serverCall.getId());
            if (call == null) {
                call = new Call();

                callMap.put(serverCall.getId(), call);
                call.setSource(serverCall.getSource());
                call.setTarget(serverCall.getTarget());
                call.setId(serverCall.getId());
                calls.add(call);
                appendCallCount++;
            }
            call.addDetectPoint(DetectPoint.SERVER);
            call.addTargetComponent(componentLibraryCatalogService.getComponentName(serverCall.getComponentId()));
        }

        ProcessTopology topology = new ProcessTopology();
        topology.getCalls().addAll(calls);
        topology.getNodes().addAll(nodes.values());
        log.debug("process topology built, total calls: {}, total nodes: {}", appendCallCount, nodes.size());
        return topology;
    }

    private ProcessNode buildNode(ProcessTraffic traffic) {
        ProcessNode processNode = new ProcessNode();
        processNode.setId(traffic.id().build());
        processNode.setServiceId(traffic.getServiceId());
        processNode.setServiceName(IDManager.ServiceID.analysisId(traffic.getServiceId()).getName());
        processNode.setServiceInstanceId(traffic.getInstanceId());
        processNode.setServiceInstanceName(IDManager.ServiceInstanceID.analysisId(traffic.getInstanceId()).getName());
        processNode.setName(traffic.getName());
        processNode.setReal(!Objects.equals(traffic.getDetectType(), ProcessDetectType.VIRTUAL.value()));
        return processNode;
    }
}
