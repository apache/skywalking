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

package org.apache.skywalking.apm.plugin.spring.mvc.v4.define;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RestControllerInstrumentationTest {
    private RestControllerInstrumentation restControllerInstrumentation;

    @Before
    public void setUp() throws Exception {
        restControllerInstrumentation = new RestControllerInstrumentation();
    }

    @Test
    public void testGetEnhanceAnnotations() throws Throwable {
        Assert.assertArrayEquals(new String[] {restControllerInstrumentation.ENHANCE_ANNOTATION}, restControllerInstrumentation
            .getEnhanceAnnotations());
    }

    @Test
    public void testGetInstanceMethodsInterceptPoints() throws Throwable {
        InstanceMethodsInterceptPoint[] methodPoints = restControllerInstrumentation.getInstanceMethodsInterceptPoints();
        assertThat(methodPoints.length, is(2));
        assertThat(methodPoints[0].getMethodsInterceptor(), is("org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.RequestMappingMethodInterceptor"));
        assertThat(methodPoints[1].getMethodsInterceptor(), is("org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.RestMappingMethodInterceptor"));

        Assert.assertFalse(methodPoints[0].isOverrideArgs());
        Assert.assertFalse(methodPoints[1].isOverrideArgs());

        Assert.assertNotNull(methodPoints[0].getMethodsMatcher());
        Assert.assertNotNull(methodPoints[1].getMethodsMatcher());

    }
}
