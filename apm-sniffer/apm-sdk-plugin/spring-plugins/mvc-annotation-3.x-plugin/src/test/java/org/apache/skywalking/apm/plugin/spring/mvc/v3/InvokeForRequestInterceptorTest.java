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


package org.apache.skywalking.apm.plugin.spring.mvc.v3;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.InvokeForRequestInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.web.context.request.NativeWebRequest;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class InvokeForRequestInterceptorTest {
    private InvokeForRequestInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Mock
    private NativeWebRequest nativeWebRequest;

    private Object argument[];

    @Before
    public void setUp() {
        interceptor = new InvokeForRequestInterceptor();
        argument = new Object[] {nativeWebRequest};
    }

    @Test
    public void testPassNativeWebRequest() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, null, argument, new Class[] {NativeWebRequest.class}, null);

        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField(Matchers.any());
    }
}
