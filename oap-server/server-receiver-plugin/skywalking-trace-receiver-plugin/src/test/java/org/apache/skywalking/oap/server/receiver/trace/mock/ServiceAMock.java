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
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

class ServiceAMock {
    public static String SERVICE_NAME = "mock_a_service";
    public static String SERVICE_INSTANCE_NAME = "mock_a_service_instance";

    static String REST_ENDPOINT = "/dubbox-case/case/dubbox-rest/404-test";
    static String DUBBO_ENDPOINT = "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()";
    static String DUBBO_ADDRESS = "DubboIPAddress:1000";

    void mock(StreamObserver<SegmentObject> streamObserver, String traceId,
              String segmentId, long startTimestamp) {
        streamObserver.onNext(createSegment(startTimestamp, traceId, segmentId).build());
    }

    private SegmentObject.Builder createSegment(long startTimestamp, String traceId, String segmentId) {
        SegmentObject.Builder segment = SegmentObject.newBuilder();
        segment.setTraceId(traceId);
        segment.setTraceSegmentId(segmentId);
        segment.setService(SERVICE_NAME);
        segment.setServiceInstance(SERVICE_INSTANCE_NAME);
        segment.addSpans(createEntrySpan(startTimestamp));
        segment.addSpans(createLocalSpan(startTimestamp));
        segment.addSpans(createExitSpan(startTimestamp));

        return segment;
    }

    private SpanObject.Builder createEntrySpan(long startTimestamp) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(0);
        span.setSpanType(SpanType.Entry);
        span.setSpanLayer(SpanLayer.Http);
        span.setParentSpanId(-1);
        span.setStartTime(startTimestamp);
        span.setEndTime(startTimestamp + 6000);
        span.setComponentId(ComponentsDefine.TOMCAT.getId());
        span.setOperationName(REST_ENDPOINT);
        span.setIsError(false);
        span.addTags(KeyStringValuePair.newBuilder().setKey("http.method").setValue("get").build());
        span.addTags(KeyStringValuePair.newBuilder().setKey("status_code").setValue("404").build());
        span.addTags(KeyStringValuePair.newBuilder().setKey("status_code").setValue("200").build());
        return span;
    }

    private SpanObject.Builder createLocalSpan(long startTimestamp) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(1);
        span.setSpanType(SpanType.Local);
        span.setParentSpanId(0);
        span.setStartTime(startTimestamp + 100);
        span.setEndTime(startTimestamp + 500);
        span.setOperationName("org.apache.skywalking.Local.do");
        span.setIsError(false);
        return span;
    }

    private SpanObject.Builder createExitSpan(long startTimestamp) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(2);
        span.setSpanType(SpanType.Exit);
        span.setSpanLayer(SpanLayer.RPCFramework);
        span.setParentSpanId(1);
        span.setStartTime(startTimestamp + 120);
        span.setEndTime(startTimestamp + 5800);
        span.setComponentId(ComponentsDefine.DUBBO.getId());
        span.setOperationName(DUBBO_ENDPOINT);
        span.setPeer(DUBBO_ADDRESS);
        span.setIsError(false);
        return span;
    }
}
