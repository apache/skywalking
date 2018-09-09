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
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class AgentDataMock {

    private static final Logger logger = LoggerFactory.getLogger(AgentDataMock.class);

    private static boolean IS_COMPLETED = false;

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();

        RegisterMock registerMock = new RegisterMock();
        registerMock.mock(channel);

        StreamObserver<UpstreamSegment> streamObserver = createStreamObserver();

        UniqueId.Builder globalTraceId = UniqueIdBuilder.INSTANCE.create();
        long startTimestamp = System.currentTimeMillis();

        ConsumerMock consumerMock = new ConsumerMock();
        UniqueId.Builder consumerSegmentId = UniqueIdBuilder.INSTANCE.create();
        consumerMock.mock(streamObserver, globalTraceId, consumerSegmentId, startTimestamp, true);

        ProviderMock providerMock = new ProviderMock();
        UniqueId.Builder providerSegmentId = UniqueIdBuilder.INSTANCE.create();
        providerMock.mock(streamObserver, globalTraceId, providerSegmentId, consumerSegmentId, startTimestamp, true);

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
