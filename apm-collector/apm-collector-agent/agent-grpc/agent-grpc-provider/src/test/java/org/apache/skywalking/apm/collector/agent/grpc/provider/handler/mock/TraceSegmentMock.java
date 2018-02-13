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
class TraceSegmentMock {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentMock.class);

    void mock(ManagedChannel channel, Long[] times) {
        TraceSegmentServiceGrpc.TraceSegmentServiceStub stub = TraceSegmentServiceGrpc.newStub(channel);
        StreamObserver<UpstreamSegment> segmentStreamObserver = stub.collect(new StreamObserver<Downstream>() {
            @Override public void onNext(Downstream downstream) {
            }

            @Override public void onError(Throwable throwable) {
            }

            @Override public void onCompleted() {
            }
        });

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
    }
}
