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
import org.skywalking.apm.network.proto.RefType;
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
public class SingleHasEntryNoExitHasRefSpan {

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

            int consumerApplicationId = 0;
            int providerApplicationId = 0;
            int consumerInstanceId = 0;
            int providerInstanceId = 0;
            int consumerEntryServiceId = 0;
            int consumerExitServiceId = 0;
            int consumerExitApplicationId = 0;
            int providerEntryServiceId = 0;

            while (consumerApplicationId == 0) {
                consumerApplicationId = ApplicationRegister.register(channel, "consumer");
            }
            while (consumerExitApplicationId == 0) {
                consumerExitApplicationId = ApplicationRegister.register(channel, "172.25.0.4:20880");
            }
            while (providerApplicationId == 0) {
                providerApplicationId = ApplicationRegister.register(channel, "provider");
            }
            while (consumerInstanceId == 0) {
                consumerInstanceId = InstanceRegister.register(channel, "ConsumerUUID", consumerApplicationId, "consumer_host_name", 1);
            }
            while (providerInstanceId == 0) {
                providerInstanceId = InstanceRegister.register(channel, "ProviderUUID", providerApplicationId, "provider_host_name", 2);
            }
            while (consumerEntryServiceId == 0) {
                consumerEntryServiceId = ServiceRegister.register(channel, consumerApplicationId, "/dubbox-case/case/dubbox-rest");
            }
            while (consumerExitServiceId == 0) {
                consumerExitServiceId = ServiceRegister.register(channel, consumerApplicationId, "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
            }
            while (providerEntryServiceId == 0) {
                providerEntryServiceId = ServiceRegister.register(channel, providerApplicationId, "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
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
            segmentBuilder.setApplicationId(consumerApplicationId);
            segmentBuilder.setApplicationInstanceId(consumerInstanceId);
            segmentBuilder.setTraceSegmentId(segmentId);

            TraceSegmentReference.Builder referenceBuilder = TraceSegmentReference.newBuilder();
            referenceBuilder.setEntryApplicationInstanceId(providerInstanceId);
            referenceBuilder.setEntryServiceName("/rest/test");
            referenceBuilder.setParentApplicationInstanceId(providerInstanceId);
            referenceBuilder.setParentServiceName("/rest/test");
            referenceBuilder.setRefType(RefType.CrossProcess);
            referenceBuilder.setNetworkAddress("localhost:8080");
            segmentBuilder.addRefs(referenceBuilder);

            SpanObject.Builder entrySpan = SpanObject.newBuilder();
            entrySpan.setSpanId(0);
            entrySpan.setSpanType(SpanType.Entry);
            entrySpan.setSpanLayer(SpanLayer.Http);
            entrySpan.setParentSpanId(-1);
            entrySpan.setStartTime(now);
            entrySpan.setEndTime(now + 3000);
            entrySpan.setComponentId(ComponentsDefine.TOMCAT.getId());
            entrySpan.setOperationNameId(consumerEntryServiceId);
            entrySpan.setIsError(false);
            segmentBuilder.addSpans(entrySpan);

            upstream.setSegment(segmentBuilder.build().toByteString());

            streamObserver.onNext(upstream.build());
            streamObserver.onCompleted();
        }
    }
}
