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
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.thrift.wrapper.ClientOutProtocolWrapper;
import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.transport.TTransport;

/**
 * @see TServiceClient
 */
public class TServiceClientInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {
    private static final ILog logger = LogManager.getLogger(TServiceClientInterceptor.class);

    @Override
    public void onConstruct(EnhancedInstance instance, Object[] arguments) {
        TServiceClient client = (TServiceClient) instance;
        TTransport transport = client.getInputProtocol().getTransport();

        String peer = "localhost";
        if (transport instanceof EnhancedInstance) {
            peer = (String) ((EnhancedInstance) transport).getSkyWalkingDynamicField();
        }
        // pattern: iface$Client -> iface$Client.
        instance.setSkyWalkingDynamicField(new ThriftInstance(peer, client.getClass().getName()));

        try {
            Field oprotField = TServiceClient.class.getDeclaredField("oprot_");
            oprotField.setAccessible(true);
            oprotField.set(instance, new ClientOutProtocolWrapper(client.getOutputProtocol()));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error("Hijacking the Protocol failed.", e);
        }
    }

    @Override
    public void beforeMethod(EnhancedInstance instance, Method method, Object[] objects, Class<?>[] classes,
                             MethodInterceptResult result) throws Throwable {

        if ("sendBase".equals(method.getName()) && !ContextManager.isActive()) {
            ThriftInstance thrift = (ThriftInstance) instance.getSkyWalkingDynamicField();
            TServiceClient serviceClient = (TServiceClient) instance;

            ContextCarrier carrier = new ContextCarrier();
            AbstractSpan span = ContextManager.createExitSpan(
                thrift.operationNamePrefix + objects[0], carrier, thrift.peer);
            span.setComponent(ComponentsDefine.THRIFT_SERVER);
            SpanLayer.asRPCFramework(span);

            List<String> arguments = Lists.newArrayList();
            TBase base = (TBase) objects[1];
            for (int i = 1; ; i++) {
                TFieldIdEnum field = base.fieldForId(i);
                if (field == null) {
                    break;
                }
                arguments.add(field.getFieldName());
            }
            if (arguments.isEmpty()) {
                span.tag(new StringTag("params"), "()");
            } else {
                span.tag(new StringTag("params"), "(" + Joiner.on(", ").join(arguments) + ")");
            }

            ClientOutProtocolWrapper protocol = (ClientOutProtocolWrapper) serviceClient.getOutputProtocol();
            protocol.inject(carrier);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance instance, Method method, Object[] objects, Class<?>[] classes,
                              Object result) throws Throwable {
        if (!"sendBase".equals(method.getName()) && ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        return result;
    }

    @Override
    public void handleMethodException(EnhancedInstance instance, Method method, Object[] objects, Class<?>[] classes,
                                      Throwable throwable) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().errorOccurred().log(throwable);
        }
    }
}
