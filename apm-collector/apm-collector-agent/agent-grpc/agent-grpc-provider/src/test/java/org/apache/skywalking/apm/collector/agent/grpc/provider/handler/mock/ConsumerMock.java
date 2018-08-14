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

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.proto.*;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * @author peng-yongsheng
 */
class ConsumerMock {

    void mock(StreamObserver<UpstreamSegment> segmentStreamObserver, UniqueId.Builder globalTraceId,
        UniqueId.Builder segmentId, long startTimestamp, boolean isPrepare) {
        UpstreamSegment.Builder upstreamSegment = UpstreamSegment.newBuilder();
        upstreamSegment.addGlobalTraceIds(globalTraceId);
        upstreamSegment.setSegment(createSegment(startTimestamp, segmentId, isPrepare));

        segmentStreamObserver.onNext(upstreamSegment.build());
    }

    private ByteString createSegment(long startTimestamp, UniqueId.Builder segmentId, boolean isPrepare) {
        TraceSegmentObject.Builder segment = TraceSegmentObject.newBuilder();
        segment.setTraceSegmentId(segmentId);
        segment.setApplicationId(-1);
        segment.setApplicationInstanceId(2);
        segment.addSpans(createEntrySpan(startTimestamp, isPrepare));
        segment.addSpans(createLocalSpan(startTimestamp, isPrepare));
        segment.addSpans(createMqEntrySpan(startTimestamp, isPrepare));
        segment.addSpans(createExitSpan(startTimestamp, isPrepare));
        segment.addSpans(createMqEntrySpan2(startTimestamp, isPrepare));
        segment.addSpans(createExitSpan2(startTimestamp, isPrepare));

        return segment.build().toByteString();
    }

    private SpanObject.Builder createEntrySpan(long startTimestamp, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(0);
        span.setSpanType(SpanType.Entry);
        span.setSpanLayer(SpanLayer.Http);
        span.setParentSpanId(-1);
        span.setStartTime(startTimestamp);
        span.setEndTime(startTimestamp + 2000);
        span.setComponentId(ComponentsDefine.TOMCAT.getId());
        if (isPrepare) {
            span.setOperationName("/dubbox-case/case/dubbox-rest");
        } else {
            span.setOperationNameId(2);
        }
        span.setIsError(false);
        return span;
    }

    private SpanObject.Builder createLocalSpan(long startTimestamp, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(1);
        span.setSpanType(SpanType.Local);
        span.setParentSpanId(0);
        span.setStartTime(startTimestamp + 100);
        span.setEndTime(startTimestamp + 1900);
        if (isPrepare) {
            span.setOperationName("org.apache.skywalking.Local.do");
        } else {
            span.setOperationNameId(2);
        }
        span.setIsError(false);
        return span;
    }

    private SpanObject.Builder createMqEntrySpan(long startTimestamp, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(2);
        span.setSpanType(SpanType.Entry);
        span.setSpanLayer(SpanLayer.MQ);
        span.setParentSpanId(1);
        span.setStartTime(startTimestamp + 110);
        span.setEndTime(startTimestamp + 1800);
        span.setComponentId(ComponentsDefine.ROCKET_MQ_CONSUMER.getId());
        if (isPrepare) {
            span.setOperationName("org.apache.skywalking.RocketMQ");
        } else {
            span.setOperationNameId(2);
        }
        span.setIsError(false);
        return span;
    }

    private SpanObject.Builder createExitSpan(long startTimestamp, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(3);
        span.setSpanType(SpanType.Exit);
        span.setSpanLayer(SpanLayer.RPCFramework);
        span.setParentSpanId(2);
        span.setStartTime(startTimestamp + 120);
        span.setEndTime(startTimestamp + 1780);
        span.setComponentId(ComponentsDefine.DUBBO.getId());
        if (isPrepare) {
            span.setPeer("172.25.0.4:20880");
            span.setOperationName("org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
        } else {
            span.setOperationNameId(-1);
            span.setPeerId(-1);
        }
        span.setIsError(false);
        return span;
    }

    private SpanObject.Builder createMqEntrySpan2(long startTimestamp, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(4);
        span.setSpanType(SpanType.Entry);
        span.setSpanLayer(SpanLayer.MQ);
        span.setParentSpanId(1);
        span.setStartTime(startTimestamp + 110);
        span.setEndTime(startTimestamp + 1800);
        span.setComponentId(ComponentsDefine.ROCKET_MQ_CONSUMER.getId());
        if (isPrepare) {
            span.setOperationName("org.apache.skywalking.RocketMQ");
        } else {
            span.setOperationNameId(2);
        }
        span.setIsError(false);
        return span;
    }

    private SpanObject.Builder createExitSpan2(long startTimestamp, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(5);
        span.setSpanType(SpanType.Exit);
        span.setSpanLayer(SpanLayer.RPCFramework);
        span.setParentSpanId(4);
        span.setStartTime(startTimestamp + 120);
        span.setEndTime(startTimestamp + 1780);
        span.setComponentId(ComponentsDefine.DUBBO.getId());
        if (isPrepare) {
            span.setPeer("172.25.0.4:20880");
            span.setOperationName("org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
        } else {
            span.setOperationNameId(-1);
            span.setPeerId(-1);
        }
        span.setIsError(false);
        return span;
    }
}
