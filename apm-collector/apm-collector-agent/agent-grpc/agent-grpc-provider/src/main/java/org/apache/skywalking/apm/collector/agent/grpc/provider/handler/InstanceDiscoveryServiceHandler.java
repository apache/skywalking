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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.collector.analysis.metric.define.AnalysisMetricModule;
import org.apache.skywalking.apm.collector.analysis.metric.define.service.IInstanceHeartBeatService;
import org.apache.skywalking.apm.collector.analysis.register.define.AnalysisRegisterModule;
import org.apache.skywalking.apm.collector.analysis.register.define.service.AgentOsInfo;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IInstanceIDService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.apache.skywalking.apm.network.proto.ApplicationInstance;
import org.apache.skywalking.apm.network.proto.ApplicationInstanceHeartbeat;
import org.apache.skywalking.apm.network.proto.ApplicationInstanceMapping;
import org.apache.skywalking.apm.network.proto.Downstream;
import org.apache.skywalking.apm.network.proto.InstanceDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.proto.OSInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceDiscoveryServiceHandler extends InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstanceDiscoveryServiceHandler.class);

    private final IInstanceIDService instanceIDService;
    private final IInstanceHeartBeatService instanceHeartBeatService;

    public InstanceDiscoveryServiceHandler(ModuleManager moduleManager) {
        this.instanceIDService = moduleManager.find(AnalysisRegisterModule.NAME).getService(IInstanceIDService.class);
        this.instanceHeartBeatService = moduleManager.find(AnalysisMetricModule.NAME).getService(IInstanceHeartBeatService.class);
    }

    @Override
    public void registerInstance(ApplicationInstance request,
        StreamObserver<ApplicationInstanceMapping> responseObserver) {
        OSInfo osinfo = request.getOsinfo();
        AgentOsInfo agentOsInfo = new AgentOsInfo();
        agentOsInfo.setHostname(osinfo.getHostname());
        agentOsInfo.setOsName(osinfo.getOsName());
        agentOsInfo.setProcessNo(osinfo.getProcessNo());
        agentOsInfo.setIpv4s(osinfo.getIpv4SList());

        int instanceId = instanceIDService.getOrCreateByAgentUUID(request.getApplicationId(), request.getAgentUUID(), request.getRegisterTime(), agentOsInfo);
        ApplicationInstanceMapping.Builder builder = ApplicationInstanceMapping.newBuilder();
        builder.setApplicationId(request.getApplicationId());
        builder.setApplicationInstanceId(instanceId);
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override public void heartbeat(ApplicationInstanceHeartbeat request, StreamObserver<Downstream> responseObserver) {
        int instanceId = request.getApplicationInstanceId();
        long heartBeatTime = request.getHeartbeatTime();
        this.instanceHeartBeatService.heartBeat(instanceId, heartBeatTime);
        responseObserver.onNext(Downstream.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
