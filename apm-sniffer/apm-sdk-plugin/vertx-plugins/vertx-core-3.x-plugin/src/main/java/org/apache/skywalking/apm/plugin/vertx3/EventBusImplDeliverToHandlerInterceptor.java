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

package org.apache.skywalking.apm.plugin.vertx3;

import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.impl.clustered.ClusteredMessage;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class EventBusImplDeliverToHandlerInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    @SuppressWarnings("rawtypes")
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        ContextManager.getRuntimeContext().remove(VertxContext.STOP_SPAN_NECESSARY + "." + getClass().getName());

        Message message = (Message) allArguments[0];
        boolean isFromWire = message instanceof ClusteredMessage && ((ClusteredMessage) message).isFromWire();
        if (!isFromWire && VertxContext.hasContext(message.address())) {
            VertxContext context = VertxContext.popContext(message.address());
            context.getSpan().asyncFinish();
        } else if (!isFromWire) {
            AbstractSpan span;
            if (VertxContext.hasContext(message.replyAddress())) {
                VertxContext context = VertxContext.peekContext(message.replyAddress());
                span = ContextManager.createLocalSpan(context.getContextSnapshot().getParentEndpoint());
                ContextManager.continued(context.getContextSnapshot());
            } else {
                span = ContextManager.createLocalSpan(message.address());
            }
            span.setComponent(ComponentsDefine.VERTX);
            SpanLayer.asRPCFramework(span);

            if (message.replyAddress() != null) {
                VertxContext.pushContext(
                    message.replyAddress(), new VertxContext(ContextManager.capture(), span.prepareForAsync()));
            }
            ContextManager.getRuntimeContext().put(VertxContext.STOP_SPAN_NECESSARY + "." + getClass().getName(), true);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        Boolean closeSpan = (Boolean) ContextManager.getRuntimeContext()
                                                    .get(VertxContext.STOP_SPAN_NECESSARY + "." + getClass().getName());
        if (Boolean.TRUE.equals(closeSpan)) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
