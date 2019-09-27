/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.mockcollector.service;

import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.network.common.KeyIntValuePair;
import org.apache.skywalking.apm.network.register.v2.*;
import org.apache.skywalking.plugin.test.mockcollector.entity.RegistryItem;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateData;

public class MockRegisterService extends RegisterGrpc.RegisterImplBase {

    private Logger logger = LogManager.getLogger(MockTraceSegmentService.class);


    @Override
    public void doEndpointRegister(Enpoints request, StreamObserver<EndpointMapping> responseObserver) {
        for (Endpoint endpoint : request.getEndpointsList()) {
            ValidateData.INSTANCE.getRegistryItem().registryOperationName(new RegistryItem.OperationName(endpoint.getServiceId(),
                    endpoint.getEndpointName()));
        }
        responseObserver.onNext(EndpointMapping.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void doNetworkAddressRegister(NetAddresses request, StreamObserver<NetAddressMapping> responseObserver) {
        responseObserver.onNext(NetAddressMapping.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void doServiceInstanceRegister(ServiceInstances request, StreamObserver<ServiceInstanceRegisterMapping> responseObserver) {
        if (request.getInstancesCount() <= 0) {
            responseObserver.onNext(ServiceInstanceRegisterMapping.getDefaultInstance());
            responseObserver.onCompleted();
            return;
        }

        for (ServiceInstance serviceInstance : request.getInstancesList()) {
            int instanceId = Sequences.INSTANCE_SEQUENCE.incrementAndGet();
            ValidateData.INSTANCE.getRegistryItem().registryInstance(new RegistryItem.Instance(serviceInstance.getServiceId(), instanceId));

            responseObserver.onNext(ServiceInstanceRegisterMapping.newBuilder().addServiceInstances(KeyIntValuePair.newBuilder()
                    .setKey(serviceInstance.getInstanceUUID()).setValue(instanceId).build()).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void doServiceRegister(Services request, StreamObserver<ServiceRegisterMapping> responseObserver) {
        logger.debug("receive application register.");
        if (request.getServicesCount() <= 0) {
            logger.warn("The service count is empty. return the default service register mapping");
            responseObserver.onNext(ServiceRegisterMapping.getDefaultInstance());
            responseObserver.onCompleted();
            return;
        }

        for (Service service : request.getServicesList()) {
            String applicationCode = service.getServiceName();
            ServiceRegisterMapping.Builder builder = ServiceRegisterMapping.newBuilder();

            if (applicationCode.startsWith("localhost") || applicationCode.startsWith("127.0.0.1") || applicationCode.contains(":") || applicationCode.contains("/")) {
                responseObserver.onNext(builder.build());
                responseObserver.onCompleted();
                return;
            }

            Integer applicationId = Sequences.SERVICE_MAPPING.get(applicationCode);
            if (applicationId == null) {
                applicationId = Sequences.ENDPOINT_SEQUENCE.incrementAndGet();
                Sequences.SERVICE_MAPPING.put(applicationCode, applicationId);
                ValidateData.INSTANCE.getRegistryItem().registryApplication(new RegistryItem.Application(applicationCode,
                        applicationId));
            }

            builder.addServices(KeyIntValuePair.newBuilder().setKey(applicationCode).setValue(applicationId).build());
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }
}
