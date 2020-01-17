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

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Methods;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.undertow.v2x.handler.TracingHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.lang.reflect.Method;

import static org.junit.Assert.assertTrue;

/**
 * @author chenpengfei
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RootHandlerInterceptorTest {

    private RootHandlerInterceptor rootHandlerInterceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private HttpHandler httpHandler;


    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Mock
    private EnhancedInstance enhancedInstance;


    @Before
    public void setUp() throws Exception {
        rootHandlerInterceptor = new RootHandlerInterceptor();
    }

    @Test
    public void testBindTracingHandler() throws Throwable {
        Object[] arguments = new Object[]{httpHandler};
        Class[] argumentType = new Class[]{HttpHandler.class};
        final Method method = Undertow.Builder.class.getMethod("setHandler", argumentType);
        rootHandlerInterceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, methodInterceptResult);
        rootHandlerInterceptor.afterMethod(enhancedInstance, method, arguments, argumentType, null);
        assertTrue(arguments[0] instanceof TracingHandler);
    }

    @Test
    public void testBindRoutingHandler() throws Throwable {
        RoutingHandler handler = new RoutingHandler();
        handler.add(Methods.GET, "/projects/{projectId}", httpHandler);
        Object[] arguments = new Object[]{handler};
        Class[] argumentType = new Class[]{HttpHandler.class};
        final Method method = Undertow.Builder.class.getMethod("setHandler", argumentType);
        rootHandlerInterceptor.beforeMethod(enhancedInstance, method, arguments, argumentType, methodInterceptResult);
        rootHandlerInterceptor.afterMethod(enhancedInstance, method, arguments, argumentType, null);
        assertTrue(arguments[0] instanceof RoutingHandler);
    }
}