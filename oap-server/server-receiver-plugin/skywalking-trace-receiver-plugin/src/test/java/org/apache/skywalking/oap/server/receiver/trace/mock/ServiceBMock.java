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

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.language.agent.*;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * @author peng-yongsheng
 */
class ServiceBMock {

    private final RegisterMock registerMock;
    private static int SERVICE_ID;
    static int SERVICE_INSTANCE_ID;
    static String DUBBO_PROVIDER_ENDPOINT = "org.skywaking.apm.testcase.dubbo.services.GreetServiceImpl.doBusiness()";
    static String ROCKET_MQ_ENDPOINT = "org.apache.skywalking.RocketMQ";
    static String ROCKET_MQ_ADDRESS = "RocketMQAddress:2000";

    ServiceBMock(RegisterMock registerMock) {
        this.registerMock = registerMock;
    }

    void register() throws InterruptedException {
        SERVICE_ID = registerMock.registerService("dubbox-provider");
        SERVICE_INSTANCE_ID = registerMock.registerServiceInstance(SERVICE_ID, "pengysB");
    }

    void mock(StreamObserver<UpstreamSegment> streamObserver, UniqueId.Builder traceId,
        UniqueId.Builder segmentId, UniqueId.Builder parentTraceSegmentId, long startTimestamp, boolean isPrepare) {
        UpstreamSegment.Builder upstreamSegment = UpstreamSegment.newBuilder();
        upstreamSegment.addGlobalTraceIds(traceId);
        upstreamSegment.setSegment(createSegment(startTimestamp, segmentId, parentTraceSegmentId, isPrepare));

        streamObserver.onNext(upstreamSegment.build());
    }

    private ByteString createSegment(long startTimestamp, UniqueId.Builder segmentId,
        UniqueId.Builder parentTraceSegmentId, boolean isPrepare) {
        TraceSegmentObject.Builder segment = TraceSegmentObject.newBuilder();
        segment.setTraceSegmentId(segmentId);
        segment.setApplicationId(SERVICE_ID);
        segment.setApplicationInstanceId(SERVICE_INSTANCE_ID);
        segment.addSpans(createEntrySpan(startTimestamp, parentTraceSegmentId, isPrepare));
        segment.addSpans(createExitSpan(startTimestamp, isPrepare));
        segment.addSpans(createMQExitSpan(startTimestamp, isPrepare));

        return segment.build().toByteString();
    }

    private TraceSegmentReference.Builder createReference(UniqueId.Builder parentTraceSegmentId, boolean isPrepare) {
        TraceSegmentReference.Builder reference = TraceSegmentReference.newBuilder();
        reference.setParentTraceSegmentId(parentTraceSegmentId);
        reference.setParentApplicationInstanceId(ServiceAMock.SERVICE_INSTANCE_ID);
        reference.setParentSpanId(2);
        reference.setEntryApplicationInstanceId(ServiceAMock.SERVICE_INSTANCE_ID);
        reference.setRefType(RefType.CrossProcess);

        if (isPrepare) {
            reference.setParentServiceName(ServiceAMock.REST_ENDPOINT);
            reference.setNetworkAddress(ServiceAMock.DUBBO_ADDRESS);
            reference.setEntryServiceName(ServiceAMock.REST_ENDPOINT);
        } else {
            reference.setParentServiceId(2);
            reference.setNetworkAddressId(2);
            reference.setEntryServiceId(2);
        }
        return reference;
    }

    private SpanObject.Builder createEntrySpan(long startTimestamp, UniqueId.Builder uniqueId, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(0);
        span.setSpanType(SpanType.Entry);
        span.setSpanLayer(SpanLayer.RPCFramework);
        span.setParentSpanId(-1);
        span.setStartTime(startTimestamp + 500);
        span.setEndTime(startTimestamp + 5000);
        span.setComponentId(ComponentsDefine.DUBBO.getId());
        span.setIsError(false);
        span.addRefs(createReference(uniqueId, isPrepare));

        if (isPrepare) {
            span.setOperationName(ServiceBMock.DUBBO_PROVIDER_ENDPOINT);
        } else {
            span.setOperationNameId(4);
        }
        return span;
    }

    private SpanObject.Builder createExitSpan(long startTimestamp, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(1);
        span.setSpanType(SpanType.Exit);
        span.setSpanLayer(SpanLayer.Database);
        span.setParentSpanId(0);
        span.setStartTime(startTimestamp + 550);
        span.setEndTime(startTimestamp + 1000);
        span.setComponentId(ComponentsDefine.MONGO_DRIVER.getId());
        span.setIsError(true);

        if (isPrepare) {
            span.setOperationName("mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]");
            span.setPeer("localhost:27017");
        } else {
            span.setOperationNameId(7);
            span.setPeerId(3);
        }
        return span;
    }

    private SpanObject.Builder createMQExitSpan(long startTimestamp, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(2);
        span.setSpanType(SpanType.Exit);
        span.setSpanLayer(SpanLayer.MQ);
        span.setParentSpanId(1);
        span.setStartTime(startTimestamp + 1100);
        span.setEndTime(startTimestamp + 1500);
        span.setComponentId(ComponentsDefine.ROCKET_MQ_PRODUCER.getId());
        span.setIsError(false);

        if (isPrepare) {
            span.setOperationName(ROCKET_MQ_ENDPOINT);
            span.setPeer(ROCKET_MQ_ADDRESS);
        } else {
            span.setOperationNameId(8);
            span.setPeerId(4);
        }
        return span;
    }
}
