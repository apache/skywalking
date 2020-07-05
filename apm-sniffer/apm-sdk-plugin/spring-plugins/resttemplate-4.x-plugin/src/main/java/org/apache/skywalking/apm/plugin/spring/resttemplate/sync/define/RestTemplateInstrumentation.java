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

package org.apache.skywalking.apm.plugin.spring.resttemplate.sync.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link RestTemplateInstrumentation} enhance the <code>doExecute</code> method,<code>handleResponse</code> method and
 * <code>handleResponse</code> method of <code>org.springframework.web.client.RestTemplate</code> by
 * <code>RestExecuteInterceptor</code>,
 * <code>RestResponseInterceptor</code> and
 * <code>RestRequestInterceptor</code>.
 *
 * <code>RestResponseInterceptor</code> set context to  header for
 * propagate trace context after execute <code>createRequest</code>.
 */
public class RestTemplateInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.springframework.web.client.RestTemplate";
    private static final String DO_EXECUTE_METHOD_NAME = "doExecute";
    private static final String DO_EXECUTE_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.resttemplate.sync.RestExecuteInterceptor";
    private static final String HANDLE_REQUEST_METHOD_NAME = "handleResponse";
    private static final String HAND_REQUEST_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.resttemplate.sync.RestResponseInterceptor";
    private static final String CREATE_REQUEST_METHOD_NAME = "createRequest";
    private static final String CREATE_REQUEST_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.resttemplate.sync.RestRequestInterceptor";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(DO_EXECUTE_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return DO_EXECUTE_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(HANDLE_REQUEST_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return HAND_REQUEST_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(CREATE_REQUEST_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return CREATE_REQUEST_INTERCEPTOR;
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
        return byName(ENHANCE_CLASS);
    }
}
