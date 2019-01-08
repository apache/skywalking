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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v5.grpc;

import com.google.common.base.Strings;
import io.grpc.stub.StreamObserver;
import java.util.Objects;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.register.service.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class InstanceDiscoveryServiceHandler extends InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstanceDiscoveryServiceHandler.class);

    private final ServiceInventoryCache serviceInventoryCache;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private final IServiceInventoryRegister serviceInventoryRegister;
    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;

    public InstanceDiscoveryServiceHandler(ModuleManager moduleManager) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
        this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
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

        ServiceInventory serviceInventory = serviceInventoryCache.get(request.getApplicationId());

        String instanceName = serviceInventory.getName();
        if (osinfo.getProcessNo() != 0) {
            instanceName += "-pid:" + osinfo.getProcessNo();
        }
        if (!Strings.isNullOrEmpty(osinfo.getHostname())) {
            instanceName += "@" + osinfo.getHostname();
        }

        int serviceInstanceId = serviceInstanceInventoryRegister.getOrCreate(request.getApplicationId(), instanceName, request.getAgentUUID(), request.getRegisterTime(), agentOsInfo);
        ApplicationInstanceMapping.Builder builder = ApplicationInstanceMapping.newBuilder();
        builder.setApplicationId(request.getApplicationId());
        builder.setApplicationInstanceId(serviceInstanceId);
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override public void heartbeat(ApplicationInstanceHeartbeat request, StreamObserver<Downstream> responseObserver) {
        int serviceInstanceId = request.getApplicationInstanceId();
        long heartBeatTime = request.getHeartbeatTime();
        serviceInstanceInventoryRegister.heartbeat(serviceInstanceId, heartBeatTime);

        ServiceInstanceInventory serviceInstanceInventory = serviceInstanceInventoryCache.get(serviceInstanceId);
        if (Objects.nonNull(serviceInstanceInventory)) {
            serviceInventoryRegister.heartbeat(serviceInstanceInventory.getServiceId(), heartBeatTime);
        } else {
            logger.warn("Can't found service by service instance id from cache, service instance id is: {}", serviceInstanceId);
        }

        responseObserver.onNext(Downstream.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
