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

package org.apache.skywalking.apm.plugin.spring.patch;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.aop.MethodMatcher;
import org.springframework.util.ReflectionUtils;

@RunWith(MockitoJUnitRunner.class)
public class AopExpressionMatchInterceptorTest {

    private AopExpressionMatchInterceptor interceptor;
    @Mock
    private MethodMatcher methodMatcher;

    @Before
    public void setUp() throws Exception {
        interceptor = new AopExpressionMatchInterceptor();
    }

    @Test
    public void beforeMethod() {
    }

    @Test
    public void afterMethod() {
        Object ret = interceptor.afterMethod(Object.class, null, new Object[] {
            methodMatcher,
            ReflectionUtils.findMethod(MockClass.class, "getSkyWalkingDynamicField"),
            MockClass.class,
            false
        }, new Class[0], true);
        Assert.assertEquals(false, ret);
    }

    @Test
    public void handleMethodException() {
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