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
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.core.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.apm.network.language.agent.v3.TraceSegmentReportServiceGrpc;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

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
    private List<SegmentObject> upstreamSegments;

    private TraceSegmentReportServiceGrpc.TraceSegmentReportServiceImplBase serviceImplBase = new TraceSegmentReportServiceGrpc.TraceSegmentReportServiceImplBase() {
        @Override
        public StreamObserver<SegmentObject> collect(final StreamObserver<Commands> responseObserver) {
            return new StreamObserver<SegmentObject>() {
                @Override
                public void onNext(SegmentObject value) {
                    upstreamSegments.add(value);
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(Commands.getDefaultInstance());
                    responseObserver.onCompleted();
                }
            };
        }
    };

    @BeforeClass
    public static void beforeClass() {
        Config.Agent.KEEP_TRACING = true;
    }

    @AfterClass
    public static void afterClass() {
        Config.Agent.KEEP_TRACING = false;
        ServiceManager.INSTANCE.shutdown();
    }

    @Before
    public void setUp() throws Throwable {
        Whitebox.setInternalState(ServiceManager.INSTANCE.findService(GRPCChannelManager.class), "reconnect", false);
        spy(serviceClient);

        Whitebox.setInternalState(
            serviceClient, "serviceStub", TraceSegmentReportServiceGrpc.newStub(grpcServerRule.getChannel()));
        Whitebox.setInternalState(serviceClient, "status", GRPCChannelStatus.CONNECTED);

        upstreamSegments = new ArrayList<>();
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
        SegmentObject traceSegmentObject = upstreamSegments.get(0);
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

        boolean reconnect = Whitebox.getInternalState(
            ServiceManager.INSTANCE.findService(GRPCChannelManager.class), "reconnect");
        assertThat(reconnect, is(true));

    }
}
