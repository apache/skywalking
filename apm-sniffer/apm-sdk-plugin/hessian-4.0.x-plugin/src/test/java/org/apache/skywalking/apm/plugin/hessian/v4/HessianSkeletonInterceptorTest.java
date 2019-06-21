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

package org.apache.skywalking.apm.plugin.hessian.v4;

import com.caucho.hessian.io.AbstractHessianInput;
import com.caucho.hessian.io.AbstractHessianOutput;
import com.caucho.hessian.server.HessianSkeleton;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * unit tests for hessian plugin
 *
 * @author Alan Lau
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest(HessianSkeleton.class)
public class HessianSkeletonInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Mock
    private String methodName;

    private HessianSkeletonInterceptor interceptor;

    @Mock
    private AbstractHessianInput input;

    @Mock
    private AbstractHessianOutput out;

    @Mock
    private MethodInterceptResult methodInterceptResult;

    private Object[] allArguments;
    private Class[] argumentTypes;

    @Mock
    private Object service;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {

        private Object object;

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override public void setSkyWalkingDynamicField(Object value) {
            this.object = value;
        }
    };

    @Before
    public void setUp() throws Exception {
        interceptor = new HessianSkeletonInterceptor();

        input = PowerMockito.mock(AbstractHessianInput.class);
        out = PowerMockito.mock(AbstractHessianOutput.class);

        service = PowerMockito.mock(Object.class);
        allArguments = new Object[] {service, input, out};
        argumentTypes = new Class[] {Object.class, AbstractHessianInput.class, AbstractHessianOutput.class};
    }

    @Test
    public void testWithoutSerializedContextData() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHessianSpan(spans.get(0));
    }

    @Test
    public void testWithSerializedContextData() throws Throwable {
//        when(request.getHeader(SW3CarrierItem.HEADER_NAME)).thenReturn("1.234.111|3|1|1|#192.168.1.8:18002|#/portal/|#/testEntrySpan|#AQA*#AQA*Et0We0tQNQA*");

        interceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);

        Assert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        Assert.assertThat(spans.size(), is(1));

        assertHessianSpan(spans.get(0));
    }

    @Test
    public void testWithErrorException() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        interceptor.handleMethodException(enhancedInstance, null, allArguments, argumentTypes, new RuntimeException());
        interceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, null);
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHessianSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertHessianSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is(service.getClass().getName()));
        assertComponent(span, ComponentsDefine.HESSIAN);
        SpanAssert.assertTag(span, 0, service.getClass().getName());
        assertThat(span.isEntry(), is(true));
        SpanAssert.assertLayer(span, SpanLayer.RPC_FRAMEWORK);
    }

}


