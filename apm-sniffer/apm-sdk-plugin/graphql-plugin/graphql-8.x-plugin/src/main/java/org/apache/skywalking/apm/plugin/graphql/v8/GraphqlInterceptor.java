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
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GraphqlInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
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
            AbstractSpan span = ContextManager.createLocalSpan(parameters.getField().get(0).getName());
            Tags.LOGIC_ENDPOINT.set(span, Tags.VAL_LOCAL_SPAN_AS_LOGIC_ENDPOINT);
            span.setComponent(ComponentsDefine.GRAPHQL);
        } catch (NoSuchFieldException | IllegalAccessException e) {
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ExecutionStrategyParameters parameters = (ExecutionStrategyParameters) allArguments[1];
        if (parameters == null) {
            return ret;
        }
        ExecutionPath path = parameters.getPath();
        try {
            Field field = ExecutionPath.class.getDeclaredField("parent");
            field.setAccessible(true);
            ExecutionPath parentPath = (ExecutionPath) field.get(path);
            if (!parentPath.equals(ExecutionPath.rootPath())) {
                return ret;
            }
            ContextManager.stopSpan();
        } catch (NoSuchFieldException | IllegalAccessException e) {
        }
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
            if (!parentPath.equals(ExecutionPath.rootPath())) {
                return;
            }
            dealException(t);
        } catch (NoSuchFieldException | IllegalAccessException e) {
        }
    }

    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(throwable);
    }
}
