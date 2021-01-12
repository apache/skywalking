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

package org.apache.skywalking.apm.plugin.light4j;

import com.networknt.exception.ExceptionHandler;
import io.undertow.server.HttpServerExchange;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class HandleRequestInterceptorTest {

    private HandleRequestInterceptor handleRequestInterceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private MethodInterceptResult methodInterceptResult;

    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() throws Exception {
        handleRequestInterceptor = new HandleRequestInterceptor();

        enhancedInstance = new EnhancedInstance() {
            @Override
            public Object getSkyWalkingDynamicField() {
                return null;
            }

            @Override
            public void setSkyWalkingDynamicField(Object value) {

            }
        };
    }

    @Test
    public void testHandleRequest() throws Throwable {
        Method method = ExceptionHandler.class.getMethod("handleRequest", HttpServerExchange.class);

        handleRequestInterceptor.beforeMethod(enhancedInstance, method, null, null, methodInterceptResult);
        handleRequestInterceptor.afterMethod(enhancedInstance, null, null, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(0));
    }
}
