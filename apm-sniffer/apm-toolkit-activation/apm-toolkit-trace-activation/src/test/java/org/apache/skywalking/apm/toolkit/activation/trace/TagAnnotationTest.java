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
import org.apache.skywalking.apm.agent.core.context.ContextManager;
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
import org.apache.skywalking.apm.toolkit.trace.Tag;
import org.apache.skywalking.apm.toolkit.trace.Tags;
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
public class TagAnnotationTest {

    @SegmentStoragePoint
    private SegmentStorage storage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    private TagAnnotationMethodInterceptor methodInterceptor;
    private ActiveSpanTagInterceptor tagInterceptor;
    private Object[] tagParameters;
    private Class[] tagParameterTypes;

    @Before
    public void setUp() throws Exception {
        methodInterceptor = new TagAnnotationMethodInterceptor();
        tagInterceptor = new ActiveSpanTagInterceptor();
        tagParameters = new Object[] {"testTagKey", "testTagValue"};
        tagParameterTypes = new Class[] {String.class, String.class};

        String operationName = "testMethod";
        ContextManager.createLocalSpan(operationName);
    }

    @Test
    public void testTraceWithTag() throws Throwable {
        Method testMethodWithTag = TestAnnotationMethodClass.class.getDeclaredMethod("testMethodWithTag", String.class);
        methodInterceptor.beforeMethod(enhancedInstance, testMethodWithTag, new Object[]{"zhangsan"}, null, null);
        methodInterceptor.afterMethod(enhancedInstance, testMethodWithTag, null, null, null);
        ContextManager.stopSpan();
        assertThat(storage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = storage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan tracingSpan = spans.get(0);
        assertThat(tracingSpan.getOperationName(), is("testMethod"));
        SpanAssert.assertLogSize(tracingSpan, 0);
        SpanAssert.assertTagSize(tracingSpan, 1);
        List<TagValuePair> tags = SpanHelper.getTags(tracingSpan);
        assertThat(tags.get(0).getKey().key(), is("username"));
        assertThat(tags.get(0).getValue(), is("zhangsan"));
    }

    @Test
    public void testTraceWithReturnTag() throws Throwable {
        Method testMethodWithTag = TestAnnotationMethodClass.class.getDeclaredMethod("testMethodWithReturnTag", String.class, Integer.class);
        methodInterceptor.beforeMethod(enhancedInstance, testMethodWithTag, new Object[]{"lisi", 14}, null, null);
        methodInterceptor.afterMethod(enhancedInstance, testMethodWithTag, null, null, new User("lisi", 14));
        ContextManager.stopSpan();
        assertThat(storage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = storage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan tracingSpan = spans.get(0);
        assertThat(tracingSpan.getOperationName(), is("testMethod"));
        SpanAssert.assertLogSize(tracingSpan, 0);
        SpanAssert.assertTagSize(tracingSpan, 1);
        List<TagValuePair> tags = SpanHelper.getTags(tracingSpan);

        assertThat(tags.get(0).getKey().key(), is("username"));
        assertThat(tags.get(0).getValue(), is("lisi"));

    }

    @Test
    public void testTraceWithTags() throws Throwable {
        Method testMethodWithTags = TestAnnotationMethodClass.class.getDeclaredMethod("testMethodWithTags", String.class, Integer.class);
        methodInterceptor.beforeMethod(enhancedInstance, testMethodWithTags, new Object[]{"lisi", 14}, null, null);
        methodInterceptor.afterMethod(enhancedInstance, testMethodWithTags, null, null, new User("lisi", 14));
        ContextManager.stopSpan();
        assertThat(storage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = storage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        AbstractTracingSpan tracingSpan = spans.get(0);
        assertThat(tracingSpan.getOperationName(), is("testMethod"));
        SpanAssert.assertLogSize(tracingSpan, 0);
        SpanAssert.assertTagSize(tracingSpan, 2);
        List<TagValuePair> tags = SpanHelper.getTags(tracingSpan);

        assertThat(tags.get(0).getKey().key(), is("username"));
        assertThat(tags.get(0).getValue(), is("lisi"));
        assertThat(tags.get(1).getKey().key(), is("info"));
        assertThat(tags.get(1).getValue(), is("username=lisi,age=14"));

    }

    private class TestAnnotationMethodClass {

        @Tag(key = "username", value = "arg[0]")
        public void testMethodWithTag(String username) {
        }

        @Tag(key = "username", value = "returnedObj.username")
        public User testMethodWithReturnTag(String username, Integer age) {
            return new User(username, age);
        }

        @Tags({@Tag(key = "username", value = "arg[0]"), @Tag(key = "info", value = "returnedObj.info")})
        public User testMethodWithTags(String username, Integer age) {
            return new User(username, age);
        }
    }

    private class User {
        private String username;
        private Integer age;
        private String info;

        public User(String username, Integer age) {
            this.username = username;
            this.age = age;
            info = String.format("username=%s,age=%s", username, age);
        }
    }
}
