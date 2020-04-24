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

package org.apache.skywalking.apm.plugin.spring.mvc.v3.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.DeclaredInstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassAnnotationMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.MethodInheritanceAnnotationMatcher.byMethodInheritanceAnnotationMatcher;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REQUEST_MAPPING_METHOD_INTERCEPTOR;

/**
 * {@link ControllerInstrumentation} intercept the constructor and the methods annotated with {@link
 * org.springframework.web.bind.annotation.RequestMapping} in the class annotated with {@link
 * org.springframework.stereotype.Controller}.
 */
public class ControllerInstrumentation extends AbstractSpring3Instrumentation {
    public static final String CONTROLLER_ENHANCE_ANNOTATION = "org.springframework.stereotype.Controller";
    public static final String CONSTRUCTOR_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.mvc.v3.ControllerConstructorInterceptor";
    public static final String REQUEST_MAPPING_ENHANCE_ANNOTATION = "org.springframework.web.bind.annotation.RequestMapping";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return any();
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_INTERCEPTOR;
                }
            }
        };
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new DeclaredInstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return byMethodInheritanceAnnotationMatcher(named(REQUEST_MAPPING_ENHANCE_ANNOTATION));
                }

                @Override
                public String getMethodsInterceptor() {
                    return REQUEST_MAPPING_METHOD_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return ClassAnnotationMatch.byClassAnnotationMatch(new String[] {CONTROLLER_ENHANCE_ANNOTATION});
    }

}
