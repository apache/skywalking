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

package org.apache.skywalking.apm.plugin.spring.patch;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.aop.framework.AdvisedSupport;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;

@RunWith(MockitoJUnitRunner.class)
public class CreateAopProxyInterceptorTest {

    private CreateAopProxyInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Mock
    private AdvisedSupport advisedSupport;

    @Before
    public void setUp() {
        interceptor = new CreateAopProxyInterceptor();

    }

    @Test
    public void testInterceptNormalObject() throws Throwable {
        doReturn(Object.class).when(advisedSupport).getTargetClass();
        assertThat(false, is(interceptor.afterMethod(enhancedInstance, null, new Object[] {advisedSupport}, new Class[] {Object.class}, false)));
    }

    @Test
    public void testInterceptEnhanceInstanceObject() throws Throwable {
        doReturn(MockClass.class).when(advisedSupport).getTargetClass();
        assertThat(true, is(interceptor.afterMethod(enhancedInstance, null, new Object[] {advisedSupport}, new Class[] {Object.class}, false)));
    }

    private class MockClass implements EnhancedInstance {

        @Override
        public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {

        }
    }

}
