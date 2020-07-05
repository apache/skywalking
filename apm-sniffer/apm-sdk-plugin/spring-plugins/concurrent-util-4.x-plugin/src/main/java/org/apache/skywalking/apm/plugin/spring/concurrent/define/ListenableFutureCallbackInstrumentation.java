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

package org.apache.skywalking.apm.plugin.spring.concurrent.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.plugin.spring.concurrent.match.ListenableFutureCallbackMatch;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link ListenableFutureCallbackInstrumentation} enhance <code>onSuccess</code> method and <code>oonFailure</code>
 * that class inherited <code>org.springframework.util.concurrent.ListenableFutureCallback</code> by
 * <code>SuccessCallbackInterceptor</code> and
 * <code>FailureCallbackInterceptor</code>.
 */
public class ListenableFutureCallbackInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
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
                    return named(SuccessCallbackInstrumentation.SUCCESS_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return SuccessCallbackInstrumentation.SUCCESS_CALLBACK_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(FailureCallbackInstrumentation.FAILURE_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return FailureCallbackInstrumentation.FAILURE_CALLBACK_INTERCEPTOR;
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
        return ListenableFutureCallbackMatch.listenableFutureCallbackMatch();
    }
}
