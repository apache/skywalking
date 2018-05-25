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
import org.apache.skywalking.apm.plugin.spring.mvc.commons.EnhanceRequireObjectCache;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.GetBeanInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.web.context.request.NativeWebRequest;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GetBeanInterceptorTest {

    @Mock
    private EnhancedInstance enhancedInstance;

    @Mock
    private NativeWebRequest request;

    @Mock
    private EnhancedInstance enhanceRet;

    private GetBeanInterceptor interceptor;

    @Before
    public void setUp() {
        interceptor = new GetBeanInterceptor();

        when(enhanceRet.getSkyWalkingDynamicField()).thenReturn(new EnhanceRequireObjectCache());
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(request);
    }

    @Test
    public void testResultIsNotEnhanceInstance() throws Throwable {
        interceptor.afterMethod(enhancedInstance, null, null, null, new Object());

        verify(enhanceRet, times(0)).setSkyWalkingDynamicField(Matchers.any());
    }
}
