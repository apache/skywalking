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

package org.apache.skywalking.oap.server.configuration.grpc;

import io.grpc.stub.StreamObserver;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.oap.server.configuration.service.Config;
import org.apache.skywalking.oap.server.configuration.service.ConfigurationRequest;
import org.apache.skywalking.oap.server.configuration.service.ConfigurationResponse;
import org.apache.skywalking.oap.server.configuration.service.ConfigurationServiceGrpc;
import org.apache.skywalking.oap.server.configuration.service.GroupConfigItems;
import org.apache.skywalking.oap.server.configuration.service.GroupConfigurationResponse;

public class MockGRPCConfigService extends ConfigurationServiceGrpc.ConfigurationServiceImplBase {
    private final AtomicInteger dataFlag;

    /**
     * @param dataFlag 0:init, 1:change, 2:no change, 3:delete
     */
    public MockGRPCConfigService(AtomicInteger dataFlag) {
        this.dataFlag = dataFlag;
    }

    @Override
    public void call(ConfigurationRequest request,
                     StreamObserver<ConfigurationResponse> responseObserver) {
        ConfigurationResponse response;
        String uuid = request.getUuid();
        switch (this.dataFlag.get()) {
            case 1:
                response = ConfigurationResponse
                    .newBuilder().setUuid(UUID.randomUUID().toString())
                    .addConfigTable(Config
                                        .newBuilder()
                                        .setName("test-module.grpc.testKey")
                                        .setValue("300")
                                        .build()).build();
                responseObserver.onNext(response);
                break;
            case 2:
                response = ConfigurationResponse.newBuilder().setUuid(uuid).build();
                responseObserver.onNext(response);
                break;
            case 3:
                response = ConfigurationResponse
                    .newBuilder().setUuid(UUID.randomUUID().toString())
                    .addConfigTable(Config
                                        .newBuilder()
                                        .setName("test-module.grpc.testKey")
                                        .build()).build();
                responseObserver.onNext(response);
                break;
            default:
                response = ConfigurationResponse
                    .newBuilder().setUuid(UUID.randomUUID().toString())
                    .addConfigTable(Config
                                        .newBuilder()
                                        .setName("test-module.grpc.testKey")
                                        .setValue("100")
                                        .build()).build();
                responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }

    @Override
    public void callGroup(ConfigurationRequest request,
                          StreamObserver<GroupConfigurationResponse> responseObserver) {
        GroupConfigurationResponse response;
        String uuid = request.getUuid();
        switch (this.dataFlag.get()) {
            case 1:
                response = GroupConfigurationResponse
                    .newBuilder().setUuid(UUID.randomUUID().toString())
                    .addGroupConfigTable(GroupConfigItems
                                             .newBuilder().setGroupName("test-module.grpc.testKeyGroup")
                                             .addItems(Config
                                                           .newBuilder()
                                                           .setName("item1")
                                                           .setValue("100")
                                                           .build())
                                             .addItems(Config
                                                           .newBuilder()
                                                           .setName("item2")
                                                           .setValue("2000")
                                                           .build()).build()).build();
                responseObserver.onNext(response);
                break;
            case 2:
                response = GroupConfigurationResponse.newBuilder().setUuid(uuid).build();
                responseObserver.onNext(response);
                break;
            case 3:
                response = GroupConfigurationResponse
                    .newBuilder().setUuid(UUID.randomUUID().toString())
                    .addGroupConfigTable(GroupConfigItems
                                             .newBuilder().setGroupName("test-module.grpc.testKeyGroup")
                                             .addItems(Config
                                                           .newBuilder()
                                                           .setName("item2")
                                                           .setValue("2000")
                                                           .build()).build()).build();
                responseObserver.onNext(response);
                break;
            default:
                response = GroupConfigurationResponse
                    .newBuilder().setUuid(UUID.randomUUID().toString())
                    .addGroupConfigTable(GroupConfigItems
                                             .newBuilder().setGroupName("test-module.grpc.testKeyGroup")
                                             .addItems(Config
                                                           .newBuilder()
                                                           .setName("item1")
                                                           .setValue("100")
                                                           .build())
                                             .addItems(Config
                                                           .newBuilder()
                                                           .setName("item2")
                                                           .setValue("200")
                                                           .build()).build()).build();
                responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }
}
