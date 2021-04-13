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

package org.apache.skywalking.apm.plugin.pulsar;

import java.util.List;
import org.apache.skywalking.apm.agent.core.context.MockContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentRefAssert;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SendCallbackInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private SendCallbackInterceptor callbackInterceptor;

    private Object[] arguments;
    private Object[] argumentsWithException;
    private Class[] argumentTypes;

    private EnhancedInstance callBackInstance = new EnhancedInstance() {

        @Override
        public Object getSkyWalkingDynamicField() {
            SendCallbackEnhanceRequiredInfo requiredInfo = new SendCallbackEnhanceRequiredInfo();
            requiredInfo.setTopic("persistent://my-tenant/my-ns/my-topic");
            requiredInfo.setContextSnapshot(MockContextSnapshot.INSTANCE.mockContextSnapshot());
            return requiredInfo;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {

        }
    };

    @Before
    public void setUp() {
        callbackInterceptor = new SendCallbackInterceptor();

        arguments = new Object[] {
            null
        };
        argumentsWithException = new Object[] {
            new RuntimeException()
        };

        argumentTypes = new Class[] {
            Exception.class
        };
    }

    @Test
    public void testCallbackWithoutException() throws Throwable {
        callbackInterceptor.beforeMethod(callBackInstance, null, arguments, argumentTypes, null);
        callbackInterceptor.afterMethod(callBackInstance, null, arguments, argumentTypes, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertThat(traceSegments.size(), is(1));
        TraceSegment traceSegment = traceSegments.get(0);

        List<AbstractTracingSpan> abstractSpans = SegmentHelper.getSpans(traceSegment);
        assertThat(abstractSpans.size(), is(1));

        assertCallbackSpan(abstractSpans.get(0));

        assertCallbackSegmentRef(traceSegment.getRef());
    }

    @Test
    public void testCallbackWithException() throws Throwable {
        callbackInterceptor.beforeMethod(callBackInstance, null, argumentsWithException, argumentTypes, null);
        callbackInterceptor.afterMethod(callBackInstance, null, argumentsWithException, argumentTypes, null);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertThat(traceSegments.size(), is(1));
        TraceSegment traceSegment = traceSegments.get(0);

        List<AbstractTracingSpan> abstractSpans = SegmentHelper.getSpans(traceSegment);
        assertThat(abstractSpans.size(), is(1));

        assertCallbackSpanWithException(abstractSpans.get(0));

        assertCallbackSegmentRef(traceSegment.getRef());
    }

    private void assertCallbackSpanWithException(AbstractTracingSpan span) {
        assertCallbackSpan(span);

        SpanAssert.assertException(SpanHelper.getLogs(span).get(0), RuntimeException.class);
        assertThat(SpanHelper.getErrorOccurred(span), is(true));
    }

    private void assertCallbackSegmentRef(TraceSegmentRef traceSegmentRef) {
        Assert.assertNotNull(traceSegmentRef);

        SegmentRefAssert.assertSpanId(traceSegmentRef, 1);
    }

    private void assertCallbackSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("Pulsar/Producer/Callback"));
    }
}
