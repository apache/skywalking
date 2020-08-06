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

package org.apache.skywalking.apm.plugin.thrift.async;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.thrift.async.TAsyncMethodCall;

/**
 * @see TAsyncMethodCall
 */
public class TAsyncMethodCallInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {
    private String operationName = null;
    private String remote = null;
    private AbstractSpan span;

    @Override
    public void onConstruct(final EnhancedInstance enhancedInstance, final Object[] objects) {
        // pattern: iface$AsyncClient$method_call -> iface$AsyncClient.method
        operationName = enhancedInstance.getClass().getName().replace("$AsyncClient$", "$AsyncClient.");
        operationName = operationName.substring(0, operationName.length() - 5); // cut off '_call'

        remote = "UNKNOWN";
        if (objects[2] instanceof EnhancedInstance) {
            remote = (String) ((EnhancedInstance) objects[2]).getSkyWalkingDynamicField();
        }
    }

    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        if ("prepareMethodCall".equals(method.getName())) {
            ContextCarrier carrier = new ContextCarrier();
            span = ContextManager.createExitSpan(operationName, carrier, remote);
            span.start(((TAsyncMethodCall) objInst).getTimeoutTimestamp());
            span.setComponent(ComponentsDefine.THRIFT_CLIENT);
            SpanLayer.asRPCFramework(span);

            Field[] declaredFields = objInst.getClass().getDeclaredFields();
            if (declaredFields.length == 1) {
                StringBuilder argumentsBuilder = new StringBuilder("(")
                    .append(declaredFields[0].getName())
                    .append(")");
                span.tag(new StringTag("params"), argumentsBuilder.toString());
            } else if (declaredFields.length > 1) {
                StringBuilder argumentsBuilder = new StringBuilder("(");
                for (final Field field : declaredFields) {
                    argumentsBuilder.append(field.getName()).append(", ");
                }
                argumentsBuilder.delete(argumentsBuilder.length() - 2, argumentsBuilder.length());
                argumentsBuilder.append(")");
                span.tag(new StringTag("params"), argumentsBuilder.toString());
            } else {
                span.tag(new StringTag("params"), "()");
            }
            span.prepareForAsync();
        }
    }

    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        if ("cleanUpAndFireCallback".equals(method.getName())) {
            span.asyncFinish();
        } else {
            ContextManager.stopSpan();
        }
        return ret;
    }

    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }

}
