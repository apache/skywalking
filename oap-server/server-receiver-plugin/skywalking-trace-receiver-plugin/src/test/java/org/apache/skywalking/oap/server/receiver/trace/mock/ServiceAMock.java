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
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.SpanType;
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v2.SpanObjectV2;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

class ServiceAMock {

    static String REST_ENDPOINT = "/dubbox-case/case/dubbox-rest";
    static String DUBBO_ENDPOINT = "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()";
    static String DUBBO_ADDRESS = "DubboIPAddress:1000";
    private final RegisterMock registerMock;
    private static int SERVICE_ID;
    static int SERVICE_INSTANCE_ID;

    ServiceAMock(RegisterMock registerMock) {
        this.registerMock = registerMock;
    }

    void register() throws InterruptedException {
        SERVICE_ID = registerMock.registerService("dubbox-consumer");
        SERVICE_INSTANCE_ID = registerMock.registerServiceInstance(SERVICE_ID, "pengysA");
    }

    void mock(StreamObserver<UpstreamSegment> streamObserver, UniqueId.Builder traceId,
              UniqueId.Builder segmentId, long startTimestamp, boolean isPrepare) {
        UpstreamSegment.Builder upstreamSegment = UpstreamSegment.newBuilder();
        upstreamSegment.addGlobalTraceIds(traceId);
        upstreamSegment.setSegment(createSegment(startTimestamp, segmentId, isPrepare));

        streamObserver.onNext(upstreamSegment.build());
    }

    private ByteString createSegment(long startTimestamp, UniqueId.Builder segmentId, boolean isPrepare) {
        SegmentObject.Builder segment = SegmentObject.newBuilder();
        segment.setTraceSegmentId(segmentId);
        segment.setServiceId(SERVICE_ID);
        segment.setServiceInstanceId(SERVICE_INSTANCE_ID);
        segment.addSpans(createEntrySpan(startTimestamp, isPrepare));
        segment.addSpans(createLocalSpan(startTimestamp, isPrepare));
        segment.addSpans(createExitSpan(startTimestamp, isPrepare));

        return segment.build().toByteString();
    }

    private SpanObjectV2.Builder createEntrySpan(long startTimestamp, boolean isPrepare) {
        SpanObjectV2.Builder span = SpanObjectV2.newBuilder();
        span.setSpanId(0);
        span.setSpanType(SpanType.Entry);
        span.setSpanLayer(SpanLayer.Http);
        span.setParentSpanId(-1);
        span.setStartTime(startTimestamp);
        span.setEndTime(startTimestamp + 6000);
        span.setComponentId(ComponentsDefine.TOMCAT.getId());
        if (isPrepare) {
            span.setOperationName(REST_ENDPOINT);
        } else {
            span.setOperationNameId(2);
        }
        span.setIsError(false);
        return span;
    }

    private SpanObjectV2.Builder createLocalSpan(long startTimestamp, boolean isPrepare) {
        SpanObjectV2.Builder span = SpanObjectV2.newBuilder();
        span.setSpanId(1);
        span.setSpanType(SpanType.Local);
        span.setParentSpanId(0);
        span.setStartTime(startTimestamp + 100);
        span.setEndTime(startTimestamp + 500);
        span.setOperationName("org.apache.skywalking.Local.do");
        span.setIsError(false);
        return span;
    }

    private SpanObjectV2.Builder createExitSpan(long startTimestamp, boolean isPrepare) {
        SpanObjectV2.Builder span = SpanObjectV2.newBuilder();
        span.setSpanId(2);
        span.setSpanType(SpanType.Exit);
        span.setSpanLayer(SpanLayer.RPCFramework);
        span.setParentSpanId(1);
        span.setStartTime(startTimestamp + 120);
        span.setEndTime(startTimestamp + 5800);
        span.setComponentId(ComponentsDefine.DUBBO.getId());
        span.setOperationName(DUBBO_ENDPOINT);
        if (isPrepare) {
            span.setPeer(DUBBO_ADDRESS);
        } else {
            span.setPeerId(2);
        }
        span.setIsError(false);
        return span;
    }
}
