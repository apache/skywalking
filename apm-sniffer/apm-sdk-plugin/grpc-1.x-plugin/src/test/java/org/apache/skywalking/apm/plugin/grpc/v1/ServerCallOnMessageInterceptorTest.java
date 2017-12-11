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

import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.grpc.v1.vo.GRPCDynamicFields;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ServerCallOnMessageInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance clientCall;

    @Mock
    private GRPCDynamicFields cachedObjects;

    private ServerCallOnMessageInterceptor serverCallOnMessageInterceptor;

    @Before
    public void setUp() {
        when(cachedObjects.getRequestMethodName()).thenReturn("org.skywalking.test.grpc.GreetService.sayHello");
        when(clientCall.getSkyWalkingDynamicField()).thenReturn(cachedObjects);

        serverCallOnMessageInterceptor = new ServerCallOnMessageInterceptor();
    }

    @Test
    public void testCallOnNext() throws Throwable {
        serverCallOnMessageInterceptor.beforeMethod(clientCall, null, null, null, null);
        serverCallOnMessageInterceptor.afterMethod(clientCall, null, null, null, null);

        verify(cachedObjects, times(1)).incrementOnNextCount();

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        assertThat(SegmentHelper.getSpans(traceSegment).size(), is(1));
        AbstractTracingSpan span = SegmentHelper.getSpans(traceSegment).get(0);
        assertThat(span.getOperationName(), is("org.skywalking.test.grpc.GreetService.sayHello/ResponseStreamObserver/OnNext"));
        assertThat(span.isEntry(), is(false));
        assertThat(span.isExit(), is(false));
    }
}
