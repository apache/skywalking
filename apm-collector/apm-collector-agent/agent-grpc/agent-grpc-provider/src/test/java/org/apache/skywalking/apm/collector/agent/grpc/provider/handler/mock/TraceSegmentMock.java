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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler.mock;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.proto.Downstream;
import org.apache.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.apache.skywalking.apm.network.proto.UniqueId;
import org.apache.skywalking.apm.network.proto.UpstreamSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceSegmentMock {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentMock.class);

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();

        RegisterMock registerMock = new RegisterMock();
        registerMock.mock(channel);

        Sleeping sleeping = new Sleeping();

        TraceSegmentServiceGrpc.TraceSegmentServiceStub stub = TraceSegmentServiceGrpc.newStub(channel);
        StreamObserver<UpstreamSegment> segmentStreamObserver = stub.collect(new StreamObserver<Downstream>() {
            @Override public void onNext(Downstream downstream) {
            }

            @Override public void onError(Throwable throwable) {
            }

            @Override public void onCompleted() {
                sleeping.setValue(Boolean.FALSE);
            }
        });

        Long[] times = TimeBuilder.INSTANCE.generateTimes();
        logger.info("times size: {}", times.length);

        for (int i = 0; i < times.length; i++) {
            long startTimestamp = times[i];

            UniqueId.Builder globalTraceId = UniqueIdBuilder.INSTANCE.create();

            ConsumerMock consumerMock = new ConsumerMock();
            UniqueId.Builder consumerSegmentId = UniqueIdBuilder.INSTANCE.create();
            consumerMock.mock(segmentStreamObserver, globalTraceId, consumerSegmentId, startTimestamp);

            ProviderMock providerMock = new ProviderMock();
            UniqueId.Builder providerSegmentId = UniqueIdBuilder.INSTANCE.create();
            providerMock.mock(segmentStreamObserver, globalTraceId, providerSegmentId, consumerSegmentId, startTimestamp);

            if (i % 100 == 0) {
                logger.info("sending segment number: {}", i);
            }
        }
        logger.info("sending segment number: {}", times.length);

        segmentStreamObserver.onCompleted();

        while (sleeping.getValue()) {
            Thread.sleep(200);
        }
    }

    static class Sleeping {
        private Boolean value = Boolean.TRUE;

        Boolean getValue() {
            return value;
        }

        void setValue(Boolean value) {
            this.value = value;
        }
    }
}
