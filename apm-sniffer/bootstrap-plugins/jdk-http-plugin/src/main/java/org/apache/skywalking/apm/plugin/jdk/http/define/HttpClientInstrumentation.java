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

package org.apache.skywalking.apm.plugin.jdk.http.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.DeclaredInstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;

public class HttpClientInstrumentation extends ClassEnhancePluginDefine {

    private static final String ENHANCE_HTTP_CLASS = "sun.net.www.http.HttpClient";

    private static final String AFTER_METHOD = "parseHTTP";

    private static final String BEFORE_METHOD = "writeRequests";

    private static final String NEW_INSTANCE_METHOD = "New";

    private static final String INTERCEPT_PARSE_HTTP_CLASS = "org.apache.skywalking.apm.plugin.jdk.http.HttpClientParseHttpInterceptor";

    private static final String INTERCEPT_WRITE_REQUEST_CLASS = "org.apache.skywalking.apm.plugin.jdk.http.HttpClientWriteRequestInterceptor";

    private static final String INTERCEPT_HTTP_NEW_INSTANCE_CLASS = "org.apache.skywalking.apm.plugin.jdk.http.HttpClientNewInstanceInterceptor";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new DeclaredInstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(AFTER_METHOD);
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_PARSE_HTTP_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new DeclaredInstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(BEFORE_METHOD).and(takesArguments(2).or(takesArguments(1)));
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_WRITE_REQUEST_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(NEW_INSTANCE_METHOD).and(takesArguments(5).and(takesArgumentWithType(0, "java.net.URL"))
                                                                           .and(takesArgumentWithType(4, "sun.net.www.protocol.http.HttpURLConnection")));
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_HTTP_NEW_INSTANCE_CLASS;
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
        return NameMatch.byName(ENHANCE_HTTP_CLASS);
    }

    @Override
    public boolean isBootstrapInstrumentation() {
        return true;
    }
}