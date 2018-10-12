/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *
 */

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v5;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.register.ServiceInstancePingGrpc;
import org.apache.skywalking.apm.network.register.ServiceInstancePingPkg;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceInstancePingPkgHandler extends ServiceInstancePingGrpc.ServiceInstancePingImplBase implements GRPCHandler {
    private static final Logger logger = LoggerFactory.getLogger(ServiceInstancePingPkgHandler.class);

    private final IServiceInstanceInventoryCacheDAO instanceInventoryCacheDAO;
    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;

    public ServiceInstancePingPkgHandler(ModuleManager moduleManager) {
        this.instanceInventoryCacheDAO = moduleManager.find(StorageModule.NAME).getService(IServiceInstanceInventoryCacheDAO.class);
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).getService(IServiceInstanceInventoryRegister.class);

    }

    @Override public void doPing(ServiceInstancePingPkg request, StreamObserver<Commands> responseObserver) {

        ServiceInstanceInventory serviceInstanceInventory = instanceInventoryCacheDAO.get(request.getServiceInstanceId());
        if (request.getServiceInstanceUUID().equals(serviceInstanceInventory.getName()) || serviceInstanceInventory.getServiceId() == Const.NONE) {
            logger.error("Your metadata loss,please set the status in reset.status in the agent {} to ON to trigger a reset!", request.getServiceInstanceUUID());
        } else {
            serviceInstanceInventoryRegister.heartbeat(request.getServiceInstanceId(), request.getTime());
        }

    }
}