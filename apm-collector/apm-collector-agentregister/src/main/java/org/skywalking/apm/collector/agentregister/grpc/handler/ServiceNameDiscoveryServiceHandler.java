/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentregister.grpc.handler;

import io.grpc.stub.StreamObserver;
import java.util.List;
import org.skywalking.apm.collector.agentregister.servicename.ServiceNameService;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.ServiceNameCollection;
import org.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.ServiceNameElement;
import org.skywalking.apm.network.proto.ServiceNameMappingCollection;
import org.skywalking.apm.network.proto.ServiceNameMappingElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceNameDiscoveryServiceHandler extends ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameDiscoveryServiceHandler.class);

    private ServiceNameService serviceNameService = new ServiceNameService();

    @Override public void discovery(ServiceNameCollection request,
        StreamObserver<ServiceNameMappingCollection> responseObserver) {
        List<ServiceNameElement> serviceNameElementList = request.getElementsList();

        ServiceNameMappingCollection.Builder builder = ServiceNameMappingCollection.newBuilder();
        for (ServiceNameElement serviceNameElement : serviceNameElementList) {
            int applicationId = serviceNameElement.getApplicationId();
            String serviceName = serviceNameElement.getServiceName();
            int serviceId = serviceNameService.getOrCreate(applicationId, serviceName);

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
