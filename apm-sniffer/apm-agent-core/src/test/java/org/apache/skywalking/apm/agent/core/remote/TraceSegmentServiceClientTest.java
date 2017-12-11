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


package org.apache.skywalking.apm.agent.core.remote;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;

import java.util.ArrayList;
import java.util.List;

import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.core.test.tools.TracingSegmentRunner;
import org.junit.*;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.network.proto.Downstream;
import org.apache.skywalking.apm.network.proto.SpanObject;
import org.apache.skywalking.apm.network.proto.SpanType;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.apache.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.apache.skywalking.apm.network.proto.UpstreamSegment;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.spy;

@RunWith(TracingSegmentRunner.class)
public class TraceSegmentServiceClientTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Rule
    public GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();

    @SegmentStoragePoint
    private SegmentStorage storage;

    private TraceSegmentServiceClient serviceClient = new TraceSegmentServiceClient();
    private List<UpstreamSegment> upstreamSegments;

    private TraceSegmentServiceGrpc.TraceSegmentServiceImplBase serviceImplBase = new TraceSegmentServiceGrpc.TraceSegmentServiceImplBase() {
        @Override
        public StreamObserver<UpstreamSegment> collect(final StreamObserver<Downstream> responseObserver) {
            return new StreamObserver<UpstreamSegment>() {
                @Override
                public void onNext(UpstreamSegment value) {
                    upstreamSegments.add(value);
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(Downstream.getDefaultInstance());
                    responseObserver.onCompleted();
                }
            };
        }
    };

    @BeforeClass
    public static void setUpBeforeClass() {
        RemoteDownstreamConfig.Agent.APPLICATION_ID = 1;
        RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID = 1;
    }

    @AfterClass
    public static void afterClass() {
        ServiceManager.INSTANCE.shutdown();
    }

    @Before
    public void setUp() throws Throwable {
        Whitebox.setInternalState(ServiceManager.INSTANCE.findService(GRPCChannelManager.class), "reconnect", false);
        spy(serviceClient);

        Whitebox.setInternalState(serviceClient, "serviceStub",
                TraceSegmentServiceGrpc.newStub(grpcServerRule.getChannel()));
        Whitebox.setInternalState(serviceClient, "status", GRPCChannelStatus.CONNECTED);

        upstreamSegments = new ArrayList<UpstreamSegment>();
    }

    @Test
    public void testSendTraceSegmentWithoutException() throws InvalidProtocolBufferException {
        grpcServerRule.getServiceRegistry().addService(serviceImplBase);

        AbstractSpan firstEntrySpan = ContextManager.createEntrySpan("/testFirstEntry", null);
        firstEntrySpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(firstEntrySpan, "GET");
        Tags.URL.set(firstEntrySpan, "127.0.0.1:8080");
        SpanLayer.asHttp(firstEntrySpan);
        ContextManager.stopSpan();

        serviceClient.consume(storage.getTraceSegments());

        assertThat(upstreamSegments.size(), is(1));
        UpstreamSegment upstreamSegment = upstreamSegments.get(0);
        assertThat(upstreamSegment.getGlobalTraceIdsCount(), is(1));
        TraceSegmentObject traceSegmentObject = TraceSegmentObject.parseFrom(upstreamSegment.getSegment());
        assertThat(traceSegmentObject.getSpans(0).getRefsCount(), is(0));
        assertThat(traceSegmentObject.getSpansCount(), is(1));

        SpanObject spanObject = traceSegmentObject.getSpans(0);
        assertThat(spanObject.getSpanType(), is(SpanType.Entry));
        assertThat(spanObject.getSpanId(), is(0));
        assertThat(spanObject.getParentSpanId(), is(-1));
    }

    @Test
    public void testSendTraceSegmentWithException() throws InvalidProtocolBufferException {
        grpcServerRule.getServiceRegistry().addService(serviceImplBase);

        AbstractSpan firstEntrySpan = ContextManager.createEntrySpan("/testFirstEntry", null);
        firstEntrySpan.setComponent(ComponentsDefine.TOMCAT);
        Tags.HTTP.METHOD.set(firstEntrySpan, "GET");
        Tags.URL.set(firstEntrySpan, "127.0.0.1:8080");
        SpanLayer.asHttp(firstEntrySpan);
        ContextManager.stopSpan();
        grpcServerRule.getServer().shutdownNow();
        serviceClient.consume(storage.getTraceSegments());

        assertThat(upstreamSegments.size(), is(0));

        boolean reconnect = Whitebox.getInternalState(ServiceManager.INSTANCE.findService(GRPCChannelManager.class), "reconnect");
        assertThat(reconnect, is(true));

    }
}
