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
 */

package org.apache.skywalking.apm.plugin.httpasyncclient.v4.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * this is a bridge for main thread and real request thread which mean hold the {@link
 * org.apache.skywalking.apm.agent.core.context.ContextSnapshot} object to be continued in "completed" method.that is
 * mean the request is ready to submit
 */
public class SessionRequestInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String CONSTRUCTOR_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestConstructorInterceptor";
    private static final String COMPLETED_METHOD = "completed";
    private static final String TIMEOUT_METHOD = "timeout";
    private static final String FAILED_METHOD = "failed";
    private static final String CANCEL_METHOD = "cancel";
    private static final String SUCCESS_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestCompleteInterceptor";
    private static final String FAIL_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestFailInterceptor";
    private static final String ENHANCE_CLASS = "org.apache.http.impl.nio.reactor.SessionRequestImpl";

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
                    return CONSTRUCTOR_INTERCEPTOR_CLASS;
                }
            }
        };
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(COMPLETED_METHOD);
                }

                @Override
                public String getMethodsInterceptor() {
                    return SUCCESS_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(TIMEOUT_METHOD).or(named(FAILED_METHOD).or(named(CANCEL_METHOD)));
                }

                @Override
                public String getMethodsInterceptor() {
                    return FAIL_INTERCEPTOR_CLASS;
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
