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

package org.apache.skywalking.apm.toolkit.activation.opentracing.tracer;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link SkywalkingTracerActivation} defines two interceptors to enhance the methods in class
 * <code>SkywalkingTracer</code>.
 * <p>
 * 1. The <code>SkywalkingTracerInjectInterceptor</code> interceptor enhance the <code>extract</code> method
 * <p>
 * 2. The <code>SkywalkingTracerExtractInterceptor</code> interceptor enhance the <code>inject</code> method
 **/
public class SkywalkingTracerActivation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.apache.skywalking.apm.toolkit.opentracing.SkywalkingTracer";
    private static final String INJECT_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.opentracing.tracer.SkywalkingTracerInjectInterceptor";
    private static final String EXTRACT_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.opentracing.tracer.SkywalkingTracerExtractInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

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
                    return named("inject");
                }

                @Override
                public String getMethodsInterceptor() {
                    return INJECT_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("extract");
                }

                @Override
                public String getMethodsInterceptor() {
                    return EXTRACT_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
