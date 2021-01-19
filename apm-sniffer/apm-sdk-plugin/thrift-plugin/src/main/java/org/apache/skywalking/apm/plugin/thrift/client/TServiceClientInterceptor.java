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

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.thrift.commons.ReflectionUtils;
import org.apache.skywalking.apm.plugin.thrift.wrapper.ClientOutProtocolWrapper;
import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TProtocol;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Here is synchronized client.
 *
 * @see TServiceClient
 */
public class TServiceClientInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {
    private static final StringTag TAG_ARGS = new StringTag("args");

    @Override
    public void onConstruct(EnhancedInstance objInst,
                            Object[] allArguments) throws NoSuchFieldException, IllegalAccessException {
        if (!(allArguments[1] instanceof ClientOutProtocolWrapper)) {
            TProtocol protocol = (TProtocol) allArguments[1];
            ReflectionUtils.setValue(
                    TServiceClient.class,
                    objInst,
                    "oprot_",
                    new ClientOutProtocolWrapper(protocol)
            );
            Object dynamicField = ((EnhancedInstance) protocol.getTransport()).getSkyWalkingDynamicField();
            objInst.setSkyWalkingDynamicField(Objects.isNull(dynamicField) ? "UNKNOWN" : dynamicField);
        }
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst,
                             Method method,
                             Object[] allArguments,
                             Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        AbstractSpan span = ContextManager.createExitSpan(
                objInst.getClass().getName() + "." + allArguments[0],
                (String) objInst.getSkyWalkingDynamicField()
        );
        SpanLayer.asRPCFramework(span);
        span.setComponent(ComponentsDefine.THRIFT_CLIENT);
        span.tag(TAG_ARGS, getArguments((String) allArguments[0], (TBase) allArguments[1]));
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst,
                              Method method,
                              Object[] allArguments,
                              Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst,
                                      Method method,
                                      Object[] allArguments,
                                      Class<?>[] argumentsTypes,
                                      Throwable t) {
        ContextManager.activeSpan().log(t);
    }

    private String getArguments(String method, TBase base) {
        int idx = 0;
        StringBuilder buffer = new StringBuilder(method).append("(");
        while (true) {
            TFieldIdEnum field = base.fieldForId(++idx);
            if (field == null) {
                idx--;
                break;
            }
            buffer.append(field.getFieldName()).append(", ");
        }
        if (idx > 0) {
            buffer.delete(buffer.length() - 2, buffer.length());
        }
        return buffer.append(")").toString();
    }
}