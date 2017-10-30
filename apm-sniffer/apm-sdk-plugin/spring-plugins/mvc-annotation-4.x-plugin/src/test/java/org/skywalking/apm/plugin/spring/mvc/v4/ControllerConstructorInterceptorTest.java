/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.spring.mvc.v4;

import java.lang.reflect.Method;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.springframework.web.bind.annotation.RequestMapping;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ControllerConstructorInterceptorTest {
    private ControllerConstructorInterceptor controllerConstructorInterceptor;
    private final MockEnhancedInstance1 inst1 = new MockEnhancedInstance1();
    private final MockEnhancedInstance2 inst2 = new MockEnhancedInstance2();
    private final MockEnhancedInstance3 inst3 = new MockEnhancedInstance3();

    @Before
    public void setUp() throws Exception {
        controllerConstructorInterceptor = new ControllerConstructorInterceptor();
    }

    @Test
    public void testOnConstruct_Accuracy1() throws Throwable {
        controllerConstructorInterceptor.onConstruct(inst1, null);
        PathMappingCache cache = (PathMappingCache)inst1.getSkyWalkingDynamicField();
        Assert.assertNotNull(cache);

        Object obj = new Object();
        Method m = obj.getClass().getMethods()[0];
        cache.addPathMapping(m, "#toString");

        Assert.assertEquals("the two value should be equal", cache.findPathMapping(m), "/test1#toString");
    }

    @Test
    public void testOnConstruct_Accuracy2() throws Throwable {
        controllerConstructorInterceptor.onConstruct(inst2, null);
        PathMappingCache cache = (PathMappingCache)inst2.getSkyWalkingDynamicField();
        Assert.assertNotNull(cache);

        Object obj = new Object();
        Method m = obj.getClass().getMethods()[0];
        cache.addPathMapping(m, "#toString");

        Assert.assertEquals("the two value should be equal", cache.findPathMapping(m), "#toString");
    }

    @Test
    public void testOnConstruct_Accuracy3() throws Throwable {
        controllerConstructorInterceptor.onConstruct(inst3, null);
        PathMappingCache cache = (PathMappingCache)inst3.getSkyWalkingDynamicField();
        Assert.assertNotNull(cache);

        Object obj = new Object();
        Method m = obj.getClass().getMethods()[0];
        cache.addPathMapping(m, "#toString");

        Assert.assertEquals("the two value should be equal", cache.findPathMapping(m), "/test3#toString");
    }

    @RequestMapping(value = "/test1")
    private class MockEnhancedInstance1 implements EnhancedInstance {
        private Object value;

        @Override
        public Object getSkyWalkingDynamicField() {
            return value;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.value = value;
        }
    }

    private class MockEnhancedInstance2 implements EnhancedInstance {
        private Object value;

        @Override
        public Object getSkyWalkingDynamicField() {
            return value;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.value = value;
        }
    }

    @RequestMapping(path = "/test3")
    private class MockEnhancedInstance3 implements EnhancedInstance {
        private Object value;

        @Override
        public Object getSkyWalkingDynamicField() {
            return value;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.value = value;
        }
    }
}
