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

package org.apache.skywalking.apm.plugin.graphql.v8;

import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStrategyParameters;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class GraphqlInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        ExecutionStrategyParameters parameters = (ExecutionStrategyParameters) allArguments[1];
        if (parameters == null) {
            return;
        }
        ExecutionPath path = parameters.getPath();
        Field field = ExecutionPath.class.getDeclaredField("parent");
        field.setAccessible(true);
        ExecutionPath parentPath = (ExecutionPath) field.get(path);
        if (parentPath != ExecutionPath.rootPath()) {
            return;
        }
        objInst.setSkyWalkingDynamicField(System.currentTimeMillis());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ExecutionStrategyParameters parameters = (ExecutionStrategyParameters) allArguments[1];
        if (parameters == null) {
            return ret;
        }
        ExecutionPath path = parameters.getPath();
        Field field = ExecutionPath.class.getDeclaredField("parent");
        field.setAccessible(true);
        ExecutionPath parentPath = (ExecutionPath) field.get(path);
        if (parentPath != ExecutionPath.rootPath()) {
            return ret;
        }
        String name = parameters.getField().get(0).getName();
        long latency = System.currentTimeMillis() - (long) objInst.getSkyWalkingDynamicField();
        String info = buildLogicEndpointTagInfo(name, latency, null);
        AbstractSpan span = ContextManager.firstSpan();
        if (span == null || !span.isEntry()) {
            return ret;
        }
        Tags.LOGIC_ENDPOINT.set(span, info);
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        ExecutionStrategyParameters parameters = (ExecutionStrategyParameters) allArguments[1];
        if (parameters == null) {
            return;
        }
        ExecutionPath path = parameters.getPath();
        try {
            Field field = ExecutionPath.class.getDeclaredField("parent");
            field.setAccessible(true);
            ExecutionPath parentPath = (ExecutionPath) field.get(path);
            if (parentPath != ExecutionPath.rootPath()) {
                return;
            }
            String name = parameters.getField().get(0).getName();
            long latency = System.currentTimeMillis() - (long) objInst.getSkyWalkingDynamicField();
            String info = buildLogicEndpointTagInfo(name, latency, t);
            AbstractSpan span = ContextManager.firstSpan();
            if (span == null || !span.isEntry()) {
                return;
            }
            Tags.LOGIC_ENDPOINT.set(span, info);
        } catch (NoSuchFieldException | IllegalAccessException e) {
        }
    }

    private String buildLogicEndpointTagInfo(String operationName, long latency, Throwable t) {
        Map<String, Object> logicEndpointInfo = new HashMap<>();
        logicEndpointInfo.put("name", operationName);
        logicEndpointInfo.put("latency", latency);
        logicEndpointInfo.put("status", t == null);
        return logicEndpointInfo.toString();
    }
}