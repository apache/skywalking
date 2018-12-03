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

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.network.language.agent.*;

/**
 * @author peng-yongsheng
 */
public class AgentDataMock {

    private static boolean IS_COMPLETED = false;

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();

        RegisterMock registerMock = new RegisterMock(channel);

        StreamObserver<UpstreamSegment> streamObserver = createStreamObserver();

        UniqueId.Builder globalTraceId = UniqueIdBuilder.INSTANCE.create();
        long startTimestamp = System.currentTimeMillis();

        // ServiceAMock
        ServiceAMock serviceAMock = new ServiceAMock(registerMock);
        serviceAMock.register();

        // ServiceBMock
        ServiceBMock serviceBMock = new ServiceBMock(registerMock);
        serviceBMock.register();

        // ServiceCMock
        ServiceCMock serviceCMock = new ServiceCMock(registerMock);
        serviceCMock.register();

        UniqueId.Builder serviceASegmentId = UniqueIdBuilder.INSTANCE.create();
        serviceAMock.mock(streamObserver, globalTraceId, serviceASegmentId, startTimestamp, true);

        UniqueId.Builder serviceBSegmentId = UniqueIdBuilder.INSTANCE.create();
        serviceBMock.mock(streamObserver, globalTraceId, serviceBSegmentId, serviceASegmentId, startTimestamp, true);

        UniqueId.Builder serviceCSegmentId = UniqueIdBuilder.INSTANCE.create();
        serviceCMock.mock(streamObserver, globalTraceId, serviceCSegmentId, serviceBSegmentId, startTimestamp, true);

        TimeUnit.SECONDS.sleep(10);

        for (int i = 0; i < 500; i++) {
            globalTraceId = UniqueIdBuilder.INSTANCE.create();
            serviceASegmentId = UniqueIdBuilder.INSTANCE.create();
            serviceBSegmentId = UniqueIdBuilder.INSTANCE.create();
            serviceCSegmentId = UniqueIdBuilder.INSTANCE.create();
            serviceAMock.mock(streamObserver, globalTraceId, serviceASegmentId, startTimestamp, true);
            serviceBMock.mock(streamObserver, globalTraceId, serviceBSegmentId, serviceASegmentId, startTimestamp, true);
            serviceCMock.mock(streamObserver, globalTraceId, serviceCSegmentId, serviceBSegmentId, startTimestamp, true);
        }

        streamObserver.onCompleted();
        while (!IS_COMPLETED) {
            TimeUnit.MILLISECONDS.sleep(500);
        }
    }

    private static StreamObserver<UpstreamSegment> createStreamObserver() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        TraceSegmentServiceGrpc.TraceSegmentServiceStub stub = TraceSegmentServiceGrpc.newStub(channel);
        return stub.collect(new StreamObserver<Downstream>() {
            @Override public void onNext(Downstream downstream) {
            }

            @Override public void onError(Throwable throwable) {
            }

            @Override public void onCompleted() {
                IS_COMPLETED = true;
            }
        });
    }
}
