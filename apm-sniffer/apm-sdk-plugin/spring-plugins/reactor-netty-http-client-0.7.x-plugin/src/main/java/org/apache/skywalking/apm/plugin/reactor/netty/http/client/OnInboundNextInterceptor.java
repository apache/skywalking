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

package org.apache.skywalking.apm.plugin.reactor.netty.http.client;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import java.lang.reflect.*;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.*;
import reactor.ipc.netty.channel.ChannelOperations;

public class OnInboundNextInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        if (allArguments[0] != null && allArguments[1] != null) {
            Object msg = allArguments[1];
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse)msg;
                HttpResponseStatus status = response.status();
                if (status != null && status.code() > 400) {
                    ContextManager.activeSpan().errorOccurred();
                    Tags.STATUS_CODE.set(ContextManager.activeSpan(), String.valueOf(status.code()));
                }
            }
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        Field channelField = ChannelOperations.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        ChannelId channelId = ((Channel)channelField.get(objInst)).id();
        HttpRequest request = (HttpRequest)ContextManager.getRuntimeContext().get(channelId.asLongText());
        if (request != null) {
            ContextManager.stopSpan();
            ContextManager.getRuntimeContext().remove(channelId.asLongText());
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
