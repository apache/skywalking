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

package org.apache.skywalking.apm.plugin.spymemcached.v2.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * {@link MemcachedInstrumentation} presents that skywalking intercept all constructors and methods of {@link
 * net.spy.memcached.MemcachedClient}.
 * <code>MemcachedConstructorWithInetSocketAddressListArgInterceptor</code> intercepts the constructor with
 * argument {@link java.net.InetSocketAddress}.
 */
public class MemcachedInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "net.spy.memcached.MemcachedClient";
    private static final String CONSTRUCTOR_WITH_INETSOCKETADDRESS_LIST_ARG_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.spymemcached.v2.MemcachedConstructorWithInetSocketAddressListArgInterceptor";
    private static final String METHOD_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.spymemcached.v2.MemcachedMethodInterceptor";

    @Override
    public ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(1, List.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_INETSOCKETADDRESS_LIST_ARG_INTERCEPT_CLASS;
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
                    return named("touch").or(named("append"))
                                         .or(named("prepend"))
                                         .or(named("asyncCAS"))
                                         .or(named("cas"))
                                         .or(named("add"))
                                         .or(named("set"))
                                         .or(named("replace"))
                                         .or(named("asyncGet"))
                                         .or(named("asyncGets"))
                                         .or(named("gets"))
                                         .or(named("getAndTouch"))
                                         .or(named("get"))
                                         .or(named("asyncGetBulk"))
                                         .or(named("asyncGetAndTouch"))
                                         .or(named("getBulk"))
                                         .or(named("getStats"))
                                         .or(named("incr"))
                                         .or(named("decr"))
                                         .or(named("asyncIncr"))
                                         .or(named("asyncDecr"))
                                         .or(named("delete"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return METHOD_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
