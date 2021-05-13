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

import java.lang.reflect.Field;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.EnhanceRequireObjectCache;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.PathMappingCache;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(PowerMockRunner.class)
public class ControllerConstructorInterceptorTest {

    private ControllerConstructorInterceptor interceptor;

    private MockRequestMappingObject mappingObject;

    private MockRequestMappingObjectWithoutRequestMapping withoutRequestMapping;

    @Before
    public void setUp() {
        mappingObject = new MockRequestMappingObject();
        withoutRequestMapping = new MockRequestMappingObjectWithoutRequestMapping();
        interceptor = new ControllerConstructorInterceptor();
    }

    @Test
    public void testClassAnnotationWithRequestMapping() throws NoSuchFieldException, IllegalAccessException {
        interceptor.onConstruct(mappingObject, null);

        assertThat("/test", is(getBasePath(mappingObject.requireObjectCache.getPathMappingCache())));
    }

    @Test
    public void testClassAnnotationWithoutRequestMapping() throws NoSuchFieldException, IllegalAccessException {
        interceptor.onConstruct(withoutRequestMapping, null);

        assertThat("", is(getBasePath(withoutRequestMapping.requireObjectCache.getPathMappingCache())));
    }

    private String getBasePath(PathMappingCache mappingCache) throws NoSuchFieldException, IllegalAccessException {
        Field classPath = mappingCache.getClass().getDeclaredField("classPath");
        classPath.setAccessible(true);
        return (String) classPath.get(mappingCache);
    }

    @RequestMapping("/test")
    private class MockRequestMappingObject implements EnhancedInstance {
        private EnhanceRequireObjectCache requireObjectCache;

        @RequestMapping("/test")
        private void mockTestMethod() {

        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return requireObjectCache;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.requireObjectCache = (EnhanceRequireObjectCache) value;
        }
    }

    private class MockRequestMappingObjectWithoutRequestMapping implements EnhancedInstance {
        private EnhanceRequireObjectCache requireObjectCache;

        private void mockTestMethod() {

        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return requireObjectCache;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.requireObjectCache = (EnhanceRequireObjectCache) value;
        }
    }
}
