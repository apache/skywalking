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


package org.apache.skywalking.apm.toolkit.activation.trace;

import java.lang.reflect.Method;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class TraceAnnotationTest {

    @SegmentStoragePoint
    private SegmentStorage storage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    private TraceAnnotationMethodInterceptor methodInterceptor;
    private ActiveSpanTagInterceptor tagInterceptor;
    private Object[] tagParameters;
    private Class[] tagParameterTypes;

    @Before
    public void setUp() throws Exception {
        methodInterceptor = new TraceAnnotationMethodInterceptor();
        tagInterceptor = new ActiveSpanTagInterceptor();
        tagParameters = new Object[] {"testTagKey", "testTagValue"};
        tagParameterTypes = new Class[] {String.class, String.class};
    }

    @Test
    public void testTraceWithOperationName() throws Throwable {
        Method withOperationNameMethod = TestAnnotationMethodClass.class.getDeclaredMethod("testMethodWithOperationName");
        methodInterceptor.beforeMethod(enhancedInstance, withOperationNameMethod, null, null, null);
        tagInterceptor.beforeMethod(TestAnnotationMethodClass.class, withOperationNameMethod, tagParameters, tagParameterTypes, null);
        tagInterceptor.afterMethod(TestAnnotationMethodClass.class, withOperationNameMethod, tagParameters, tagParameterTypes, null);
        methodInterceptor.afterMethod(enhancedInstance, withOperationNameMethod, null, null, null);

        assertThat(storage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = storage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan tracingSpan = spans.get(0);
        assertThat(tracingSpan.getOperationName(), is("testMethod"));
        SpanAssert.assertLogSize(tracingSpan, 0);
        SpanAssert.assertTagSize(tracingSpan, 1);
        List<TagValuePair> tags = SpanHelper.getTags(tracingSpan);
        assertThat(tags.get(0).getKey().key(), is("testTagKey"));
        assertThat(tags.get(0).getValue(), is("testTagValue"));
    }

    @Test
    public void testTrace() throws Throwable {
        Method withOperationNameMethod = TestAnnotationMethodClass.class.getDeclaredMethod("testMethodWithDefaultValue");
        methodInterceptor.beforeMethod(enhancedInstance, withOperationNameMethod, null, null, null);
        methodInterceptor.afterMethod(enhancedInstance, withOperationNameMethod, null, null, null);

        assertThat(storage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = storage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan tracingSpan = spans.get(0);
        assertThat(tracingSpan.getOperationName(), is(TestAnnotationMethodClass.class.getName() + "." + withOperationNameMethod.getName() + "()"));
        SpanAssert.assertLogSize(tracingSpan, 0);
        SpanAssert.assertTagSize(tracingSpan, 0);
    }

    private class TestAnnotationMethodClass {
        @Trace(operationName = "testMethod")
        public void testMethodWithOperationName() {
        }

        @Trace
        public void testMethodWithDefaultValue() {
        }
    }
}
