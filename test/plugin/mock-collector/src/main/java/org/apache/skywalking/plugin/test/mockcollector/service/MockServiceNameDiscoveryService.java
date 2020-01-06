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
import org.apache.skywalking.apm.network.language.agent.ServiceNameCollection;
import org.apache.skywalking.apm.network.language.agent.ServiceNameDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.language.agent.ServiceNameElement;
import org.apache.skywalking.apm.network.language.agent.ServiceNameMappingCollection;
import org.apache.skywalking.plugin.test.mockcollector.entity.RegistryItem;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateData;

public class MockServiceNameDiscoveryService extends ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceImplBase {

    @Override
    public void discovery(ServiceNameCollection request,
                          StreamObserver<ServiceNameMappingCollection> responseObserver) {
        for (ServiceNameElement element : request.getElementsList()) {
            ValidateData.INSTANCE.getRegistryItem().registryOperationName(new RegistryItem.OperationName(element.getApplicationId(),
                    element.getServiceName()));
        }
        responseObserver.onNext(ServiceNameMappingCollection.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
