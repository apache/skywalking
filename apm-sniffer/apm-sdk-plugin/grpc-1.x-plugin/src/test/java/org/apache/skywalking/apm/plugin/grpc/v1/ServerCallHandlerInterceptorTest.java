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


package org.apache.skywalking.apm.plugin.grpc.v1;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(MethodDescriptor.class)
public class ServerCallHandlerInterceptorTest {
    @Mock
    private EnhancedInstance enhancedInstance;

    private ServerCallHandlerInterceptor callHandlerInterceptor;

    @Mock
    private ServerCall serverCall;
    @Mock
    private MethodDescriptor methodDescriptor;

    private Metadata metadata;

    private Object[] arguments;
    private Class[] argumentTypes;

    @Before
    public void setUp() {
        when(methodDescriptor.getFullMethodName()).thenReturn("org.skywalking.test.GreetService/SayHello");
        when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);

        callHandlerInterceptor = new ServerCallHandlerInterceptor();
        metadata = new Metadata();
        arguments = new Object[] {serverCall, metadata};
        argumentTypes = new Class[] {serverCall.getClass(), metadata.getClass()};
    }

    @Test
    public void testSetCachedObjects() throws Throwable {
        callHandlerInterceptor.afterMethod(null, null, arguments, argumentTypes, enhancedInstance);
        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField(Matchers.any());
    }
}
