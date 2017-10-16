/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.grpc.v1;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.skywalking.apm.plugin.grpc.v1.vo.GRPCDynamicFields;
import org.skywalking.apm.util.StringUtil;

import static org.skywalking.apm.plugin.grpc.v1.define.Constants.STREAM_CALL_OPERATION_NAME_SUFFIX;
import static org.skywalking.apm.plugin.grpc.v1.define.Constants.BLOCK_CALL_OPERATION_NAME_SUFFIX;

/**
 * {@link ServerCallOnReadyInterceptor} create a entry span when the server side is ready for receive the message from
 * the client side.
 *
 * @author zhangxin
 */
public class ServerCallOnReadyInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        GRPCDynamicFields cachedObjects = (GRPCDynamicFields)objInst.getSkyWalkingDynamicField();
        Metadata headers = cachedObjects.getMetadata();
        Map<String, String> headerMap = new HashMap<String, String>();
        for (String key : headers.keys()) {
            if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                String value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
                headerMap.put(key, value);
            }
        }

        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            String contextValue = headerMap.get(next.getHeadKey());
            if (!StringUtil.isEmpty(contextValue)) {
                next.setHeadValue(contextValue);
            }
        }

        final AbstractSpan span = ContextManager.createEntrySpan(cachedObjects.getRequestMethodName() + (cachedObjects.getMethodType() != MethodDescriptor.MethodType.UNARY ? STREAM_CALL_OPERATION_NAME_SUFFIX : BLOCK_CALL_OPERATION_NAME_SUFFIX), contextCarrier);
        span.setComponent(ComponentsDefine.GRPC);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
