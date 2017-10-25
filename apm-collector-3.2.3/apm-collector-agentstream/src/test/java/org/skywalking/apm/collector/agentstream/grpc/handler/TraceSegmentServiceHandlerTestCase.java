/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.grpc.handler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.Test;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.KeyWithStringValue;
import org.skywalking.apm.network.proto.LogMessage;
import org.skywalking.apm.network.proto.SpanLayer;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.SpanType;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UniqueId;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceSegmentServiceHandlerTestCase {

    private final Logger logger = LoggerFactory.getLogger(TraceSegmentServiceHandlerTestCase.class);

    private TraceSegmentServiceGrpc.TraceSegmentServiceStub stub;

    @Test
    public void testCollect() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        stub = TraceSegmentServiceGrpc.newStub(channel);

        StreamObserver<UpstreamSegment> streamObserver = stub.collect(new StreamObserver<Downstream>() {
            @Override public void onNext(Downstream downstream) {
            }

            @Override public void onError(Throwable throwable) {
                logger.error(throwable.getMessage(), throwable);
            }

            @Override public void onCompleted() {

            }
        });

        UpstreamSegment.Builder builder = UpstreamSegment.newBuilder();
        buildGlobalTraceIds(builder);
        buildSegment(builder);

        streamObserver.onNext(builder.build());
        streamObserver.onCompleted();

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
        }
    }

    private void buildGlobalTraceIds(UpstreamSegment.Builder builder) {
        UniqueId.Builder builder1 = UniqueId.newBuilder();
        builder1.addIdParts(100);
        builder1.addIdParts(100);
        builder1.addIdParts(100);
        builder.addGlobalTraceIds(builder1.build());
    }

    private void buildSegment(UpstreamSegment.Builder builder) {
        long now = System.currentTimeMillis();

        TraceSegmentObject.Builder segmentBuilder = TraceSegmentObject.newBuilder();
        segmentBuilder.setApplicationId(2);
        segmentBuilder.setApplicationInstanceId(2);
        segmentBuilder.setTraceSegmentId(UniqueId.newBuilder().addIdParts(200).addIdParts(200).addIdParts(200).build());

        SpanObject.Builder span0 = SpanObject.newBuilder();
        span0.setSpanId(0);
        span0.setOperationName("/dubbox-case/case/dubbox-rest");
        span0.setOperationNameId(0);
        span0.setParentSpanId(-1);
        span0.setSpanLayer(SpanLayer.Http);
        span0.setStartTime(now);
        span0.setEndTime(now + 100000);
        span0.setComponentId(ComponentsDefine.TOMCAT.getId());
        span0.setIsError(false);
        span0.setSpanType(SpanType.Entry);
        span0.setPeerId(2);
        span0.setPeer("localhost:8082");

        LogMessage.Builder log0 = LogMessage.newBuilder();
        log0.setTime(now);
        log0.addData(KeyWithStringValue.newBuilder().setKey("log1").setValue("value1"));
        log0.addData(KeyWithStringValue.newBuilder().setKey("log2").setValue("value2"));
        log0.addData(KeyWithStringValue.newBuilder().setKey("log3").setValue("value3"));
        span0.addLogs(log0.build());

        span0.addTags(KeyWithStringValue.newBuilder().setKey("tag1").setValue("value1"));
        span0.addTags(KeyWithStringValue.newBuilder().setKey("tag2").setValue("value2"));
        span0.addTags(KeyWithStringValue.newBuilder().setKey("tag3").setValue("value3"));
        segmentBuilder.addSpans(span0);

        TraceSegmentReference.Builder ref0 = TraceSegmentReference.newBuilder();
        ref0.setEntryServiceId(1);
        ref0.setEntryServiceName("ServiceName");
        ref0.setNetworkAddress("localhost:8081");
        ref0.setNetworkAddressId(1);
        ref0.setParentApplicationInstanceId(1);
        ref0.setParentServiceId(1);
        ref0.setParentServiceName("");
        ref0.setParentSpanId(2);
        ref0.setParentTraceSegmentId(UniqueId.newBuilder().addIdParts(100).addIdParts(100).addIdParts(100).build());
//        segmentBuilder.addRefs(ref_0);

        builder.setSegment(segmentBuilder.build().toByteString());
    }
}
