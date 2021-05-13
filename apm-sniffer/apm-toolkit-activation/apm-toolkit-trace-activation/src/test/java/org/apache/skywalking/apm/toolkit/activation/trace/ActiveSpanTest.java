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
 */

package org.apache.skywalking.apm.toolkit.activation.trace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ActiveSpanTest {

    private TraceAnnotationMethodInterceptor methodInterceptor;
    private ActiveSpanErrorInterceptor activeSpanErrorInterceptor;
    private ActiveSpanErrorMsgInterceptor activeSpanErrorMsgInterceptor;
    private ActiveSpanErrorThrowableInteceptor activeSpanErrorThrowableInteceptor;
    private ActiveSpanInfoInterceptor activeSpanInfoInterceptor;

    @Mock
    private EnhancedInstance enhancedInstance;
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @SegmentStoragePoint
    private SegmentStorage storage;

    private Object[] tagParametersMsg;
    private Class[] tagParameterTypesMsg;

    private Object[] tagParametersThrowable;
    private Class[] tagParameterTypesThrowable;

    @Before
    public void setUp() throws Exception {
        methodInterceptor = new TraceAnnotationMethodInterceptor();
        activeSpanErrorInterceptor = new ActiveSpanErrorInterceptor();
        activeSpanErrorMsgInterceptor = new ActiveSpanErrorMsgInterceptor();
        activeSpanErrorThrowableInteceptor = new ActiveSpanErrorThrowableInteceptor();
        activeSpanInfoInterceptor = new ActiveSpanInfoInterceptor();

        tagParametersMsg = new Object[] {"testMsgValue"};
        tagParameterTypesMsg = new Class[] {String.class};

        tagParametersThrowable = new Object[] {new RuntimeException("test-Throwable")};
        tagParameterTypesThrowable = new Class[] {Throwable.class};
    }

    @Test
    public void testActiveSpanError() throws Throwable {
        Method withOperationNameMethod = MockActiveSpan.class.getDeclaredMethod("testErrorMethod");
        methodInterceptor.beforeMethod(enhancedInstance, withOperationNameMethod, null, null, null);
        activeSpanErrorMsgInterceptor.beforeMethod(MockActiveSpan.class, withOperationNameMethod, tagParametersMsg, tagParameterTypesMsg, null);
        activeSpanErrorMsgInterceptor.afterMethod(MockActiveSpan.class, withOperationNameMethod, tagParametersMsg, tagParameterTypesMsg, null);
        methodInterceptor.afterMethod(enhancedInstance, withOperationNameMethod, null, null, null);

        assertThat(storage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = storage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan tracingSpan = spans.get(0);
        Field field = AbstractTracingSpan.class.getDeclaredField("errorOccurred");
        field.setAccessible(true);
        assertTrue(field.getBoolean(tracingSpan));
        SpanAssert.assertLogSize(tracingSpan, 1);
        SpanAssert.assertTagSize(tracingSpan, 0);
    }

    @Test
    public void testActiveSpanErrorNoMsg() throws Throwable {
        Method withOperationNameMethod = MockActiveSpan.class.getDeclaredMethod("testErrorNoMsgMethod");
        methodInterceptor.beforeMethod(enhancedInstance, withOperationNameMethod, null, null, null);
        activeSpanErrorInterceptor.beforeMethod(MockActiveSpan.class, withOperationNameMethod, null, null, null);
        activeSpanErrorInterceptor.afterMethod(MockActiveSpan.class, withOperationNameMethod, null, null, null);
        methodInterceptor.afterMethod(enhancedInstance, withOperationNameMethod, null, null, null);

        assertThat(storage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = storage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan tracingSpan = spans.get(0);
        Field field = AbstractTracingSpan.class.getDeclaredField("errorOccurred");
        field.setAccessible(true);
        assertTrue(field.getBoolean(tracingSpan));
        SpanAssert.assertLogSize(tracingSpan, 0);
        SpanAssert.assertTagSize(tracingSpan, 0);
    }

    @Test
    public void testActiveSpanErrorThrowable() throws Throwable {
        Method withOperationNameMethod = MockActiveSpan.class.getDeclaredMethod("testErrorThrowableMethod");
        methodInterceptor.beforeMethod(enhancedInstance, withOperationNameMethod, null, null, null);
        activeSpanErrorThrowableInteceptor.beforeMethod(MockActiveSpan.class, withOperationNameMethod, tagParametersThrowable, tagParameterTypesThrowable, null);
        activeSpanErrorThrowableInteceptor.afterMethod(MockActiveSpan.class, withOperationNameMethod, tagParametersThrowable, tagParameterTypesThrowable, null);
        methodInterceptor.afterMethod(enhancedInstance, withOperationNameMethod, null, null, null);

        assertThat(storage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = storage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        AbstractTracingSpan tracingSpan = spans.get(0);
        Field field = AbstractTracingSpan.class.getDeclaredField("errorOccurred");
        field.setAccessible(true);
        assertTrue(field.getBoolean(tracingSpan));
        SpanAssert.assertLogSize(tracingSpan, 1);
        SpanAssert.assertTagSize(tracingSpan, 0);
    }

    @Test
    public void testActiveSpanInfo() throws Throwable {
        Method withOperationNameMethod = MockActiveSpan.class.getDeclaredMethod("testInfoMethod");
        methodInterceptor.beforeMethod(enhancedInstance, withOperationNameMethod, null, null, null);
        activeSpanInfoInterceptor.beforeMethod(MockActiveSpan.class, withOperationNameMethod, tagParametersThrowable, tagParameterTypesThrowable, null);
        activeSpanInfoInterceptor.afterMethod(MockActiveSpan.class, withOperationNameMethod, tagParametersThrowable, tagParameterTypesThrowable, null);
        methodInterceptor.afterMethod(enhancedInstance, withOperationNameMethod, null, null, null);

        assertThat(storage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = storage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan tracingSpan = spans.get(0);
        SpanAssert.assertLogSize(tracingSpan, 1);
        SpanAssert.assertTagSize(tracingSpan, 0);
    }

    private class MockActiveSpan {
        @Trace
        public void testErrorMethod() {
            ActiveSpan.error("testValue");
        }

        @Trace
        public void testInfoMethod() {
            ActiveSpan.info("testValue");
        }

        @Trace
        public void testErrorNoMsgMethod() {
            ActiveSpan.error();
        }

        @Trace
        public void testErrorThrowableMethod() {
            ActiveSpan.error(new RuntimeException("test-Throwable"));
        }
    }
}
