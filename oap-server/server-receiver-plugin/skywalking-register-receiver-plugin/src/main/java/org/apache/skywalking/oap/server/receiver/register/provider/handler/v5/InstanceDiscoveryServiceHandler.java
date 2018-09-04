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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v5;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class InstanceDiscoveryServiceHandler extends InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstanceDiscoveryServiceHandler.class);

    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
//    private final IInstanceHeartBeatService instanceHeartBeatService;

    public InstanceDiscoveryServiceHandler(ModuleManager moduleManager) {
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).getService(IServiceInstanceInventoryRegister.class);
//        this.instanceHeartBeatService = moduleManager.find(CoreModule.NAME).getService(IInstanceHeartBeatService.class);
    }

    @Override
    public void registerInstance(ApplicationInstance request,
        StreamObserver<ApplicationInstanceMapping> responseObserver) {
        OSInfo osinfo = request.getOsinfo();
        ServiceInstanceInventory.AgentOsInfo agentOsInfo = new ServiceInstanceInventory.AgentOsInfo();
        agentOsInfo.setHostname(osinfo.getHostname());
        agentOsInfo.setOsName(osinfo.getOsName());
        agentOsInfo.setProcessNo(osinfo.getProcessNo());
        agentOsInfo.getIpv4s().addAll(osinfo.getIpv4SList());

        int serviceInstanceId = serviceInstanceInventoryRegister.getOrCreate(request.getApplicationId(), request.getAgentUUID(), request.getRegisterTime(), agentOsInfo);
        ApplicationInstanceMapping.Builder builder = ApplicationInstanceMapping.newBuilder();
        builder.setApplicationId(request.getApplicationId());
        builder.setApplicationInstanceId(serviceInstanceId);
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override public void heartbeat(ApplicationInstanceHeartbeat request, StreamObserver<Downstream> responseObserver) {
//        int instanceId = request.getApplicationInstanceId();
//        long heartBeatTime = request.getHeartbeatTime();
//        this.instanceHeartBeatService.heartBeat(instanceId, heartBeatTime);
//        responseObserver.onNext(Downstream.getDefaultInstance());
//        responseObserver.onCompleted();
    }
}
