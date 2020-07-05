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
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

class ServiceBMock {
    public static String SERVICE_NAME = "mock_b_service";
    public static String SERVICE_INSTANCE_NAME = "mock_b_service_instance";

    static String DUBBO_PROVIDER_ENDPOINT = "org.skywaking.apm.testcase.dubbo.services.GreetServiceImpl.doBusiness()";
    static String ROCKET_MQ_ENDPOINT = "org.apache.skywalking.RocketMQ";
    static String ROCKET_MQ_ADDRESS = "RocketMQAddress:2000";

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
        segment.addSpans(createExitSpan(startTimestamp));
        segment.addSpans(createMQExitSpan(startTimestamp));

        return segment;
    }

    private SegmentReference.Builder createReference(String traceId, String parentTraceSegmentId) {
        SegmentReference.Builder reference = SegmentReference.newBuilder();
        reference.setTraceId(traceId);
        reference.setParentTraceSegmentId(parentTraceSegmentId);
        reference.setParentService(ServiceAMock.SERVICE_NAME);
        reference.setParentServiceInstance(ServiceAMock.SERVICE_INSTANCE_NAME);
        reference.setParentSpanId(2);
        reference.setParentEndpoint(ServiceAMock.REST_ENDPOINT);
        reference.setRefType(RefType.CrossProcess);
        reference.setNetworkAddressUsedAtPeer(ServiceAMock.DUBBO_ADDRESS);

        return reference;
    }

    private SpanObject.Builder createEntrySpan(long startTimestamp, String traceId, String parentSegmentId) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(0);
        span.setSpanType(SpanType.Entry);
        span.setSpanLayer(SpanLayer.RPCFramework);
        span.setParentSpanId(-1);
        span.setStartTime(startTimestamp + 500);
        span.setEndTime(startTimestamp + 5000);
        span.setComponentId(ComponentsDefine.DUBBO.getId());
        span.setIsError(false);
        span.addRefs(createReference(traceId, parentSegmentId));

        span.setOperationName(ServiceBMock.DUBBO_PROVIDER_ENDPOINT);
        return span;
    }

    private SpanObject.Builder createExitSpan(long startTimestamp) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(1);
        span.setSpanType(SpanType.Exit);
        span.setSpanLayer(SpanLayer.Database);
        span.setParentSpanId(0);
        span.setStartTime(startTimestamp + 550);
        span.setEndTime(startTimestamp + 1500);
        span.setComponentId(ComponentsDefine.MONGO_DRIVER.getId());
        span.setIsError(true);
        span.addTags(KeyStringValuePair.newBuilder()
                                       .setKey("db.statement")
                                       .setValue("select * from database where complex = 1;")
                                       .build());
        span.addTags(KeyStringValuePair.newBuilder().setKey("db.type").setValue("mongodb").build());

        span.setOperationName(
            "mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]");
        span.setPeer("localhost:27017");
        return span;
    }

    private SpanObject.Builder createMQExitSpan(long startTimestamp) {
        SpanObject.Builder span = SpanObject.newBuilder();
        span.setSpanId(2);
        span.setSpanType(SpanType.Exit);
        span.setSpanLayer(SpanLayer.MQ);
        span.setParentSpanId(1);
        span.setStartTime(startTimestamp + 1100);
        span.setEndTime(startTimestamp + 1500);
        span.setComponentId(ComponentsDefine.ROCKET_MQ_PRODUCER.getId());
        span.setIsError(false);

        span.setOperationName(ROCKET_MQ_ENDPOINT);
        span.setPeer(ROCKET_MQ_ADDRESS);
        return span;
    }
}
