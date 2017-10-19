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

package org.skywalking.apm.plugin.grpc.v1;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.test.helper.SegmentHelper;
import org.skywalking.apm.agent.test.helper.SpanHelper;
import org.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.test.tools.SegmentStorage;
import org.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.test.tools.SpanAssert;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.skywalking.apm.plugin.grpc.v1.vo.GRPCDynamicFields;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ClientCallStartInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private ClientCallStartInterceptor clientCallStartInterceptor;
    private UnaryClientOnCloseInterceptor unaryClientOnCloseInterceptor;

    @Mock
    private EnhancedInstance clientCallImpl;

    @Mock
    private EnhancedInstance clientCallListener;

    @Mock
    private GRPCDynamicFields unaryCachedObjects;

    @Mock
    private GRPCDynamicFields streamCachedObjects;

    private Status exceptionStatus = Status.NOT_FOUND.withCause(new RuntimeException());

    private Object[] arguments;
    private Class[] argumentTypes;

    @Before
    public void setUp() {
        when(unaryCachedObjects.getRequestMethodName()).thenReturn("org.skywalking.test.grpc.GreetService.sayHello");
        when(unaryCachedObjects.getAuthority()).thenReturn("localhost:500051");
        when(unaryCachedObjects.getMethodType()).thenReturn(MethodDescriptor.MethodType.UNARY);

        when(streamCachedObjects.getRequestMethodName()).thenReturn("org.skywalking.test.grpc.GreetService.sayHello");
        when(streamCachedObjects.getAuthority()).thenReturn("localhost:500051");
        when(streamCachedObjects.getMethodType()).thenReturn(MethodDescriptor.MethodType.SERVER_STREAMING);

        arguments = new Object[] {clientCallListener, new Metadata()};
        argumentTypes = new Class[] {clientCallListener.getClass(), Metadata.class};

        clientCallStartInterceptor = new ClientCallStartInterceptor();
        unaryClientOnCloseInterceptor = new UnaryClientOnCloseInterceptor();
    }

    @Test
    public void testNormalUnaryCallStart() throws Throwable {
        when(clientCallImpl.getSkyWalkingDynamicField()).thenReturn(unaryCachedObjects);

        clientCallStartInterceptor.beforeMethod(clientCallImpl, null, arguments, argumentTypes, null);
        clientCallStartInterceptor.afterMethod(clientCallImpl, null, arguments, argumentTypes, null);
        unaryClientOnCloseInterceptor.afterMethod(null, null, new Object[] {Status.OK, new Metadata()}, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        assertThat(SegmentHelper.getSpans(traceSegment).size(), is(1));
        AbstractTracingSpan abstractTracingSpan = SegmentHelper.getSpans(traceSegment).get(0);
        SpanAssert.assertComponent(abstractTracingSpan, ComponentsDefine.GRPC);
        SpanAssert.assertLayer(abstractTracingSpan, SpanLayer.RPC_FRAMEWORK);
        SpanAssert.assertOccurException(abstractTracingSpan, false);
    }

    @Test
    public void testUnaryCallStartWithException() throws Throwable {
        when(clientCallImpl.getSkyWalkingDynamicField()).thenReturn(unaryCachedObjects);

        clientCallStartInterceptor.beforeMethod(clientCallImpl, null, arguments, argumentTypes, null);
        clientCallStartInterceptor.afterMethod(clientCallImpl, null, arguments, argumentTypes, null);
        unaryClientOnCloseInterceptor.afterMethod(null, null, new Object[] {exceptionStatus, new Metadata()}, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        assertThat(SegmentHelper.getSpans(traceSegment).size(), is(1));
        AbstractTracingSpan abstractTracingSpan = SegmentHelper.getSpans(traceSegment).get(0);
        SpanAssert.assertComponent(abstractTracingSpan, ComponentsDefine.GRPC);
        SpanAssert.assertLayer(abstractTracingSpan, SpanLayer.RPC_FRAMEWORK);
        SpanAssert.assertOccurException(abstractTracingSpan, true);
        SpanAssert.assertException(SpanHelper.getLogs(abstractTracingSpan).get(0), StatusRuntimeException.class, "NOT_FOUND");
    }

    @Test
    public void testNormalStreamCallStart() throws Throwable {
        when(clientCallImpl.getSkyWalkingDynamicField()).thenReturn(streamCachedObjects);

        clientCallStartInterceptor.beforeMethod(clientCallImpl, null, arguments, argumentTypes, null);
        clientCallStartInterceptor.afterMethod(clientCallImpl, null, arguments, argumentTypes, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        assertThat(SegmentHelper.getSpans(traceSegment).size(), is(1));
        AbstractTracingSpan abstractTracingSpan = SegmentHelper.getSpans(traceSegment).get(0);
        SpanAssert.assertComponent(abstractTracingSpan, ComponentsDefine.GRPC);
        SpanAssert.assertLayer(abstractTracingSpan, SpanLayer.RPC_FRAMEWORK);
        SpanAssert.assertOccurException(abstractTracingSpan, false);
    }

}
