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

package org.apache.skywalking.apm.plugin.jedis.v2;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import redis.clients.jedis.Client;
import redis.clients.jedis.Pipeline;

import java.lang.reflect.Method;

import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
public class PipelineSetClientMethodInterceptorTest {

    private PipelineSetClientMethodInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    private Object[] allArgument;

    private Class[] argumentType;

    @Before
    public void setUp() throws Exception {
        interceptor = new PipelineSetClientMethodInterceptor();
        allArgument = new Object[] {
                new Client("127.0.0.1", 6379)
        };
        argumentType = new Class[] {
                Client.class
        };
    }

    @Test
    public void onConstruct() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, getMockSetClientMethod(), allArgument, argumentType, null);

        verify(enhancedInstance).setSkyWalkingDynamicField("127.0.0.1:6379");
    }

    private Method getMockSetClientMethod() {
        try {
            return Pipeline.class.getMethod("setClient", Client.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }
}