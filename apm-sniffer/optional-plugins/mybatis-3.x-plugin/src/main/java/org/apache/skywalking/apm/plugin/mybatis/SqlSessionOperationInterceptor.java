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

package org.apache.skywalking.apm.plugin.mybatis;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class SqlSessionOperationInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String MYBATIS_ENTRY_METHOD_NAME = "mybatis_entry_method_name";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        String operationName = MethodUtil.generateOperationName(method);
        AbstractSpan lastSpan = null;
        if (ContextManager.isActive()) {
            lastSpan = ContextManager.activeSpan();
        }
        boolean isMyBatisEntryMethod = !(lastSpan instanceof NoopSpan) && (lastSpan == null || lastSpan.getComponentId() != ComponentsDefine.MYBATIS.getId());
        if (isMyBatisEntryMethod) {
            AbstractSpan span = ContextManager.createLocalSpan(operationName);
            span.setComponent(ComponentsDefine.MYBATIS);
            if (allArguments != null && allArguments.length != 0) {
                Tags.MYBATIS_MAPPER.set(span, (String) allArguments[0]);
            }
            ContextManager.getRuntimeContext().put(MYBATIS_ENTRY_METHOD_NAME, operationName);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        String operationName = MethodUtil.generateOperationName(method);
        if (String.valueOf(ContextManager.getRuntimeContext().get(MYBATIS_ENTRY_METHOD_NAME)).equals(operationName)) {
            ContextManager.getRuntimeContext().remove(MYBATIS_ENTRY_METHOD_NAME);
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        String operationName = MethodUtil.generateOperationName(method);
        if (String.valueOf(ContextManager.getRuntimeContext().get(MYBATIS_ENTRY_METHOD_NAME)).equals(operationName)) {
            ContextManager.activeSpan().log(t);
        }
    }
}
