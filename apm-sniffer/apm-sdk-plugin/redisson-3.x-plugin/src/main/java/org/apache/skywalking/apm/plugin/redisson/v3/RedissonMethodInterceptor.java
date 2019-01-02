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


package org.apache.skywalking.apm.plugin.redisson.v3;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;

/**
 * @author zhaoyuguang
 */
public class RedissonMethodInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        RedisConnection connection = (RedisConnection) objInst;
        String peer = "";
        if (connection.getRedisClient() != null) {
            Field field = RedisClient.class.getDeclaredField("uri");
            field.setAccessible(true);
            URI uri = (URI) field.get(connection.getRedisClient());
            if (uri != null) {
                peer = uri.getHost() + ":" + uri.getPort();
            }
        }

        StringBuilder command = new StringBuilder();
        if (allArguments[0] instanceof CommandsData) {
            CommandsData commands = (CommandsData) allArguments[0];
            for (CommandData commandData : commands.getCommands()) {
                command.append(commandData.getCommand().getName() + "&");
            }
            command.deleteCharAt(command.length() - 1);
        }
        if (allArguments[0] instanceof CommandData) {
            command.append(((CommandData) allArguments[0]).getCommand().getName());
        }

        AbstractSpan span = ContextManager.createExitSpan("Redisson/" + command, peer);
        span.setComponent(ComponentsDefine.REDISSON);
        Tags.DB_TYPE.set(span, "Redis");
        SpanLayer.asCache(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.errorOccurred();
        span.log(t);
    }
}
