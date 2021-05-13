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

package org.apache.skywalking.apm.plugin.pulsar.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * The pulsar consumer instrumentation use {@link org.apache.pulsar.client.impl.ConsumerImpl} as an enhanced class.
 * {@link org.apache.pulsar.client.api.Consumer} is a user-oriented interface and the implementations are {@link
 * org.apache.pulsar.client.impl.ConsumerImpl} and {@link org.apache.pulsar.client.impl.MultiTopicsConsumerImpl}
 * <p>
 * The MultiTopicsConsumerImpl is a complex type with multiple ConsumerImpl to support uses receive messages from
 * multiple topics. As each ConsumerImpl has it's own topic name and it is the initial unit of a single topic to
 * receiving messages, so use ConsumerImpl as an enhanced class is an effective way.
 * <p>
 * Use <code>messageProcessed</code> as the enhanced method since pulsar consumer has multiple ways to receiving
 * messages such as sync method, async method and listeners. Method messageProcessed is a basic unit of ConsumerImpl, no
 * matter which way uses uses, messageProcessed will always record the message receiving.
 */
public class PulsarConsumerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    public static final String CONSTRUCTOR_INTERCEPT_TYPE = "org.apache.pulsar.client.impl.PulsarClientImpl";
    public static final String CONSTRUCTOR_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.pulsar.ConsumerConstructorInterceptor";
    public static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.pulsar.PulsarConsumerInterceptor";
    public static final String ENHANCE_METHOD = "messageProcessed";
    public static final String ENHANCE_METHOD_TYPE = "org.apache.pulsar.client.api.Message";
    public static final String ENHANCE_CLASS = "org.apache.pulsar.client.impl.ConsumerImpl";

    @Override
    protected ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgumentWithType(0, CONSTRUCTOR_INTERCEPT_TYPE);
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
                    return named(ENHANCE_METHOD).and(takesArgumentWithType(0, ENHANCE_METHOD_TYPE));
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
