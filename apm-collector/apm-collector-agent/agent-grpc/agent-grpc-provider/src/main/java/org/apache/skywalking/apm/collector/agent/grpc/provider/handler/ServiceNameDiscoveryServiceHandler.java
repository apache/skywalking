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
import java.util.List;
import org.apache.skywalking.apm.collector.analysis.register.define.AnalysisRegisterModule;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IServiceNameService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.apache.skywalking.apm.network.proto.ServiceNameCollection;
import org.apache.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.proto.ServiceNameElement;
import org.apache.skywalking.apm.network.proto.ServiceNameMappingCollection;
import org.apache.skywalking.apm.network.proto.ServiceNameMappingElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceNameDiscoveryServiceHandler extends ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameDiscoveryServiceHandler.class);

    private final IServiceNameService serviceNameService;

    public ServiceNameDiscoveryServiceHandler(ModuleManager moduleManager) {
        this.serviceNameService = moduleManager.find(AnalysisRegisterModule.NAME).getService(IServiceNameService.class);
    }

    @Override public void discovery(ServiceNameCollection request,
        StreamObserver<ServiceNameMappingCollection> responseObserver) {
        List<ServiceNameElement> serviceNameElementList = request.getElementsList();

        ServiceNameMappingCollection.Builder builder = ServiceNameMappingCollection.newBuilder();
        for (ServiceNameElement serviceNameElement : serviceNameElementList) {
            int applicationId = serviceNameElement.getApplicationId();
            String serviceName = serviceNameElement.getServiceName();
            int srcSpanType = serviceNameElement.getSrcSpanTypeValue();
            int serviceId = serviceNameService.get(applicationId, srcSpanType, serviceName);

            if (serviceId != 0) {
                ServiceNameMappingElement.Builder mappingElement = ServiceNameMappingElement.newBuilder();
                mappingElement.setServiceId(serviceId);
                mappingElement.setElement(serviceNameElement);
                builder.addElements(mappingElement);
            }
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
