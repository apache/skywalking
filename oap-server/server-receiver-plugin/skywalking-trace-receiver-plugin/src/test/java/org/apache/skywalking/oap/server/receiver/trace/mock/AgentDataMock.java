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

package org.apache.skywalking.oap.server.receiver.trace.mock;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.TraceSegmentReportServiceGrpc;
import org.apache.skywalking.apm.network.management.v3.InstancePingPkg;
import org.apache.skywalking.apm.network.management.v3.InstanceProperties;
import org.apache.skywalking.apm.network.management.v3.ManagementServiceGrpc;

public class AgentDataMock {
    private static boolean IS_COMPLETED = false;

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext().build();

        StreamObserver<SegmentObject> streamObserver = createStreamObserver(channel);

        long startTimestamp = System.currentTimeMillis();
        //long startTimestamp = new DateTime().minusDays(2).getMillis();

        ManagementServiceGrpc.ManagementServiceBlockingStub managementServiceBlockingStub = ManagementServiceGrpc.newBlockingStub(
            channel);

        // ServiceAMock
        ServiceAMock serviceAMock = new ServiceAMock();
        managementServiceBlockingStub.keepAlive(InstancePingPkg.newBuilder()
                                                               .setService(ServiceAMock.SERVICE_NAME)
                                                               .setServiceInstance(ServiceAMock.SERVICE_INSTANCE_NAME)
                                                               .build());

        // ServiceBMock
        ServiceBMock serviceBMock = new ServiceBMock();

        // ServiceCMock
        ServiceCMock serviceCMock = new ServiceCMock();

        TimeUnit.SECONDS.sleep(10);

        for (int i = 0; i < 5; i++) {
            String traceId = UUID.randomUUID().toString();
            String serviceASegmentId = UUID.randomUUID().toString();
            String serviceBSegmentId = UUID.randomUUID().toString();
            String serviceCSegmentId = UUID.randomUUID().toString();
            serviceAMock.mock(
                streamObserver, traceId, serviceASegmentId, startTimestamp);
            serviceBMock.mock(
                streamObserver, traceId, serviceBSegmentId, serviceASegmentId, startTimestamp);
            serviceCMock.mock(
                streamObserver, traceId, serviceCSegmentId, serviceBSegmentId, startTimestamp);
        }

        streamObserver.onCompleted();

        managementServiceBlockingStub.reportInstanceProperties(
            InstanceProperties.newBuilder()
                              .setService(ServiceAMock.SERVICE_NAME)
                              .setServiceInstance(ServiceAMock.SERVICE_INSTANCE_NAME)
                              .addProperties(
                                  KeyStringValuePair.newBuilder()
                                                    .setKey("os_name").setValue("MacOS")
                                                    .build())
                              .addProperties(
                                  KeyStringValuePair.newBuilder()
                                                    .setKey("language").setValue("java")
                                                    .build()
                              )
                              .build());
        managementServiceBlockingStub.reportInstanceProperties(
            InstanceProperties.newBuilder()
                              .setService(ServiceBMock.SERVICE_NAME)
                              .setServiceInstance(ServiceBMock.SERVICE_INSTANCE_NAME)
                              .addProperties(
                                  KeyStringValuePair.newBuilder()
                                                    .setKey("os_name").setValue("MacOS")
                                                    .build())
                              .addProperties(
                                  KeyStringValuePair.newBuilder()
                                                    .setKey("language").setValue("java")
                                                    .build()
                              )
                              .build());

        while (!IS_COMPLETED) {
            TimeUnit.MILLISECONDS.sleep(500);
        }

    }

    private static StreamObserver<SegmentObject> createStreamObserver(ManagedChannel channel) {
        TraceSegmentReportServiceGrpc.TraceSegmentReportServiceStub stub = TraceSegmentReportServiceGrpc.newStub(
            channel);
        return stub.collect(new StreamObserver<Commands>() {
            @Override
            public void onNext(Commands downstream) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onCompleted() {
                IS_COMPLETED = true;
            }
        });
    }
}
