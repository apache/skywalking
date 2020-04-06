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

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

class ServiceCMock {
    public static String SERVICE_NAME = "mock_c_service";
    public static String SERVICE_INSTANCE_NAME = "mock_c_service_instance";

    void mock(StreamObserver<SegmentObject> streamObserver, String traceId,
              String segmentId, String parentSegmentId, long startTimestamp) {
        streamObserver.onNext(createSegment(startTimestamp, traceId, segmentId, parentSegmentId).build());
    }

    private SegmentObject.Builder createSegment(long startTimestamp,
                                                String traceId,
                                                String segmentId,
                                                String parentSegmentId) {
        SegmentObject.Builder segment = SegmentObject.newBuilder();
        segment.setTraceId(traceId);
        segment.setTraceSegmentId(segmentId);
        segment.setService(SERVICE_NAME);
        segment.setServiceInstance(SERVICE_INSTANCE_NAME);
        segment.addSpans(createEntrySpan(startTimestamp, traceId, parentSegmentId));

        return segment;
    }

    private SpanObject.Builder createEntrySpan(long startTimestamp, String traceId, String parentSegmentId) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(0);
        span.setSpanType(SpanType.Entry);
        span.setSpanLayer(SpanLayer.MQ);
        span.setParentSpanId(-1);
        span.setStartTime(startTimestamp + 3000);
        span.setEndTime(startTimestamp + 5000);
        span.setComponentId(ComponentsDefine.ROCKET_MQ_CONSUMER.getId());
        span.setIsError(false);
        span.addRefs(createReference(traceId, parentSegmentId));
        span.setOperationName(ServiceBMock.ROCKET_MQ_ENDPOINT);
        return span;
    }

    private SegmentReference.Builder createReference(String traceId, String parentTraceSegmentId) {
        SegmentReference.Builder reference = SegmentReference.newBuilder();
        reference.setTraceId(traceId);
        reference.setParentTraceSegmentId(parentTraceSegmentId);
        reference.setParentService(ServiceBMock.SERVICE_NAME);
        reference.setParentServiceInstance(ServiceBMock.SERVICE_INSTANCE_NAME);
        reference.setParentSpanId(2);
        reference.setRefType(RefType.CrossProcess);
        reference.setNetworkAddressUsedAtPeer(ServiceBMock.ROCKET_MQ_ADDRESS);
        reference.setParentEndpoint(ServiceBMock.DUBBO_PROVIDER_ENDPOINT);
        return reference;
    }
}
