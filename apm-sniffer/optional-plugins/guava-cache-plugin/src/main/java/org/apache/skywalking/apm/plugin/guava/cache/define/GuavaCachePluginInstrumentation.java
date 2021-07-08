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

package org.apache.skywalking.apm.plugin.guava.cache.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch.byMultiClassMatch;

public class GuavaCachePluginInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    public static final String INTERCEPT_LOCALMANUAL_CLASS = "com.google.common.cache.LocalCache$LocalManualCache";
    public static final String INTERCEPT_LOCALLOADING_CLASS = "com.google.common.cache.LocalCache$LocalLoadingCache";
    public static final String INTERCEPT_FORWARDING_CLASS = "com.google.common.cache.ForwardingCache";
    public static final String GET_ALL_PRESENT_ENHANCE_METHOD = "getAllPresent";
    public static final String INVALIDATE_ALL_ENHANCE_METHOD = "invalidateAll";
    public static final String GET_ENHANCE_METHOD = "get";
    public static final String INVALIDATE_ENHANCE_METHOD = "invalidate";
    public static final String PUT_ALL_ENHANCE_METHOD = "putAll";
    public static final String PUT_ENHANCE_METHOD = "put";
    public static final String GET_IF_PRESENT_ENHANCE_METHOD = "getIfPresent";
    public static final String GUAVA_CACHE_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.guava.cache.GuavaCacheInterceptor";
    public static final String GUAVA_CACHE_ALL_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.guava.cache.GuavaCacheAllInterceptor";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named(GET_ENHANCE_METHOD)
                                .or(named(INVALIDATE_ENHANCE_METHOD))
                                .or(named(PUT_ENHANCE_METHOD))
                                .or(named(GET_IF_PRESENT_ENHANCE_METHOD));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return GUAVA_CACHE_INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }

                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named(GET_ALL_PRESENT_ENHANCE_METHOD)
                                .or(named(INVALIDATE_ALL_ENHANCE_METHOD))
                                .or(named(PUT_ALL_ENHANCE_METHOD));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return GUAVA_CACHE_ALL_INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }

                }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return byMultiClassMatch(INTERCEPT_LOCALMANUAL_CLASS, INTERCEPT_LOCALLOADING_CLASS, INTERCEPT_FORWARDING_CLASS);
    }
}
