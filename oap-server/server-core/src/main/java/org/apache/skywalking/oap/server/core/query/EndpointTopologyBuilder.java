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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.EndpointNode;
import org.apache.skywalking.oap.server.core.query.type.EndpointTopology;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.source.DetectPoint;

public class EndpointTopologyBuilder {

    EndpointTopology buildDebuggable(List<Call.CallDetail> serverSideCalls) {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Build endpoint topology");
            }
            return build(serverSideCalls);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    EndpointTopology build(List<Call.CallDetail> serverSideCalls) {
        EndpointTopology topology = new EndpointTopology();
        serverSideCalls.forEach(callDetail -> {
            Call call = new Call();
            call.setId(callDetail.getId());
            call.setSource(callDetail.getSource());
            call.setTarget(callDetail.getTarget());
            call.addDetectPoint(DetectPoint.SERVER);
            topology.getCalls().add(call);
        });

        Set<String> nodeIds = new HashSet<>();
        serverSideCalls.forEach(call -> {
            if (!nodeIds.contains(call.getSource())) {
                topology.getNodes().add(buildEndpointDependencyNode(call.getSource()));
                nodeIds.add(call.getSource());
            }
            if (!nodeIds.contains(call.getTarget())) {
                topology.getNodes().add(buildEndpointDependencyNode(call.getTarget()));
                nodeIds.add(call.getTarget());
            }
        });
        return topology;
    }

    private EndpointNode buildEndpointDependencyNode(String endpointId) {
        final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
            endpointId);
        EndpointNode instanceNode = new EndpointNode();
        instanceNode.setId(endpointId);
        instanceNode.setName(endpointIDDefinition.getEndpointName());
        instanceNode.setServiceId(endpointIDDefinition.getServiceId());
        final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
            endpointIDDefinition.getServiceId());
        instanceNode.setServiceName(serviceIDDefinition.getName());
        instanceNode.setReal(serviceIDDefinition.isReal());
        return instanceNode;
    }
}
