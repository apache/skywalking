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
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstanceNode;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstanceTopology;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
public class ServiceInstanceTopologyBuilder {

    public ServiceInstanceTopologyBuilder(ModuleManager moduleManager) {
    }

    ServiceInstanceTopology build(List<Call.CallDetail> serviceInstanceRelationClientCalls,
                                  List<Call.CallDetail> serviceInstanceRelationServerCalls) {

        Map<String, ServiceInstanceNode> nodes = new HashMap<>();
        List<Call> calls = new LinkedList<>();
        HashMap<String, Call> callMap = new HashMap<>();

        /*
         * Build Calls and Nodes based on client side detected data.
         */
        for (Call.CallDetail clientCall : serviceInstanceRelationClientCalls) {
            final IDManager.ServiceInstanceID.InstanceIDDefinition sourceServiceInstance = IDManager.ServiceInstanceID.analysisId(
                clientCall.getSource());
            final IDManager.ServiceID.ServiceIDDefinition sourceService = IDManager.ServiceID.analysisId(
                sourceServiceInstance.getServiceId());

            IDManager.ServiceInstanceID.InstanceIDDefinition destServiceInstance = IDManager.ServiceInstanceID.analysisId(
                clientCall.getTarget());
            final IDManager.ServiceID.ServiceIDDefinition destService = IDManager.ServiceID.analysisId(
                destServiceInstance.getServiceId());

            if (!nodes.containsKey(clientCall.getSource())) {
                nodes.put(clientCall.getSource(), buildNode(sourceService, sourceServiceInstance));
            }
            if (!nodes.containsKey(clientCall.getTarget())) {
                final ServiceInstanceNode node = buildNode(destService, destServiceInstance);
                nodes.put(clientCall.getTarget(), node);
            }

            if (!callMap.containsKey(clientCall.getId())) {
                Call call = new Call();

                callMap.put(clientCall.getId(), call);
                call.setSource(clientCall.getSource());
                call.setTarget(clientCall.getTarget());
                call.setId(clientCall.getId());
                call.addDetectPoint(DetectPoint.CLIENT);
                calls.add(call);
            }
        }

        /*
         * Build Calls and Nodes based on server side detected data.
         */
        for (Call.CallDetail serverCall : serviceInstanceRelationServerCalls) {
            final IDManager.ServiceInstanceID.InstanceIDDefinition sourceServiceInstance = IDManager.ServiceInstanceID.analysisId(
                serverCall.getSource());
            final IDManager.ServiceID.ServiceIDDefinition sourceService = IDManager.ServiceID.analysisId(
                sourceServiceInstance.getServiceId());

            IDManager.ServiceInstanceID.InstanceIDDefinition destServiceInstance = IDManager.ServiceInstanceID.analysisId(
                serverCall.getTarget());
            final IDManager.ServiceID.ServiceIDDefinition destService = IDManager.ServiceID.analysisId(
                destServiceInstance.getServiceId());

            if (!nodes.containsKey(serverCall.getSource())) {
                nodes.put(serverCall.getSource(), buildNode(sourceService, sourceServiceInstance));
            }
            if (!nodes.containsKey(serverCall.getTarget())) {
                final ServiceInstanceNode node = buildNode(destService, destServiceInstance);
                nodes.put(serverCall.getTarget(), node);
            }

            if (!callMap.containsKey(serverCall.getId())) {
                Call call = new Call();

                callMap.put(serverCall.getId(), call);
                call.setSource(serverCall.getSource());
                call.setTarget(serverCall.getTarget());
                call.setId(serverCall.getId());
                call.addDetectPoint(DetectPoint.SERVER);
                calls.add(call);
            } else {
                Call call = callMap.get(serverCall.getId());
                call.addDetectPoint(DetectPoint.SERVER);
            }
        }

        ServiceInstanceTopology topology = new ServiceInstanceTopology();
        topology.getCalls().addAll(calls);
        topology.getNodes().addAll(nodes.values());
        return topology;
    }

    private ServiceInstanceNode buildNode(
        IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition,
        IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition) {
        ServiceInstanceNode instanceNode = new ServiceInstanceNode();
        instanceNode.setId(
            IDManager.ServiceInstanceID.buildId(instanceIDDefinition.getServiceId(), instanceIDDefinition.getName()));
        instanceNode.setName(instanceIDDefinition.getName());
        instanceNode.setServiceId(instanceIDDefinition.getServiceId());
        instanceNode.setServiceName(serviceIDDefinition.getName());
        instanceNode.setReal(serviceIDDefinition.isReal());
        instanceNode.setType(Const.EMPTY_STRING); //Since 9.4.0, don't provide type for instance topology node.
        return instanceNode;
    }
}
