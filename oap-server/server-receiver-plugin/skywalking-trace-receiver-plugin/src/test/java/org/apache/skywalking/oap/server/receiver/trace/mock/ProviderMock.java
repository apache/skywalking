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
class ProviderMock {

    void mock(StreamObserver<UpstreamSegment> streamObserver, UniqueId.Builder globalTraceId,
        UniqueId.Builder segmentId, UniqueId.Builder parentTraceSegmentId, long startTimestamp, boolean isPrepare) {
        UpstreamSegment.Builder upstreamSegment = UpstreamSegment.newBuilder();
        upstreamSegment.addGlobalTraceIds(globalTraceId);
        upstreamSegment.setSegment(createSegment(startTimestamp, segmentId, parentTraceSegmentId, isPrepare));

        streamObserver.onNext(upstreamSegment.build());
    }

    private ByteString createSegment(long startTimestamp, UniqueId.Builder segmentId,
        UniqueId.Builder parentTraceSegmentId, boolean isPrepare) {
        TraceSegmentObject.Builder segment = TraceSegmentObject.newBuilder();
        segment.setTraceSegmentId(segmentId);
        segment.setApplicationId(2);
        segment.setApplicationInstanceId(2);
        segment.addSpans(createExitSpan(startTimestamp, isPrepare));
        segment.addSpans(createEntrySpan(startTimestamp, parentTraceSegmentId, isPrepare));

        return segment.build().toByteString();
    }

    private TraceSegmentReference.Builder createReference(UniqueId.Builder parentTraceSegmentId, boolean isPrepare) {
        TraceSegmentReference.Builder reference = TraceSegmentReference.newBuilder();
        reference.setParentTraceSegmentId(parentTraceSegmentId);
        reference.setParentApplicationInstanceId(1);
        reference.setParentSpanId(1);
        reference.setEntryApplicationInstanceId(1);
        reference.setRefType(RefType.CrossProcess);

        if (isPrepare) {
            reference.setParentServiceName("/dubbox-case/case/dubbox-rest");
            reference.setNetworkAddress("172.25.0.4:20880");
            reference.setEntryServiceName("/dubbox-case/case/dubbox-rest");
        } else {
            reference.setParentServiceId(1);
            reference.setNetworkAddressId(2);
            reference.setEntryServiceId(1);
        }
        return reference;
    }

    private SpanObject.Builder createExitSpan(long startTimestamp, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(1);
        span.setSpanType(SpanType.Exit);
        span.setSpanLayer(SpanLayer.Database);
        span.setParentSpanId(0);
        span.setStartTime(startTimestamp + 510);
        span.setEndTime(startTimestamp + 1490);
        span.setComponentId(ComponentsDefine.MONGO_DRIVER.getId());
        span.setIsError(true);

        if (isPrepare) {
            span.setOperationName("mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]");
            span.setPeer("localhost:27017");
        } else {
            span.setOperationNameId(5);
            span.setPeerId(1);
        }
        return span;
    }

    private SpanObject.Builder createEntrySpan(long startTimestamp, UniqueId.Builder uniqueId, boolean isPrepare) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(0);
        span.setSpanType(SpanType.Entry);
        span.setSpanLayer(SpanLayer.RPCFramework);
        span.setParentSpanId(-1);
        span.setStartTime(startTimestamp + 500);
        span.setEndTime(startTimestamp + 1500);
        span.setComponentId(ComponentsDefine.DUBBO.getId());
        span.setIsError(false);
        span.addRefs(createReference(uniqueId, isPrepare));

        if (isPrepare) {
            span.setOperationName("org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
        } else {
            span.setOperationNameId(6);
        }
        return span;
    }
}
