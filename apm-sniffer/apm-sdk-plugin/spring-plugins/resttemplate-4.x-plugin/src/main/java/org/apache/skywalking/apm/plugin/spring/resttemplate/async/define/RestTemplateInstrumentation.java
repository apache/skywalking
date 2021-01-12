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

package org.apache.skywalking.apm.plugin.spring.resttemplate.async.define;

import java.net.URI;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link RestTemplateInstrumentation} enhance the <code>doExecute</code> method and <code>createAsyncRequest</code>
 * method of <code>org.springframework.web.client.AsyncRestTemplate</code> by <code>RestExecuteInterceptor</code> and
 * <code>org.springframework.http.client.RestRequestInterceptor</code>.
 *
 * <code>org.springframework.http.client.RestRequestInterceptor</code> set {@link URI} and {@link ContextSnapshot} to
 * <code>org.springframework.web.client.AsyncRestTemplate$ResponseExtractorFuture</code> for propagate trace context
 * after execute <code>doExecute</code> .
 */
public class RestTemplateInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.springframework.web.client.AsyncRestTemplate";
    private static final String DO_EXECUTE_METHOD_NAME = "doExecute";
    private static final String DO_EXECUTE_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.resttemplate.async.RestExecuteInterceptor";
    private static final String CREATE_REQUEST_METHOD_NAME = "createAsyncRequest";
    private static final String CREATE_REQUEST_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.resttemplate.async.RestRequestInterceptor";

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
        return NameMatch.byName(ENHANCE_CLASS);
    }
}
