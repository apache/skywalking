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
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.Node;
import org.apache.skywalking.oap.server.core.query.type.Topology;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
class ServiceTopologyBuilder {
    private final IComponentLibraryCatalogService componentLibraryCatalogService;
    private final NetworkAddressAliasCache networkAddressAliasCache;
    private final String userID;

    ServiceTopologyBuilder(ModuleManager moduleManager) {
        this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME)
                                                           .provider()
                                                           .getService(IComponentLibraryCatalogService.class);
        this.networkAddressAliasCache = moduleManager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(NetworkAddressAliasCache.class);
        this.userID = IDManager.ServiceID.buildId(Const.USER_SERVICE_NAME, NodeType.User);
    }

    Topology build(List<Call.CallDetail> serviceRelationClientCalls, List<Call.CallDetail> serviceRelationServerCalls) {

        Map<String, Node> nodes = new HashMap<>();
        List<Call> calls = new LinkedList<>();
        HashMap<String, Call> callMap = new HashMap<>();

        for (Call.CallDetail clientCall : serviceRelationClientCalls) {
            final IDManager.ServiceID.ServiceIDDefinition sourceService = IDManager.ServiceID.analysisId(
                clientCall.getSource());
            String sourceServiceId = clientCall.getSource();
            IDManager.ServiceID.ServiceIDDefinition destService = IDManager.ServiceID.analysisId(
                clientCall.getTarget());
            String targetServiceId = clientCall.getTarget();

            /*
             * Use the alias name to make topology relationship accurate.
             */
            if (!destService.isReal()
                && networkAddressAliasCache.get(destService.getName()) != null) {
                /*
                 * If alias exists, mean this network address is representing a real service.
                 */
                final NetworkAddressAlias networkAddressAlias = networkAddressAliasCache.get(destService.getName());
                destService = IDManager.ServiceID.analysisId(
                    networkAddressAlias.getRepresentServiceId());
                targetServiceId = IDManager.ServiceID.buildId(destService.getName(), NodeType.Normal);
            }

            /*
             * Set the conjectural node type.
             */
            if (!nodes.containsKey(targetServiceId)) {
                final Node conjecturalNode = buildNode(targetServiceId, destService);
                nodes.put(targetServiceId, conjecturalNode);
                if (!conjecturalNode.isReal() && StringUtil.isEmpty(conjecturalNode.getType())) {
                    conjecturalNode.setType(
                        componentLibraryCatalogService.getServerNameBasedOnComponent(clientCall.getComponentId()));
                }
            }

            if (!nodes.containsKey(sourceServiceId)) {
                nodes.put(sourceServiceId, buildNode(sourceServiceId, sourceService));
            }

            final String relationId = IDManager.ServiceID.buildRelationId(
                new IDManager.ServiceID.ServiceRelationDefine(sourceServiceId, targetServiceId));

            if (!callMap.containsKey(relationId)) {
                Call call = new Call();

                callMap.put(relationId, call);
                call.setSource(sourceServiceId);
                call.setTarget(targetServiceId);
                call.setId(relationId);
                call.addDetectPoint(DetectPoint.CLIENT);
                call.addSourceComponent(componentLibraryCatalogService.getComponentName(clientCall.getComponentId()));
                calls.add(call);
            }
        }

        for (Call.CallDetail serverCall : serviceRelationServerCalls) {
            final IDManager.ServiceID.ServiceIDDefinition sourceService = IDManager.ServiceID.analysisId(
                serverCall.getSource());
            IDManager.ServiceID.ServiceIDDefinition destService = IDManager.ServiceID.analysisId(
                serverCall.getTarget());

            /*
             * Create the client node if it hasn't been created in client side call.
             */
            Node clientSideNode = nodes.get(serverCall.getSource());
            if (clientSideNode == null) {
                clientSideNode = buildNode(serverCall.getSource(), sourceService);
                nodes.put(serverCall.getSource(), clientSideNode);
            }
            /*
             * conjectural node type.
             */
            if (!clientSideNode.isReal()) {
                clientSideNode.setType(
                    componentLibraryCatalogService.getServerNameBasedOnComponent(serverCall.getComponentId()));
            }
            /*
             * Format the User name type.
             */
            if (userID.equals(serverCall.getSource())) {
                nodes.get(userID).setType(Const.USER_SERVICE_NAME.toUpperCase());
            }
            /*
             * Create the server node if it hasn't been created.
             */
            if (!nodes.containsKey(serverCall.getTarget())) {
                final Node node = buildNode(serverCall.getTarget(), destService);
                nodes.put(serverCall.getTarget(), node);
            }
            /*
             * Set the node type due to service side component id has higher priority
             */
            final Node serverSideNode = nodes.get(serverCall.getTarget());
            serverSideNode.setType(
                componentLibraryCatalogService.getComponentName(serverCall.getComponentId()));

            if (!callMap.containsKey(serverCall.getId())) {
                Call call = new Call();
                callMap.put(serverCall.getId(), call);
                call.setSource(serverCall.getSource());
                call.setTarget(serverCall.getTarget());
                call.setId(serverCall.getId());
                call.addDetectPoint(DetectPoint.SERVER);
                call.addTargetComponent(componentLibraryCatalogService.getComponentName(serverCall.getComponentId()));
                calls.add(call);
            } else {
                Call call = callMap.get(serverCall.getId());
                call.addDetectPoint(DetectPoint.SERVER);
                call.addTargetComponent(componentLibraryCatalogService.getComponentName(serverCall.getComponentId()));
            }
        }

        Topology topology = new Topology();
        topology.getCalls().addAll(calls);
        topology.getNodes().addAll(nodes.values());
        return topology;
    }

    private Node buildNode(String sourceId, IDManager.ServiceID.ServiceIDDefinition sourceService) {
        Node serviceNode = new Node();
        serviceNode.setId(sourceId);
        serviceNode.setName(sourceService.getName());
        serviceNode.setReal(sourceService.isReal());
        return serviceNode;
    }
}
