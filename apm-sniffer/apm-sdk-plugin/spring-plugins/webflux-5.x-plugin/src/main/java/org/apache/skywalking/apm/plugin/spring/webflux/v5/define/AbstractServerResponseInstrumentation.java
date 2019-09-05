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

package org.apache.skywalking.apm.plugin.spring.webflux.v5.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch.byMultiClassMatch;

/**
 * @author zhaoyuguang
 */

public class AbstractServerResponseInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    protected ClassMatch enhanceClass() {
        return byMultiClassMatch("org.springframework.web.reactive.function.server.DefaultServerResponseBuilder$BodyInserterResponse",
                "org.springframework.web.reactive.function.server.DefaultServerResponseBuilder$BodyInserterServerResponse",
                "org.springframework.web.reactive.function.server.DefaultEntityResponseBuilder$DefaultEntityResponse");
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("writeToInternal");
                }

                @Override public String getMethodsInterceptor() {
                    return "org.apache.skywalking.apm.plugin.spring.webflux.v5.AbstractServerResponseMethodInterceptor";
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
