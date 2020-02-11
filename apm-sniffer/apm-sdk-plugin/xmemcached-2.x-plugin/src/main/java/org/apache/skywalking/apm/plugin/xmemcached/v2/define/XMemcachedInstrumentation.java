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

package org.apache.skywalking.apm.plugin.xmemcached.v2.define;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * {@link XMemcachedInstrumentation} presents that skywalking intercept all constructors and methods of {@link
 * net.rubyeye.xmemcached.XMemcachedClient}.
 */
public class XMemcachedInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "net.rubyeye.xmemcached.XMemcachedClient";
    private static final String CONSTRUCTOR_WITH_HOSTPORT_ARG_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.xmemcached.v2.XMemcachedConstructorWithHostPortArgInterceptor";
    private static final String CONSTRUCTOR_WITH_INETSOCKETADDRESS_ARG_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.xmemcached.v2.XMemcachedConstructorWithInetSocketAddressArgInterceptor";
    private static final String CONSTRUCTOR_WITH_INETSOCKETADDRESS_LIST_ARG_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.xmemcached.v2.XMemcachedConstructorWithInetSocketAddressListArgInterceptor";
    private static final String CONSTRUCTOR_WITH_COMPLEX_ARG_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.xmemcached.v2.XMemcachedConstructorWithComplexArgInterceptor";
    private static final String METHOD_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.xmemcached.v2.XMemcachedMethodInterceptor";

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
                    return takesArguments(String.class, int.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_HOSTPORT_ARG_INTERCEPT_CLASS;
                }
            },
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(0, InetSocketAddress.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_INETSOCKETADDRESS_ARG_INTERCEPT_CLASS;
                }
            },
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(0, List.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_INETSOCKETADDRESS_LIST_ARG_INTERCEPT_CLASS;
                }
            },
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(6, Map.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_COMPLEX_ARG_INTERCEPT_CLASS;
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
                    return named("get").or(named("set"))
                                       .or(named("add"))
                                       .or(named("replace"))
                                       .or(named("gets"))
                                       .or(named("append"))
                                       .or(named("prepend"))
                                       .or(named("cas"))
                                       .or(named("delete"))
                                       .or(named("touch"))
                                       .
                                           or(named("getAndTouch"))
                                       .or(named("incr"))
                                       .or(named("decr"));
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
