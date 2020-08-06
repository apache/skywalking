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

package org.apache.skywalking.apm.plugin.thrift;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.RuntimeContext;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;

public abstract class AbstractProcessorInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {

    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        final RuntimeContext context = ContextManager.getRuntimeContext();
        context.put("prefix", objInst.getSkyWalkingDynamicField());
        context.put("processor", this);
    }

    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        if (ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        ContextManager.getRuntimeContext().remove("prefix");
        ContextManager.getRuntimeContext().remove("processor");
        return ret;
    }

    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().errorOccurred().log(t);
        }
    }

    public String getArgumentNames(String method) {
        List<String> arguments = Lists.newArrayList();
        TBase<?, ?> base = getFunction(method);
        for (int i = 1; ; i++) {
            TFieldIdEnum field = base.fieldForId(i);
            if (field == null) {
                break;
            }
            arguments.add(field.getFieldName());
        }
        if (arguments.isEmpty()) {
            return "()";
        }
        return "(" + Joiner.on(", ").join(arguments) + ")";
    }

    protected abstract TBase<?, ?> getFunction(String method);
}
