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


package org.apache.skywalking.apm.plugin.rocketMQ.v4.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.plugin.rocketMQ.v4.MessageSendInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link MQClientAPIImplInstrumentation} intercepts the {@link org.apache.rocketmq.client.impl.MQClientAPIImpl#sendMessage(String,
 * String, org.apache.rocketmq.common.message.Message, org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader,
 * long, org.apache.rocketmq.client.impl.CommunicationMode, org.apache.rocketmq.client.producer.SendCallback,
 * org.apache.rocketmq.client.impl.producer.TopicPublishInfo, org.apache.rocketmq.client.impl.factory.MQClientInstance,
 * int, org.apache.rocketmq.client.hook.SendMessageContext, org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl)}
 * method by using {@link MessageSendInterceptor}.
 *
 * @author zhang xin
 */
public class MQClientAPIImplInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.apache.rocketmq.client.impl.MQClientAPIImpl";
    private static final String SEND_MESSAGE_METHOD_NAME = "sendMessage";
    private static final String ASYNC_METHOD_INTERCEPTOR = "org.apache.skywalking.apm.plugin.rocketMQ.v4.MessageSendInterceptor";
    public static final String UPDATE_NAME_SERVER_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.rocketMQ.v4.UpdateNameServerInterceptor";
    public static final String UPDATE_NAME_SERVER_METHOD_NAME = "updateNameServerAddressList";

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(SEND_MESSAGE_METHOD_NAME).and(takesArguments(12));
                }

                @Override public String getMethodsInterceptor() {
                    return ASYNC_METHOD_INTERCEPTOR;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(UPDATE_NAME_SERVER_METHOD_NAME);
                }

                @Override public String getMethodsInterceptor() {
                    return UPDATE_NAME_SERVER_INTERCEPT_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override protected ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }
}
