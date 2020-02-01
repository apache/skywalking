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
package org.apache.skywalking.apm.agent.core.plugin.bytebuddy;

import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.Assert;
import org.junit.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.MethodInheritanceAnnotationMatcher.byMethodInheritanceAnnotationMatcher;

/**
 * @author jialong
 */
public class MethodInheritanceAnnotationMatcherTest {

    @Test
    public void testMatch() throws Exception {
        ElementMatcher.Junction<AnnotationSource> matcher = byMethodInheritanceAnnotationMatcher(named("org.apache.skywalking.apm.agent.core.plugin.bytebuddy.MethodInheritanceAnnotationMatcherTest$TestAnnotaion"));

        Assert.assertTrue(matcher.matches(new MethodDescription.ForLoadedMethod(TestBean.class.getMethod("test1", String.class))));
        Assert.assertTrue(matcher.matches(new MethodDescription.ForLoadedMethod(TestBean.class.getMethod("test2", String.class))));
        Assert.assertTrue(matcher.matches(new MethodDescription.ForLoadedMethod(TestBean.class.getMethod("test3", String.class))));
    }

    private class TestBean implements TestInterface1, TestInterface3 {
        @Override
        public String test1(String test) {
            return null;
        }

        @Override
        public String test2(String test) {
            return null;
        }

        @Override
        public String test3(String test) {
            return null;
        }
    }

    private interface TestInterface1 extends TestInterface2 {
        @TestAnnotaion
        String test1(String test);
    }

    private interface TestInterface2 {
        @TestAnnotaion
        String test2(String test);
    }

    private interface TestInterface3 {
        @TestAnnotaion
        String test3(String test);
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface TestAnnotaion {
    }

}
