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

import io.grpc.stub.StreamObserver;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.register.service.IEndpointInventoryRegister;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceNameDiscoveryHandler extends ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceImplBase implements GRPCHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNameDiscoveryHandler.class);

    private final IEndpointInventoryRegister inventoryService;

    public ServiceNameDiscoveryHandler(ModuleManager moduleManager) {
        this.inventoryService = moduleManager.find(CoreModule.NAME).provider().getService(IEndpointInventoryRegister.class);
    }

    @Override public void discovery(ServiceNameCollection request,
        StreamObserver<ServiceNameMappingCollection> responseObserver) {
        List<ServiceNameElement> serviceNameElementList = request.getElementsList();

        ServiceNameMappingCollection.Builder builder = ServiceNameMappingCollection.newBuilder();
        for (ServiceNameElement serviceNameElement : serviceNameElementList) {
            int serviceId = serviceNameElement.getApplicationId();
            String endpointName = serviceNameElement.getServiceName();
            int endpointId = inventoryService.getOrCreate(serviceId, endpointName, DetectPoint.fromSpanType(serviceNameElement.getSrcSpanType()));

            if (endpointId != Const.NONE) {
                ServiceNameMappingElement.Builder mappingElement = ServiceNameMappingElement.newBuilder();
                mappingElement.setServiceId(endpointId);
                mappingElement.setElement(serviceNameElement);
                builder.addElements(mappingElement);
            }
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
