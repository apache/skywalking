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

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.DeclaredInstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassAnnotationMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.MethodInheritanceAnnotationMatcher.byMethodInheritanceAnnotationMatcher;

/**
 * {@link ControllerInstrumentation} enhance all constructor and method annotated with
 * <code>org.springframework.web.bind.annotation.RequestMapping</code> that class has
 * <code>org.springframework.stereotype.Controller</code> annotation.
 *
 * <code>ControllerConstructorInterceptor</code> set the controller base path to
 * dynamic field before execute constructor.
 *
 * <code>org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.RequestMappingMethodInterceptor</code> get the request path
 * from dynamic field first, if not found, <code>RequestMappingMethodInterceptor</code> generate request path  that
 * combine the path value of current annotation on current method and the base path and set the new path to the dynamic
 * filed
 */
public abstract class AbstractControllerInstrumentation extends AbstractSpring4Instrumentation {
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
                    return "org.apache.skywalking.apm.plugin.spring.mvc.v4.ControllerConstructorInterceptor";
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
                    return byMethodInheritanceAnnotationMatcher(named("org.springframework.web.bind.annotation.RequestMapping"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REQUEST_MAPPING_METHOD_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new DeclaredInstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return byMethodInheritanceAnnotationMatcher(named("org.springframework.web.bind.annotation.GetMapping"))
                        .or(byMethodInheritanceAnnotationMatcher(named("org.springframework.web.bind.annotation.PostMapping")))
                        .or(byMethodInheritanceAnnotationMatcher(named("org.springframework.web.bind.annotation.PutMapping")))
                        .or(byMethodInheritanceAnnotationMatcher(named("org.springframework.web.bind.annotation.DeleteMapping")))
                        .or(byMethodInheritanceAnnotationMatcher(named("org.springframework.web.bind.annotation.PatchMapping")));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.REST_MAPPING_METHOD_INTERCEPTOR;
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
        return ClassAnnotationMatch.byClassAnnotationMatch(getEnhanceAnnotations());
    }

    protected abstract String[] getEnhanceAnnotations();

}
