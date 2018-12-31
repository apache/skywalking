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
package org.apache.skywalking.apm.plugin.springrabbit.define;

import com.rabbitmq.client.Channel;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch;
import org.apache.skywalking.apm.plugin.springrabbit.RabbitListenerInterceptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;

/**
 * @author jjlu521016@gmail.com
 * for spring-rabbit 2.x {@link AbstractMessageListenerContainer#actualInvokeListener(Channel,Message)};
 * for spring-rabbit 1.x {@link AbstractMessageListenerContainer#invokeListener(Channel, Message)};
 */
public class RabbitMQListenerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    /**
     * {@link RabbitListenerInterceptor}
     */
    public static final String INTERCEPTOR_TLISTENER_CLASS = "org.apache.skywalking.apm.plugin.springrabbit.RabbitListenerInterceptor";
    public static final String ENHANCE_CLASS_COMSUMER = "org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer";
    // spring-rabbit 2.x method
    public static final String ACTUALINVOKELISTENER_METHOD = "actualInvokeListener";
    // spring-rabbit 1.x method
    public static final String INVOKELISTENER_METHOD = "invokeListener";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
            InstanceMethodsInterceptPoint(ACTUALINVOKELISTENER_METHOD),
            //for spring-rabbit 1.x ,if not use this ,can't aspect !!
            InstanceMethodsInterceptPoint(INVOKELISTENER_METHOD),
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return MultiClassNameMatch.byMultiClassMatch(ENHANCE_CLASS_COMSUMER);
    }

    /**
     * gen InstanceMethodsInterceptPoint object
     * @param methodName
     * @return
     */
    private InstanceMethodsInterceptPoint InstanceMethodsInterceptPoint(final String methodName) {
        return  new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named(methodName)
                        .and(takesArgumentWithType(0, "com.rabbitmq.client.Channel"))
                        .and(takesArgumentWithType(1, "org.springframework.amqp.core.Message"));
            }

            @Override
            public String getMethodsInterceptor() {
                return INTERCEPTOR_TLISTENER_CLASS;
            }

            @Override
            public boolean isOverrideArgs() {
                return true;
            }
        };
    }
}