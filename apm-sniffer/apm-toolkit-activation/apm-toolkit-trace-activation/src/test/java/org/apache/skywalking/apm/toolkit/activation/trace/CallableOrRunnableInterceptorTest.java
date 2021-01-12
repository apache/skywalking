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

import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class CallableOrRunnableInterceptorTest {

    private CallableOrRunnableConstructInterceptor constructorInterceptor;

    private CallableOrRunnableInvokeInterceptor callableCallInterceptor;
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {

        private Object object;

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.object = value;
        }
    };

    @Mock
    private ContextSnapshot contextSnapshot;

    private Object[] arguments;

    private Method callMethod;

    @Before
    public void setUp() throws NoSuchMethodException {
        constructorInterceptor = new CallableOrRunnableConstructInterceptor();
        callableCallInterceptor = new CallableOrRunnableInvokeInterceptor();

        Callable<String> call = new Callable<String>() {
            @Override
            public String call() throws Exception {
                return null;
            }
        };
        callMethod = call.getClass().getMethod("call");
        arguments = new Object[0];
    }

    @Test
    public void testOnConstructor() {
        constructorInterceptor.onConstruct(enhancedInstance, null);
        Assert.assertNull(enhancedInstance.getSkyWalkingDynamicField());
    }

    @Test
    public void testCall() throws Throwable {

        enhancedInstance.setSkyWalkingDynamicField(contextSnapshot);
        callableCallInterceptor.beforeMethod(enhancedInstance, callMethod, arguments, null, null);
        callableCallInterceptor.afterMethod(enhancedInstance, callMethod, arguments, null, "result");

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

    }

}
