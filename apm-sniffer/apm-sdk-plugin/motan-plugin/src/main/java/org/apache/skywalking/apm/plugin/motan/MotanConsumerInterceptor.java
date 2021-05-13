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

package org.apache.skywalking.apm.plugin.motan;

import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link MotanProviderInterceptor} create span by fetch request url from {@link EnhancedInstance#getSkyWalkingDynamicField()}
 * and transport serialized context data to provider side through {@link Request#setAttachment(String, String)}.
 */
public class MotanConsumerInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

        URL url = (URL) objInst.getSkyWalkingDynamicField();
        Request request = (Request) allArguments[0];
        if (url != null) {
            ContextCarrier contextCarrier = new ContextCarrier();
            String remotePeer = url.getHost() + ":" + url.getPort();
            AbstractSpan span = ContextManager.createExitSpan(generateOperationName(url, request), contextCarrier, remotePeer);
            span.setComponent(ComponentsDefine.MOTAN);
            Tags.URL.set(span, url.getIdentity());
            SpanLayer.asRPCFramework(span);
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                request.setAttachment(next.getHeadKey(), next.getHeadValue());
            }
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        Response response = (Response) ret;
        if (response != null && response.getException() != null) {
            AbstractSpan span = ContextManager.activeSpan();
            span.log(response.getException());
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(t);
    }

    /**
     * Generate operation name.
     *
     * @return operation name.
     */
    private static String generateOperationName(URL serviceURI, Request request) {
        return new StringBuilder(serviceURI.getPath()).append(".")
                                                      .append(request.getMethodName())
                                                      .append("(")
                                                      .append(request.getParamtersDesc())
                                                      .append(")")
                                                      .toString();
    }
}
