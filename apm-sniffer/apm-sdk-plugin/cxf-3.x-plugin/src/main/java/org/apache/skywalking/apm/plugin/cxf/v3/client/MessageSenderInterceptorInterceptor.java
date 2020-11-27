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

package org.apache.skywalking.apm.plugin.cxf.v3.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * Used to intercept client requests and transparently transmit trace header and other information
 */
public class MessageSenderInterceptorInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        Message message = (Message) allArguments[0];
        final String httpRequestMethod = (String) message.get(Message.HTTP_REQUEST_METHOD);
        final MessageInfo messageInfo = (MessageInfo) message.get("org.apache.cxf.service.model.MessageInfo");
        final Address address = (Address) message.get("http.connection.address");

        if (null == httpRequestMethod || null == messageInfo || null == address) {
            return;
        }
        final String operationName = generateOperationName(messageInfo, address);
        AbstractSpan span = ContextManager.createExitSpan(operationName, address.getURI().getAuthority());

        ContextCarrier contextCarrier = new ContextCarrier();
        ContextManager.inject(contextCarrier);
        span.setComponent(ComponentsDefine.APACHE_CXF);
        Tags.HTTP.METHOD.set(span, httpRequestMethod);
        Tags.URL.set(span, generateRequestURL(messageInfo, address));
        SpanLayer.asRPCFramework(span);

        //Set trace headers.
        Map protocolHeaders = (Map) message.get(Message.PROTOCOL_HEADERS);

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            List<String> headerList = new ArrayList<>(1);
            headerList.add(next.getHeadValue());
            protocolHeaders.put(next.getHeadKey(), headerList);
        }
    }

    private String generateOperationName(MessageInfo messageInfo, Address address) {
        return address.getURI().getPath() + "/" + messageInfo.getOperation().getInputName();
    }

    private String generateRequestURL(MessageInfo messageInfo, Address address) {
        return address.getString() + "/" + messageInfo.getOperation().getInputName();
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst,
                              final Method method,
                              final Object[] allArguments,
                              final Class<?>[] argumentsTypes,
                              final Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst,
                                      final Method method,
                                      final Object[] allArguments,
                                      final Class<?>[] argumentsTypes,
                                      final Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
