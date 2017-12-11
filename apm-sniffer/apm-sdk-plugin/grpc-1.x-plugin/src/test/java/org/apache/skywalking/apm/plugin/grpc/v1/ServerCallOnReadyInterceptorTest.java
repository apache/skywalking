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


package org.apache.skywalking.apm.plugin.grpc.v1;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.apache.skywalking.apm.agent.test.tools.SegmentRefAssert;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.grpc.v1.vo.GRPCDynamicFields;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ServerCallOnReadyInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    private GRPCDynamicFields cachedObjects;

    @Mock
    private MethodDescriptor.Marshaller requestMarshaller;
    @Mock
    private MethodDescriptor.Marshaller responseMarshaller;

    private ServerCallOnReadyInterceptor serverCallOnReadyInterceptor;

    private ServerCallOnCloseInterceptor serverCallOnCloseInterceptor;

    private ServerCallOnMessageInterceptor serverCallOnMessageInterceptor;

    @Before
    public void setUp() {
        cachedObjects = new GRPCDynamicFields();
        cachedObjects.setDescriptor(MethodDescriptor.create(MethodDescriptor.MethodType.SERVER_STREAMING, "org.skywalking.test.grpc.GreetService/SayHello", requestMarshaller, responseMarshaller));
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(cachedObjects);

        serverCallOnReadyInterceptor = new ServerCallOnReadyInterceptor();
        serverCallOnCloseInterceptor = new ServerCallOnCloseInterceptor();
        serverCallOnMessageInterceptor = new ServerCallOnMessageInterceptor();
    }

    @Test
    public void testOnReadyWithoutContextCarrier() throws Throwable {
        cachedObjects.setMetadata(new Metadata());
        serverCallOnReadyInterceptor.beforeMethod(enhancedInstance, null, null, null, null);
        serverCallOnMessageInterceptor.beforeMethod(enhancedInstance, null, null, null, null);
        serverCallOnMessageInterceptor.afterMethod(enhancedInstance, null, null, null, null);
        serverCallOnCloseInterceptor.afterMethod(enhancedInstance, null, null, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);

        assertThat(segment.getRefs() == null, is(true));

        assertThat(SegmentHelper.getSpans(segment).size(), is(2));
        AbstractTracingSpan abstractTracingSpan = SegmentHelper.getSpans(segment).get(0);
        assertThat(abstractTracingSpan.getOperationName(), is("org.skywalking.test.grpc.GreetService.sayHello/ResponseStreamObserver/OnNext"));

        abstractTracingSpan = SegmentHelper.getSpans(segment).get(1);
        assertThat(abstractTracingSpan.getOperationName(), is("org.skywalking.test.grpc.GreetService.sayHello/StreamCall"));
        assertThat(abstractTracingSpan.isEntry(), is(true));
        assertThat(SpanHelper.getTags(abstractTracingSpan).size(), is(1));
        assertThat(SpanHelper.getTags(abstractTracingSpan).get(0).getKey(), is("onNext.count"));
        assertThat(SpanHelper.getTags(abstractTracingSpan).get(0).getValue(), is("1"));
    }

    @Test
    public void testOnReadyWithContextCarrier() throws Throwable {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("sw3", Metadata.ASCII_STRING_MARSHALLER), "1.234.111|3|1|1|#192.168.1.100:50051|#/portal/|#/testEntrySpan|#AQA*#AQA*Et0We0tQNQA*");
        cachedObjects.setMetadata(metadata);
        serverCallOnReadyInterceptor.beforeMethod(enhancedInstance, null, null, null, null);
        serverCallOnMessageInterceptor.beforeMethod(enhancedInstance, null, null, null, null);
        serverCallOnMessageInterceptor.afterMethod(enhancedInstance, null, null, null, null);
        serverCallOnCloseInterceptor.afterMethod(enhancedInstance, null, null, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);

        assertThat(segment.getRefs() != null, is(true));
        SegmentRefAssert.assertPeerHost(segment.getRefs().get(0), "192.168.1.100:50051");
        SegmentRefAssert.assertEntryApplicationInstanceId(segment.getRefs().get(0), 1);
        SegmentRefAssert.assertSpanId(segment.getRefs().get(0), 3);
        SegmentRefAssert.assertSegmentId(segment.getRefs().get(0), "1.234.111");

        assertThat(SegmentHelper.getSpans(segment).size(), is(2));
        AbstractTracingSpan abstractTracingSpan = SegmentHelper.getSpans(segment).get(0);
        assertThat(abstractTracingSpan.getOperationName(), is("org.skywalking.test.grpc.GreetService.sayHello/ResponseStreamObserver/OnNext"));

        abstractTracingSpan = SegmentHelper.getSpans(segment).get(1);
        assertThat(abstractTracingSpan.getOperationName(), is("org.skywalking.test.grpc.GreetService.sayHello/StreamCall"));
        assertThat(abstractTracingSpan.isEntry(), is(true));
        assertThat(SpanHelper.getTags(abstractTracingSpan).size(), is(1));
        assertThat(SpanHelper.getTags(abstractTracingSpan).get(0).getKey(), is("onNext.count"));
        assertThat(SpanHelper.getTags(abstractTracingSpan).get(0).getValue(), is("1"));
    }
}
