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

package org.apache.skywalking.apm.plugin.kafka;

import java.util.List;
import org.apache.kafka.clients.producer.RecordMetadata;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest({RecordMetadata.class})
public class CallbackInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private RecordMetadata recordMetadata;

    private CallbackInterceptor callbackInterceptor;

    private Object[] arguments;
    private Object[] argumentsWithException;
    private Class[] argumentTypes;

    private EnhancedInstance callBackInstance;

    private static class CallbackInstance implements EnhancedInstance {
        private CallbackCache cache;

        public CallbackInstance(CallbackCache cache) {
            this.cache = cache;
        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return cache;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
        }
    }

    @Before
    public void setUp() {
        callbackInterceptor = new CallbackInterceptor();

        CallbackCache cache = new CallbackCache();
        cache.setSnapshot(MockContextSnapshot.INSTANCE.mockContextSnapshot());
        callBackInstance = new CallbackInstance(cache);

        arguments = new Object[] {
            recordMetadata,
            null
        };
        argumentsWithException = new Object[] {
            recordMetadata,
            new RuntimeException()
        };

        argumentTypes = new Class[] {
            RecordMetadata.class,
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

        assertCallbackSegmentRef(traceSegment.getRefs());
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

        assertCallbackSegmentRef(traceSegment.getRefs());
    }

    @Test
    public void testCallbackWithCallbackAdapterInterceptor() throws Throwable {
        CallbackCache cacheForAdapter = new CallbackCache();
        cacheForAdapter.setSnapshot(MockContextSnapshot.INSTANCE.mockContextSnapshot());
        CallbackAdapterInterceptor callbackAdapterInterceptor = new CallbackAdapterInterceptor(cacheForAdapter);

        CallbackCache cache = new CallbackCache();
        cache.setCallback(callbackAdapterInterceptor);
        EnhancedInstance instance = new CallbackInstance(cache);
        callbackInterceptor.beforeMethod(instance, null, arguments, argumentTypes, null);
        callbackInterceptor.afterMethod(instance, null, arguments, argumentTypes, null);
    }

    @Test
    public void testCallbackWithNullCallbackCache() throws Throwable {
        EnhancedInstance instance = new CallbackInstance(null);
        callbackInterceptor.beforeMethod(instance, null, arguments, argumentTypes, null);
        callbackInterceptor.afterMethod(instance, null, arguments, argumentTypes, null);
        callbackInterceptor.handleMethodException(instance, null, arguments, argumentTypes, null);
    }

    private void assertCallbackSpanWithException(AbstractTracingSpan span) {
        assertCallbackSpan(span);

        SpanAssert.assertException(SpanHelper.getLogs(span).get(0), RuntimeException.class);
        assertThat(SpanHelper.getErrorOccurred(span), is(true));
    }

    private void assertCallbackSegmentRef(List<TraceSegmentRef> refs) {
        assertThat(refs.size(), is(1));

        TraceSegmentRef segmentRef = refs.get(0);
        SegmentRefAssert.assertSpanId(segmentRef, 1);
    }

    private void assertCallbackSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("Kafka/Producer/Callback"));
    }
}