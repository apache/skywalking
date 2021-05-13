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

package org.apache.skywalking.apm.plugin.thrift.client;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.AsyncSpan;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.thrift.commons.ReflectionUtils;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncMethodCall;

/**
 * Here is asynchronized client.
 */
public class TAsyncMethodCallInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {
    private String remotePeer = "UNKNOWN";

    @Override
    public void onConstruct(EnhancedInstance objInst,
                            Object[] allArguments) throws NoSuchFieldException, IllegalAccessException {
        ReflectionUtils.setValue(TAsyncMethodCall.class, objInst, "callback", new AsyncMethodCallback<Object>() {
            final AsyncMethodCallback<Object> callback = (AsyncMethodCallback) allArguments[3];

            @Override
            public void onComplete(final Object response) {
                try {
                    AsyncSpan span = (AsyncSpan) objInst.getSkyWalkingDynamicField();
                    span.asyncFinish();
                } finally {
                    callback.onComplete(response);
                }
            }

            @Override
            public void onError(final Exception exception) {
                try {
                    AsyncSpan span = (AsyncSpan) objInst.getSkyWalkingDynamicField();
                    span.asyncFinish().log(exception);
                } finally {
                    callback.onError(exception);
                }
            }
        });
        if (allArguments[2] instanceof EnhancedInstance) {
            remotePeer = (String) ((EnhancedInstance) allArguments[2]).getSkyWalkingDynamicField();
        }
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst,
                             Method method,
                             Object[] allArguments,
                             Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        AbstractSpan span = ContextManager.createExitSpan(objInst.getClass().getName(), remotePeer);
        span.setComponent(ComponentsDefine.THRIFT_CLIENT);
        SpanLayer.asRPCFramework(span);

        AbstractSpan async = span.prepareForAsync();
        objInst.setSkyWalkingDynamicField(async);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst,
                              Method method,
                              Object[] allArguments,
                              Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst,
                                      Method method,
                                      Object[] allArguments,
                                      Class<?>[] argumentsTypes,
                                      Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}