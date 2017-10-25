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

package org.skywalking.apm.collector.agentstream.mock.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.SpanLayer;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.SpanType;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UniqueId;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SingleNoEntryHasExitNoRefSpan {

    public static void main(String[] args) {
        Post post = new Post();
        post.send();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }
    }

    static class Post {
        private final Logger logger = LoggerFactory.getLogger(Post.class);

        public void send() {
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).maxInboundMessageSize(1024 * 1024 * 50).usePlaintext(true).build();

            int applicationId = 0;
            int instanceId = 0;
            int entryServiceId = 0;
            while (applicationId == 0) {
                applicationId = ApplicationRegister.register(channel, "consumer");
            }

            while (instanceId == 0) {
                instanceId = InstanceRegister.register(channel, "ConsumerUUID", applicationId, "consumer_host_name", 1);
            }

            while (entryServiceId == 0) {
                entryServiceId = ServiceRegister.register(channel, applicationId, "/dubbox-case/case/dubbox-rest");
            }

            TraceSegmentServiceGrpc.TraceSegmentServiceStub stub = TraceSegmentServiceGrpc.newStub(channel);
            StreamObserver<UpstreamSegment> streamObserver = stub.collect(new StreamObserver<Downstream>() {
                @Override public void onNext(Downstream downstream) {
                }

                @Override public void onError(Throwable throwable) {
                    logger.error(throwable.getMessage(), throwable);
                }

                @Override public void onCompleted() {
                }
            });

            long now = System.currentTimeMillis();

            int id = 1;
            UniqueId.Builder builder = UniqueId.newBuilder();
            builder.addIdParts(id);
            builder.addIdParts(id);
            builder.addIdParts(id);
            UniqueId segmentId = builder.build();

            UpstreamSegment.Builder upstream = UpstreamSegment.newBuilder();
            upstream.addGlobalTraceIds(segmentId);

            TraceSegmentObject.Builder segmentBuilder = TraceSegmentObject.newBuilder();
            segmentBuilder.setApplicationId(applicationId);
            segmentBuilder.setApplicationInstanceId(instanceId);
            segmentBuilder.setTraceSegmentId(segmentId);

            SpanObject.Builder exitSpan = SpanObject.newBuilder();
            exitSpan.setSpanId(0);
            exitSpan.setSpanType(SpanType.Exit);
            exitSpan.setSpanLayer(SpanLayer.Database);
            exitSpan.setParentSpanId(-1);
            exitSpan.setStartTime(now);
            exitSpan.setEndTime(now + 3000);
            exitSpan.setComponentId(ComponentsDefine.MONGODB.getId());
            exitSpan.setOperationNameId(entryServiceId);
            exitSpan.setIsError(false);
            exitSpan.setPeer("localhost:8888");
            segmentBuilder.addSpans(exitSpan);

            upstream.setSegment(segmentBuilder.build().toByteString());

            streamObserver.onNext(upstream.build());
            streamObserver.onCompleted();
        }
    }
}
