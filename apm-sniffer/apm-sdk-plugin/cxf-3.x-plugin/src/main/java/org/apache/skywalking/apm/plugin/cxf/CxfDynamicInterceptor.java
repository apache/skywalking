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
package org.apache.skywalking.apm.plugin.cxf;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CxfDynamicInterceptor implements InstanceMethodsAroundInterceptor {
    private static final ILog logger = LogManager.getLogger(CxfDynamicInterceptor.class);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {

        Object messageObject = allArguments[0];
        Message message = null;
        String operationName = "unknowOperationName";
        String remotePeer = "unknowRemotePeer";
        String inputName = "";
        Object endpoint_address = null;
        if (messageObject instanceof SoapMessage) {
            message = (SoapMessage) messageObject;
            MessageInfo messageInfo = (MessageInfo) message.get("org.apache.cxf.service.model.MessageInfo");
            OperationInfo operationInfo = messageInfo.getOperation();
            inputName = operationInfo.getInputName();
            endpoint_address = message.get(Message.ENDPOINT_ADDRESS);
            if (null != inputName && !"".equals(inputName)) {
                operationName = inputName;
            }
        } else if (messageObject instanceof MessageImpl) {
            message = (MessageImpl) messageObject;
            operationName = "get-remote-xxx?wsdl-info";
            endpoint_address = message.get(Message.ENDPOINT_ADDRESS);
        }

        if (message == null) {
            return;
        }

        if (null != endpoint_address) {
            remotePeer = endpoint_address.toString();
        }

        HashMap headsMap = (HashMap) message.get("org.apache.cxf.mime.headers");

        AbstractSpan span;
        final ContextCarrier contextCarrier = new ContextCarrier();
        span = ContextManager.createExitSpan(operationName, contextCarrier,
                remotePeer);
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (null == headsMap) {
                headsMap = new HashMap();
            }
            List<String> headerList = new ArrayList<String>();
            String headKey = next.getHeadKey();
            String headValue = next.getHeadValue();
            headerList.add(headValue);
            headsMap.put(headKey, headerList);
            message.put(Message.PROTOCOL_HEADERS, headsMap);
        }
        Tags.URL.set(span, operationName);
        if (messageObject instanceof SoapMessage) {
            SpanLayer.asRPCFramework(span);
        } else if (messageObject instanceof MessageImpl) {
            SpanLayer.asHttp(span);
        }


    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        dealException(t);
    }

    /**
     * Log the throwable, which occurs in Dubbo RPC service.
     */
    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.errorOccurred();
        span.log(throwable);
    }


}
