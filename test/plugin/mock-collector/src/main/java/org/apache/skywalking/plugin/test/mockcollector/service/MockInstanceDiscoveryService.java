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

import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.plugin.test.mockcollector.entity.RegistryItem;
import org.apache.skywalking.plugin.test.mockcollector.entity.ValidateData;

public class MockInstanceDiscoveryService extends InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceImplBase {


    @Override
    public void heartbeat(ApplicationInstanceHeartbeat request, StreamObserver<Downstream> responseObserver) {
        ValidateData.INSTANCE.getRegistryItem().registryHeartBeat(new RegistryItem.HeartBeat(request.getApplicationInstanceId()));
        responseObserver.onNext(Downstream.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void registerInstance(ApplicationInstance request,
                                 StreamObserver<ApplicationInstanceMapping> responseObserver) {
        int instanceId = Sequences.INSTANCE_SEQUENCE.incrementAndGet();
        ValidateData.INSTANCE.getRegistryItem().registryInstance(new RegistryItem.Instance(request.getApplicationId(), instanceId));

        responseObserver.onNext(ApplicationInstanceMapping.newBuilder().setApplicationId(request.getApplicationId())
                .setApplicationInstanceId(instanceId).build());
        responseObserver.onCompleted();
    }
}
