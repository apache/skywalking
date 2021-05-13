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

import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ControllerInstrumentationTest {
    private ControllerInstrumentation controllerInstrumentation;

    @Before
    public void setUp() throws Exception {
        controllerInstrumentation = new ControllerInstrumentation();
    }

    @Test
    public void testGetEnhanceAnnotations() throws Throwable {
        Assert.assertArrayEquals(new String[] {ControllerInstrumentation.ENHANCE_ANNOTATION}, controllerInstrumentation.getEnhanceAnnotations());
    }

    @Test
    public void testGetInstanceMethodsInterceptPoints() throws Throwable {
        InstanceMethodsInterceptPoint[] methodPoints = controllerInstrumentation.getInstanceMethodsInterceptPoints();
        assertThat(methodPoints.length, is(2));
        assertThat(methodPoints[0].getMethodsInterceptor(), is("org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.RequestMappingMethodInterceptor"));
        assertThat(methodPoints[1].getMethodsInterceptor(), is("org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.RestMappingMethodInterceptor"));

        Assert.assertFalse(methodPoints[0].isOverrideArgs());
        Assert.assertFalse(methodPoints[1].isOverrideArgs());

        Assert.assertNotNull(methodPoints[0].getMethodsMatcher());
        Assert.assertNotNull(methodPoints[1].getMethodsMatcher());

    }

    @Test
    public void testGetConstructorsInterceptPoints() throws Throwable {
        ConstructorInterceptPoint[] cips = controllerInstrumentation.getConstructorsInterceptPoints();
        Assert.assertEquals(cips.length, 1);
        ConstructorInterceptPoint cip = cips[0];
        Assert.assertNotNull(cip);

        Assert.assertEquals(cip.getConstructorInterceptor(), "org.apache.skywalking.apm.plugin.spring.mvc.v4.ControllerConstructorInterceptor");
        Assert.assertTrue(cip.getConstructorMatcher().equals(ElementMatchers.any()));
    }
}
