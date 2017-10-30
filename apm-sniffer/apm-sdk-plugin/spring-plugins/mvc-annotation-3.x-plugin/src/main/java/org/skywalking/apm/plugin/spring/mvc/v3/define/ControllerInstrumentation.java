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

package org.skywalking.apm.plugin.spring.mvc.v3.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.skywalking.apm.agent.core.plugin.match.ClassAnnotationMatch.byClassAnnotationMatch;

/**
 * {@link ControllerInstrumentation} intercept the constructor and the methods annotated with {@link
 * org.springframework.web.bind.annotation.RequestMapping} in the class annotated with {@link
 * org.springframework.stereotype.Controller}.
 *
 * @author zhangxin
 */
public class ControllerInstrumentation extends AbstractSpring3Instrumentation {
    public static final String CONTROLLER_ENHANCE_ANNOTATION = "org.springframework.stereotype.Controller";
    public static final String CONSTRUCTOR_INTERCEPTOR = "org.skywalking.apm.plugin.spring.mvc.v3.ControllerConstructorInterceptor";
    public static final String REQUEST_MAPPING_ENHANCE_ANNOTATION = "org.springframework.web.bind.annotation.RequestMapping";
    public static final String REQUEST_MAPPING_METHOD_INTERCEPTOR = "org.skywalking.apm.plugin.spring.mvc.v3.ControllerMethodInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
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
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return isAnnotatedWith(named(REQUEST_MAPPING_ENHANCE_ANNOTATION));
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
        return byClassAnnotationMatch(new String[] {CONTROLLER_ENHANCE_ANNOTATION});
    }

}
