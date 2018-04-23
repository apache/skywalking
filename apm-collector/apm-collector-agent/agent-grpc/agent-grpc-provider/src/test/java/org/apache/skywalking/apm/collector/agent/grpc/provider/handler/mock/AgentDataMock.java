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
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.network.proto.Downstream;
import org.apache.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.apache.skywalking.apm.network.proto.UpstreamSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class AgentDataMock {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentMock.class);

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();

        List<StreamObserver<UpstreamSegment>> upstreamSegments = new LinkedList<>();
        upstreamSegments.add(createStreamObserver());
        upstreamSegments.add(createStreamObserver());
        upstreamSegments.add(createStreamObserver());
        upstreamSegments.add(createStreamObserver());

        RegisterMock registerMock = new RegisterMock();
        registerMock.mock(channel);

        TraceSegmentMock segmentMock = new TraceSegmentMock();
        segmentMock.mock(upstreamSegments, new Long[] {System.currentTimeMillis()}, true);

        Thread.sleep(30000);

        Long[] times = TimeBuilder.INSTANCE.generateTimes();
        logger.info("times size: {}", times.length);

//        segmentMock.mock(upstreamSegments, times, false);

        JVMMetricMock jvmMetricMock = new JVMMetricMock();
        jvmMetricMock.mock(channel, times);

        Thread.sleep(60);
    }

    private static StreamObserver<UpstreamSegment> createStreamObserver() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        TraceSegmentServiceGrpc.TraceSegmentServiceStub stub = TraceSegmentServiceGrpc.newStub(channel);
        StreamObserver<UpstreamSegment> segmentStreamObserver = stub.collect(new StreamObserver<Downstream>() {
            @Override public void onNext(Downstream downstream) {
            }

            @Override public void onError(Throwable throwable) {
            }

            @Override public void onCompleted() {
            }
        });

        return segmentStreamObserver;
    }
}
