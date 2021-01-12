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

package org.apache.skywalking.apm.plugin.undertow.v2x;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.util.KeyValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.List;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ForwardInterceptorTest {

    private ForwardInterceptor forwardInterceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    ServletRequest request;
    @Mock
    ServletResponse response;

    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Mock
    private EnhancedInstance enhancedInstance;

    private Object[] arguments;
    private Class[] argumentType;

    @Before
    public void setUp() throws Exception {
        forwardInterceptor = new ForwardInterceptor();
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn("http://localhost:8080/test/testRequestURL");
        arguments = new Object[] {
            request,
            response
        };
        argumentType = new Class[] {
            request.getClass(),
            response.getClass()
        };
    }

    @Test
    public void testWithoutSerializedContextData() throws Throwable {
        AbstractSpan span = ContextManager.createLocalSpan("/testForward");
        forwardInterceptor.onConstruct(enhancedInstance, arguments);
        forwardInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        forwardInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(span);
        assertThat(logDataEntities.size(), is(1));
        List<KeyValuePair> logs = logDataEntities.get(0).getLogs();
        assertThat(logs.size(), is(1));
        assertThat(logs.get(0).getKey(), is("forward-url"));
        assertThat(logs.get(0).getValue(), is("http://localhost:8080/test/testRequestURL"));

        assertThat(ContextManager.getRuntimeContext()
                                 .get(Constants.FORWARD_REQUEST_FLAG), CoreMatchers.<Object>is(true));
    }
}